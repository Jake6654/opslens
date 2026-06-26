from fastapi import FastAPI

from app.models import (AnalyzeLogRequest, AnalyzeLogResponse, CodeSearchRequest, CodeSearchResponse)

from app.services.analyzer import analyze_log
from app.services.code_search import search_code

app = FastAPI(title="OpsLens AI Orchestrator")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "OK"}


@app.post("/analyze-log", response_model=AnalyzeLogResponse)
def analyze_log_endpoint(request: AnalyzeLogRequest) -> AnalyzeLogResponse:
    return analyze_log(request)

@app.post("/search-code", response_model=CodeSearchResponse)
def search_code_endpoint(request: CodeSearchRequest) -> CodeSearchResponse:
    return search_code(request)