import shutil
import subprocess
import tempfile
from pathlib import Path
from time import perf_counter

from app.config import settings
from app.models import RunTestsRequest, RunTestsResponse

ALLOWED_TEST_COMMANDS = {
    "./gradlew test",
    "./mvnw test",
    "npm test",
    "pytest",
}

MAX_OUTPUT_CHARS = 20_000


def run_tests(request: RunTestsRequest) -> RunTestsResponse:
    start_time = perf_counter()
    test_command = request.test_command or default_test_command(request.repository)

    if test_command not in ALLOWED_TEST_COMMANDS:
        return RunTestsResponse(
            incident_id=request.incident_id,
            patch_suggestion_id=request.patch_suggestion_id,
            status="ERROR",
            passed=False,
            test_command=test_command,
            output=f"Test command is not allowed: {test_command}",
            duration_ms=elapsed_ms(start_time),
        )

    try:
        with tempfile.TemporaryDirectory(prefix="opslens-test-") as temp_dir:
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

            source_test_path = Path(settings.test_working_directory)
            relative_test_path = source_test_path.relative_to(source_repo_path)
            temp_test_path = temp_repo_path / relative_test_path

            patch_applied, patch_output = apply_patch(
                temp_repo_path,
                request.suggested_diff,
            )

            if not patch_applied:
                return RunTestsResponse(
                    incident_id=request.incident_id,
                    patch_suggestion_id=request.patch_suggestion_id,
                    status="PATCH_APPLY_FAILED",
                    passed=False,
                    test_command=test_command,
                    output=truncate_output(f"PATCH APPLY OUTPUT:\n{patch_output}"),
                    duration_ms=elapsed_ms(start_time),
                )

            try:
                completed = subprocess.run(
                    test_command.split(),
                    cwd=temp_test_path,
                    capture_output=True,
                    text=True,
                    timeout=settings.test_timeout_seconds,
                )
            except subprocess.TimeoutExpired as error:
                timeout_output = build_timeout_output(patch_output, error)
                return RunTestsResponse(
                    incident_id=request.incident_id,
                    patch_suggestion_id=request.patch_suggestion_id,
                    status="ERROR",
                    passed=False,
                    test_command=test_command,
                    output=truncate_output(timeout_output),
                    duration_ms=elapsed_ms(start_time),
                )
            except Exception as error:
                return RunTestsResponse(
                    incident_id=request.incident_id,
                    patch_suggestion_id=request.patch_suggestion_id,
                    status="ERROR",
                    passed=False,
                    test_command=test_command,
                    output=truncate_output(
                        f"PATCH APPLY OUTPUT:\n{patch_output}\n\n"
                        f"TEST ERROR:\nTest command failed before completion: "
                        f"{type(error).__name__} - {error}"
                    ),
                    duration_ms=elapsed_ms(start_time),
                )

            output = (
                f"PATCH APPLY OUTPUT:\n{patch_output}\n\n"
                f"TEST OUTPUT:\n{build_output(completed.stdout, completed.stderr)}"
            )

            status = "PASSED" if completed.returncode == 0 else "FAILED"
            passed = completed.returncode == 0

            return RunTestsResponse(
                incident_id=request.incident_id,
                patch_suggestion_id=request.patch_suggestion_id,
                status=status,
                passed=passed,
                test_command=test_command,
                output=truncate_output(output),
                duration_ms=elapsed_ms(start_time),
            )
    except Exception as error:
        return RunTestsResponse(
            incident_id=request.incident_id,
            patch_suggestion_id=request.patch_suggestion_id,
            status="ERROR",
            passed=False,
            test_command=test_command,
            output=f"Test setup failed before execution: {type(error).__name__} - {error}",
            duration_ms=elapsed_ms(start_time),
        )


def apply_patch(temp_repo_path: Path, suggested_diff: str | None) -> tuple[bool, str]:
    if not suggested_diff or not suggested_diff.strip():
        return True, "No patch diff provided. Running tests without applying a patch."

    completed = subprocess.run(
        ["git", "apply", "--whitespace=fix"],
        cwd=temp_repo_path,
        input=normalize_diff(suggested_diff),
        capture_output=True,
        text=True,
        timeout=30,
    )

    if completed.returncode == 0:
        return True, build_output(completed.stdout, completed.stderr)

    return False, build_output(completed.stdout, completed.stderr)


def normalize_diff(suggested_diff: str) -> str:
    diff = suggested_diff.strip()

    if diff.startswith("```"):
        lines = diff.splitlines()[1:]

        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]

        diff = "\n".join(lines)

    return diff + "\n"


def default_test_command(repository: str | None) -> str:
    if repository == "local-workspace":
        return "./gradlew test"

    return "test command not configured"


def build_output(stdout: str | None, stderr: str | None) -> str:
    return f"STDOUT:\n{stdout or ''}\n\nSTDERR:\n{stderr or ''}"


def build_timeout_output(patch_output: str, error: subprocess.TimeoutExpired) -> str:
    return (
        f"PATCH APPLY OUTPUT:\n{patch_output}\n\n"
        f"TEST OUTPUT BEFORE TIMEOUT:\n"
        f"{build_output(to_text(error.stdout), to_text(error.stderr))}\n\n"
        f"TEST ERROR:\nTest command timed out after "
        f"{settings.test_timeout_seconds} seconds."
    )


def to_text(value: str | bytes | None) -> str:
    if value is None:
        return ""

    if isinstance(value, bytes):
        return value.decode(errors="replace")

    return value


def truncate_output(output: str) -> str:
    if len(output) <= MAX_OUTPUT_CHARS:
        return output

    return output[:MAX_OUTPUT_CHARS] + "\n\n... output truncated ..."


# Calculate elapsed time, which means the total amount of time that passes from start to end.
def elapsed_ms(start_time: float) -> int:
    return int((perf_counter() - start_time) * 1000)
