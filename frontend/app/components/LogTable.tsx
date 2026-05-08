"use client";

import { useState } from "react";

type LogItem = {
  id: number;
  timestamp: string;
  level: string;
  service: string;
  message: string;
  project: string | null;
  environment: string | null;
};

type LogTableProps = {
  logs: LogItem[];
};

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

export default function LogTable({ logs }: LogTableProps) {
  const [selectedLog, setSelectedLog] = useState<LogItem | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleLogClick(id: number) {
    try {
      setIsOpen(true);
      setLoading(true);
      setError("");
      setSelectedLog(null);

      const response = await fetch(`/api/logs/${id}`);

      if (!response.ok) {
        throw new Error(`Failed to fetch log detail: ${response.status}`);
      }

      const data: LogItem = await response.json();
      setSelectedLog(data);
    } catch {
      setError("Could not load log details.");
    } finally {
      setLoading(false);
    }
  }

  function closePanel() {
    setIsOpen(false);
    setSelectedLog(null);
    setError("");
  }

  return (
    <>
      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-200 px-5 py-4">
          <h2 className="text-lg font-semibold text-gray-900">Logs</h2>
          <p className="mt-1 text-sm text-gray-500">
            Click a log row to view details.
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-5 py-3 font-medium">Time</th>
                <th className="px-5 py-3 font-medium">Level</th>
                <th className="px-5 py-3 font-medium">Project</th>
                <th className="px-5 py-3 font-medium">Environment</th>
                <th className="px-5 py-3 font-medium">Service</th>
                <th className="px-5 py-3 font-medium">Message</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-gray-200">
              {logs.map((log) => (
                <tr
                  key={log.id}
                  onClick={() => handleLogClick(log.id)}
                  className="cursor-pointer hover:bg-gray-50"
                >
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
                    {log.project}
                  </td>

                  <td className="whitespace-nowrap px-5 py-4 text-gray-700">
                    {log.environment}
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

      {isOpen && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/20">
          <aside className="h-full w-full max-w-md bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">
                  Log Details
                </h2>
                <p className="text-sm text-gray-500">
                  Full information for one log event.
                </p>
              </div>

              <button
                onClick={closePanel}
                className="rounded-md px-3 py-1 text-sm text-gray-500 hover:bg-gray-100 hover:text-gray-900"
              >
                Close
              </button>
            </div>

            <div className="p-5">
              {loading && (
                <p className="text-sm text-gray-500">Loading log details...</p>
              )}

              {error && (
                <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                  {error}
                </p>
              )}

              {!loading && selectedLog && (
                <div className="space-y-4">
                  <DetailRow label="ID" value={String(selectedLog.id)} />
                  <DetailRow label="Timestamp" value={selectedLog.timestamp} />
                  <DetailRow label="Level" value={selectedLog.level} />
                  <DetailRow
                    label="Project"
                    value={selectedLog.project ?? "N/A"}
                  />
                  <DetailRow
                    label="Environment"
                    value={selectedLog.environment ?? "N/A"}
                  />
                  <DetailRow label="Service" value={selectedLog.service} />

                  <div>
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-gray-500">
                      Message
                    </p>
                    <div className="rounded-md border border-gray-200 bg-gray-50 p-3 text-sm text-gray-800">
                      {selectedLog.message}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </aside>
        </div>
      )}
    </>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="mb-1 text-xs font-medium uppercase tracking-wide text-gray-500">
        {label}
      </p>
      <p className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-800">
        {value}
      </p>
    </div>
  );
}
