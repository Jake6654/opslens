import json
from openai import OpenAI

from app.config import settings
from app.models import AnalyzeTestFailureRequest, AnalyzeTestFailureResponse


client = OpenAI(api_key=settings.openai_api_key)


def analyze_test_failure(request: AnalyzeTestFailureRequest) -> AnalyzeTestFailureResponse:
    try:
        prompt = build_prompt(request)

        response = client.responses.create(
            model=settings.openai_model,
            input=prompt,
            text={
                "format": {
                    "type": "json_schema",
                    "name": "test_failure_analysis",
                    "schema": {
                        "type": "object",
                        "properties": {
                            "failure_summary": {"type": "string"},
                            "likely_cause": {"type": "string"},
                            "recommended_action": {"type": "string"},
                            "confidence": {"type": "number"}
                        },
                        "required": [
                            "failure_summary",
                            "likely_cause",
                            "recommended_action",
                            "confidence"
                        ],
                        "additionalProperties": False
                    },
                    "strict": True
                }
            }
        )

        parsed = json.loads(response.output_text)

        return AnalyzeTestFailureResponse(
            incident_id=request.incident_id,
            patch_suggestion_id=request.patch_suggestion_id,
            test_run_id=request.test_run_id,
            failure_summary=parsed["failure_summary"],
            likely_cause=parsed["likely_cause"],
            recommended_action=parsed["recommended_action"],
            confidence=parsed["confidence"],
            raw_response=response.output_text,
        )

    except Exception as error:
        return fallback_analysis(request, error)

def build_prompt(request: AnalyzeTestFailureRequest) -> str:
    return f"""
You are analyzing a backend test failure for an incident response platform.

Incident ID: {request.incident_id}
Patch Suggestion ID: {request.patch_suggestion_id}
Test Run ID: {request.test_run_id}
Test Command: {request.test_command}
Test Status: {request.status}

Test Output:
{request.output}

Analyze the test failure.

Focus on:
1. Which test failed
2. The most important exception chain
3. The likely root cause
4. Whether this looks like app code, configuration, dependency injection, database, or environment issue
5. What a developer should check next

Do not invent code that is not present in the output.
Return only JSON.
"""

def fallback_analysis(
    request: AnalyzeTestFailureRequest,
    error: Exception
) -> AnalyzeTestFailureResponse:
    return AnalyzeTestFailureResponse(
        incident_id=request.incident_id,
        patch_suggestion_id=request.patch_suggestion_id,
        test_run_id=request.test_run_id,
        failure_summary="Test failure analysis could not be completed.",
        likely_cause="AI test failure analyzer was unavailable or returned an invalid response.",
        recommended_action="Inspect the raw test output and retry analysis later.",
        confidence=0.0,
        raw_response=json.dumps({
            "error": "test_failure_analysis_failed",
            "message": str(error),
        }),
    )