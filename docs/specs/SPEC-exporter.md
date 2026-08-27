# Spec Técnica — Exporter (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-27

---

## 1. Visão Geral

O Exporter é o penúltimo componente do pipeline do ArqSync. Ele recebe o `ProjectScan` (Scanner) e o `AnalysisResult` (Analyzer) e produz os dois artefatos de saída do v1, em `arqsync-reports/[timestamp]/`:

1. **`report.json`** — dados estruturados, serializados diretamente pelo Java (Jackson).
2. **`report.html`** — a entrega didática principal do v1 (PRD, seção 2): diagrama Mermaid, lista de ciclos, lista de violações e métricas descritivas, gerado por um processo Python separado que lê o `report.json` e renderiza um template Jinja2.

O Exporter é o único componente do pipeline que combina duas linguagens: **Java gera dados, Python gera apresentação.** O `report.json` é o contrato entre as duas metades — o processo Python nunca acessa os modelos Java diretamente, só o arquivo JSON.

> **Nota sobre escopo de ambiente:** esta Spec adiciona **Python 3 + Jinja2** como dependência de runtime para gerar o `report.html`. O PRD (seção 7, Restrições) hoje só lista "Java 21 obrigatório no ambiente de execução". Isso é uma expansão real das restrições do v1 que deveria ser refletida no PRD e no `STATUS.md` — registrado como pendência na seção 8.

---

## 2. Decisões de Design

Cada decisão segue o princípio Hashimoto: a decisão, a justificativa, e as alternativas descartadas.

### 2.1 `report.json` gerado pelo Java, via Jackson

**Decisão:** o Exporter serializa `ProjectScan` e `AnalysisResult` (ou um DTO de relatório construído a partir deles — ver 2.5) diretamente com `ObjectMapper` do Jackson, sem escrever JSON manualmente.

**Justificativa:**
- Os modelos (`ProjectScan`, `AnalysisResult` e seus componentes) já são records Java — o Jackson serializa records nativamente desde a versão 2.12, sem necessidade de anotações extras na maioria dos casos. Serialização manual (concatenação de string) seria mais código e mais propensa a erro para uma estrutura com listas aninhadas (ciclos, violações, métricas por pacote).
- Jackson é a biblioteca de fato padrão no ecossistema Java/Spring que a stack do ArqSync já usa (Persistence spec já assume Spring Data JPA).

**Alternativas descartadas:**
- *Serialização manual via `StringBuilder`.* Rejeitado: verboso e frágil para estruturas aninhadas, sem nenhum ganho sobre uma biblioteca madura.
- *Gson.* Rejeitado: sem motivo para introduzir uma segunda biblioteca de JSON quando Jackson já é natural na stack.

### 2.2 `report.html` gerado por Python/Jinja2

**Decisão:** a geração do HTML não usa nenhuma template engine Java (Thymeleaf, Freemarker) — é feita por um script Python separado (`scripts/generate-report.py`) usando Jinja2 sobre um template (`templates/report.html.j2`).

**Justificativa:**
- Separação clara de responsabilidade: Java conhece os modelos de domínio e produz dados; Python conhece apenas o contrato JSON e produz apresentação. Isso evita acoplar a lógica de análise arquitetural (Scanner/Analyzer/Persistence, todos em Java) a uma dependência de template engine HTML só para a etapa final.
- Introduzir Thymeleaf/Freemarker no lado Java adicionaria uma dependência pesada e um paradigma de templating adicional só para gerar uma única página estática — desproporcional ao problema.

**Alternativas descartadas:**
- *Template engine Java (Thymeleaf/Freemarker) dentro do próprio módulo Java.* Rejeitado por decisão explícita: mantém tudo em uma linguagem, mas é exatamente a complexidade que essa decisão evita — "isso evita que o Java tenha que lidar com templates HTML complexos".
- *Gerar HTML via JavaScript/Node como processo externo.* Não escolhido — Python/Jinja2 é a escolha feita, mais simples de invocar via `ProcessBuilder` e mais leve que iniciar um runtime Node só para isso.

### 2.3 Chamada ao Python via `ProcessBuilder`, com fallback silencioso

**Decisão:** o Java invoca o script Python via `ProcessBuilder`, tentando `python3` primeiro e usando `python` como fallback se `python3` não for encontrado no `PATH`. Se o interpretador não existir, ou o script retornar código de saída diferente de zero, ou lançar qualquer exceção durante a execução do processo, o Exporter loga o erro e **continua** — o `report.json` já gerado (2.1) é o artefato primário e nunca é invalidado por uma falha na geração do HTML.

**Justificativa:**
- Mesma filosofia de resiliência já usada no Scanner (arquivo com erro não interrompe o scan) e no Persistence (falha de banco não interrompe o pipeline): o HTML é a entrega didática principal para o usuário, mas não pode ser um ponto único de falha que impede o usuário de obter *algum* resultado.
- Tentar `python3` antes de `python` evita ambiguidade em sistemas onde `python` ainda aponta para Python 2 (comum em distros mais antigas) — `python3` é o nome que a comunidade Python padronizou para evitar essa ambiguidade.
- `ProcessBuilder` é a forma padrão da JVM para invocar um processo externo, sem exigir uma biblioteca de integração adicional (ex.: bindings Python-Java como Jep ou GraalVM polyglot).

**Alternativas descartadas:**
- *Embutir um interpretador Python na JVM (Jep, GraalVM polyglot).* Rejeitado: adiciona complexidade significativa (empacotamento, compatibilidade de versão do interpretador embutido) para um caso de uso que só precisa invocar um script uma vez por execução.
- *Falhar a execução inteira do ArqSync se o Python não estiver disponível.* Rejeitado: contraria a filosofia de resiliência já estabelecida nas Specs anteriores — o usuário ainda recebe o `report.json`.

### 2.4 Estrutura de pastas com timestamp

**Decisão:** os artefatos de cada execução vão para `arqsync-reports/yyyy-MM-dd-HH-mm-ss/`. O Exporter recebe esse diretório já resolvido como parâmetro (`outputDir` em `ReportExporter.export(...)`) — a responsabilidade de calcular o timestamp e montar o caminho `arqsync-reports/...` é do orquestrador (CLI, ainda não especificado), não do Exporter. O Exporter apenas garante que o diretório exista (`Files.createDirectories`) e escreve nele.

**Justificativa:**
- Timestamp no nome do diretório evita sobrescrever execuções anteriores — como a persistência em banco é opcional (Spec do Persistence, 2.1), os diretórios de relatório em disco funcionam como um histórico local mínimo mesmo quando o banco está indisponível.
- Manter o cálculo do timestamp fora do `ReportExporter` simplifica seus testes (qualquer `@TempDir` serve como `outputDir` sem precisar simular um relógio) e mantém a convenção de nomenclatura (`arqsync-reports/...`) como uma decisão do ponto de entrada (CLI), não do Exporter — o Exporter não deveria precisar saber o nome do diretório raiz de relatórios.

**Alternativas descartadas:**
- *Sobrescrever sempre o mesmo diretório (`arqsync-reports/latest/`).* Rejeitado: destruiria o único histórico local disponível quando o banco estiver indisponível.
- *`ReportExporter` calcula o próprio timestamp internamente.* Rejeitado: acopla o componente a uma convenção de nomenclatura de mais alto nível (prefixo `arqsync-reports/`) que pertence à orquestração da CLI, e dificulta testá-lo de forma determinística.

### 2.5 `report.json` autocontido — Python nunca reinterpreta dados

**Decisão:** o `report.json` contém tudo que o script Python precisa para renderizar o HTML: métricas agregadas, ciclos (caminho completo + explicação didática já pronta), violações (camadas envolvidas + explicação didática já pronta), dependências incoming/outgoing por pacote, e amostras de classes usadas nas explicações. O script Python **não recalcula nem reinterpreta** nada — ele só formata o que já vem pronto do Analyzer.

**Justificativa:**
- As explicações didáticas de ciclo/violação já existem como campos de texto no `AnalysisResult` (`Cycle`/`LayerViolation.explanation()`, conforme Spec do Analyzer, seção 3.2) — reaproveitar esse texto em vez de gerar um novo no Python evita duplicar a lógica de "por que isso é um problema" em duas linguagens, o que criaria risco real de divergência entre o texto e a análise que o gerou.
- Um `report.json` autocontido também é consistente com o que o PRD já previa para esse artefato: "desenhado como contrato de dados para consumo futuro... sem consumidor formal ainda" (seção 6) — um JSON que exige contexto externo para ser interpretado não cumpriria esse papel de contrato independente.

**Alternativas descartadas:**
- *Python reconstrói parte do texto explicativo a partir de dados brutos (ex.: monta a frase "controller chamando repository" a partir só dos nomes de pacote).* Rejeitado: duplicaria a lógica de interpretação didática que já vive no Analyzer, e um ajuste futuro no texto exigiria mudar duas linguagens em vez de uma.

### 2.6 Conteúdo e tema do `report.html`

**Decisão:** o `report.html` contém, nesta ordem: diagrama Mermaid (gerado a partir do grafo de dependências), lista de ciclos com caminho e explicação didática, lista de violações com explicação didática, e métricas descritivas em tabela. Tema visual neutro e claro, sem customização (nenhuma opção de tema escuro/branding no v1). O Mermaid.js é carregado via CDN.

**Justificativa:**
- Reflete diretamente o critério de sucesso do PRD (seção 2): o usuário precisa "reconhecer visualmente um ciclo... entender por que aquilo é um problema arquitetural" — diagrama primeiro, depois as duas listas didáticas, depois as métricas de apoio.
- Tema neutro e Mermaid via CDN já são decisões explicitamente fechadas no PRD (seção 9, "Decisões explicitamente adiadas... Tema visual do `report.html` — decidido como neutro no v1"; seção 6, nota de restrição sobre o CDN) — esta Spec não reabre nenhuma das duas, apenas as aplica.

**Alternativas descartadas:**
- *Tema configurável (claro/escuro) no v1.* Já descartado pelo próprio PRD; reafirmado aqui para não ser reaberto por engano durante a implementação.

### 2.7 Script Python: argumentos nomeados, template separado

**Decisão:** `scripts/generate-report.py` recebe os argumentos nomeados `--json-path` e `--output-dir` (não posicionais), e renderiza `templates/report.html.j2` via Jinja2 — a marcação HTML fica inteiramente no arquivo de template, não embutida como f-strings no script.

**Justificativa:**
- Argumentos nomeados tornam a chamada feita pelo `ProcessBuilder` autoexplicativa e resistente a erro de ordem (ao contrário de argumentos posicionais, onde inverter `json-path` e `output-dir` falharia silenciosamente ou de forma confusa).
- Manter o HTML em um arquivo `.j2` separado da lógica Python separa apresentação de lógica de script — dado que o `report.html` é a entrega didática principal do v1 e provavelmente vai iterar bastante em cima do texto/layout, isolar o template facilita esse ajuste sem tocar no código Python que lê o JSON.

**Alternativas descartadas:**
- *Argumentos posicionais (`generate-report.py <json> <output>`).* Rejeitado: menos legível na chamada via `ProcessBuilder` e mais frágil a erro de ordem.
- *HTML embutido como f-strings no script Python.* Rejeitado: mistura lógica de leitura de dados com marcação de apresentação, dificultando qualquer iteração futura só no design do relatório.

### 2.8 Estrutura do `report.json`: DTO de relatório, não um dump bruto dos dois modelos

**Decisão:** o Exporter monta um DTO de serialização (`ReportData`, nome sujeito a ajuste na implementação) combinando os dados relevantes de `ProjectScan` e `AnalysisResult`, mais um `generatedAt` (timestamp da execução) — em vez de serializar `ProjectScan` e `AnalysisResult` como dois objetos JSON soltos e desconectados.

```java
record ReportData(
    String projectName,          // derivado do rootPath do ProjectScan
    String rootPath,
    Instant generatedAt,
    AnalysisMetrics metrics,
    List<Cycle> cycles,
    List<LayerViolation> violations,
    DependencyGraph dependencyGraph   // para o Python montar o diagrama Mermaid
) {}
```

**Justificativa:**
- `ProjectScan` e `AnalysisResult` foram desenhados (Specs do Scanner e do Analyzer) como contratos internos entre componentes Java, não como o formato final de um relatório para consumo externo — por exemplo, `ProjectScan.errors()` (falhas de parsing) não tem valor para o relatório didático, e nenhum dos dois modelos carrega o `generatedAt` da execução. Um DTO dedicado tem exatamente o que o relatório (e o script Python) precisam, nem mais nem menos.

**Alternativas descartadas:**
- *Serializar `ProjectScan` e `AnalysisResult` como dois campos-raiz separados do JSON.* Rejeitado: obrigaria o script Python a conhecer os dois formatos de origem (incluindo campos irrelevantes ao relatório, como `ScanError`), em vez de um único formato pensado para o consumo que o `report.html` realmente precisa.

---

## 3. Interface Pública

```java
public interface ReportExporter {
    void export(ProjectScan projectScan, AnalysisResult analysisResult, Path outputDir);
}

public interface JsonExporter {
    Path export(ProjectScan projectScan, AnalysisResult analysisResult, Path outputDir);
    // grava report.json em outputDir e retorna o Path do arquivo gerado
}

public interface HtmlReportGenerator {
    boolean generate(Path jsonPath, Path outputDir);
    // invoca o script Python; retorna true em sucesso, false em qualquer falha — nunca lança exceção
}
```

A implementação padrão (`DefaultReportExporter`) orquestra `JsonExporter` e `HtmlReportGenerator` por composição. `HtmlReportGenerator.generate(...)` retornar `boolean` (em vez de lançar exceção) espelha, no nível de interface, a mesma decisão de resiliência já usada no `ParseOutcome` do Scanner (Spec do Scanner, 2.2) e no fire-and-forget do Persistence (Spec do Persistence, 2.1): o tipo de retorno já comunica que a falha é um caso esperado, não excepcional.

---

## 4. Fluxo de Execução

1. O orquestrador (CLI) calcula `outputDir = arqsync-reports/{timestamp}` e chama `ReportExporter.export(projectScan, analysisResult, outputDir)`.
2. `DefaultReportExporter` garante que `outputDir` existe (`Files.createDirectories`).
3. `JsonExporter.export(...)`:
   a. Monta o `ReportData` (2.8) a partir de `projectScan` e `analysisResult`.
   b. Serializa via `ObjectMapper` (Jackson) em `outputDir/report.json`.
   c. Retorna o `Path` do arquivo gerado.
4. `HtmlReportGenerator.generate(jsonPath, outputDir)`:
   a. Monta o comando `["python3", "scripts/generate-report.py", "--json-path", jsonPath, "--output-dir", outputDir]` (ou `python` como fallback, 2.3).
   b. Executa via `ProcessBuilder`, aguarda o término do processo.
   c. Se o interpretador não for encontrado (`IOException` ao iniciar o processo), o código de saída for diferente de zero, ou qualquer outra exceção ocorrer: loga o erro (com o `stderr` do processo, quando disponível) e retorna `false` — nenhuma exceção escapa deste método.
   d. Em sucesso (código de saída zero e `output-dir/report.html` presente): retorna `true`.
5. `DefaultReportExporter.export(...)` não propaga o resultado de `HtmlReportGenerator` como falha do pipeline — o método é `void`; o log já registrado em 4c é a única sinalização de que o HTML não foi gerado.
6. Do lado do script Python (`scripts/generate-report.py`):
   a. Parseia `--json-path` e `--output-dir`.
   b. Lê e desserializa o `report.json`.
   c. Renderiza `templates/report.html.j2` via Jinja2, incluindo a construção da sintaxe do diagrama Mermaid a partir do `dependencyGraph` do JSON.
   d. Escreve `output-dir/report.html`.
   e. Encerra com código de saída `0` em sucesso; código diferente de zero e mensagem de erro em `stderr` em qualquer falha (JSON inválido, erro de renderização de template).

---

## 5. Estratégia de Testes

### Lado Java — JUnit 5 + AssertJ

**`JsonExporterTest`** (unitário)
- `ProjectScan` + `AnalysisResult` válidos, com ciclos e violações → `report.json` gerado contém todos os campos esperados (métricas, ciclos com caminho e explicação, violações com explicação, dependências por pacote, amostras de classe).
- `AnalysisResult` sem ciclos/violações → JSON válido com arrays vazios, sem erro.
- `generatedAt` presente e coerente com o momento da chamada.
- Arquivo é escrito exatamente em `outputDir/report.json`.

**`DefaultReportExporterTest`** (integração — chamada real ao Python, quando disponível no ambiente de execução dos testes)
- Python disponível e script bem-sucedido → `report.json` e `report.html` presentes em `outputDir`.
- Python indisponível (simulado apontando o comando para um executável inexistente) → `report.json` presente, `report.html` ausente, `export(...)` não lança exceção.
- Script Python retorna código de saída diferente de zero (fixture de script que sempre falha) → mesmo comportamento: `report.json` presente, sem exceção propagada.
- `outputDir` não existe antes da chamada → é criado pelo Exporter.

### Lado Python — pytest

> Introduz `pytest` como primeiro framework de teste Python do projeto (consequência direta de 2.2 trazer Python para o pipeline) — ver Pendências, seção 6.

**`test_generate_report.py`**
- `report.json` de fixture válido (com ciclos e violações) → `report.html` gerado contém: bloco de diagrama Mermaid, seção de ciclos com os caminhos esperados, seção de violações com as explicações esperadas, tabela de métricas com os valores esperados.
- `report.json` de fixture sem ciclos/violações → `report.html` renderiza estado vazio nessas seções, sem erro de template (ex.: loop Jinja2 sobre lista vazia não deve quebrar o layout).
- `--json-path` apontando para arquivo inexistente ou JSON malformado → script encerra com código de saída diferente de zero e mensagem de erro clara em `stderr` (nunca falha silenciosamente nem gera um HTML corrompido).
- Sintaxe do diagrama Mermaid gerado corresponde à estrutura de grafo esperada para um `dependencyGraph` de fixture pequeno e conhecido.

---

## 6. Pendências

- **Python como nova dependência de ambiente:** esta Spec introduz a exigência de Python 3 + Jinja2 instalados para a geração do `report.html`, que hoje não está refletida nas Restrições do PRD (seção 7, que só cita Java 21) nem no `STATUS.md`. Recomenda-se atualizar os dois documentos para registrar essa restrição formalmente.
- **Customização de templates HTML** (tema escuro, branding): fora do v1 — decisão de tema neutro já fechada pelo PRD (2.6).
- **Geração de PDF ou outros formatos de saída:** fora do v1 — só `report.json`/`report.html`.
- **Upload automático para S3/cloud:** fora do v1 — os artefatos ficam apenas em disco local (`arqsync-reports/`).
- **Relatório com histórico (diffs entre execuções):** fora do v1 — depende de uma futura interface de consulta ao histórico persistido (já registrada como pendência na Spec do Persistence, seção 8), que ainda não existe.
