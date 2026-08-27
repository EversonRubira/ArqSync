# STATUS — ArqSync

> Visibilidade de progresso do projeto, conforme mitigação de risco definida no PRD (seção 8: "abandono do projeto ou featuritis").
> **Última atualização:** 2026-08-27

---

## Fase atual

**Documentação/design.** Nenhum código de produção foi escrito ainda — o projeto está na fase de Specs técnicas (Spec-Driven Development), formalizando o desenho de cada componente antes de implementar.

---

## Decisões fechadas (não revisitar)

- **Stack:** Java 21, JavaParser 3.26.2 (parsing), PostgreSQL (produção/desenvolvimento) com H2 em memória para testes, Mermaid.js via CDN (diagrama do relatório), CLI como ponto de entrada único (`java -jar arqsync.jar /caminho/do/projeto`). **Python 3 + Jinja2** também são exigidos em runtime para gerar o `report.html` (Spec do Exporter) — ainda não refletido nas Restrições do PRD (ver Pendências).
- **Arquitetura:** pipeline sequencial de componentes — Scanner → Analyzer → Persistence → Exporter → CLI — sem framework de orquestração, sem depender de build tool do projeto escaneado (Maven/Gradle).
- **Escopo do v1:** detecção de ciclos de dependência entre pacotes e violação de camadas por convenção de nomenclatura fixa (`controller`, `service`, `repository`, `domain`); saída em `report.json` + `report.html`. Métricas de qualidade estrutural (coesão/LCOM, instabilidade de Robert Martin), histórico/evolução entre execuções, suporte a outras linguagens, plugins de IDE, CI/CD e configuração customizável de camadas ficam fora do v1. Ver `docs/prd/PRD-arqsync.md`, seção 3.
- **Prioridade do banco:** persistência é **P1 em prioridade de valor** (o relatório funciona sem ela) mas **P0 em prioridade de execução** — implementada já no v1 porque a estrutura (schema, entidades, `docker-compose.yml`) já está planejada, e adiar geraria retrabalho maior do que implementar agora (PRD, seção 5).
- **Persistência é opcional em runtime:** se o banco não estiver disponível, o scan roda e os relatórios são gerados normalmente (resiliência sem banco — PRD, P0 item 8).

---

## Progresso

| Item | Status | Local |
|---|---|---|
| PRD | ✅ Concluído | [`docs/prd/PRD-arqsync.md`](docs/prd/PRD-arqsync.md) |
| Spec do Scanner | ✅ Concluída | [`docs/specs/SPEC-scanner.md`](docs/specs/SPEC-scanner.md) |
| Spec do Analyzer | ✅ Concluída | [`docs/specs/SPEC-analyzer.md`](docs/specs/SPEC-analyzer.md) |
| Spec do Persistence | ✅ Concluída | [`docs/specs/SPEC-persistence.md`](docs/specs/SPEC-persistence.md) |
| Spec do Exporter | ✅ Concluída | [`docs/specs/SPEC-exporter.md`](docs/specs/SPEC-exporter.md) |
| Spec do CLI | ⏳ Pendente | — |

## Próximo passo imediato

**Spec do CLI** — ponto de entrada único, orquestrando Scanner → Analyzer → Persistence → Exporter.

---

## Pendências registradas

Consolidadas das seções de Pendências das Specs já escritas:

- **Imports estáticos/wildcard:** capturados como texto bruto pelo Scanner; a resolução (o que é interno ao projeto, como tratar `.*`/`static `) é responsabilidade do Analyzer — formato confirmado entre as duas Specs, mas ainda não validado com código real (sem implementação ainda).
- **Colisão de nomes de pacote em projeto multi-módulo:** se dois módulos declararem o mesmo pacote totalmente qualificado, o Scanner os funde em uma única `PackageScan` — comportamento aceito no v1, não validado contra um projeto multi-módulo real.
- **Classes aninhadas:** deliberadamente fora do v1 — não geram `ClassScan` próprio; revisitar apenas se uma métrica futura precisar delas.
- **Paralelização futura do Scanner:** adiada por decisão (sem paralelismo no v1); revisitar apenas com evidência real de gargalo de performance, especialmente para projetos fora da faixa alvo (>500 classes).
- **Atualização de `Project` (nome/URL) em caminho diferente:** fora do v1 — hoje um `path` diferente gera um `Project` novo, sem vínculo com o anterior.
- **Deduplicação de `Analysis`** (mesmo commit/estado escaneado duas vezes): fora do v1 — modelo é puramente aditivo, sem detecção de "nada mudou".
- **Histórico com interface de consulta** (diffs, gráficos de evolução): fora do v1, aguardando uma futura extensão do Exporter/CLI — o schema do Persistence já é compatível, mas nenhuma consulta é implementada ainda.
- **Python como nova dependência de ambiente:** a Spec do Exporter exige Python 3 + Jinja2 para gerar o `report.html`; o PRD (seção 7) só documenta Java 21 como restrição de ambiente — recomenda-se atualizar o PRD para refletir essa exigência real do v1.
- **Customização de templates HTML** (tema escuro, branding), **geração de PDF/outros formatos** e **upload automático para S3/cloud**: fora do v1 (Spec do Exporter).

---

## Links

- PRD: [`docs/prd/PRD-arqsync.md`](docs/prd/PRD-arqsync.md)
- Spec do Scanner: [`docs/specs/SPEC-scanner.md`](docs/specs/SPEC-scanner.md)
- Spec do Analyzer: [`docs/specs/SPEC-analyzer.md`](docs/specs/SPEC-analyzer.md)
- Spec do Persistence: [`docs/specs/SPEC-persistence.md`](docs/specs/SPEC-persistence.md)
- Spec do Exporter: [`docs/specs/SPEC-exporter.md`](docs/specs/SPEC-exporter.md)
