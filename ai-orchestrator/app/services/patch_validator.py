import shutil
import subprocess
import tempfile
from pathlib import Path

from app.config import settings


def validate_patch(suggested_diff: str | None) -> tuple[bool, str]:
    if not suggested_diff or not suggested_diff.strip():
        return False, "No suggested diff was provided."

    with tempfile.TemporaryDirectory(prefix="opslens-patch-check-") as temp_dir:
        temp_repo_path = Path(temp_dir) / "repo"
        source_repo_path = Path(settings.local_repository_path)

        shutil.copytree(
            source_repo_path,
            temp_repo_path,
            ignore=shutil.ignore_patterns(
                ".git",
                ".gradle",
                ".venv",
                "__pycache__",
                "node_modules",
                "build",
                "target",
                ".next",
                "dist",
                "out",
            ),
        )

        completed = subprocess.run(
            ["git", "apply", "--check", "--whitespace=fix"],
            cwd=temp_repo_path,
            input=normalize_diff(suggested_diff),
            capture_output=True,
            text=True,
            timeout=30,
        )

        output = f"STDOUT:\n{completed.stdout or ''}\n\nSTDERR:\n{completed.stderr or ''}"

        return completed.returncode == 0, output

def normalize_diff(suggested_diff: str) -> str:
    diff = suggested_diff.strip()

    if diff.startswith("```"):
        lines = diff.splitlines()[1:]

        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]

        diff = "\n".join(lines)

    return diff + "\n"