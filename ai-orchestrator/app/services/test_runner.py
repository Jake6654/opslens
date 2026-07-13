from app.models import RunTestsRequest, RunTestsResponse



def run_tests(request: RunTestsRequest) -> RunTestsResponse:
  return RunTestsResponse(
        incident_id=request.incident_id,
        patch_suggestion_id=request.patch_suggestion_id,
        status="SKIPPED",
        passed=False,
        test_command=request.test_command or "test command not configured",
        output="Phase 4A skeleton: test execution has not been enabled yet.",
        duration_ms=0,
    )

def default_test_command(repository: str|None) -> str:
  if repository == "local-workspace":
    return "./gradlew test"
  
  return "test commnad not configured"