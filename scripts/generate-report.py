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

    return template.render(
        project_name=report_data.get("projectName", ""),
        root_path=report_data.get("rootPath", ""),
        generated_at=format_generated_at(report_data.get("generatedAt", "")),
        metrics=metrics,
        dependency_counts=build_dependency_counts_view(metrics),
        cycles=build_cycles_view(report_data.get("cycles", [])),
        violations=build_violations_view(report_data.get("violations", [])),
        mermaid_diagram=build_mermaid_diagram(report_data.get("dependencyGraph", {})),
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
