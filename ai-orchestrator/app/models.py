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