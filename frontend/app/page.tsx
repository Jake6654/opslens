import LogTable from "./components/LogTable";

export const dynamic = "force-dynamic";

type LogItem = {
  id: number;
  timestamp: string;
  level: string;
  service: string;
  message: string;
  project: string | null;
  environment: string | null;
};

type LogSummary = {
  totalLogs: number;
  errorCount: number;
  warnCount: number;
  infoCount: number;
};

function getServerBaseUrl() {
  return (
    process.env.API_INTERNAL_URL ??
    process.env.NEXT_PUBLIC_API_URL ??
    "http://localhost:8080"
  ).replace(/\/$/, "");
}

async function getLogs(level?: string): Promise<LogItem[]> {
  const baseUrl = getServerBaseUrl();

  const url = level
    ? `${baseUrl}/logs?level=${encodeURIComponent(level)}`
    : `${baseUrl}/logs`;

  const res = await safeFetch(url);
  return res.json();
}

async function getSummary(): Promise<LogSummary> {
  const baseUrl = getServerBaseUrl();

  const res = await safeFetch(`${baseUrl}/logs/summary`);
  return res.json();
}

// This function retries a request at most 5 times.
// delay = 1000 means 1 second.
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

        <LogTable logs={logs} />
      </div>
    </main>
  );
}
