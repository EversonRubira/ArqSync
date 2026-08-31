"""Tests for generate-report.py (SPEC-exporter.md, section 5 - lado Python)."""

import importlib.util
import json
import sys
from pathlib import Path

import pytest

SCRIPTS_DIR = Path(__file__).resolve().parent.parent

# "generate-report.py" has a hyphen, so it can't be imported as a normal
# module name (`import generate_report`) - load it from its file path instead.
_spec = importlib.util.spec_from_file_location(
    "generate_report", SCRIPTS_DIR / "generate-report.py"
)
gr = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gr)

FIXTURES_DIR = (
    SCRIPTS_DIR.parent / "src" / "test" / "resources" / "fixtures" / "exporter"
)


def load_fixture(name: str) -> dict:
    with (FIXTURES_DIR / name).open("r", encoding="utf-8") as f:
        return json.load(f)


def test_report_with_cycle_and_violation_renders_mermaid_cycles_and_violations(tmp_path, monkeypatch):
    report_data = load_fixture("with-cycle-and-violation.json")
    # Forces the Graphviz 'dot' binary to look unavailable regardless of
    # whether it's actually installed on the machine running this test -
    # render_html must still succeed and fall back to text, never crash.
    monkeypatch.setattr(gr, "build_pdf_diagram_svg", lambda *a, **k: "")

    html = gr.render_html(report_data)

    assert "graph TD" in html
    assert '"com.acme.controller"' in html or "&#34;com.acme.controller&#34;" in html
    assert "com.acme.a → com.acme.b → com.acme.a" in html
    assert "OrderController depende diretamente de OrderRepository" in html
    assert "Introduza a chamada através de service" in html  # violation suggestion
    assert "Quebre o ciclo extraindo" in html  # cycle suggestion
    assert "Arquitetura detectada: Arquitetura em Camadas (Layered)" in html
    assert "1 violação crítica e 1 ciclo de dependência" in html  # header status summary
    assert "Sugestões de melhoria" in html
    assert "Gerado por ArqSync v1.2.0" in html
    # pdf_diagram_only: with pdf_diagram_svg forced empty above, and problem
    # packages present in this fixture, it must fall back to text.
    assert "pdf-diagram-only" in html
    assert "Graphviz não está disponível" in html


def test_report_without_cycles_or_violations_renders_empty_state(tmp_path):
    report_data = load_fixture("empty.json")

    html = gr.render_html(report_data)

    assert "Nenhuma violação de camada detectada." in html
    assert "Nenhum ciclo de dependência detectado." in html
    assert "Arquitetura detectada: Não identificado" in html
    assert "Nenhum problema estrutural detectado" in html  # header status summary
    assert "Nenhuma ação necessária" in html  # suggestions empty state
    # no problem packages at all - build_pdf_diagram_svg returns "" before
    # even trying Graphviz, and the template shows the "nothing to show" text
    assert "sem pacotes problemáticos" in html


def test_missing_json_path_exits_non_zero_with_stderr_message(tmp_path, capsys):
    missing_path = tmp_path / "does-not-exist.json"
    output_dir = tmp_path / "out"

    exit_code = gr.main(["--json-path", str(missing_path), "--output-dir", str(output_dir)])

    captured = capsys.readouterr()
    assert exit_code != 0
    assert "Error" in captured.err
    assert not (output_dir / "report.html").exists()


def test_malformed_json_exits_non_zero_with_stderr_message(tmp_path, capsys):
    malformed_path = tmp_path / "malformed.json"
    malformed_path.write_text("{not valid json", encoding="utf-8")
    output_dir = tmp_path / "out"

    exit_code = gr.main(["--json-path", str(malformed_path), "--output-dir", str(output_dir)])

    captured = capsys.readouterr()
    assert exit_code != 0
    assert "Error" in captured.err


def test_full_run_writes_report_html_to_output_dir(tmp_path):
    json_path = tmp_path / "report.json"
    json_path.write_text(
        json.dumps(load_fixture("with-cycle-and-violation.json")), encoding="utf-8"
    )
    output_dir = tmp_path / "out"

    exit_code = gr.main(["--json-path", str(json_path), "--output-dir", str(output_dir)])

    assert exit_code == 0
    assert (output_dir / "report.html").exists()


def test_mermaid_diagram_matches_a_small_known_graph():
    dependency_graph = {
        "nodes": [{"value": "a"}, {"value": "b"}],
        "edges": [{"from": {"value": "a"}, "to": {"value": "b"}}],
    }

    diagram = gr.build_mermaid_diagram(dependency_graph)

    lines = diagram.splitlines()
    assert lines[0] == gr.MERMAID_INIT
    assert lines[1] == "graph TD"
    assert '    n0["a"]' in lines
    assert '    n1["b"]' in lines
    assert "    n0 --> n1" in lines


def test_mermaid_diagram_for_empty_graph_does_not_crash():
    diagram = gr.build_mermaid_diagram({"nodes": [], "edges": []})

    assert "graph TD" in diagram


def test_mermaid_diagram_highlights_edges_that_are_part_of_a_cycle():
    dependency_graph = {
        "nodes": [{"value": "a"}, {"value": "b"}, {"value": "c"}],
        "edges": [
            {"from": {"value": "a"}, "to": {"value": "b"}},
            {"from": {"value": "b"}, "to": {"value": "a"}},
            {"from": {"value": "a"}, "to": {"value": "c"}},
        ],
    }
    cycles = [{"path": [{"value": "a"}, {"value": "b"}, {"value": "a"}]}]

    diagram = gr.build_mermaid_diagram(dependency_graph, cycles)

    lines = diagram.splitlines()
    # edge 0 (a->b) and edge 1 (b->a) are the cycle; edge 2 (a->c) is not.
    assert "    linkStyle 0 stroke:#c11d3a,stroke-width:2.5px;" in lines
    assert "    linkStyle 1 stroke:#c11d3a,stroke-width:2.5px;" in lines
    assert not any(line.startswith("    linkStyle 2 ") for line in lines)


def test_mermaid_diagram_without_cycles_has_no_link_styles():
    dependency_graph = {
        "nodes": [{"value": "a"}, {"value": "b"}],
        "edges": [{"from": {"value": "a"}, "to": {"value": "b"}}],
    }

    diagram = gr.build_mermaid_diagram(dependency_graph, cycles=[])

    assert "linkStyle" not in diagram


def test_status_view_with_no_problems_is_ok():
    status = gr.build_status_view({"violationCount": 0, "cycleCount": 0})

    assert status == {"headline": "Nenhum problema estrutural detectado", "severity": "ok"}


def test_status_view_with_only_violations_is_critical_and_singular():
    status = gr.build_status_view({"violationCount": 1, "cycleCount": 0})

    assert status == {"headline": "1 violação crítica", "severity": "critical"}


def test_status_view_with_only_cycles_is_warning_and_plural():
    status = gr.build_status_view({"violationCount": 0, "cycleCount": 2})

    assert status == {"headline": "2 ciclos de dependência", "severity": "warning"}


def test_status_view_with_both_combines_and_is_critical():
    status = gr.build_status_view({"violationCount": 2, "cycleCount": 1})

    assert status == {
        "headline": "2 violações críticas e 1 ciclo de dependência",
        "severity": "critical",
    }


def test_metrics_summary_view_counts_edges_as_total_dependencies():
    metrics = {"totalPackages": 3, "totalClasses": 5, "cycleCount": 1, "violationCount": 1}
    dependency_graph = {"edges": [{"from": {}, "to": {}}, {"from": {}, "to": {}}]}

    summary = gr.build_metrics_summary_view(metrics, dependency_graph)

    assert summary == {
        "total_packages": 3,
        "total_classes": 5,
        "total_dependencies": 2,
        "cycle_count": 1,
        "violation_count": 1,
    }


def test_suggestions_view_ranks_by_impact_descending():
    # violation impact = class_sample_count; cycle impact = length (packages spanned)
    violations_view = [{
        "from": "com.acme.controller", "to": "com.acme.repository",
        "suggestion": "fix violation", "class_sample_count": 1,
    }]
    cycles_view = [{
        "path": "com.acme.a → com.acme.b → com.acme.c → com.acme.a",
        "suggestion": "fix cycle", "length": 3,
    }]

    suggestions = gr.build_suggestions_view(violations_view, cycles_view)

    # the 3-package cycle (impact 3) outranks the 1-sample violation (impact 1)
    assert suggestions == [
        {
            "kind": "cycle",
            "context": "com.acme.a → com.acme.b → com.acme.c → com.acme.a",
            "text": "fix cycle",
            "impact": 3,
            "impact_label": "3 pacotes",
        },
        {
            "kind": "violation",
            "context": "com.acme.controller → com.acme.repository",
            "text": "fix violation",
            "impact": 1,
            "impact_label": "1 classe (amostra)",
        },
    ]


def test_suggestions_view_violation_without_class_samples_has_no_impact_label():
    violations_view = [{
        "from": "com.acme.a", "to": "com.acme.b",
        "suggestion": "fix it", "class_sample_count": 0,
    }]

    suggestions = gr.build_suggestions_view(violations_view, [])

    assert suggestions[0]["impact_label"] == ""


def test_ai_suggestions_view_is_a_straight_transcription_preserving_order():
    ai_suggestions = [
        {"type": "CYCLE_BREAK", "title": "Quebre o ciclo", "description": "d1", "codeExample": "interface X {}"},
        {"type": "GENERAL", "title": "Dica geral", "description": "d2", "codeExample": None},
    ]

    view = gr.build_ai_suggestions_view(ai_suggestions)

    assert view == [
        {"type": "CYCLE_BREAK", "title": "Quebre o ciclo", "description": "d1", "codeExample": "interface X {}"},
        {"type": "GENERAL", "title": "Dica geral", "description": "d2", "codeExample": None},
    ]


def test_ai_suggestions_view_defaults_to_empty_list_when_key_missing():
    assert gr.build_ai_suggestions_view([]) == []


def test_render_html_without_ai_suggestions_omits_the_section():
    report_data = load_fixture("with-cycle-and-violation.json")

    html = gr.render_html(report_data)

    assert "ai-suggestions" not in html
    assert "Sugestões de IA" not in html


def test_render_html_with_ai_suggestions_renders_the_section(monkeypatch):
    report_data = dict(load_fixture("with-cycle-and-violation.json"))
    report_data["aiSuggestions"] = [
        {"type": "CYCLE_BREAK", "title": "Quebre o ciclo A-B", "description": "Extraia uma interface.",
         "codeExample": "interface Shared {}"},
        {"type": "GENERAL", "title": "Dica geral", "description": "Adicione testes.", "codeExample": None},
    ]
    monkeypatch.setattr(gr, "build_pdf_diagram_svg", lambda *a, **k: "")

    html = gr.render_html(report_data)

    assert 'id="ai-suggestions"' in html
    assert "Sugestões de IA (Groq)" in html
    assert "Quebre o ciclo A-B" in html
    assert "interface Shared {}" in html
    assert "Dica geral" in html


def test_dependency_counts_view_sorted_by_incoming_descending():
    metrics = {
        "dependencyCounts": [
            {"pkg": {"value": "com.acme.low"}, "incoming": 1, "outgoing": 0},
            {"pkg": {"value": "com.acme.high"}, "incoming": 5, "outgoing": 2},
            {"pkg": {"value": "com.acme.mid"}, "incoming": 3, "outgoing": 1},
        ]
    }

    counts = gr.build_dependency_counts_view(metrics)

    assert [c["package"] for c in counts] == ["com.acme.high", "com.acme.mid", "com.acme.low"]


def test_cycle_groups_single_module_cycle_is_grouped_and_labeled():
    # A sibling module (pagamento) is included so the shared-prefix heuristic
    # stops at "com.acme", not "com.acme.matricula" - otherwise, with only
    # one module in the whole graph, "service"/"repository" would themselves
    # look like the module segment.
    dependency_graph = {"nodes": [
        {"value": "com.acme.matricula.service"}, {"value": "com.acme.matricula.repository"},
        {"value": "com.acme.pagamento.service"},
    ]}
    raw_cycles = [{"path": [
        {"value": "com.acme.matricula.service"}, {"value": "com.acme.matricula.repository"},
        {"value": "com.acme.matricula.service"},
    ]}]
    cycles_view = gr.build_cycles_view(
        [{**raw_cycles[0], "explanation": "e", "suggestion": "s"}]
    )

    groups = gr.build_cycle_groups(cycles_view, raw_cycles, dependency_graph)

    assert len(groups) == 1
    assert groups[0]["label"] == "Ciclos no módulo matricula (1 ciclo)"
    assert len(groups[0]["cycles"]) == 1


def test_cycle_groups_cross_module_cycle_is_labeled_as_between_modules():
    dependency_graph = {"nodes": [
        {"value": "com.acme.matricula.service"}, {"value": "com.acme.pagamento.service"},
    ]}
    raw_cycles = [{"path": [
        {"value": "com.acme.matricula.service"}, {"value": "com.acme.pagamento.service"},
        {"value": "com.acme.matricula.service"},
    ]}]
    cycles_view = gr.build_cycles_view(
        [{**raw_cycles[0], "explanation": "e", "suggestion": "s"}]
    )

    groups = gr.build_cycle_groups(cycles_view, raw_cycles, dependency_graph)

    assert groups[0]["label"] == "Ciclos entre os módulos matricula e pagamento (1 ciclo)"


def test_cycle_groups_multiple_cycles_in_same_module_are_combined_and_counted():
    dependency_graph = {"nodes": [
        {"value": "com.acme.matricula.a"}, {"value": "com.acme.matricula.b"},
        {"value": "com.acme.pagamento.x"},
    ]}
    one_cycle_path = [
        {"value": "com.acme.matricula.a"}, {"value": "com.acme.matricula.b"}, {"value": "com.acme.matricula.a"}
    ]
    raw_cycles = [{"path": one_cycle_path}, {"path": one_cycle_path}]
    cycles_view = gr.build_cycles_view(
        [{**c, "explanation": "e", "suggestion": "s"} for c in raw_cycles]
    )

    groups = gr.build_cycle_groups(cycles_view, raw_cycles, dependency_graph)

    assert len(groups) == 1
    assert groups[0]["label"] == "Ciclos no módulo matricula (2 ciclos)"


def test_hotspot_modules_ranks_module_with_most_problems_first():
    dependency_graph = {"nodes": [
        {"value": "com.acme.matricula.a"}, {"value": "com.acme.matricula.b"}, {"value": "com.acme.pagamento.x"},
    ]}
    raw_cycles = [{"path": [
        {"value": "com.acme.matricula.a"}, {"value": "com.acme.matricula.b"}, {"value": "com.acme.matricula.a"},
    ]}]
    violations_view = [{"from": "com.acme.matricula.a", "to": "com.acme.matricula.b"}]

    hotspots = gr.build_hotspot_modules(raw_cycles, violations_view, dependency_graph)

    assert hotspots[0]["module"] == "matricula"
    assert hotspots[0]["count"] > 0


def test_hotspot_modules_empty_when_no_cycles_or_violations():
    dependency_graph = {"nodes": [{"value": "com.acme.a"}]}

    hotspots = gr.build_hotspot_modules([], [], dependency_graph)

    assert hotspots == []


def test_mermaid_init_header_requests_18px_fonts():
    diagram = gr.build_mermaid_diagram({"nodes": [{"value": "a"}], "edges": []})

    assert "'fontSize': '18px'" in diagram
    assert "'nodeFontSize': '18px'" in diagram


def test_mermaid_init_header_requests_spacing_and_wrap_config():
    diagram = gr.build_mermaid_diagram({"nodes": [{"value": "a"}], "edges": []})

    assert "'nodeSpacing': 150" in diagram
    assert "'rankSpacing': 150" in diagram
    assert "'nodeWidth': 200" in diagram
    assert "'wrap': true" in diagram
    assert "'maxWidth': 200" in diagram


def test_wrap_package_name_leaves_short_names_unchanged():
    assert gr.wrap_package_name_for_diagram("com.acme.service") == "com.acme.service"


def test_wrap_package_name_splits_long_names_in_half_by_segment():
    name = "br.com.caelum.cursos.adapters.database.jpa.entity"

    wrapped = gr.wrap_package_name_for_diagram(name)

    assert wrapped == "br.com.caelum.cursos<br/>adapters.database.jpa.entity"


def test_wrap_package_name_exactly_at_threshold_is_unchanged():
    # WRAP_AFTER_SEGMENTS is 3 - exactly 3 segments must not be wrapped, only >3
    assert gr.wrap_package_name_for_diagram("com.acme.service") == "com.acme.service"
    assert "<br/>" in gr.wrap_package_name_for_diagram("com.acme.service.impl")


def test_mermaid_diagram_wraps_long_package_names_in_node_labels():
    dependency_graph = {
        "nodes": [{"value": "br.com.caelum.cursos.adapters.database.jpa.entity"}],
        "edges": [],
    }

    diagram = gr.build_mermaid_diagram(dependency_graph)

    assert 'n0["br.com.caelum.cursos<br/>adapters.database.jpa.entity"]' in diagram


def test_generate_pdf_gracefully_skips_when_weasyprint_is_unavailable(monkeypatch, tmp_path, capsys):
    # Forces "from weasyprint import HTML" to raise ImportError regardless of
    # whether weasyprint is actually installed in this environment - the
    # simplest reliable way to exercise the "package unavailable" path.
    monkeypatch.setitem(sys.modules, "weasyprint", None)
    output_path = tmp_path / "report.pdf"

    result = gr.generate_pdf("<html><body>x</body></html>", output_path)

    assert result is False
    assert not output_path.exists()
    assert "Warning" in capsys.readouterr().err


def test_generate_pdf_gracefully_handles_native_library_load_failure(monkeypatch, tmp_path, capsys):
    # Simulates WeasyPrint's real Windows-without-GTK failure mode: it raises
    # OSError (not ImportError) right at import time.
    import types
    fake_module = types.ModuleType("weasyprint")

    def _raise_oserror():
        raise OSError("cannot load library 'libgobject-2.0-0'")

    fake_module.__getattr__ = lambda name: _raise_oserror()
    monkeypatch.setitem(sys.modules, "weasyprint", fake_module)
    output_path = tmp_path / "report.pdf"

    result = gr.generate_pdf("<html><body>x</body></html>", output_path)

    assert result is False
    assert not output_path.exists()
    assert "Warning" in capsys.readouterr().err


def test_main_with_pdf_flag_still_succeeds_and_writes_html_regardless_of_pdf_outcome(tmp_path):
    # report.pdf itself is environment-dependent (needs weasyprint's native
    # libraries); what must always hold is that --pdf never breaks the run.
    json_path = tmp_path / "report.json"
    json_path.write_text(
        json.dumps(load_fixture("with-cycle-and-violation.json")), encoding="utf-8"
    )
    output_dir = tmp_path / "out"

    exit_code = gr.main(["--json-path", str(json_path), "--output-dir", str(output_dir), "--pdf"])

    assert exit_code == 0
    assert (output_dir / "report.html").exists()


def test_wrap_package_name_for_graphviz_leaves_short_names_unchanged():
    assert gr._wrap_package_name_for_graphviz("com.acme.service") == "com.acme.service"


def test_wrap_package_name_for_graphviz_splits_long_names_with_dot_line_break_escape():
    name = "br.com.caelum.cursos.adapters.database.jpa.entity"

    wrapped = gr._wrap_package_name_for_graphviz(name)

    # literal backslash-n (DOT's label line-break escape), not a real newline
    # character - verified against graphviz.Digraph.source before writing this.
    assert wrapped == "br.com.caelum.cursos\\nadapters.database.jpa.entity"


def test_build_pdf_diagram_svg_returns_empty_when_no_problem_packages():
    dependency_graph = {"nodes": [{"value": "a"}, {"value": "b"}], "edges": []}

    result = gr.build_pdf_diagram_svg(dependency_graph, [], [])

    assert result == ""


def test_build_pdf_diagram_svg_gracefully_skips_when_graphviz_package_unavailable(monkeypatch, capsys):
    # Forces "import graphviz" to raise ImportError regardless of whether the
    # package is actually installed in this environment.
    monkeypatch.setitem(sys.modules, "graphviz", None)
    cycles = [{"path": [{"value": "a"}, {"value": "b"}, {"value": "a"}]}]

    result = gr.build_pdf_diagram_svg({"nodes": [], "edges": []}, cycles, [])

    assert result == ""
    assert "Warning" in capsys.readouterr().err


def test_build_pdf_diagram_svg_gracefully_skips_when_dot_binary_is_missing(monkeypatch, capsys):
    # Simulates the real scenario where the 'graphviz' Python package is
    # installed but the 'dot' executable it shells out to is not on PATH -
    # via a fake Digraph whose pipe() raises the same exception type
    # graphviz.pipe() raises in that case, rather than relying on the actual
    # 'dot' binary being absent from this machine (it may well be installed).
    import graphviz

    class FakeDigraph:
        def __init__(self, *args, **kwargs):
            pass

        def attr(self, *args, **kwargs):
            pass

        def node(self, *args, **kwargs):
            pass

        def edge(self, *args, **kwargs):
            pass

        def pipe(self, format=None):
            raise graphviz.ExecutableNotFound("dot")

    monkeypatch.setattr(graphviz, "Digraph", FakeDigraph)
    dependency_graph = {
        "nodes": [{"value": "a"}, {"value": "b"}],
        "edges": [
            {"from": {"value": "a"}, "to": {"value": "b"}},
            {"from": {"value": "b"}, "to": {"value": "a"}},
        ],
    }
    cycles = [{"path": [{"value": "a"}, {"value": "b"}, {"value": "a"}]}]

    result = gr.build_pdf_diagram_svg(dependency_graph, cycles, [])

    assert result == ""
    assert "Warning" in capsys.readouterr().err


def test_build_pdf_diagram_svg_filters_to_only_problem_packages_and_highlights_cycle_edges(monkeypatch):
    # Stubs only graphviz.Digraph.pipe() (the part that needs the real 'dot'
    # binary) - node()/edge() run for real, so the actual subgraph-filtering
    # logic in build_pdf_diagram_svg is exercised, not just the wiring.
    import types

    captured = {}

    class FakeDigraph:
        def __init__(self, *args, **kwargs):
            self.nodes = []
            self.edges = []

        def attr(self, *args, **kwargs):
            pass

        def node(self, name, label=None):
            self.nodes.append(name)

        def edge(self, a, b, **kwargs):
            self.edges.append((a, b, kwargs))

        def pipe(self, format=None):
            captured["nodes"] = list(self.nodes)
            captured["edges"] = list(self.edges)
            return "<svg>fake</svg>".encode("utf-8")

    fake_module = types.SimpleNamespace(Digraph=FakeDigraph)
    monkeypatch.setitem(sys.modules, "graphviz", fake_module)

    dependency_graph = {
        "nodes": [{"value": "a"}, {"value": "b"}, {"value": "c"}],
        "edges": [
            {"from": {"value": "a"}, "to": {"value": "b"}},  # cycle edge - included, red
            {"from": {"value": "b"}, "to": {"value": "a"}},  # cycle edge - included, red
            {"from": {"value": "a"}, "to": {"value": "c"}},  # c is not a problem package - excluded
        ],
    }
    cycles = [{"path": [{"value": "a"}, {"value": "b"}, {"value": "a"}]}]

    result = gr.build_pdf_diagram_svg(dependency_graph, cycles, [])

    assert result == "<svg>fake</svg>"
    assert sorted(captured["nodes"]) == ["a", "b"]
    edge_pairs = [(f, t) for f, t, kw in captured["edges"]]
    assert sorted(edge_pairs) == [("a", "b"), ("b", "a")]
    assert all(kw.get("color") == "#c11d3a" for _, _, kw in captured["edges"])


def test_build_pdf_diagram_svg_includes_packages_from_violations_too(monkeypatch):
    import types

    captured = {}

    class FakeDigraph:
        def __init__(self, *args, **kwargs):
            self.nodes = []

        def attr(self, *args, **kwargs):
            pass

        def node(self, name, label=None):
            self.nodes.append(name)

        def edge(self, a, b, **kwargs):
            pass

        def pipe(self, format=None):
            captured["nodes"] = list(self.nodes)
            return b"<svg>fake</svg>"

    fake_module = types.SimpleNamespace(Digraph=FakeDigraph)
    monkeypatch.setitem(sys.modules, "graphviz", fake_module)

    dependency_graph = {"nodes": [{"value": "com.acme.controller"}, {"value": "com.acme.repository"}], "edges": []}
    violations_view = [{"from": "com.acme.controller", "to": "com.acme.repository"}]

    result = gr.build_pdf_diagram_svg(dependency_graph, [], violations_view)

    assert result == "<svg>fake</svg>"
    assert sorted(captured["nodes"]) == ["com.acme.controller", "com.acme.repository"]


if __name__ == "__main__":
    sys.exit(pytest.main([__file__]))
