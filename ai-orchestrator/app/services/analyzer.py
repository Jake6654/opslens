import json

from app.models import AnalyzeLogRequest, AnalyzeLogResponse

def analyze_log(request: AnalyzeLogRequest) -> AnalyzeLogResponse:
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
