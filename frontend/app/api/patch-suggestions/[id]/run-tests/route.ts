// API
// POST /api/patch-suggestions/{id}/run-tests
// -> Spring Boot POST /patch-suggestions/{id}/run-tests

import { NextResponse } from "next/server";

function getServerBaseUrl() {
  return (
    process.env.API_INTERNAL_URL ??
    process.env.NEXT_PUBLIC_API_URL ??
    "http://localhost:8080"
  ).replace(/\/$/, "");
}

export async function POST(
  _request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const baseUrl = getServerBaseUrl();

  const response = await fetch(
    `${baseUrl}/patch-suggestions/${encodeURIComponent(id)}/run-tests`,
    {
      method: "POST",
      cache: "no-store",
    }
  );

  if (!response.ok) {
    return NextResponse.json(
      { message: "Could not run tests." },
      { status: response.status }
    );
  }

  const testRun = await response.json();
  return NextResponse.json(testRun);
}
