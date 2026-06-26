from fastapi import FastAPI

from app.models import AnalyzeLogRequest, AnalyzeLogResponse
from app.services.analyzer import analyze_log

app = FastAPI(title="OpsLens AI Orchestrator")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "OK"}


@app.post("/analyze-log", response_model=AnalyzeLogResponse)
def analyze_log_endpoint(request: AnalyzeLogRequest) -> AnalyzeLogResponse:
    return analyze_log(request)