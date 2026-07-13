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

type CodeSearchResult = {
  id: number;
  incidentId: number;
  repository: string;
  filePath: string;
  symbolName: string | null;
  snippet: string;
  relevanceReason: string;
  score: number;
  createdAt: string;
};

type IncidentTableProps = {
  incidents: Incident[];
};

type PatchSuggestion = {
  id: number;
  incidentId: number;
  rootCause: string;
  patchSummary: string;
  suggestedDiff: string;
  riskLevel: string;
  requiresHumanReview: boolean;
  createdAt: string;
};

type TestRunResult = {
  id: number;
  incidentId: number;
  patchSuggestionId: number;
  status: string;
  passed: boolean;
  testCommand: string;
  output: string;
  durationMs: number | null;
  createdAt: string | null;
};

type DetailTab = "report" | "code" | "patches";

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
  const [codeSearchResults, setCodeSearchResults] = useState<
    CodeSearchResult[]
  >([]);
  const [isCodeSearchLoading, setIsCodeSearchLoading] = useState(false);
  const [codeSearchError, setCodeSearchError] = useState("");
  const [patchSuggestions, setPatchSuggestions] = useState<PatchSuggestion[]>(
    []
  );
  const [isPatchLoading, setIsPatchLoading] = useState(false);
  const [patchError, setPatchError] = useState("");
  const [activeTab, setActiveTab] = useState<DetailTab>("report");

  // Test run states
  // save the test run list by patchSuggetionId
  const [testRunsByPatchId, setTestRunsByPatchId] = useState<
    Record<number, TestRunResult[]>
  >({});

  // save where is currently a test running at
  const [runningTestPatchId, setRunningTestPatchId] = useState<number | null>(
    null
  );

  // save error messages
  const [testRunError, setTestRunError] = useState("");

  async function handleIncidentClick(incident: Incident) {
    try {
      setIsOpen(true);
      setLoading(true);
      setError("");
      setCodeSearchError("");
      setActiveTab("report");
      setSelectedIncident(incident);
      setSelectedReport(null);
      setCodeSearchResults([]);

      // reset patch state here
      setPatchSuggestions([]);
      setPatchError("");
      setTestRunsByPatchId({});
      setRunningTestPatchId(null);
      setTestRunError("");

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

    try {
      await fetchPatchSuggestions(incident.id);
    } catch {
      setPatchError("Could not load patch suggestions.");
    } finally {
      setLoading(false);
    }

    try {
      await fetchCodeSearchResults(incident.id);
    } catch {
      setCodeSearchError("Could not load code search results.");
    }
  }

  // GET saved results
  async function fetchCodeSearchResults(incidentId: number) {
    const response = await fetch(`/api/incidents/${incidentId}/code-search`);

    if (!response.ok) {
      throw new Error("Failed to fetch code search results");
    }

    const data: CodeSearchResult[] = await response.json();
    setCodeSearchResults(data);
  }

  // POST to run a new search and save new results
  async function runCodeSearch(incidentId: number) {
    try {
      setIsCodeSearchLoading(true);
      setCodeSearchError("");

      // Run code sarch
      const response = await fetch(`/api/incidents/${incidentId}/code-search`, {
        method: "POST",
      });

      if (!response.ok) {
        throw new Error("Failed to run code search");
      }

      const data: CodeSearchResult[] = await response.json();
      setCodeSearchResults(data);
    } catch {
      setCodeSearchError("Could not run code search.");
    } finally {
      setIsCodeSearchLoading(false);
    }
  }

  async function fetchPatchSuggestions(incidentId: number) {
    const response = await fetch(
      `/api/incidents/${incidentId}/patch-suggestions`
    );

    if (!response.ok) {
      throw new Error("Failed to fetch patch suggestions");
    }

    const data: PatchSuggestion[] = await response.json();
    setPatchSuggestions(data);

    await Promise.all(data.map((patch) => fetchTestRuns(patch.id)));
  }

  async function handleGeneratePatchSuggestion() {
    if (!selectedIncident) return;

    try {
      setIsPatchLoading(true);
      setPatchError("");

      const response = await fetch(
        `/api/incidents/${selectedIncident.id}/suggest-patch`,
        {
          method: "POST",
        }
      );

      if (!response.ok) {
        throw new Error("Failed to generate patch suggestion");
      }

      await fetchPatchSuggestions(selectedIncident.id);
    } catch {
      setPatchError("Could not generate patch suggestion.");
    } finally {
      setIsPatchLoading(false);
    }
  }

  async function fetchTestRuns(patchSuggestionId: number) {
    const response = await fetch(
      `/api/patch-suggestions/${patchSuggestionId}/test-runs`
    );

    if (!response.ok) {
      throw new Error("Failed to fetch test runs");
    }

    const data: TestRunResult[] = await response.json();

    setTestRunsByPatchId((previous) => ({
      ...previous,
      [patchSuggestionId]: data,
    }));
  }

  async function handleRunTests(patchSuggestionId: number) {
    try {
      setRunningTestPatchId(patchSuggestionId);
      setTestRunError("");

      const response = await fetch(
        `/api/patch-suggestions/${patchSuggestionId}/run-tests`,
        {
          method: "POST",
        }
      );

      if (!response.ok) {
        throw new Error("Failed to run tests");
      }

      await fetchTestRuns(patchSuggestionId);
    } catch {
      setTestRunError("Could not run tests.");
    } finally {
      setRunningTestPatchId(null);
    }
  }

  function closePanel() {
    setIsOpen(false);
    setSelectedIncident(null);
    setSelectedReport(null);
    setCodeSearchResults([]);
    setCodeSearchError("");
    setPatchSuggestions([]);
    setPatchError("");
    setTestRunsByPatchId({});
    setRunningTestPatchId(null);
    setTestRunError("");
    setActiveTab("report");
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
          <aside className="flex max-h-[88vh] w-full max-w-4xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl">
            <div className="flex shrink-0 items-center justify-between border-b border-gray-200 px-6 py-4">
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

            <div className="shrink-0 border-b border-gray-200 px-6 py-3">
              <div className="inline-flex rounded-lg border border-gray-200 bg-gray-50 p-1">
                <TabButton
                  label="Report"
                  isActive={activeTab === "report"}
                  onClick={() => setActiveTab("report")}
                />
                <TabButton
                  label={`Related Code (${codeSearchResults.length})`}
                  isActive={activeTab === "code"}
                  onClick={() => setActiveTab("code")}
                />
                <TabButton
                  label={`Patch Suggestions (${patchSuggestions.length})`}
                  isActive={activeTab === "patches"}
                  onClick={() => setActiveTab("patches")}
                />
              </div>
            </div>

            <div className="flex-1 overflow-y-auto px-6 py-5">
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

              {!loading &&
                selectedIncident &&
                selectedReport &&
                activeTab === "report" && (
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

              {!loading && selectedIncident && activeTab === "code" && (
                <div className="space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold text-gray-900">
                        Related Code
                      </h3>
                      <p className="text-sm text-gray-500">
                        Source files connected to this incident.
                      </p>
                    </div>
                    <button
                      onClick={() => runCodeSearch(selectedIncident.id)}
                      disabled={isCodeSearchLoading}
                      className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:bg-gray-400"
                    >
                      {isCodeSearchLoading ? "Searching..." : "Run Code Search"}
                    </button>
                  </div>

                  {codeSearchError && (
                    <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                      {codeSearchError}
                    </p>
                  )}

                  {codeSearchResults.length === 0 ? (
                    <p className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-500">
                      No code search results yet.
                    </p>
                  ) : (
                    <div className="space-y-3">
                      {codeSearchResults.map((result) => (
                        <article
                          key={result.id}
                          className="rounded-lg border border-gray-200 bg-gray-50 p-4"
                        >
                          <div className="mb-2">
                            <p className="break-all text-sm font-medium text-gray-900">
                              {result.filePath}
                            </p>
                            <p className="mt-1 text-xs text-gray-500">
                              {result.repository} · score {result.score}
                            </p>
                          </div>

                          <p className="mb-2 text-xs text-gray-600">
                            {result.relevanceReason}
                          </p>

                          <pre className="max-h-72 overflow-auto rounded-md bg-gray-950 p-3 text-xs text-gray-100">
                            <code>{result.snippet}</code>
                          </pre>
                        </article>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {!loading && selectedIncident && activeTab === "patches" && (
                <div className="space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold text-gray-900">
                        Patch Suggestions
                      </h3>
                      <p className="text-sm text-gray-500">
                        AI-generated fix suggestions for this incident.
                      </p>
                    </div>
                    <button
                      onClick={handleGeneratePatchSuggestion}
                      disabled={isPatchLoading}
                      className="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                      {isPatchLoading
                        ? "Generating Patch..."
                        : "Generate Patch Suggestion"}
                    </button>
                  </div>

                  {patchError && (
                    <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                      {patchError}
                    </p>
                  )}

                  {testRunError && (
                    <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
                      {testRunError}
                    </p>
                  )}

                  {patchSuggestions.length === 0 && !isPatchLoading ? (
                    <p className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-500">
                      No patch suggestions yet.
                    </p>
                  ) : (
                    <div className="space-y-4">
                      {patchSuggestions.map((patch) => {
                        const testRuns = testRunsByPatchId[patch.id] ?? [];

                        return (
                          <article
                            key={patch.id}
                            className="rounded-lg border border-gray-200 bg-gray-50 p-4"
                          >
                            <div className="flex flex-wrap items-start justify-between gap-3">
                              <div>
                                <p className="text-sm font-medium text-gray-900">
                                  {patch.patchSummary}
                                </p>

                                <p className="mt-1 text-xs text-gray-500">
                                  Risk: {patch.riskLevel} · Human review:{" "}
                                  {patch.requiresHumanReview
                                    ? "Required"
                                    : "Not required"}
                                </p>
                              </div>

                              <button
                                onClick={() => handleRunTests(patch.id)}
                                disabled={runningTestPatchId === patch.id}
                                className="rounded-md bg-gray-900 px-3 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:cursor-not-allowed disabled:opacity-60"
                              >
                                {runningTestPatchId === patch.id
                                  ? "Running Tests..."
                                  : "Run Tests"}
                              </button>
                            </div>

                            <pre className="mt-3 max-h-96 overflow-auto rounded-md bg-gray-950 p-3 text-xs text-gray-100">
                              <code>{patch.suggestedDiff}</code>
                            </pre>

                            <div className="mt-4 border-t border-gray-200 pt-4">
                              <h4 className="text-sm font-semibold text-gray-900">
                                Test Runs
                              </h4>

                              {testRuns.length === 0 ? (
                                <p className="mt-2 rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-500">
                                  No test runs yet.
                                </p>
                              ) : (
                                <div className="mt-3 space-y-3">
                                  {testRuns.map((testRun) => (
                                    <TestRunCard
                                      key={testRun.id}
                                      testRun={testRun}
                                    />
                                  ))}
                                </div>
                              )}
                            </div>
                          </article>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}
            </div>
          </aside>
        </div>
      )}
    </>
  );
}

function TestRunCard({ testRun }: { testRun: TestRunResult }) {
  const statusClassName =
    testRun.status === "PASSED"
      ? "bg-green-100 text-green-700"
      : testRun.status === "FAILED"
        ? "bg-red-100 text-red-700"
        : testRun.status === "ERROR"
          ? "bg-amber-100 text-amber-700"
          : "bg-gray-100 text-gray-700";

  return (
    <div className="rounded-md border border-gray-200 bg-white p-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span
          className={`rounded-full px-2 py-1 text-xs font-semibold ${statusClassName}`}
        >
          {testRun.status}
        </span>
        <span className="text-xs text-gray-500">
          {testRun.durationMs ?? 0} ms
        </span>
      </div>

      <p className="mt-2 text-xs text-gray-500">
        Passed: {testRun.passed ? "true" : "false"}
      </p>
      <p className="mt-1 break-all text-xs text-gray-500">
        Command: {testRun.testCommand}
      </p>

      <pre className="mt-3 max-h-64 overflow-auto rounded-md bg-gray-950 p-3 text-xs text-gray-100">
        <code>{testRun.output}</code>
      </pre>
    </div>
  );
}

function TabButton({
  label,
  isActive,
  onClick,
}: {
  label: string;
  isActive: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
        isActive
          ? "bg-white text-gray-900 shadow-sm"
          : "text-gray-500 hover:text-gray-900"
      }`}
    >
      {label}
    </button>
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
