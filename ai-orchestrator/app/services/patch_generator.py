import json
import os

from openai import OpenAI


def generate_patch_suggestion(state: dict) -> dict:
    if not os.getenv("OPENAI_API_KEY"):
        return placeholder_patch_suggestion(state)

    try:
        return openai_patch_suggestion(state)
    # If OpenAI fails, return safe fallback
    except Exception as error:
        fallback = placeholder_patch_suggestion(state)
        return {
            # Copy all key/value pairs from fallback, then override specific fields
            **fallback,
            "patch_summary": (
                "AI patch generation failed, so OpsLens returned a safe fallback "
                "patch suggestion."
            ),
            "suggested_diff": (
                fallback["suggested_diff"]
                + f"\n# AI patch generation error: {str(error)}\n"
            ),
            "risk_level": "NEEDS_REVIEW",
            "requires_human_review": True,
        }


def placeholder_patch_suggestion(state: dict) -> dict:
    first_file = (
        # Take the first code search result and read its file path
        state["code_results"][0]["file_path"]
        if state.get("code_results")
        else "unknown file"
    )

    return {
        "patch_summary": (
            "Review the related code and add validation around the failing path."
        ),
        "suggested_diff": (
            f"--- a/{first_file}\n"
            f"+++ b/{first_file}\n"
            "@@ suggested change @@\n"
            "+ Add null checks or input validation before saving.\n"
        ),
        "risk_level": "LOW",
        "requires_human_review": True,
    }


def openai_patch_suggestion(state: dict) -> dict:
    client = OpenAI()
    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

    response = client.responses.create(
        model=model,
        input=build_patch_prompt(state),
        text={
            "format": {
                "type": "json_object"
            }
        },
    )

    data = json.loads(response.output_text)

    return {
        "patch_summary": data["patch_summary"],
        "suggested_diff": data["suggested_diff"],
        "risk_level": data["risk_level"],
        "requires_human_review": bool(data["requires_human_review"]),
    }

# This function turns state into a clear AI instruction
def build_patch_prompt(state: dict) -> str:
    code_context = "\n\n".join(
        (
            f"File: {item.get('file_path')}\n"
            f"Repository: {item.get('repository')}\n"
            f"Reason: {item.get('relevance_reason')}\n"
            "Snippet:\n"
            f"{item.get('snippet')}"
        )
        for item in state.get("code_results", [])
    )

    return f"""
You are a cautious senior backend engineer helping with incident repair.

Generate a minimal patch suggestion based only on the incident report and code
snippets below. Return only valid JSON.

Safety rules:
- Do not claim the patch has been applied.
- Do not modify more than 3 files.
- Do not include secrets.
- Do not invent files that are not present in the code context.
- Prefer small validation, null-handling, or guard-clause fixes.
- If there is not enough context for a real diff, return a conservative
  suggested diff with comments and set requires_human_review to true.

Required JSON shape:
{{
  "patch_summary": "short explanation of the proposed fix",
  "suggested_diff": "unified diff-style patch suggestion",
  "risk_level": "LOW|MEDIUM|HIGH|NEEDS_REVIEW",
  "requires_human_review": true
}}

Incident ID: {state.get("incident_id")}

Incident summary:
{state.get("summary")}

Suspected root cause:
{state.get("suspected_root_cause")}

Recommended action:
{state.get("recommended_action")}

Root cause reasoning:
{state.get("root_cause")}

Code context:
{code_context}
""".strip()
