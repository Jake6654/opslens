"use client";

import { useState } from "react";

type Incident = {
  id: number;
  sourceLogId: number;
  project: string | null;
  environment: string | null;
  service: string | null;
  severity: string;
  status: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

type IncidentReport = {
  id: number;
  incidentId: number;
  summary: string;
  suspectedRootCause: string;
  recommendedAction: string;
  confidence: number;
  rawResponse: string;
  createdAt: string;
};

type IncidentTableProps = {
  incidents: Incident[];
};

export default function IncidentTable({ incidents }: IncidentTableProps) {
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(
    null
  );
  const [selectedReport, setSelectedReport] = useState<IncidentReport | null>(
    null
  );
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleIncidentClick(incident: Incident) {
    try {
      setIsOpen(true);
      setLoading(true);
      setError("");
      setSelectedIncident(incident);
      setSelectedReport(null);

      const response = await fetch(`/api/incidents/${incident.id}/report`);

      if (!response.ok) {
        throw new Error(`Failed to fetch incident report: ${response.status}`);
      }

      const data: IncidentReport = await response.json();
      setSelectedReport(data);
    } catch {
      setError("Could not load incident report.");
    } finally {
      setLoading(false);
    }
  }

  function closePanel() {
    setIsOpen(false);
    setSelectedIncident(null);
    setSelectedReport(null);
    setError("");
  }

  return (
    <>
      <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-200 px-5 py-4">
          <h2 className="text-lg font-semibold text-gray-900">Incidents</h2>
          <p className="mt-1 text-sm text-gray-500">
            Tracked incidents created from logs.
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="px-5 py-3 font-medium">ID</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 font-medium">Severity</th>
                <th className="px-5 py-3 font-medium">Project</th>
                <th className="px-5 py-3 font-medium">Service</th>
                <th className="px-5 py-3 font-medium">Title</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-gray-200">
              {incidents.map((incident) => (
                <tr
                  key={incident.id}
                  onClick={() => handleIncidentClick(incident)}
                  className="cursor-pointer hover:bg-gray-50"
                >
                  <td className="whitespace-nowrap px-5 py-4 text-gray-600">
                    #{incident.id}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-gray-700">
                    {incident.status}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-gray-700">
                    {incident.severity}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-gray-700">
                    {incident.project ?? "N/A"}
                  </td>
                  <td className="whitespace-nowrap px-5 py-4 text-gray-700">
                    {incident.service ?? "N/A"}
                  </td>
                  <td className="px-5 py-4 text-gray-800">{incident.title}</td>
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
                  Incident Report
                </h2>
                <p className="text-sm text-gray-500">
                  Initial analysis placeholder.
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
                <p className="text-sm text-gray-500">
                  Loading incident report...
                </p>
              )}

              {error && (
                <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                  {error}
                </p>
              )}

              {!loading && selectedIncident && selectedReport && (
                <div className="space-y-4">
                  <DetailRow
                    label="Incident"
                    value={`#${selectedIncident.id}`}
                  />
                  <DetailRow label="Status" value={selectedIncident.status} />
                  <DetailRow
                    label="Severity"
                    value={selectedIncident.severity}
                  />
                  <DetailRow label="Summary" value={selectedReport.summary} />
                  <DetailRow
                    label="Suspected Root Cause"
                    value={selectedReport.suspectedRootCause}
                  />
                  <DetailRow
                    label="Recommended Action"
                    value={selectedReport.recommendedAction}
                  />
                  <DetailRow
                    label="Confidence"
                    value={`${selectedReport.confidence}`}
                  />
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
