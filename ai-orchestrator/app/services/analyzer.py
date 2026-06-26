import json
import os

from openai import OpenAI
from app.models import AnalyzeLogRequest, AnalyzeLogResponse

def analyze_log(request: AnalyzeLogRequest) -> AnalyzeLogResponse:
  if not os.getenv("OPENAI_API_KEY"):
        return placeholder_analysis(request)
  
  # Try AI analysis, if anything fails, safely return fallback.
  try:
      return openai_analysis(request)
  except Exception as error:
      fallback = placeholder_analysis(request)

      return AnalyzeLogResponse(
            summary="AI analysis failed, so OpsLens returned a fallback analysis.",
            suspected_root_cause=fallback.suspected_root_cause,
            recommended_action="Check the AI orchestrator logs and retry analysis.",
            confidence=0.0,
            raw_response=json.dumps(
                {
                    "mode": "ai_error_fallback",
                    "error": str(error),
                    "fallback": json.loads(fallback.raw_response),
                }
            ),
        )

def placeholder_analysis(request: AnalyzeLogRequest) -> AnalyzeLogResponse:
  severity = request.severity or "UNKNOWN"
  service = request.service or "unknown-service"
  project = request.project or "unknown-project"

  raw_response = {
    "mode": "placeholder",
    "incident_id": request.incident_id,
    "log_id": request.log_id,
    "project": project,
    "service": service,
    "severity": severity,
  }

  return AnalyzeLogResponse(
        summary=f"{service} reported a {severity} log in {project}.",
        suspected_root_cause=(
            "Real AI analysis is not enabled yet. "
            "This placeholder confirms the orchestrator received the log."
        ),
        recommended_action=(
            "Connect the LangGraph Log Analyzer and Root Cause agents in Phase 1D."
        ),
        confidence=0.1,
        raw_response=json.dumps(raw_response),
    )

def openai_analysis(request: AnalyzeLogRequest) -> AnalyzeLogResponse:
    client = OpenAI()
    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

    prompt = build_analysis_prompt(request)

    response = client.responses.create(
        model=model,
        input=prompt,
    )

    output_text = response.output_text
    data = json.loads(output_text)

    return AnalyzeLogResponse(
        summary=data["summary"],
        suspected_root_cause=data["suspected_root_cause"],
        recommended_action=data["recommended_action"],
        confidence=float(data["confidence"]),
        raw_response=json.dumps(
            {
                "mode": "openai",
                "model": model,
                "response": data,
            }
        ),
    )

def build_analysis_prompt(request: AnalyzeLogRequest) -> str:
    return f"""
You are an incident analysis assistant for a backend operations platform.

Analyze the following backend log and return only valid JSON.

Rules:
- Do not include markdown.
- Do not include explanations outside JSON.
- Do not include secrets.
- Do not invent stack traces, file names, or code locations that are not present.
- Base the analysis only on the provided log fields.
- Keep the recommendation safe and developer-review friendly.

Required JSON shape:
{{
  "summary": "short summary of what happened",
  "suspected_root_cause": "likely cause based only on the log",
  "recommended_action": "safe next step for a developer",
  "confidence": 0.0
}}

Incident ID: {request.incident_id}
Log ID: {request.log_id}
Project: {request.project}
Environment: {request.environment}
Service: {request.service}
Severity: {request.severity}

Message:
{request.message}
""".strip()




