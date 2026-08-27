# STATUS — ArqSync

> Visibilidade de progresso do projeto, conforme mitigação de risco definida no PRD (seção 8: "abandono do projeto ou featuritis").
> **Última atualização:** 2026-08-27

---

## Fase atual

**Implementação — Scanner, Analyzer e Persistence concluídos.** Todas as Specs técnicas estão prontas; Scanner (`com.arqsync.scanner`), Analyzer (`com.arqsync.analyzer`) e Persistence (`com.arqsync.persistence`) estão implementados e testados (`./mvnw test` verde, 50/50 testes, cobertura ~96% de linhas). `./mvnw verify` só é validável fora deste ambiente de sessão — ver Pendências.

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
| Scanner (`com.arqsync.scanner`) | ✅ Implementado, testado | [`src/main/java/com/arqsync/scanner`](src/main/java/com/arqsync/scanner) |
| Analyzer (`com.arqsync.analyzer`) | ✅ Implementado, testado | [`src/main/java/com/arqsync/analyzer`](src/main/java/com/arqsync/analyzer) |
| Persistence (`com.arqsync.persistence`) | ✅ Implementado, testado (H2) — `PersistenceIT` (Testcontainers) não executável neste ambiente de sessão, sem daemon Docker | [`src/main/java/com/arqsync/persistence`](src/main/java/com/arqsync/persistence) |
| Exporter | ⏳ Pendente | — |
| CLI | ⏳ Pendente | — |

## Próximo passo imediato

**Implementação do Exporter** (`com.arqsync.exporter`), consumindo `ProjectScan` + `AnalysisResult` para gerar `report.json` (Java/Jackson) e `report.html` (script Python/Jinja2) — seguindo a ordem já usada nas Specs (Scanner → Analyzer → Persistence → Exporter → CLI).

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
- **Achado da implementação do Analyzer:** a Spec (2.6) descreve resolver o pacote candidato "removendo o último segmento", mas não detalha o caso de imports estáticos (`static com.acme.x.Y.metodo`), onde é preciso remover **dois** segmentos (classe + membro), não um — implementado em `DefaultDependencyGraphBuilder`, coberto por teste (`staticImportResolvesToTheDeclaringClassPackage`). Não chegou a ser registrado como pendência explícita na Spec do Analyzer; documentado aqui.
- **`PersistenceIT` (Testcontainers) não verificado nesta sessão:** o ambiente de sessão não tem um daemon Docker rodando (só o CLI do Docker está instalado) — `./mvnw verify` falha exclusivamente nesse teste (`IllegalStateException: Could not find a valid Docker environment`), não por bug de código. `./mvnw test` (50/50, sem Testcontainers) está verde. Rodar `./mvnw verify` em um ambiente com Docker (CI ou máquina local) antes de considerar o Persistence totalmente validado contra o schema Postgres real.
- **Gap de resiliência "sem banco" no nível da aplicação Spring, não apenas do `PersistenceService`:** a Spec do Persistence (2.1) garante que `save(...)` nunca lança exceção, mas isso só cobre falhas *durante* uma chamada — com `spring-boot-starter-data-jpa` + Flyway agora no classpath, se o Postgres estiver genuinamente indisponível, o **contexto Spring inteiro pode falhar ao subir** (Flyway tenta migrar no startup), o que impediria até o Scanner/Analyzer/Exporter de rodar — contradizendo a resiliência "sem banco" do PRD (P0, item 8) num nível acima do que esta Spec cobre. Mitigação parcial já aplicada (`spring.datasource.hikari.initialization-fail-timeout: -1`, adia a validação de conexão do pool), mas o Flyway em si ainda tenta conectar no startup. Resolver isso é uma decisão de arquitetura do bootstrap da aplicação — provavelmente da Spec/implementação do CLI, não do Persistence isoladamente.
- **Migration Flyway criada nesta implementação, não pré-existente:** o pedido presumia `V1__initial_schema.sql` já em `src/main/resources/db/migration/`, mas o arquivo não existia no repositório — foi criado agora com o conteúdo da Spec do Persistence (seção 3.2), com o nome `V1__initial_schema.sql` (a Spec usa `V1__init.sql`; mantido o nome pedido nesta tarefa).

---

## Links

- PRD: [`docs/prd/PRD-arqsync.md`](docs/prd/PRD-arqsync.md)
- Spec do Scanner: [`docs/specs/SPEC-scanner.md`](docs/specs/SPEC-scanner.md)
- Spec do Analyzer: [`docs/specs/SPEC-analyzer.md`](docs/specs/SPEC-analyzer.md)
- Spec do Persistence: [`docs/specs/SPEC-persistence.md`](docs/specs/SPEC-persistence.md)
- Spec do Exporter: [`docs/specs/SPEC-exporter.md`](docs/specs/SPEC-exporter.md)
- Spec do CLI: [`docs/specs/SPEC-cli.md`](docs/specs/SPEC-cli.md)
- Spec de Testes: [`docs/specs/SPEC-testing.md`](docs/specs/SPEC-testing.md)
