import subprocess
from app.models import RunTestsRequest, RunTestsResponse
from time import perf_counter
from app.config import settings

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
        completed = subprocess.run(
          test_command.split(),
          cwd=settings.test_working_directory,
          capture_output=True,
          text=True,
          timeout=settings.test_timeout_seconds,
        )
    except subprocess.TimeoutExpired:
        return RunTestsResponse(
            incident_id=request.incident_id,
            patch_suggestion_id=request.patch_suggestion_id,
            status="ERROR",
            passed=False,
            test_command=test_command,
            output=(
                f"Test command timed out after "
                f"{settings.test_timeout_seconds} seconds."
            ),
            duration_ms=elapsed_ms(start_time),
        )
    except Exception as error:
        return RunTestsResponse(
            incident_id=request.incident_id,
            patch_suggestion_id=request.patch_suggestion_id,
            status="ERROR",
            passed=False,
            test_command=test_command,
            output=f"Test command failed before completion: {type(error).__name__} - {error}",
            duration_ms=elapsed_ms(start_time),
        )

    output = build_output(completed.stdout, completed.stderr)

    if completed.returncode == 0:
      status = "PASSED"
      passed = True
    else:
      status = "FAILED"
      passed = False

    return RunTestsResponse(
        incident_id=request.incident_id,
        patch_suggestion_id=request.patch_suggestion_id,
        status=status,
        passed=passed,
        test_command=test_command,
        output=truncate_output(output),
        duration_ms=elapsed_ms(start_time),
    )


def default_test_command(repository: str|None) -> str:
  if repository == "local-workspace":
    return "./gradlew test"
  
  return "test command not configured"


def build_output(stdout: str, stderr: str) -> str:
    return f"STDOUT:\n{stdout}\n\nSTDERR:\n{stderr}"


def truncate_output(output: str) -> str:
    if len(output) <= MAX_OUTPUT_CHARS:
        return output

    return output[:MAX_OUTPUT_CHARS] + "\n\n... output truncated ..."


def elapsed_ms(start_time: float) -> int:
    return int((perf_counter() - start_time) * 1000)