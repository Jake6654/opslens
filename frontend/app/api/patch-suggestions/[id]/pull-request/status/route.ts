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
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const baseUrl = getServerBaseUrl();

  const response = await fetch(
    `${baseUrl}/patch-suggestions/${encodeURIComponent(id)}/pull-request/status`,
    {
      cache: "no-store",
    },
  );

  const body = await response.json();

  return NextResponse.json(body, {
    status: response.status,
  });
}
