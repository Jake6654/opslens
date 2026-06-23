from pydantic import BaseModel, ConfigDict, Field


class AnalyzeLogRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    incident_id: int = Field(alias="incidentId")
    log_id: int = Field(alias="logId")
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
