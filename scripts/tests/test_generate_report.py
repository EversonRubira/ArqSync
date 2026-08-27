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
    assert "3" in html  # totalPackages metric tile


def test_report_without_cycles_or_violations_renders_empty_state(tmp_path):
    report_data = load_fixture("empty.json")

    html = gr.render_html(report_data)

    assert "Nenhum ciclo de dependência detectado." in html
    assert "Nenhuma violação de camada detectada." in html


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


if __name__ == "__main__":
    sys.exit(pytest.main([__file__]))
