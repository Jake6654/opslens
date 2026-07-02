from app.models import PatchSuggestionRequest, PatchSuggestionResponse
from app.workflows.incident_repair_graph import incident_repair_graph


def suggest_patch(request: PatchSuggestionRequest) -> PatchSuggestionResponse:
    initial_state = {
        "incident_id": request.incident_id,
        "summary": request.summary,
        "suspected_root_cause": request.suspected_root_cause,
        "recommended_action": request.recommended_action,
        "code_results": [item.model_dump() for item in request.code_results],
        "root_cause": None,
        "patch_summary": None,
        "suggested_diff": None,
        "risk_level": None,
        "requires_human_review": True,
    }

    result = incident_repair_graph.invoke(initial_state)

    return PatchSuggestionResponse(
        incident_id=result["incident_id"],
        root_cause=result["root_cause"],
        patch_summary=result["patch_summary"],
        suggested_diff=result["suggested_diff"],
        risk_level=result["risk_level"],
        requires_human_review=result["requires_human_review"],
    )