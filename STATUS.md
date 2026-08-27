# STATUS — ArqSync

> Visibilidade de progresso do projeto, conforme mitigação de risco definida no PRD (seção 8: "abandono do projeto ou featuritis").
> **Última atualização:** 2026-08-27

---

## Fase atual

**Implementação — Scanner concluído.** Todas as Specs técnicas estão prontas; o Scanner (`com.arqsync.scanner`) é o primeiro componente implementado e testado (build Maven verificado: `mvn test` com sucesso, 15/15 testes).

---

## Decisões fechadas (não revisitar)

- **Stack:** Java 21, JavaParser 3.26.2 (parsing), PostgreSQL (produção/desenvolvimento) com H2 em memória para testes, Mermaid.js via CDN (diagrama do relatório), CLI como ponto de entrada único (`java -jar arqsync.jar /caminho/do/projeto`). Python 3.8+ com Jinja2 também é exigido em runtime para gerar o `report.html` (com fallback gracioso para apenas `report.json`) — já refletido no PRD, seção 7.
- **Arquitetura:** pipeline sequencial de componentes — Scanner → Analyzer → Persistence → Exporter → CLI — sem framework de orquestração, sem depender de build tool do projeto escaneado (Maven/Gradle).
- **Escopo do v1:** detecção de ciclos de dependência entre pacotes e violação de camadas por convenção de nomenclatura fixa (`controller`, `service`, `repository`, `domain`); saída em `report.json` + `report.html`. Métricas de qualidade estrutural (coesão/LCOM, instabilidade de Robert Martin), histórico/evolução entre execuções, suporte a outras linguagens, plugins de IDE, CI/CD e configuração customizável de camadas ficam fora do v1. Ver `docs/prd/PRD-arqsync.md`, seção 3.
- **Prioridade do banco:** persistência é **P1 em prioridade de valor** (o relatório funciona sem ela) mas **P0 em prioridade de execução** — implementada já no v1 porque a estrutura (schema, entidades, `docker-compose.yml`) já está planejada, e adiar geraria retrabalho maior do que implementar agora (PRD, seção 5).
- **Persistência é opcional em runtime:** se o banco não estiver disponível, o scan roda e os relatórios são gerados normalmente (resiliência sem banco — PRD, P0 item 8).
- **Testes:** pirâmide ~70% unitário / ~20% integração / ~10% e2e; `./mvnw test` (unitários, Surefire, `*Test`) e `./mvnw verify` (+ integração via Failsafe, `*IT`, + gate de cobertura JaCoCo ≥70% de linhas). Maven Wrapper (`./mvnw`) adicionado ao repositório. Ver `docs/specs/SPEC-testing.md`.

---

## Progresso

| Item | Status | Local |
|---|---|---|
| PRD | ✅ Concluído | [`docs/prd/PRD-arqsync.md`](docs/prd/PRD-arqsync.md) |
| Spec do Scanner | ✅ Concluída | [`docs/specs/SPEC-scanner.md`](docs/specs/SPEC-scanner.md) |
| Spec do Analyzer | ✅ Concluída | [`docs/specs/SPEC-analyzer.md`](docs/specs/SPEC-analyzer.md) |
| Spec do Persistence | ✅ Concluída | [`docs/specs/SPEC-persistence.md`](docs/specs/SPEC-persistence.md) |
| Spec do Exporter | ✅ Concluída | [`docs/specs/SPEC-exporter.md`](docs/specs/SPEC-exporter.md) |
| Spec do CLI | ✅ Concluída | [`docs/specs/SPEC-cli.md`](docs/specs/SPEC-cli.md) |
| Spec de Testes | ✅ Concluída | [`docs/specs/SPEC-testing.md`](docs/specs/SPEC-testing.md) |

Todas as Specs técnicas do v1 estão concluídas, incluindo a estratégia de testes centralizada.

## Implementação

| Componente | Status | Local |
|---|---|---|
| Scanner (`com.arqsync.scanner`) | ✅ Implementado, testado (`./mvnw verify` verde, cobertura JaCoCo ≥70% já aplicada como gate) | [`src/main/java/com/arqsync/scanner`](src/main/java/com/arqsync/scanner) |
| Analyzer | ⏳ Pendente | — |
| Persistence | ⏳ Pendente | — |
| Exporter | ⏳ Pendente | — |
| CLI | ⏳ Pendente | — |

## Próximo passo imediato

**Implementação do Analyzer** (`com.arqsync.analyzer`), consumindo o `ProjectScan` já produzido pelo Scanner — seguindo a ordem já usada nas Specs (Scanner → Analyzer → Persistence → Exporter → CLI).

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
- **Customização de templates HTML** (tema escuro, branding), **geração de PDF/outros formatos** e **upload automático para S3/cloud**: fora do v1 (Spec do Exporter).
- **Flags de linha de comando** (`--help`, `--verbose`, `--output-dir`), **modo silencioso**, **saída via stdout**, **paralelismo entre etapas** e **modo "apenas JSON"/"apenas HTML"**: fora do v1 (Spec do CLI) — adicionar apenas com fricção real de uso.
- **Rename de `CommandLineRunner`:** o nome pedido na Spec do CLI colide com `org.springframework.boot.CommandLineRunner`; recomendado renomear (ex.: `ArqSyncPipelineRunner`) na implementação.
- **Achado da implementação do Scanner:** `StaticJavaParser` usa por padrão um `LanguageLevel` antigo, que rejeita `record`/`sealed` (sintaxe Java 17+ citada como risco no PRD, seção 8). Corrigido configurando `ParserConfiguration.LanguageLevel.JAVA_21` explicitamente em `DefaultJavaParserAdapter` — descoberto por um teste real, não estava previsto na Spec.
- **Cenário de denylist do Scanner não é uma fixture versionada:** um diretório literal `.git` não pode ser commitado normalmente (Git o trata como fronteira de outro repositório). O teste de denylist monta esse cenário programaticamente via `@TempDir`, em vez de usar `src/test/resources/fixtures/scanner/build-dirs/` como a Spec original previa — a Spec do Scanner já foi atualizada para refletir isso.

---

## Links

- PRD: [`docs/prd/PRD-arqsync.md`](docs/prd/PRD-arqsync.md)
- Spec do Scanner: [`docs/specs/SPEC-scanner.md`](docs/specs/SPEC-scanner.md)
- Spec do Analyzer: [`docs/specs/SPEC-analyzer.md`](docs/specs/SPEC-analyzer.md)
- Spec do Persistence: [`docs/specs/SPEC-persistence.md`](docs/specs/SPEC-persistence.md)
- Spec do Exporter: [`docs/specs/SPEC-exporter.md`](docs/specs/SPEC-exporter.md)
- Spec do CLI: [`docs/specs/SPEC-cli.md`](docs/specs/SPEC-cli.md)
- Spec de Testes: [`docs/specs/SPEC-testing.md`](docs/specs/SPEC-testing.md)
