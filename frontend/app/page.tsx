type LogItem = {
  timestamp: string;
  level: string;
  service: string;
  message: string;
};

type LogSummary = {
  totalLogs: number;
  errorCount: number;
  warnCount: number;
  infoCount: number;
};

const baseUrl = process.env.NEXT_PUBLIC_API_URL?.replace(/\/$/, "");

if (!baseUrl) {
  throw new Error("NEXT_PUBLIC_API_URL is not defined");
}

async function getLogs(level?: string): Promise<LogItem[]> {
  const url = level ? `${baseUrl}/logs?level=${level}` : `${baseUrl}/logs`;

  const res = await safeFetch(url);
  return res.json();
}

async function getSummary(): Promise<LogSummary> {
  const res = await safeFetch(`${baseUrl}/logs/summary`);

  return res.json();
}

function getBadgeClass(level: string) {
  switch (level.toUpperCase()) {
    case "ERROR":
      return "bg-red-100 text-red-700";
    case "WARN":
      return "bg-yellow-100 text-yellow-700";
    case "INFO":
      return "bg-blue-100 text-blue-700";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

// this function retries to send a request at most 5 times
// delay = 1000 = 1s
async function safeFetch(
  url: string,
  options?: RequestInit,
  retries = 5,
  delay = 1000
) {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      const response = await fetch(url, {
        ...options,
        cache: "no-store",
      });

      if (!response.ok) {
        throw new Error(`Request failed with status ${response.status}`);
      }

      return response;
    } catch (error) {
      if (attempt === retries) {
        throw error;
      }

      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }

  throw new Error("Unexpected fetch failure");
}

export default async function Home({
  searchParams,
}: {
  searchParams?: Promise<{ level?: string }>;
}) {
  const params = await searchParams;
  const selectedLevel = params?.level;

  const [logs, summary] = await Promise.all([
    getLogs(selectedLevel),
    getSummary(),
  ]);

  const filters = ["ALL", "ERROR", "WARN", "INFO"];

  return (
    <main className="min-h-screen bg-gray-50 px-6 py-10">
      <div className="mx-auto max-w-7xl">
        <section className="mb-8">
          <h1 className="text-3xl font-bold tracking-tight text-gray-900">
            OpsLens Dashboard
          </h1>
          <p className="mt-2 text-sm text-gray-600">
            Log monitoring and summary view for backend services.
          </p>
        </section>

        <section className="mb-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <p className="text-sm text-gray-500">Total Logs</p>
            <p className="mt-2 text-2xl font-semibold text-gray-900">
              {summary.totalLogs}
            </p>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <p className="text-sm text-gray-500">Errors</p>
            <p className="mt-2 text-2xl font-semibold text-red-600">
              {summary.errorCount}
            </p>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <p className="text-sm text-gray-500">Warnings</p>
            <p className="mt-2 text-2xl font-semibold text-yellow-600">
              {summary.warnCount}
            </p>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
            <p className="text-sm text-gray-500">Info</p>
            <p className="mt-2 text-2xl font-semibold text-blue-600">
              {summary.infoCount}
            </p>
          </div>
        </section>

        <section className="mb-6 flex flex-wrap gap-2">
          {filters.map((filter) => {
            const isActive =
              (filter === "ALL" && !selectedLevel) || selectedLevel === filter;

            const href =
              filter === "ALL" ? "/" : `/?level=${encodeURIComponent(filter)}`;

            return (
              <a
                key={filter}
                href={href}
                className={`rounded-md border px-3 py-2 text-sm font-medium transition ${
                  isActive
                    ? "border-gray-900 bg-gray-900 text-white"
                    : "border-gray-300 bg-white text-gray-700 hover:bg-gray-100"
                }`}
              >
                {filter}
              </a>
            );
          })}
        </section>

        <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="border-b border-gray-200 px-5 py-4">
            <h2 className="text-lg font-semibold text-gray-900">Logs</h2>
            <p className="mt-1 text-sm text-gray-500">
              {selectedLevel
                ? `Showing ${selectedLevel} logs`
                : "Showing all logs"}
            </p>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-gray-50 text-gray-600">
                <tr>
                  <th className="px-5 py-3 font-medium">Time</th>
                  <th className="px-5 py-3 font-medium">Level</th>
                  <th className="px-5 py-3 font-medium">Service</th>
                  <th className="px-5 py-3 font-medium">Message</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {logs.map((log, index) => (
                  <tr key={index} className="hover:bg-gray-50">
                    <td className="whitespace-nowrap px-5 py-4 text-gray-600">
                      {log.timestamp}
                    </td>
                    <td className="px-5 py-4">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${getBadgeClass(
                          log.level
                        )}`}
                      >
                        {log.level}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-5 py-4 text-gray-700">
                      {log.service}
                    </td>
                    <td className="px-5 py-4 text-gray-800">{log.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </main>
  );
}
