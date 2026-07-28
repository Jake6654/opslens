import difflib
import json

from openai import OpenAI

from app.config import settings


PATCH_RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "patch_summary": {"type": "string"},
        "patched_file_content": {"type": "string"},
        "risk_level": {
            "type": "string",
            "enum": ["LOW", "MEDIUM", "HIGH", "NEEDS_REVIEW"],
        },
        "requires_human_review": {"type": "boolean"},
    },
    "required": [
        "patch_summary",
        "patched_file_content",
        "risk_level",
        "requires_human_review",
    ],
    "additionalProperties": False,
}


def generate_patch_suggestion(state: dict) -> dict:
    if not settings.openai_api_key:
        return placeholder_patch_suggestion(state)

    try:
        return openai_patch_suggestion(state)
    except Exception as error:
        fallback = placeholder_patch_suggestion(state)
        return {
            **fallback,
            "patch_summary": (
                "AI patch generation failed, so OpsLens returned a safe fallback "
                f"response. Error: {type(error).__name__} - {error}"
            ),
            "suggested_diff": "",
            "risk_level": "NEEDS_REVIEW",
            "requires_human_review": True,
        }


def placeholder_patch_suggestion(state: dict) -> dict:
    target = select_target_code_result(state)
    target_file = target.get("file_path") or "unknown file"

    return {
        "patch_summary": (
            "OpenAI patch generation is unavailable. Review the related code in "
            f"{target_file} and create a minimal validated diff manually."
        ),
        "suggested_diff": "",
        "risk_level": "NEEDS_REVIEW",
        "requires_human_review": True,
    }


def openai_patch_suggestion(state: dict) -> dict:
    target = select_target_code_result(state)
    target_file = target.get("file_path") or "unknown file"
    original_content = target.get("snippet") or ""

    if not is_full_file_context(original_content):
        return {
            "patch_summary": (
                "Could not generate a safe patch because the target context is "
                "not a complete source file."
            ),
            "suggested_diff": "",
            "risk_level": "NEEDS_REVIEW",
            "requires_human_review": True,
        }

    client = OpenAI(api_key=settings.openai_api_key)

    response = client.responses.create(
        model=settings.openai_model,
        input=build_patch_prompt(state),
        text={
            "format": {
                "type": "json_schema",
                "name": "patch_suggestion",
                "schema": PATCH_RESPONSE_SCHEMA,
                "strict": True,
            }
        },
    )

    data = json.loads(response.output_text)
    patched_content = data["patched_file_content"]
    suggested_diff = build_unified_diff(target_file, original_content, patched_content)

    risk_level = data["risk_level"]
    if not suggested_diff:
        risk_level = "NEEDS_REVIEW"

    return {
        "patch_summary": data["patch_summary"],
        "suggested_diff": suggested_diff,
        "risk_level": risk_level,
        "requires_human_review": bool(data["requires_human_review"]),
    }


# This function turns state into a strict AI instruction for generating a patched file.
def build_patch_prompt(state: dict) -> str:
    target = select_target_code_result(state)
    target_file = target.get("file_path") or "unknown file"
    target_repository = target.get("repository") or "unknown repository"
    target_reason = target.get("relevance_reason") or "No relevance reason provided."
    target_snippet = target.get("snippet") or "No code snippet provided."

    return f"""
You are a cautious senior backend engineer helping with incident repair.

Generate a minimal patch suggestion based only on the incident report and the
complete target file below. Return only valid JSON that matches the required schema.

Important implementation rule:
- Do not write a unified diff yourself.
- Return the complete modified target file content in patched_file_content.
- OpsLens will generate the unified diff programmatically.

Safety rules:
- Do not claim the patch has been applied.
- Do not include secrets.
- Prefer small validation, null-handling, or guard-clause fixes.
- Modify only the target file for this MVP.
- Do not invent classes, methods, DTOs, imports, packages, or fields that are not visible in the target file.
- Preserve the package declaration, imports, annotations, class name, existing methods, and existing comments unless the fix directly requires changing them.
- If the file already contains the needed validation, return patched_file_content exactly equal to the original target file content, set risk_level to NEEDS_REVIEW, and explain that no safe code change is needed.
- If the target file is not enough to produce a safe fix, return patched_file_content exactly equal to the original target file content, set risk_level to NEEDS_REVIEW, and explain the limitation in patch_summary.

Required JSON shape:
{{
  "patch_summary": "short explanation of the proposed fix or why no safe change could be generated",
  "patched_file_content": "the complete modified target file content, not a diff",
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

Target code result:
File: {target_file}
Repository: {target_repository}
Reason: {target_reason}

Complete target file content:
{target_snippet}
""".strip()


def select_target_code_result(state: dict) -> dict:
    code_results = state.get("code_results") or []
    if not code_results:
        return {}

    return max(
        code_results,
        key=lambda item: item.get("score") or 0,
    )


def is_full_file_context(content: str) -> bool:
    return "package " in content and "class " in content


def build_unified_diff(
    target_file: str,
    original_content: str,
    patched_content: str,
) -> str:
    original = ensure_trailing_newline(original_content).splitlines(keepends=True)
    patched = ensure_trailing_newline(patched_content).splitlines(keepends=True)

    if original == patched:
        return ""

    diff_lines = difflib.unified_diff(
        original,
        patched,
        fromfile=f"a/{target_file}",
        tofile=f"b/{target_file}",
        lineterm="",
    )

    return "\n".join(line.rstrip("\n") for line in diff_lines) + "\n"


def ensure_trailing_newline(content: str) -> str:
    if content.endswith("\n"):
        return content

    return content + "\n"
