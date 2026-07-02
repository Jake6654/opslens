# walk through rhw local repo folder
import os
from pathlib import Path
from app.services.source_filter import is_supported_source_file

EXCLUDED_DIRECTORY_NAMES = {
    ".git",
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
        # turns a string path into a Python path object
        self.repository_path = Path(repository_path)

    def search_code(self, query: str, limit: int = 5) -> list[dict]:
        results = []

        # recursively walks through every file under the repository
        for current_root, directory_names, file_names in os.walk(self.repository_path):
            directory_names[:] = [
                directory_name
                for directory_name in directory_names
                if directory_name not in EXCLUDED_DIRECTORY_NAMES
            ]

            for file_name in file_names:
                file_path = Path(current_root) / file_name

                # turns full local path into repo-relative path
                # This matters because your API response should not expose the container's internal full path
                relative_path = str(file_path.relative_to(self.repository_path))

                if not is_supported_source_file(relative_path):
                    continue

                if file_path.stat().st_size > MAX_FILE_SIZE_BYTES:
                    continue

                content = file_path.read_text(encoding="utf-8", errors="replace")

                # only keeps files that contain the search query
                if query.lower() not in content.lower():
                    continue

                results.append({
                    "path": relative_path,
                    "content": content,
                })

                if len(results) >= limit:
                    return results

        return results
