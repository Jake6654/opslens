from pydantic import BaseModel


class AnalyzeLogRequest(BaseModel):
    incident_id: int
    log_id: int
    project: str | None = None
    environment: str | None = None
    service: str | None = None
    severity: str | None = None
    message: str


class AnalyzeLogResponse(BaseModel):
    summary: str
    suspected_root_cause: str
    recommended_action: str
    confidence: float
    raw_response: str