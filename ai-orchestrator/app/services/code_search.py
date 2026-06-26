from app.models import CodeSearchItem, CodeSearchRequest, CodeSearchResponse


def search_code(request: CodeSearchRequest) -> CodeSearchResponse:
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
                    "Placeholder code search result. Real GitHub repository "
                    "search will be connected in the next Phase 2 step."
                ),
                score=0.1,
            )
        ]
    )