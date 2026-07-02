from typing import TypedDict

from langgraph.graph import END, StateGraph


class IncidentRepairState(TypedDict):
    incident_id: int
    summary: str
    suspected_root_cause: str
    recommended_action: str
    code_results: list[dict]
    root_cause: str | None
    patch_summary: str | None
    suggested_diff: str | None
    risk_level: str | None
    requires_human_review: bool


def root_cause_node(state: IncidentRepairState):
    code_context = "\n\n".join(
        f"File: {item.get('file_path')}\n{item.get('snippet')}"
        for item in state["code_results"]
    )

    root_cause = (
        f"{state['suspected_root_cause']}\n\n"
        f"Relevant code context:\n{code_context}"
    )

    return {
        "root_cause": root_cause,
    }


def patch_suggestion_node(state: IncidentRepairState):
    first_file = (
        state["code_results"][0]["file_path"]
        if state["code_results"]
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


def build_incident_repair_graph():
    graph = StateGraph(IncidentRepairState)

    graph.add_node("root_cause", root_cause_node)
    graph.add_node("patch_suggestion", patch_suggestion_node)

    graph.set_entry_point("root_cause")
    graph.add_edge("root_cause", "patch_suggestion")
    graph.add_edge("patch_suggestion", END)

    return graph.compile()


incident_repair_graph = build_incident_repair_graph()
