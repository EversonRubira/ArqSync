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
REPORT_PDF_NAME = "report.pdf"


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Generate report.html from report.json")
    parser.add_argument("--json-path", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument(
        "--pdf", action="store_true",
        help="Also generate report.pdf (requires the optional 'weasyprint' package)"
    )
    return parser.parse_args(argv)


def load_report_data(json_path: Path) -> dict:
    with json_path.open("r", encoding="utf-8") as f:
        return json.load(f)


MERMAID_INIT = (
    "%%{init: { 'theme': 'base', "
    "'themeVariables': { 'fontSize': '18px', 'nodeFontSize': '18px' }, "
    "'flowchart': { 'nodeSpacing': 150, 'rankSpacing': 150, 'nodeWidth': 200, "
    "'wrap': true, 'maxWidth': 200 } } }%%"
)
CYCLE_EDGE_STYLE = "stroke:#c11d3a,stroke-width:2.5px"
WRAP_AFTER_SEGMENTS = 3


def _cycle_edge_pairs(cycles: list) -> set:
    """Directed (from, to) package-name pairs that appear as a consecutive
    step in some cycle's path - used to highlight those edges in the diagram."""
    pairs = set()
    for cycle in cycles:
        path = cycle.get("path", [])
        for i in range(len(path) - 1):
            pairs.add((path[i]["value"], path[i + 1]["value"]))
    return pairs


def wrap_package_name_for_diagram(name: str) -> str:
    """Splits a package name with more than WRAP_AFTER_SEGMENTS dot-separated
    segments into 2 lines (manual break, `<br/>`), roughly in half by segment
    count - Mermaid's own `wrap`/`maxWidth` are heuristic and don't reliably
    break on `.`, so a long fully-qualified name can still overflow the node
    without this. E.g. "br.com.caelum.cursos.adapters.database.jpa.entity"
    (8 segments) -> "br.com.caelum.cursos<br/>adapters.database.jpa.entity".
    Short names (<= WRAP_AFTER_SEGMENTS segments) are returned unchanged."""
    segments = name.split(".")
    if len(segments) <= WRAP_AFTER_SEGMENTS:
        return name
    midpoint = (len(segments) + 1) // 2
    first_line = ".".join(segments[:midpoint])
    second_line = ".".join(segments[midpoint:])
    return f"{first_line}<br/>{second_line}"


def build_mermaid_diagram(dependency_graph: dict, cycles: list = None) -> str:
    """Builds Mermaid flowchart syntax straight from the graph's nodes/edges -
    a direct structural transcription, not an interpretation of the data.
    Edges that are part of a cycle (per `cycles`) are highlighted via
    `linkStyle`, straight from data already computed by the Analyzer."""
    nodes = dependency_graph.get("nodes", [])
    edges = dependency_graph.get("edges", [])
    cycle_edges = _cycle_edge_pairs(cycles or [])

    lines = [MERMAID_INIT, "graph TD"]

    if not nodes:
        lines.append('    empty["(no internal package dependencies found)"]')
        return "\n".join(lines)

    node_ids = {}
    for index, node in enumerate(nodes):
        name = node["value"]
        node_id = f"n{index}"
        node_ids[name] = node_id
        label = wrap_package_name_for_diagram(name)
        lines.append(f'    {node_id}["{label}"]')

    link_styles = []
    for edge_index, edge in enumerate(edges):
        from_name = edge["from"]["value"]
        to_name = edge["to"]["value"]
        from_id = node_ids.get(from_name, from_name)
        to_id = node_ids.get(to_name, to_name)
        lines.append(f"    {from_id} --> {to_id}")
        if (from_name, to_name) in cycle_edges:
            link_styles.append(f"    linkStyle {edge_index} {CYCLE_EDGE_STYLE};")

    lines.extend(link_styles)

    return "\n".join(lines)


def _wrap_package_name_for_graphviz(name: str) -> str:
    """Same wrapping rule as wrap_package_name_for_diagram (>WRAP_AFTER_SEGMENTS
    segments, split ~in half), but joined with the DOT label line-break escape
    (literal `\\n`, verified against graphviz.Digraph.source) instead of the
    HTML `<br/>` Mermaid's htmlLabels understands."""
    segments = name.split(".")
    if len(segments) <= WRAP_AFTER_SEGMENTS:
        return name
    midpoint = (len(segments) + 1) // 2
    first_line = ".".join(segments[:midpoint])
    second_line = ".".join(segments[midpoint:])
    return f"{first_line}\\n{second_line}"


def build_pdf_diagram_svg(dependency_graph: dict, cycles: list, violations: list) -> str:
    """A small, static SVG (rendered server-side via Graphviz) of only the
    packages involved in a cycle or a layer violation - the full Mermaid
    graph is client-side rendered (needs JS) and, for a large project, too
    dense to stay legible once printed to a fixed page size. This is
    deliberately a *different*, purpose-built diagram for print/PDF, not a
    re-render of the Mermaid one: scoped down to just the packages worth
    calling out keeps it small and legible regardless of overall project size.

    `cycles` is the raw cycles list (list of {"path": [{"value": ...}, ...]}
    dicts, as in report_data["cycles"]); `violations` is the already-flattened
    violations_view (list of {"from": str, "to": str, ...} dicts).

    Returns "" (never raises) if there are no problem packages, if the
    optional 'graphviz' package isn't installed, or if the 'dot' binary it
    shells out to isn't on PATH - the same graceful-degradation contract as
    generate_pdf() for WeasyPrint. The template falls back to explanatory
    text in that case.
    """
    problem_packages = set()
    for cycle in cycles:
        for pkg in cycle.get("path", []):
            problem_packages.add(pkg["value"])
    for violation in violations:
        problem_packages.add(violation["from"])
        problem_packages.add(violation["to"])

    if not problem_packages:
        return ""

    try:
        import graphviz
    except ImportError as e:
        print(
            f"Warning: PDF diagram skipped - the optional 'graphviz' package is not "
            f"installed ({e}). Install it with: pip install graphviz",
            file=sys.stderr,
        )
        return ""

    cycle_edges = _cycle_edge_pairs(cycles)
    relevant_edges = [
        edge for edge in dependency_graph.get("edges", [])
        if edge["from"]["value"] in problem_packages and edge["to"]["value"] in problem_packages
    ]

    graph = graphviz.Digraph()
    graph.attr(rankdir="TB", nodesep="0.5", ranksep="0.6", bgcolor="white")
    graph.attr("node", shape="box", style="rounded,filled", fillcolor="#eaf0fe",
               color="#c7d7fb", fontname="Helvetica,Arial,sans-serif", fontsize="11")
    graph.attr("edge", fontname="Helvetica,Arial,sans-serif", fontsize="9", color="#5c6472")

    for package in sorted(problem_packages):
        graph.node(package, label=_wrap_package_name_for_graphviz(package))

    for edge in relevant_edges:
        from_name = edge["from"]["value"]
        to_name = edge["to"]["value"]
        if (from_name, to_name) in cycle_edges:
            graph.edge(from_name, to_name, color="#c11d3a", penwidth="2")
        else:
            graph.edge(from_name, to_name)

    try:
        svg_bytes = graph.pipe(format="svg")
        return svg_bytes.decode("utf-8")
    except Exception as e:
        # Broad on purpose, same rationale as generate_pdf(): graphviz.pipe()
        # raises different exception types across versions/platforms when
        # the 'dot' binary itself is missing (ExecutableNotFound) or fails
        # (CalledProcessError) - none of them should crash the whole script.
        print(
            f"Warning: PDF diagram skipped - the Graphviz 'dot' executable is not "
            f"available or failed ({e}). Install Graphviz from https://graphviz.org/download/ "
            f"and ensure 'dot' is on PATH.",
            file=sys.stderr,
        )
        return ""


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
            # bounded sample size (SPEC-analyzer.md, 2.2 - up to 5), used only
            # as a relative impact signal for prioritization, not an exact count
            "class_sample_count": len(violation.get("classSamples", [])),
        }
        for violation in violations
    ]


def build_architecture_style_view(architecture_style: dict) -> dict:
    return {
        "name": architecture_style.get("name", "Não identificado"),
        "description": architecture_style.get("description", ""),
    }


def build_dependency_counts_view(metrics: dict) -> list:
    """Sorted by incoming dependencies (descending) so the most-depended-on
    packages - the highest-coupling points - surface first."""
    counts = [
        {
            "package": count["pkg"]["value"],
            "incoming": count["incoming"],
            "outgoing": count["outgoing"],
        }
        for count in metrics.get("dependencyCounts", [])
    ]
    return sorted(counts, key=lambda c: c["incoming"], reverse=True)


def _common_root_segments(package_names: list) -> list:
    """The dot-separated segments shared by every package name (e.g. ["com",
    "acme"] for "com.acme.matricula.service" + "com.acme.pagamento.domain") -
    used to strip the shared project prefix before picking a "module" name."""
    if not package_names:
        return []
    split = [name.split(".") for name in package_names]
    common = []
    for segments_at_depth in zip(*split):
        if len(set(segments_at_depth)) == 1:
            common.append(segments_at_depth[0])
        else:
            break
    return common


def _module_of(package_name: str, root_segments: list) -> str:
    """The first package segment after the shared project root - used purely
    for grouping/labeling in the report, e.g. "matricula" for
    "com.acme.matricula.service" when the root is ["com", "acme"]."""
    segments = package_name.split(".")
    if len(segments) > len(root_segments):
        return segments[len(root_segments)]
    return segments[-1] if segments else package_name


def build_cycle_groups(cycles_view: list, raw_cycles: list, dependency_graph: dict) -> list:
    """Groups cycles by the module(s) their packages belong to (e.g. "Ciclos
    no módulo matricula (4 ciclos)"), so a report with many cycles doesn't
    read as one undifferentiated wall of cards."""
    all_packages = [n["value"] for n in dependency_graph.get("nodes", [])]
    root = _common_root_segments(all_packages)

    groups = {}
    order = []
    for view, raw in zip(cycles_view, raw_cycles):
        packages = [pkg["value"] for pkg in raw["path"][:-1]]
        modules = sorted(set(_module_of(p, root) for p in packages))
        if len(modules) == 1:
            key = modules[0]
            label = f"Ciclos no módulo {modules[0]}"
        else:
            key = " + ".join(modules)
            label = f"Ciclos entre os módulos {' e '.join(modules)}"

        if key not in groups:
            groups[key] = {"label": label, "cycles": []}
            order.append(key)
        groups[key]["cycles"].append(view)

    result = []
    for key in order:
        group = groups[key]
        count = len(group["cycles"])
        result.append({
            "label": f"{group['label']} ({count} {_plural(count, 'ciclo', 'ciclos')})",
            "cycles": group["cycles"],
        })
    return result


def build_hotspot_modules(raw_cycles: list, violations_view: list, dependency_graph: dict, top_n: int = 2) -> list:
    """The modules with the most cycle/violation occurrences, for the
    executive summary's "concentrado em" callout - a simple tally over data
    already computed, not a new analysis."""
    all_packages = [n["value"] for n in dependency_graph.get("nodes", [])]
    root = _common_root_segments(all_packages)

    tally = {}
    for cycle in raw_cycles:
        for pkg in cycle["path"][:-1]:
            module = _module_of(pkg["value"], root)
            tally[module] = tally.get(module, 0) + 1
    for violation in violations_view:
        for pkg in (violation["from"], violation["to"]):
            module = _module_of(pkg, root)
            tally[module] = tally.get(module, 0) + 1

    ranked = sorted(tally.items(), key=lambda kv: kv[1], reverse=True)
    return [{"module": module, "count": count} for module, count in ranked[:top_n] if count > 0]


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
    by the Analyzer) into one actionable list, ranked by impact (descending)
    so the highest-leverage fix surfaces first: for a cycle, the number of
    packages it spans; for a violation, its class-sample size. Both are real
    numbers already present in the data, not a fabricated "affects N
    classes" estimate."""
    suggestions = [
        {
            "kind": "violation",
            "context": f"{v['from']} → {v['to']}",
            "text": v["suggestion"],
            "impact": v["class_sample_count"],
            "impact_label": (
                f"{v['class_sample_count']} {_plural(v['class_sample_count'], 'classe', 'classes')} (amostra)"
                if v["class_sample_count"] else ""
            ),
        }
        for v in violations_view
    ]
    suggestions += [
        {
            "kind": "cycle",
            "context": c["path"],
            "text": c["suggestion"],
            "impact": c["length"],
            "impact_label": f"{c['length']} {_plural(c['length'], 'pacote', 'pacotes')}",
        }
        for c in cycles_view
    ]
    suggestions.sort(key=lambda item: item["impact"], reverse=True)
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
    raw_cycles = report_data.get("cycles", [])
    cycles_view = build_cycles_view(raw_cycles)
    violations_view = build_violations_view(report_data.get("violations", []))

    return template.render(
        project_name=report_data.get("projectName", ""),
        root_path=report_data.get("rootPath", ""),
        generated_at=format_generated_at(report_data.get("generatedAt", "")),
        status=build_status_view(metrics),
        metrics_summary=build_metrics_summary_view(metrics, dependency_graph),
        hotspots=build_hotspot_modules(raw_cycles, violations_view, dependency_graph),
        dependency_counts=build_dependency_counts_view(metrics),
        cycles=cycles_view,
        cycle_groups=build_cycle_groups(cycles_view, raw_cycles, dependency_graph),
        violations=violations_view,
        suggestions=build_suggestions_view(violations_view, cycles_view),
        mermaid_diagram=build_mermaid_diagram(dependency_graph, raw_cycles),
        pdf_diagram_svg=build_pdf_diagram_svg(dependency_graph, raw_cycles, violations_view),
        architecture_style=build_architecture_style_view(report_data.get("architectureStyle", {})),
    )


def generate_pdf(html: str, output_path: Path) -> bool:
    """Best-effort report.pdf generation via WeasyPrint. Optional and never
    fatal - mirrors the graceful degradation already used for report.html
    itself when Python/an interpreter isn't found (DefaultHtmlReportGenerator):
    a missing 'weasyprint' package, or its native Pango/cairo libraries not
    being installed (a common gap on Windows, where they need a separate GTK
    runtime install - see the link in the warning below), just skips the PDF
    with a warning instead of failing the whole run.

    Note: WeasyPrint renders HTML/CSS only, it does not execute JavaScript,
    so the Mermaid diagram (client-side rendered) appears as its raw source
    text in the PDF rather than the visual graph - report.html remains the
    place to view the rendered diagram. The template's print stylesheet
    adds a note explaining this in the PDF/print output.
    """
    try:
        # WeasyPrint raises OSError (not ImportError) at import time when its
        # native Pango/cairo libraries can't be loaded - e.g. on Windows
        # without the GTK runtime installed - even if the Python package
        # itself is present. Both cases mean "the optional dependency isn't
        # usable here", so both are handled the same way.
        from weasyprint import HTML
    except (ImportError, OSError) as e:
        print(
            f"Warning: report.pdf was not generated - could not load the optional "
            f"'weasyprint' package or its native libraries ({e}). Install it with: "
            f"pip install weasyprint (on Windows, native libraries also require the GTK "
            f"runtime - see "
            f"https://doc.courtbouillon.org/weasyprint/stable/first_steps.html#installation)",
            file=sys.stderr,
        )
        return False

    try:
        HTML(string=html).write_pdf(str(output_path))
        return True
    except Exception as e:
        # Broad on purpose: WeasyPrint's rendering step can raise a variety of
        # internal exceptions depending on the input (fonts, malformed CSS,
        # etc.) - PDF generation is explicitly best-effort and must never
        # crash the whole script over it.
        print(f"Warning: report.pdf generation failed ({e})", file=sys.stderr)
        return False


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

    if args.pdf:
        generate_pdf(html, args.output_dir / REPORT_PDF_NAME)

    return 0


if __name__ == "__main__":
    sys.exit(main())
