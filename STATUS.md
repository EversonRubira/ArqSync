# STATUS — ArqSync

> Visibilidade de progresso do projeto, conforme mitigação de risco definida no PRD (seção 8: "abandono do projeto ou featuritis").
> **Última atualização:** 2026-08-27 (gap de resiliência "sem banco" resolvido)

---

## Fase atual

**v1 funcionalmente completo — todos os 5 componentes implementados, incluindo resiliência real sem banco.** Scanner, Analyzer, Persistence, Exporter e CLI (`com.arqsync.cli` + `com.arqsync.ArqSyncApplication`) estão implementados e testados (`./mvnw test` verde, 75/75 testes Java, cobertura ~94% de linhas; `pytest` verde, 7/7 testes Python). **Pipeline validado de ponta a ponta de verdade** nesta sessão em dois cenários contra um PostgreSQL real (instalado localmente, não Docker): banco disponível (persistência real acontece) e banco indisponível (aplicação sobe mesmo assim, sem persistência, gerando `report.json`/`report.html` normalmente) — ver "Gap de resiliência resolvido" nas Pendências. `./mvnw verify` só é validável fora deste ambiente de sessão (Testcontainers) — ver Pendências.

---

## Decisões fechadas (não revisitar)

- **Stack:** Java 21, JavaParser 3.26.2 (parsing), PostgreSQL (produção/desenvolvimento) com H2 em memória para testes, Mermaid.js via CDN (diagrama do relatório), CLI como ponto de entrada único (`java -jar arqsync.jar /caminho/do/projeto`). Python 3.8+ com Jinja2 também é exigido em runtime para gerar o `report.html` (com fallback gracioso para apenas `report.json`) — já refletido no PRD, seção 7.
- **Arquitetura:** pipeline sequencial de componentes — Scanner → Analyzer → Persistence → Exporter → CLI — sem framework de orquestração, sem depender de build tool do projeto escaneado (Maven/Gradle).
- **Escopo do v1:** detecção de ciclos de dependência entre pacotes e violação de camadas por convenção de nomenclatura fixa (`controller`, `service`, `repository`, `domain`); saída em `report.json` + `report.html`. Métricas de qualidade estrutural (coesão/LCOM, instabilidade de Robert Martin), histórico/evolução entre execuções, suporte a outras linguagens, plugins de IDE, CI/CD e configuração customizável de camadas ficam fora do v1. Ver `docs/prd/PRD-arqsync.md`, seção 3.
- **Prioridade do banco:** persistência é **P1 em prioridade de valor** (o relatório funciona sem ela) mas **P0 em prioridade de execução** — implementada já no v1 porque a estrutura (schema, entidades, `docker-compose.yml`) já está planejada, e adiar geraria retrabalho maior do que implementar agora (PRD, seção 5).
- **Persistência é opcional em runtime:** se o banco não estiver disponível, o scan roda e os relatórios são gerados normalmente (resiliência sem banco — PRD, P0 item 8). **Implementado e validado nesta sessão** — ver "Gap de resiliência resolvido" nas Pendências.
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
| Exporter (`com.arqsync.exporter` + `scripts/generate-report.py`) | ✅ Implementado, testado (Java + pytest) | [`src/main/java/com/arqsync/exporter`](src/main/java/com/arqsync/exporter), [`scripts/`](scripts) |
| CLI (`com.arqsync.cli` + `com.arqsync.ArqSyncApplication`) | ✅ Implementado, testado, validado ponta a ponta com Postgres real | [`src/main/java/com/arqsync/cli`](src/main/java/com/arqsync/cli) |

Todos os 5 componentes do v1 estão implementados. `java -jar arqsync.jar <caminho>` é um comando funcional de verdade.

## Próximo passo imediato

**v1 funcionalmente completo, incluindo resiliência real sem banco.** Não há mais componentes pendentes no pipeline. Próximos passos possíveis (não decididos): rodar `./mvnw verify` em ambiente com Docker para validar o `PersistenceIT`, revisar a lista de pendências acumuladas e decidir o que endereçar antes de considerar o v1 "pronto para uso real", ou dar início a uma evolução pós-v1 (histórico, CI/CD — ambos hoje fora de escopo).

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
- **Gap de resiliência "sem banco" — RESOLVIDO nesta sessão.** A implementação anterior do CLI só mitigava o sintoma (timeout mais curto); o contexto Spring inteiro ainda falhava ao subir com o banco fora do ar, porque Flyway/DataSource/JPA são autoconfigurados incondicionalmente pelo Spring Boot e travam o `refresh()` do contexto antes de qualquer bean nosso existir. Solução implementada, exatamente como proposto: um `EnvironmentPostProcessor` (`com.arqsync.bootstrap.PersistenceAvailabilityEnvironmentPostProcessor`) roda *antes* do `ApplicationContext` existir, tenta uma conexão JDBC crua com timeout de 3s usando `spring.datasource.url/username/password` já resolvidos do ambiente e, se o banco estiver inalcançável, seta `arqsync.persistence.enabled=false` e popula `spring.autoconfigure.exclude` com as 5 autoconfigurações relevantes (`HibernateJpaAutoConfiguration`, `JpaRepositoriesAutoConfiguration`, `DataSourceAutoConfiguration`, `DataSourceTransactionManagerAutoConfiguration`, `FlywayAutoConfiguration`) — elas nunca chegam a rodar, então Flyway/Hikari nunca travam o startup. Do lado dos beans, `PersistenceServiceConfiguration` encadeia três `@Bean` condicionais (`@ConditionalOnProperty` → `@ConditionalOnBean` → `@ConditionalOnMissingBean`) para escolher entre `DefaultPersistenceService` (real, JPA) e `NoOpPersistenceService` (novo — `save()` só loga um WARN e retorna) sem exigir Profiles do Spring.
  - **Achado de infraestrutura durante a implementação:** `EnvironmentPostProcessor` no Spring Boot 3.4.1 continua sendo registrado via `META-INF/spring.factories` clássico, não via `META-INF/spring/<Interface>.imports` (esse mecanismo mais novo é só para classes `@AutoConfiguration`). Confirmado inspecionando o próprio `spring.factories` dentro do jar do `spring-boot-3.4.1`. Uma primeira tentativa usando `.imports` falhou silenciosamente (o post-processor nunca era chamado); corrigido usando `spring.factories`.
  - **Bug real encontrado e corrigido ao revalidar o caminho "banco disponível" de ponta a ponta:** `DefaultPersistenceService.save()` chamava `this.persist(...)` dentro da própria classe — clássico anti-padrão de self-invocation do Spring AOP, que faz o `@Transactional` nunca abrir uma transação de verdade (a chamada não passa pelo proxy CGLIB). Isso causava `LazyInitializationException` ao reutilizar um `Project` existente em scans repetidos do mesmo caminho (a coleção `Project.analyses`, carregada por `findByPath` numa sessão Hibernate já fechada, era tocada depois em `addAnalysis()`). O bug já existia desde a implementação original do Persistence, mas ficava mascarado nos testes porque `@DataJpaTest` mantém uma transação ambiente aberta durante todo o teste, independente da fronteira `@Transactional` real da produção ter sido acionada. Corrigido extraindo a lógica transacional para um bean separado, `PersistenceWriter` (`@Transactional void persist(...)`), de forma que `DefaultPersistenceService.save()` agora chama `persistenceWriter.persist(...)` — uma chamada entre beans de verdade, que passa pelo proxy. `DefaultPersistenceServiceTest` foi ajustado (`@Transactional(propagation = Propagation.NOT_SUPPORTED)` no teste de reuso) para suspender a transação ambiente e realmente exercitar as fronteiras de produção — esse teste teria pego o bug se já estivesse assim.
  - **Validado de ponta a ponta contra PostgreSQL real (não Docker), nos dois cenários:** banco disponível (contexto sobe em ~5.3s, `DefaultPersistenceService` é ligado, reuso de `Project` entre 3 scans confirmado via `psql`, sem exceção) e banco indisponível (detecção em ~3s, contexto sobe em ~1.7-2.5s no total, `NoOpPersistenceService` é ligado, pipeline completo Scanner→Analyzer→[WARN, sem persistir]→Exporter roda até o fim, `report.json`/`report.html` gerados, exit code 0). `./mvnw test`: 75/75 verdes (68 anteriores + 7 novos testes: `PersistenceAvailabilityEnvironmentPostProcessorTest`, `NoOpPersistenceServiceTest`, `PersistenceServiceConfigurationTest`), cobertura de linhas ~94%, acima do gate de 70%.
- **Migration Flyway criada nesta implementação, não pré-existente:** o pedido presumia `V1__initial_schema.sql` já em `src/main/resources/db/migration/`, mas o arquivo não existia no repositório — foi criado agora com o conteúdo da Spec do Persistence (seção 3.2), com o nome `V1__initial_schema.sql` (a Spec usa `V1__init.sql`; mantido o nome pedido nesta tarefa).
- **Gap real entre a Spec do Exporter e o modelo já implementado do Analyzer:** `SPEC-exporter.md` (2.5, 2.6) assume que `Cycle` carrega uma "explicação didática já pronta", mas `com.arqsync.analyzer.Cycle` — já implementado e commitado — só tem `path`; nunca teve campo de explicação (só `LayerViolation` tem `explanation`, mesmo na própria `SPEC-analyzer.md`, seção 3.2). Resolvido seguindo o modelo real: o `report.html` mostra o caminho do ciclo (e o diagrama Mermaid, que é a peça didática principal para ciclos, per PRD seção 2) sem uma frase de "explicação", em vez de inventar um campo novo no Analyzer fora do escopo desta tarefa.
- **`DefaultHtmlReportGenerator` recebe a lista de comandos Python via construtor de teste** (além do caminho do script), não só o `scriptPath` como a Spec sugeria — necessário para simular de verdade o cenário "Python indisponível" (interpretador com nome inexistente) sem depender do `PATH` real da máquina.
- **`@SpringBootTest` do CLI dispara o `ApplicationRunner` de verdade:** confirmado empiricamente (log do teste mostra a mensagem de erro de "uso" sendo emitida durante o teste de contexto). Como `ArqSyncPipelineRunner` implementa `ApplicationRunner`, qualquer `@SpringBootTest` que não mocka `ProcessExiter` correria o risco de `System.exit(...)` real matar a JVM de teste. `ArqSyncPipelineRunnerSpringContextTest` usa `@MockBean` em `ProcessExiter` por esse motivo — registrado aqui porque não é óbvio à primeira vista e pode pegar alguém de surpresa em testes futuros que envolvam `@SpringBootTest` nesse pacote.
- **`com.arqsync.ArqSyncApplication` criada:** primeira classe `@SpringBootApplication` real do projeto — antes só existia `com.arqsync.TestApplication` (test-only, removida agora porque as duas coexistindo no mesmo pacote causariam ambiguidade de configuração no Spring Boot). `spring-boot-maven-plugin` também adicionado ao `pom.xml` — o jar gerado agora é executável (`java -jar arqsync.jar <projeto>`), validado de verdade nesta sessão.

---

## Links

- PRD: [`docs/prd/PRD-arqsync.md`](docs/prd/PRD-arqsync.md)
- Spec do Scanner: [`docs/specs/SPEC-scanner.md`](docs/specs/SPEC-scanner.md)
- Spec do Analyzer: [`docs/specs/SPEC-analyzer.md`](docs/specs/SPEC-analyzer.md)
- Spec do Persistence: [`docs/specs/SPEC-persistence.md`](docs/specs/SPEC-persistence.md)
- Spec do Exporter: [`docs/specs/SPEC-exporter.md`](docs/specs/SPEC-exporter.md)
- Spec do CLI: [`docs/specs/SPEC-cli.md`](docs/specs/SPEC-cli.md)
- Spec de Testes: [`docs/specs/SPEC-testing.md`](docs/specs/SPEC-testing.md)
