# Spec de Testes — ArqSync (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-27

---

## 1. Visão Geral

As Specs de Scanner, Analyzer, Persistence, Exporter e CLI já definem, cada uma, sua própria seção de Testes — mas até agora não havia um documento que amarrasse essas seções em uma estratégia única: proporção entre tipos de teste, ferramentas compartilhadas, convenção de nomenclatura, cobertura mínima, e o que "passar nos testes" significa para o v1 como um todo.

Em SDD, testes são parte da especificação, não um apêndice de implementação — cada Spec de componente já descreve o que testar; esta Spec descreve **como** testar de forma consistente entre componentes, para que a decisão de "unitário vs. integração", a convenção de nomes, e o critério de cobertura não sejam reinventados a cada componente implementado.

Esta Spec não substitui a seção de Testes de nenhuma Spec existente — ela é a camada de convenção compartilhada por cima delas. Onde há conflito aparente, a Spec do componente específico é a autoridade sobre *o que* testar; esta Spec é a autoridade sobre *como* organizar, rodar e medir esses testes.

---

## 2. Estratégia: pirâmide de testes

```
        /\
       /e2e\        poucos, lentos, caros de manter
      /------\
     / integr. \     alguns, cruzam um limite de I/O real
    /------------\
   /   unitários   \  muitos, rápidos, isolados
  /------------------\
```

| Camada | Proporção alvo | O que caracteriza |
|---|---|---|
| **Unitários** | ~70% | Testam uma classe/função isolada, sem I/O real (arquivo, banco, processo externo, rede). Dependências são test doubles (mock/stub) ou, quando o próprio componente já é uma função pura (ex.: `CycleDetector`, `LayerViolationDetector` do Analyzer), nenhuma dependência externa existe para simular. |
| **Integração** | ~20% | Cruzam um limite de I/O real que o componente foi desenhado para tocar: parsing real de arquivos `.java` (Scanner), banco real via Testcontainers (Persistence), processo Python real (Exporter), contexto Spring subindo (CLI). |
| **E2E** | ~10% | Executam o pipeline completo via o artefato final (`java -jar arqsync.jar <projeto-fixture>`), verificando que `report.json`/`report.html` saem corretos ponta a ponta. Só existem depois que o CLI (Spec do CLI) estiver implementado — ver seção 7. |

**Justificativa da proporção:** reflete o que as cinco Specs de componente já definem organicamente. A maioria dos casos de teste em cada Spec (Analyzer inteiro, a maior parte do Scanner e do Exporter) já são unitários por natureza — componentes puros ou com poucas dependências externas. Testes de integração aparecem pontualmente, nos limites reais de I/O que cada Spec já identificou (Scanner: parsing real; Persistence: Testcontainers; Exporter: `ProcessBuilder`; CLI: contexto Spring). E2E é deliberadamente raro — um pipeline completo é lento e caro de manter a cada mudança, e seu papel é validar a integração final, não recobrir casos que os níveis abaixo já cobrem.

Essas proporções são uma referência de distribuição, não uma métrica obrigatória a ser calculada e cobrada por ferramenta — ao contrário da cobertura de linhas (seção 5), que é verificada automaticamente.

---

## 3. Ferramentas

| Ferramenta | Papel | Onde já é usada / onde será usada |
|---|---|---|
| **JUnit 5** | Framework de teste base | Todos os componentes (já em uso no Scanner). |
| **AssertJ** | Asserções fluentes | Todos os componentes (já em uso no Scanner). |
| **Mockito** | Test doubles (mocks/stubs) | Onde um componente depende de outro já especificado como interface (ex.: CLI mockando `ScannerService`/`DependencyAnalyzer`/`PersistenceService`/`ReportExporter`, conforme SPEC-cli.md, seção 5). Trazido transitivamente por `spring-boot-starter-test` (já no `pom.xml`) — nenhuma dependência adicional é necessária. |
| **Testcontainers** | PostgreSQL real em teste de integração | Persistence (SPEC-persistence.md, seção 6) — valida o schema Flyway real contra as entidades JPA, algo que H2 não garante por si só. Ainda não adicionado ao `pom.xml`; entra junto da implementação do Persistence. |
| **H2 (modo PostgreSQL)** | Banco em memória para testes rápidos | Persistence — camada rápida de teste de repositório, complementar ao Testcontainers (não substitui a validação de schema real). |
| **pytest** | Testes do script Python do Exporter | `scripts/generate-report.py` (SPEC-exporter.md, seção 5) — único componente cujos testes não rodam sob Maven/JUnit, por rodar em um processo Python separado. |
| **JaCoCo** | Medição e gate de cobertura de linhas | Todo o projeto — configurado no `pom.xml` raiz (ver seção 5), executa em `./mvnw verify`. |

Nenhuma ferramenta nova além do que as Specs de componente já previam — esta seção apenas consolida a lista em um único lugar.

---

## 4. Organização de fixtures

```
src/test/resources/fixtures/
├── scanner/
├── analyzer/       (quando aplicável — o Analyzer testa majoritariamente com
│                     modelos ProjectScan/AnalysisResult construídos em memória
│                     via fixtures de código, não arquivos; ver SPEC-analyzer.md, seção 6)
├── persistence/
├── exporter/
└── cli/
```

**Convenção:** um subdiretório por componente sob `src/test/resources/fixtures/`, espelhando o pacote (`com.arqsync.<componente>`). Dentro de cada subdiretório, um subdiretório por cenário nomeado pelo que ele cobre (ex.: `scanner/valid-project/`, `scanner/syntax-error/`) — já é o padrão estabelecido em `SPEC-scanner.md`, seção 6, e implementado em `src/test/resources/fixtures/scanner/`.

**Exceção já registrada:** nem todo cenário pode virar fixture versionada — um diretório literal `.git` não é rastreável pelo Git (ele o trata como fronteira de outro repositório). Esse tipo de cenário é montado programaticamente dentro do teste (ex.: via `@TempDir`), não como fixture em disco. Já é o caso do cenário de denylist do Scanner (`docs/specs/SPEC-scanner.md`, seção 6) — registrado aqui para que o mesmo padrão seja reconhecido, e não redescoberto, em outros componentes.

**Fixtures de código (não arquivo):** para componentes que operam inteiramente sobre modelos em memória (Analyzer, e partes do Exporter/Persistence que recebem `ProjectScan`/`AnalysisResult` já prontos), a "fixture" é um builder Java, não um arquivo em `src/test/resources/`. Isso já é a decisão registrada em `SPEC-analyzer.md`, seção 6 ("sem I/O... fixtures construídos em memória"). Continua sendo o padrão para esse tipo de componente.

---

## 5. Testes obrigatórios por camada

Cada Spec de componente já define seus próprios casos de teste em detalhe — esta seção não os duplica, apenas resume o que cada uma exige e aponta para a fonte.

| Componente | Nível predominante | Casos obrigatórios (resumo) | Fonte |
|---|---|---|---|
| **Scanner** | Unitário (adapter) + integração leve (service, parsing real) | Parsing válido/inválido, múltiplos tipos por arquivo, pacote default, denylist de diretórios, projeto vazio, caminho inválido. | `SPEC-scanner.md`, seção 6 |
| **Analyzer** | Unitário (componente puro, sem I/O) | Construção do grafo, detecção de ciclos (incluindo diamante sem ciclo — falso positivo), violação de camada (skip vs. inversão vs. domain-como-alvo), métricas, integração dos quatro colaboradores. | `SPEC-analyzer.md`, seção 6 |
| **Persistence** | Unitário (mapper) + integração (H2 rápido + Testcontainers real) | Mapeamento `ProjectScan`+`AnalysisResult` → entidades, `findByPath`, fluxo completo de `save`, resiliência a falha/banco indisponível, validação do schema Flyway real. | `SPEC-persistence.md`, seção 6 |
| **Exporter** | Unitário (Java) + integração (chamada real ao Python) + testes Python separados (pytest) | Serialização do `report.json`, chamada ao script com Python disponível/indisponível/falhando, geração do `report.html` a partir de fixtures de JSON. | `SPEC-exporter.md`, seção 5 |
| **CLI** | Unitário (orquestração, com doubles) + integração (contexto Spring) | Pipeline completo, fatalidade assimétrica por etapa (Scanner/Analyzer fatais, Persistence/Exporter não), código de saída via `ProcessExiter`, contexto Spring sobe com todos os beans. | `SPEC-cli.md`, seção 5 |

**Regra geral:** nenhum componente é considerado implementado (independente do que o `STATUS.md` diga sobre o código em si) sem que os casos listados na seção de Testes da sua própria Spec estejam cobertos — esta tabela é um índice, não uma redução de escopo.

---

## 6. Como rodar

```bash
./mvnw test      # testes unitários (fase `test` do Maven — Surefire)
./mvnw verify     # testes unitários + testes de integração (fase `verify` — Surefire + Failsafe)
                  # também aplica o gate de cobertura do JaCoCo (seção 7)
```

**Convenção de nomenclatura:** classes de teste terminadas em `*Test` são unitárias e rodam sob o Surefire (fase `test`); classes terminadas em `*IT` são de integração e rodam sob o Failsafe (fase `integration-test`/`verify`) — convenção padrão do ecossistema Maven, já configurada no `pom.xml` raiz (plugin `maven-failsafe-plugin`, adicionado junto com esta Spec).

**Nota sobre o Scanner hoje:** os testes atuais de `DefaultScannerServiceTest` fazem parsing real de arquivos `.java` (não são "unitários" no sentido estrito de isolamento total), mas seguem nomeados `*Test` e rodam sob Surefire — o limite de I/O que tocam (sistema de arquivos local) é rápido e determinístico o suficiente para não justificar o custo de uma fase separada. A distinção `*Test`/`*IT` desta Spec importa mais a partir do Persistence (Testcontainers, que sobe um container Docker) e do CLI (contexto Spring completo) — onde o custo de execução realmente diverge entre os dois níveis.

Testes Python do Exporter rodam fora do Maven:

```bash
cd scripts && pytest
```

---

## 7. Critérios de aceitação

Para o v1, "passar nos testes" significa, simultaneamente:

1. `./mvnw verify` termina com `BUILD SUCCESS` — todos os testes unitários e de integração Java passam, incluindo o gate de cobertura do JaCoCo.
2. **Cobertura mínima de 70% de linhas**, medida por componente (`BUNDLE` do JaCoCo sobre o módulo Maven único do projeto), configurada como regra de `jacoco-maven-plugin` vinculada à fase `verify` — uma execução que fique abaixo do limite falha o build, não é um número apenas relatado.
3. Todos os casos de teste listados na seção de Testes de cada Spec de componente (seção 5 desta Spec) existem e passam — a tabela da seção 5 é o checklist mínimo por componente.
4. Testes Python do Exporter (`pytest`) passam, quando o componente Exporter estiver implementado — hoje fora do escopo do Maven build, então não bloqueia `./mvnw verify`, mas bloqueia a conclusão do componente Exporter.
5. Nenhum teste é ignorado (`@Disabled`) sem uma pendência registrada explicitamente no `STATUS.md` explicando por quê.

O v1 como um todo é considerado testado quando os cinco componentes cumprem os quatro primeiros critérios — o quinto é uma regra permanente, não um marco.

---

## 8. Evolução futura (fora do v1)

- **CI via GitHub Actions:** rodar `./mvnw verify` (e `pytest` para o Exporter) automaticamente a cada push/PR. Fora do v1 — o fluxo de trabalho atual é local, sem repositório de CI configurado. Quando adotado, deve rodar exatamente os mesmos comandos desta Spec (seção 6), não uma configuração paralela.
- **Testes de mutação** (ex.: PIT) para validar a qualidade dos testes além da cobertura de linhas — cobertura de linha não garante que as asserções sejam efetivas; fora do v1 por não haver necessidade demonstrada ainda.
- **Testes de performance/carga** contra o alvo de 50–500 classes do PRD (seção 7) — hoje nenhuma Spec define um teste automatizado de performance; ficaria para quando houver um projeto real grande o suficiente para medir contra.
- **E2E automatizado** (seção 2): só é viável depois que o CLI (Spec do CLI) estiver implementado e existir um jar executável — hoje não há artefato para rodar ponta a ponta.
