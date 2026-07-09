from fastapi import FastAPI

from app.models import (AnalyzeLogRequest, AnalyzeLogResponse, CodeSearchRequest, CodeSearchResponse, PatchSuggestionResponse, PatchSuggestionRequest, RunTestsRequest, RunTestsResponse)

from app.services.analyzer import analyze_log
from app.services.code_search import search_code
from app.services.patch_suggester import suggest_patch
from app.services.test_runner import run_tests


app = FastAPI(title="OpsLens AI Orchestrator")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "OK"}


@app.post("/analyze-log", response_model=AnalyzeLogResponse)
def analyze_log_endpoint(request: AnalyzeLogRequest) -> AnalyzeLogResponse:
    return analyze_log(request)

@app.post("/search-code", response_model=CodeSearchResponse)
async def search_code_endpoint(request: CodeSearchRequest) -> CodeSearchResponse:
    return await search_code(request)

@app.post("/suggest-patch", response_model=PatchSuggestionResponse)
def suggest_patch_endpoint(request: PatchSuggestionRequest) -> PatchSuggestionResponse:
    return suggest_patch(request)

@app.post("/run-tests", response_model=RunTestsResponse)
def run_tests_endpoint(request: RunTestsRequest) -> RunTestsResponse:
    return run_tests(request)