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
    # If Github returns the same file for multiple queries, we do not wnat duplicate results
    seen_paths: set[str] = set()

    for query in queries:
        github_items = await github.search_code(query, limit=3)

        for item in github_items:
            path = item.get("path")
            
            # if the file path is missing or already used, skip 
            if not path or path in seen_paths:
                continue

            seen_paths.add(path)

            # Fetches the real source code from GitHub
            content = await github.fetch_file(path)
            # Extracts a smaller relevant section from the file
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

            # Stops after collecting enough results
            if len(results) >= 5:
                break

        if len(results) >= 5:
            break

    if not results:
        return placeholder_result(request)

    return CodeSearchResponse(results=results)

# This fuction decides what keywords tosearch in GitHub 
def build_queries(request: CodeSearchRequest) -> list[str]:
    # It uses data from the incident
    # service = DiaryService
    # message = NullPointerException while saving diary entry
    # analysis_summary = Diary save failed with null pointer
    # suspected_root_cause = A required field may be null
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

    # returns only the first 3 queries to avoid calling GitHib too many times
    # Usually the most useful search terms are top 3
    return queries[:3]

# This function custs a large source file into a smaller readable snippet
# fils can be hundreds of lines long, so OpsLens should not store or show the entire file
def make_snippet(content: str, query: str, max_lines: int = 20) -> str:
    # Splits the file into individual lines
    lines = content.splitlines()

    query_lower = query.lower()

    match_index = 0
    for index, line in enumerate(lines):
        if query_lower in line.lower():
            match_index = index
            break
    # decide to cut from where to where 
    start = max(match_index - 5, 0)
    # 파일 끝을 넘어가지 않도록 막는다
    end = min(match_index + max_lines, len(lines))

    return "\n".join(lines[start:end])