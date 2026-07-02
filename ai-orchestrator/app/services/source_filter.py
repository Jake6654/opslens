SUPPORTED_SOURCE_EXTENSIONS = (
    ".java",
    ".kt",
    ".py",
    ".js",
    ".jsx",
    ".ts",
    ".tsx",
    ".yml",
    ".yaml",
    ".properties",
    ".xml",
    ".sql",
)

EXCLUDED_PATH_PARTS = (
    "/node_modules/",
    "/build/",
    "/dist/",
    "/target/",
    "/.next/",
    "/.git/",
)


def is_supported_source_file(path: str) -> bool:
    normalized_path = f"/{path.lower()}"

    if any(excluded_part in normalized_path for excluded_part in EXCLUDED_PATH_PARTS):
        return False

    return normalized_path.endswith(SUPPORTED_SOURCE_EXTENSIONS)