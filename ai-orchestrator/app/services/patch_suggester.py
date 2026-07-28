from app.models import PatchSuggestionRequest, PatchSuggestionResponse
from app.services.patch_validator import validate_patch
from app.workflows.incident_repair_graph import incident_repair_graph


def suggest_patch(request: PatchSuggestionRequest) -> PatchSuggestionResponse:
    try:
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
        suggested_diff = result.get("suggested_diff") or ""
        patch_valid, patch_validation_output = validate_patch(suggested_diff)

        risk_level = result.get("risk_level") or "NEEDS_REVIEW"
        if not patch_valid:
            risk_level = "NEEDS_REVIEW"

        return PatchSuggestionResponse(
            incident_id=result.get("incident_id") or request.incident_id,
            root_cause=result.get("root_cause") or request.suspected_root_cause,
            patch_summary=(
                result.get("patch_summary")
                or "Patch suggestion generated with incomplete workflow output."
            ),
            suggested_diff=suggested_diff,
            risk_level=risk_level,
            requires_human_review=True,
            patch_valid=patch_valid,
            patch_validation_output=patch_validation_output,
        )
    except Exception as error:
        return fallback_patch_response(request, error)


def fallback_patch_response(
    request: PatchSuggestionRequest,
    error: Exception,
) -> PatchSuggestionResponse:
    return PatchSuggestionResponse(
        incident_id=request.incident_id,
        root_cause=request.suspected_root_cause,
        patch_summary=(
            "Patch generation failed, so OpsLens returned a safe fallback response."
        ),
        suggested_diff="",
        risk_level="NEEDS_REVIEW",
        requires_human_review=True,
        patch_valid=False,
        patch_validation_output=(
            "Patch validation was skipped because patch generation failed: "
            f"{type(error).__name__} - {error}"
        ),
    )
