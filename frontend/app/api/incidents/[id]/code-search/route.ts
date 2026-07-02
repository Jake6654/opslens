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

  const response = await fetch(
    `${baseUrl}/incidents/${encodeURIComponent(id)}/code-search`,
    {
      cache: "no-store",
    }
  );

  if (!response.ok) {
    return NextResponse.json(
      { message: "Could not load code search results." },
      { status: response.status }
    );
  }

  const results = await response.json();
  return NextResponse.json(results);
}

export async function POST(
  _request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const baseUrl = getServerBaseUrl();

  const response = await fetch(
    `${baseUrl}/incidents/${encodeURIComponent(id)}/code-search`,
    {
      method: "POST",
      cache: "no-store",
    }
  );

  if (!response.ok) {
    return NextResponse.json(
      { message: "Could not run code search." },
      { status: response.status }
    );
  }

  const results = await response.json();
  return NextResponse.json(results);
}
