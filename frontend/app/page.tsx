type LogItem = {
  timestamp: string;
  level: string;
  service: string;
  message: string;
};

async function getLogs(): Promise<LogItem[]> {
  const res = await fetch("http://localhost:8080/logs", {
    cache: "no-store",
  });

  if (!res.ok) {
    throw new Error("Failed to fetch logs");
  }

  return res.json();
}

export default async function Home() {
  const logs = await getLogs();

  return (
    <main style={{ padding: "20px" }}>
      <h1>OpsLens Logs</h1>

      {logs.map((log, index) => (
        <div key={index} style={{ marginBottom: "10px" }}>
          <div>
            <b>{log.level}</b> - {log.service}
          </div>
          <div>{log.message}</div>
          <div style={{ fontSize: "12px", color: "gray" }}>{log.timestamp}</div>
        </div>
      ))}
    </main>
  );
}
