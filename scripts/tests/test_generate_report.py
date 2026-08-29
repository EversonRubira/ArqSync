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


def test_report_with_cycle_and_violation_renders_mermaid_cycles_and_violations(tmp_path):
    report_data = load_fixture("with-cycle-and-violation.json")

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


def test_report_without_cycles_or_violations_renders_empty_state(tmp_path):
    report_data = load_fixture("empty.json")

    html = gr.render_html(report_data)

    assert "Nenhuma violação de camada detectada." in html
    assert "Nenhum ciclo de dependência detectado." in html
    assert "Arquitetura detectada: Não identificado" in html
    assert "Nenhum problema estrutural detectado" in html  # header status summary
    assert "Nenhuma ação necessária" in html  # suggestions empty state


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
    assert lines[0] == "graph TD"
    assert '    n0["a"]' in lines
    assert '    n1["b"]' in lines
    assert "    n0 --> n1" in lines


def test_mermaid_diagram_for_empty_graph_does_not_crash():
    diagram = gr.build_mermaid_diagram({"nodes": [], "edges": []})

    assert diagram.startswith("graph TD")


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


def test_suggestions_view_combines_violations_then_cycles_in_order():
    violations_view = [{"from": "com.acme.controller", "to": "com.acme.repository", "suggestion": "fix violation"}]
    cycles_view = [{"path": "com.acme.a → com.acme.b → com.acme.a", "suggestion": "fix cycle"}]

    suggestions = gr.build_suggestions_view(violations_view, cycles_view)

    assert suggestions == [
        {"kind": "violation", "context": "com.acme.controller → com.acme.repository", "text": "fix violation"},
        {"kind": "cycle", "context": "com.acme.a → com.acme.b → com.acme.a", "text": "fix cycle"},
    ]


if __name__ == "__main__":
    sys.exit(pytest.main([__file__]))
