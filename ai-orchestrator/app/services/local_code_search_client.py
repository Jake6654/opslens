import os
from pathlib import Path

from app.services.source_filter import is_supported_source_file

EXCLUDED_DIRECTORY_NAMES = {
    ".git",
    ".gradle",
    ".next",
    ".venv",
    ".vercel",
    "__pycache__",
    "build",
    "dist",
    "node_modules",
    "target",
}

MAX_FILE_SIZE_BYTES = 1_000_000


class LocalCodeSearchClient:
    def __init__(self, repository_path: str):
        self.repository_path = Path(repository_path)

    def search_code(self, query: str, limit: int = 10) -> list[dict]:
        results = []
        query_lower = query.lower()

        for current_root, directory_names, file_names in os.walk(self.repository_path):
            directory_names[:] = [
                directory_name
                for directory_name in directory_names
                if directory_name not in EXCLUDED_DIRECTORY_NAMES
            ]

            for file_name in file_names:
                file_path = Path(current_root) / file_name
                relative_path = str(file_path.relative_to(self.repository_path))

                if not is_supported_source_file(relative_path):
                    continue

                if file_path.stat().st_size > MAX_FILE_SIZE_BYTES:
                    continue

                content = file_path.read_text(encoding="utf-8", errors="replace")

                if query_lower not in content.lower() and query_lower not in relative_path.lower():
                    continue

                results.append(
                    {
                        "path": relative_path,
                        "content": content,
                        "score": score_local_result(relative_path, content, query),
                    }
                )

        return sorted(results, key=lambda item: item["score"], reverse=True)[:limit]


def score_local_result(path: str, content: str, query: str) -> float:
    path_lower = path.lower()
    file_name = Path(path).name.lower()
    query_lower = query.lower()
    content_lower = content.lower()

    score = 0.45

    if query_lower in file_name:
        score += 0.35
    elif query_lower in path_lower:
        score += 0.25

    if f"class {query}".lower() in content_lower:
        score += 0.25

    if "dto" in path_lower or "request" in file_name:
        score += 0.20

    if "controller" in file_name:
        score += 0.18

    if "service" in file_name:
        score += 0.14

    if "diary" in path_lower:
        score += 0.12

    if "@valid" in content_lower or "@notblank" in content_lower or "@notnull" in content_lower:
        score += 0.16

    if "globalexceptionhandler" in file_name:
        score -= 0.12

    return round(max(score, 0.1), 2)
