import { NextResponse } from "next/server";

function getServerBaseUrl() {
  return (
    process.env.API_INTERNAL_URL ??
    process.env.NEXT_PUBLIC_API_URL ??
    "http://localhost:8080"
  ).replace(/\/$/, "");
}

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const baseUrl = getServerBaseUrl();

  const response = await fetch(`${baseUrl}/logs/${encodeURIComponent(id)}`, {
    cache: "no-store",
  });

  if (!response.ok) {
    return NextResponse.json(
      { message: "Could not load log details." },
      { status: response.status }
    );
  }

  const log = await response.json();
  return NextResponse.json(log);
}
