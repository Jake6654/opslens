from app.config import settings
from app.models import CodeSearchItem, CodeSearchRequest, CodeSearchResponse
from app.services.github_client import GitHubClient
from app.services.local_code_search_client import LocalCodeSearchClient
from app.services.source_filter import is_supported_source_file

MAX_RESULTS = 5


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
                    "No code search result was found. "
                    "This placeholder keeps the code search workflow testable."
                ),
                score=0.1,
            )
        ]
    )


async def search_code(request: CodeSearchRequest) -> CodeSearchResponse:
    if settings.code_search_mode == "local":
        return search_local_code(request)
    return await search_github_code(request)


async def search_github_code(request: CodeSearchRequest) -> CodeSearchResponse:
    github = GitHubClient()
    queries = build_queries(request)

    results_by_path: dict[str, CodeSearchItem] = {}

    for query in queries:
        github_items = await github.search_code(query, limit=5)

        for item in github_items:
            path = item.get("path")
            if not path or not is_supported_source_file(path):
                continue

            content = await github.fetch_file(path)
            snippet = make_snippet(content, query)
            score = score_search_result(path, content, query)

            existing = results_by_path.get(path)
            if existing and existing.score >= score:
                continue

            results_by_path[path] = CodeSearchItem(
                repository=f"{github.owner}/{github.repo}",
                file_path=path,
                symbol_name=request.service,
                snippet=snippet,
                relevance_reason=f"Matched GitHub search query: {query}",
                score=score,
            )

    results = sorted(
        results_by_path.values(),
        key=lambda item: item.score,
        reverse=True,
    )[:MAX_RESULTS]

    if not results:
        return placeholder_result(request)

    return CodeSearchResponse(results=results)


# local development
def search_local_code(request: CodeSearchRequest) -> CodeSearchResponse:
    client = LocalCodeSearchClient(settings.local_repository_path)
    queries = build_queries(request)

    results_by_path: dict[str, CodeSearchItem] = {}

    for query in queries:
        local_items = client.search_code(query, limit=10)

        for item in local_items:
            path = item["path"]
            snippet = make_snippet(item["content"], query)
            score = item.get("score") or score_search_result(path, item["content"], query)

            existing = results_by_path.get(path)
            if existing and existing.score >= score:
                continue

            results_by_path[path] = CodeSearchItem(
                repository="local-workspace",
                file_path=path,
                symbol_name=request.service,
                snippet=snippet,
                relevance_reason=f"Matched local search query: {query}",
                score=score,
            )

    results = sorted(
        results_by_path.values(),
        key=lambda item: item.score,
        reverse=True,
    )[:MAX_RESULTS]

    if not results:
        return placeholder_result(request)

    return CodeSearchResponse(results=results)


# This function decides what keywords to search in GitHub/local source.
def build_queries(request: CodeSearchRequest) -> list[str]:
    combined_text = " ".join(
        value or ""
        for value in [
            request.service,
            request.message,
            request.analysis_summary,
            request.suspected_root_cause,
        ]
    ).lower()

    candidates = [
        request.service,
        request.message,
        request.analysis_summary,
        request.suspected_root_cause,
    ]

    if "diary" in combined_text:
        candidates.extend(["SaveDiaryRequest", "DiaryController", "DiaryService"])

    if "userid" in combined_text or "user id" in combined_text:
        candidates.extend(["userId", "SaveDiaryRequest", "@NotBlank", "@Valid"])

    if "validation" in combined_text or "bad request" in combined_text:
        candidates.extend(["SaveDiaryRequest", "@Valid", "MethodArgumentNotValidException"])

    if "null" in combined_text:
        candidates.extend(["@NotNull", "@NotBlank", "Objects.isNull"])

    return dedupe_queries(candidates)[:10]


def dedupe_queries(candidates: list[str | None]) -> list[str]:
    queries = []
    seen = set()

    for candidate in candidates:
        if not candidate:
            continue

        query = candidate.strip()
        if not query:
            continue

        key = query.lower()
        if key in seen:
            continue

        seen.add(key)
        queries.append(query)

    return queries


def score_search_result(path: str, content: str, query: str) -> float:
    path_lower = path.lower()
    content_lower = content.lower()
    query_lower = query.lower()

    score = 0.5

    if query_lower in path_lower:
        score += 0.25

    if query_lower in content_lower:
        score += 0.1

    if "dto" in path_lower or "request" in path_lower:
        score += 0.18

    if "controller" in path_lower:
        score += 0.16

    if "service" in path_lower:
        score += 0.14

    if "diary" in path_lower:
        score += 0.12

    if "@valid" in content_lower or "@notblank" in content_lower or "@notnull" in content_lower:
        score += 0.16

    if "globalexceptionhandler" in path_lower:
        score -= 0.12

    return round(max(score, 0.1), 2)


# This function cuts a large source file into a smaller readable snippet.
def make_snippet(content: str, query: str, max_lines: int = 35) -> str:
    lines = content.splitlines()

    if len(lines) <= 120:
        return content

    query_lower = query.lower()

    match_index = 0
    for index, line in enumerate(lines):
        if query_lower in line.lower():
            match_index = index
            break

    start = max(match_index - 8, 0)
    end = min(match_index + max_lines, len(lines))

    return "\n".join(lines[start:end])
