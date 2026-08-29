#!/usr/bin/env python3
"""Renders report.html from report.json (SPEC-exporter.md).

Reads the JSON contract written by the Java side (DefaultJsonExporter) and
renders templates/report.html.j2 via Jinja2. Never recomputes or
reinterprets analysis data (SPEC-exporter.md, 2.5) - only formats what is
already in the JSON for display.
"""

import argparse
import json
import sys
from pathlib import Path

from jinja2 import Environment, FileSystemLoader, TemplateError, select_autoescape

SCRIPT_DIR = Path(__file__).resolve().parent
TEMPLATE_DIR = SCRIPT_DIR.parent / "templates"
REPORT_HTML_NAME = "report.html"


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Generate report.html from report.json")
    parser.add_argument("--json-path", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    return parser.parse_args(argv)


def load_report_data(json_path: Path) -> dict:
    with json_path.open("r", encoding="utf-8") as f:
        return json.load(f)


def build_mermaid_diagram(dependency_graph: dict) -> str:
    """Builds Mermaid flowchart syntax straight from the graph's nodes/edges -
    a direct structural transcription, not an interpretation of the data."""
    nodes = dependency_graph.get("nodes", [])
    edges = dependency_graph.get("edges", [])

    lines = ["graph TD"]

    if not nodes:
        lines.append('    empty["(no internal package dependencies found)"]')
        return "\n".join(lines)

    node_ids = {}
    for index, node in enumerate(nodes):
        name = node["value"]
        node_id = f"n{index}"
        node_ids[name] = node_id
        lines.append(f'    {node_id}["{name}"]')

    for edge in edges:
        from_name = edge["from"]["value"]
        to_name = edge["to"]["value"]
        from_id = node_ids.get(from_name, from_name)
        to_id = node_ids.get(to_name, to_name)
        lines.append(f"    {from_id} --> {to_id}")

    return "\n".join(lines)


def build_cycles_view(cycles: list) -> list:
    return [
        {
            "path": " → ".join(pkg["value"] for pkg in cycle["path"]),
            "length": len(cycle["path"]) - 1,
            "explanation": cycle["explanation"],
            "suggestion": cycle["suggestion"],
        }
        for cycle in cycles
    ]


def build_violations_view(violations: list) -> list:
    return [
        {
            "from": violation["from"]["value"],
            "to": violation["to"]["value"],
            "from_layer": violation["fromLayer"],
            "to_layer": violation["toLayer"],
            "type": violation["type"],
            "explanation": violation["explanation"],
            "suggestion": violation["suggestion"],
        }
        for violation in violations
    ]


def build_architecture_style_view(architecture_style: dict) -> dict:
    return {
        "name": architecture_style.get("name", "Não identificado"),
        "description": architecture_style.get("description", ""),
    }


def build_dependency_counts_view(metrics: dict) -> list:
    return [
        {
            "package": count["pkg"]["value"],
            "incoming": count["incoming"],
            "outgoing": count["outgoing"],
        }
        for count in metrics.get("dependencyCounts", [])
    ]


def build_metrics_summary_view(metrics: dict, dependency_graph: dict) -> dict:
    """The 5 headline numbers (Métricas table): packages, classes, internal
    dependencies (edge count - a direct structural count, not a new metric),
    cycles, violations."""
    return {
        "total_packages": metrics.get("totalPackages", 0),
        "total_classes": metrics.get("totalClasses", 0),
        "total_dependencies": len(dependency_graph.get("edges", [])),
        "cycle_count": metrics.get("cycleCount", 0),
        "violation_count": metrics.get("violationCount", 0),
    }


def _plural(count: int, singular: str, plural: str) -> str:
    return singular if count == 1 else plural


def build_status_view(metrics: dict) -> dict:
    """The header's one-line status summary (e.g. "2 violações críticas") and
    its severity, derived directly from the already-computed cycle/violation
    counts - just phrasing them as a sentence, not a new judgment."""
    violation_count = metrics.get("violationCount", 0)
    cycle_count = metrics.get("cycleCount", 0)

    violation_phrase = f"{violation_count} " + _plural(
        violation_count, "violação crítica", "violações críticas"
    )
    cycle_phrase = f"{cycle_count} " + _plural(
        cycle_count, "ciclo de dependência", "ciclos de dependência"
    )

    if violation_count and cycle_count:
        return {"headline": f"{violation_phrase} e {cycle_phrase}", "severity": "critical"}
    if violation_count:
        return {"headline": violation_phrase, "severity": "critical"}
    if cycle_count:
        return {"headline": cycle_phrase, "severity": "warning"}
    return {"headline": "Nenhum problema estrutural detectado", "severity": "ok"}


def build_suggestions_view(violations_view: list, cycles_view: list) -> list:
    """Consolidates each violation's and cycle's suggestion (already computed
    by the Analyzer) into one actionable list - re-presented, not reinterpreted."""
    suggestions = [
        {"kind": "violation", "context": f"{v['from']} → {v['to']}", "text": v["suggestion"]}
        for v in violations_view
    ]
    suggestions += [
        {"kind": "cycle", "context": c["path"], "text": c["suggestion"]}
        for c in cycles_view
    ]
    return suggestions


def format_generated_at(raw: str) -> str:
    # Purely cosmetic: drop sub-second precision and the ISO 'T' separator.
    # Same instant, just easier to read - not a reinterpretation of the data.
    if not raw:
        return ""
    return raw.split(".")[0].replace("T", " ") + " UTC"


def render_html(report_data: dict) -> str:
    env = Environment(
        loader=FileSystemLoader(str(TEMPLATE_DIR)),
        autoescape=select_autoescape(["html", "j2"]),
    )
    template = env.get_template("report.html.j2")

    metrics = report_data.get("metrics", {})
    dependency_graph = report_data.get("dependencyGraph", {})
    cycles_view = build_cycles_view(report_data.get("cycles", []))
    violations_view = build_violations_view(report_data.get("violations", []))

    return template.render(
        project_name=report_data.get("projectName", ""),
        root_path=report_data.get("rootPath", ""),
        generated_at=format_generated_at(report_data.get("generatedAt", "")),
        status=build_status_view(metrics),
        metrics_summary=build_metrics_summary_view(metrics, dependency_graph),
        dependency_counts=build_dependency_counts_view(metrics),
        cycles=cycles_view,
        violations=violations_view,
        suggestions=build_suggestions_view(violations_view, cycles_view),
        mermaid_diagram=build_mermaid_diagram(dependency_graph),
        architecture_style=build_architecture_style_view(report_data.get("architectureStyle", {})),
    )


def main(argv=None) -> int:
    args = parse_args(argv)

    try:
        report_data = load_report_data(args.json_path)
    except (OSError, json.JSONDecodeError) as e:
        print(f"Error: could not read report.json at {args.json_path}: {e}", file=sys.stderr)
        return 1

    try:
        html = render_html(report_data)
    except (TemplateError, KeyError) as e:
        print(f"Error: failed to render {REPORT_HTML_NAME}: {e}", file=sys.stderr)
        return 1

    output_path = args.output_dir / REPORT_HTML_NAME
    try:
        args.output_dir.mkdir(parents=True, exist_ok=True)
        output_path.write_text(html, encoding="utf-8")
    except OSError as e:
        print(f"Error: could not write {output_path}: {e}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
