from app.models import CodeSearchItem, CodeSearchRequest, CodeSearchResponse

from app.services.github_client import GitHubClient



def placeholder_result(request: CodeSearchRequest) -> CodeSearchResponse:
    service = request.service or "ExampleService"
    project = request.project or "unknown-project"

    return CodeSearchResponse(
        results=[
            CodeSearchItem(
                repository=project,
                file_path="backend/src/main/java/example/ExampleService.java",
                symbol_name=service,
                snippet=(
                    "public class ExampleService {\n"
                    "    // Placeholder code search result.\n"
                    "}"
                ),
                relevance_reason=(
                    "No GitHub code search result was found. "
                    "This placeholder keeps the code search workflow testable."
                ),
                score=0.1,
            )
        ]
    )


async def search_code(request: CodeSearchRequest) -> CodeSearchResponse:
    github = GitHubClient()
    queries = build_queries(request)

    results: list[CodeSearchItem] = []
    seen_paths: set[str] = set()

    for query in queries:
        github_items = await github.search_code(query, limit=3)

        for item in github_items:
            path = item.get("path")

            if not path or path in seen_paths:
                continue

            seen_paths.add(path)

            content = await github.fetch_file(path)
            snippet = make_snippet(content, query)

            results.append(
                CodeSearchItem(
                    repository=f"{github.owner}/{github.repo}",
                    file_path=path,
                    symbol_name=request.service,
                    snippet=snippet,
                    relevance_reason=f"Matched GitHub search query: {query}",
                    score=0.7,
                )
            )

            if len(results) >= 5:
                break

        if len(results) >= 5:
            break

    if not results:
        return placeholder_result(request)

    return CodeSearchResponse(results=results)

def build_queries(request: CodeSearchRequest) -> list[str]:
    candidates = [
        request.service,
        request.message,
        request.analysis_summary,
        request.suspected_root_cause,
    ]

    queries = []

    for candidate in candidates:
        if candidate:
            queries.append(candidate)

    return queries[:3]

def make_snippet(content: str, query: str, max_lines: int = 20) -> str:
    lines = content.splitlines()

    query_lower = query.lower()

    match_index = 0
    for index, line in enumerate(lines):
        if query_lower in line.lower():
            match_index = index
            break

    start = max(match_index - 5, 0)
    end = min(match_index + max_lines, len(lines))

    return "\n".join(lines[start:end])