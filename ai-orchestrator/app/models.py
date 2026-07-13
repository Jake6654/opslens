from pydantic import BaseModel

# It defines the request and response shapes for the AI API
class AnalyzeLogRequest(BaseModel):
    incident_id: int
    log_id: int
    project: str | None = None
    environment: str | None = None
    service: str | None = None
    severity: str | None = None
    message: str
# {
#   "incident_id": 1,
#   "log_id": 2,
#   "project": "sketch-my-day",
#   "environment": "dev",
#   "service": "DiaryService",
#   "severity": "ERROR",
#   "message": "NullPointerException while saving diary"
# }

class AnalyzeLogResponse(BaseModel):
    summary: str
    suspected_root_cause: str
    recommended_action: str
    confidence: float
    raw_response: str
# {
#   "summary": "The service reported an ERROR log.",
#   "suspected_root_cause": "AI analysis is not enabled yet.",
#   "recommended_action": "Connect the real Log Analyzer agent in Phase 1D.",
#   "confidence": 0.1,
#   "raw_response": "{\"mode\":\"placeholder\"}"
# }

class CodeSearchRequest(BaseModel):
    incident_id: int
    project: str | None = None
    environment: str | None = None
    service: str | None = None
    severity: str | None = None
    message: str
    analysis_summary: str | None = None
    suspected_root_cause: str | None = None


class CodeSearchItem(BaseModel):
    repository: str
    file_path: str
    symbol_name: str | None = None
    snippet: str
    relevance_reason: str
    score: float


class CodeSearchResponse(BaseModel): 
    results: list[CodeSearchItem]

class PatchCodeResult(BaseModel):
    file_path: str
    snippet: str
    repository: str | None = None
    relevance_reason: str | None = None
    score: float | None = None


class PatchSuggestionRequest(BaseModel):
    incident_id: int
    summary: str
    suspected_root_cause: str
    recommended_action: str
    code_results: list[PatchCodeResult]


class PatchSuggestionResponse(BaseModel):
    incident_id: int
    root_cause: str
    patch_summary: str
    suggested_diff: str
    risk_level: str
    requires_human_review: bool

class RunTestsRequest(BaseModel):
    incident_id: int
    patch_suggestion_id: int
    repository: str | None = None
    test_command: str | None = None

# { Example response
#   "incident_id": 13,
#   "patch_suggestion_id": 1,
#   "status": "SKIPPED",
#   "passed": false,
#   "test_command": "./gradlew test",
#   "output": "Test execution has not been enabled yet.",
#   "duration_ms": 0
# }
class RunTestsResponse(BaseModel):
    incident_id: int
    patch_suggestion_id: int
    status: str
    passed: bool
    test_command: str
    output: str
    duration_ms: int

