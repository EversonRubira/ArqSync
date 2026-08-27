# Spec Técnica — CLI (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-27

---

## 1. Visão Geral

O CLI é o ponto de entrada do ArqSync e o último componente antes da implementação. Ele recebe o caminho de um projeto via linha de comando, orquestra as quatro etapas do pipeline em sequência — Scanner → Analyzer → Persistence → Exporter — e é responsável por toda a comunicação com o usuário no terminal (progresso, avisos, erros, mensagem final).

O CLI não tem lógica própria de domínio — sua única responsabilidade é orquestrar os componentes já especificados e decidir, para cada etapa, se uma falha é fatal (interrompe a execução) ou absorvível (loga e segue). Essa decisão de "o que é fatal" é o núcleo real desta Spec: as quatro etapas não são todas igualmente críticas para o resultado mínimo que o ArqSync promete ao usuário.

O v1 é uma CLI pura de um único argumento, sem flags, sem modo interativo, sem interface gráfica.

---

## 2. Decisões de Design

Cada decisão segue o princípio Hashimoto: a decisão, a justificativa, e as alternativas descartadas.

### 2.1 Entrada única: um argumento, sem flags

**Decisão:** o CLI aceita exatamente um argumento posicional — o caminho do projeto. Nenhuma flag (`--help`, `--verbose`, `--output-dir`) é implementada no v1.

**Justificativa:**
- É exatamente o que o PRD já define como ponto de entrada (seção 3, item 3: `java -jar arqsync.jar /caminho/do/projeto`). Adicionar flags especulativamente, sem um caso de uso real que as exija, seria featuritis — o próprio risco que o PRD identifica e mitiga com critério de pronto claro (seção 8).

**Alternativas descartadas:**
- *Adicionar `--verbose`/`--output-dir` desde já, "porque toda CLI tem isso".* Rejeitado por Hashimoto: sem fricção real demonstrada ainda (ninguém usou a ferramenta e sentiu falta), a complexidade é prematura.

### 2.2 Orquestração síncrona, sem paralelismo entre etapas

**Decisão:** Scanner, Analyzer, Persistence e Exporter são chamados sequencialmente, um depois do outro, na mesma thread.

**Justificativa:**
- Há uma dependência de dados real e inescapável entre as etapas: o Analyzer precisa do `ProjectScan` completo; Persistence e Exporter precisam do `AnalysisResult` completo. Não existe paralelismo genuíno a explorar entre Scanner→Analyzer (uma depende inteiramente da outra).
- Persistence e Exporter, uma vez que ambos só dependem de `ProjectScan`+`AnalysisResult` (não um do outro), *poderiam* rodar em paralelo — mas ambos são operações rápidas (um insert local e uma escrita de arquivo + subprocess), sem custo de espera (I/O de rede) que justifique a complexidade de coordenar threads e intercalar logs de duas etapas concorrentes. Consistente com a mesma decisão de "sem paralelismo" já tomada no Scanner (Spec do Scanner, 2.8) pelo mesmo motivo: sem gargalo demonstrado na escala alvo do v1.

**Alternativas descartadas:**
- *Rodar Persistence e Exporter em paralelo (threads/futures).* Rejeitado: complexidade real (sincronização, intercalação de logs, tratamento de duas falhas concorrentes) sem ganho de performance demonstrado.

### 2.3 Níveis de log: INFO para progresso, WARN para avisos, ERROR para falhas fatais

**Decisão:** `INFO` marca cada etapa iniciada (ex.: "Scanning project...", "Analyzing dependencies..."); `WARN` marca avisos que não interrompem o fluxo (arquivo pulado no Scanner, Python não encontrado no Exporter, falha de persistência); `ERROR` marca uma falha que interrompe a execução.

**Justificativa:**
- Mantém consistência com o logging já decidido no Scanner (SLF4J, WARN por arquivo pulado — Spec do Scanner, 2.9) — o CLI estende a mesma convenção de níveis para o pipeline inteiro, em vez de inventar uma nova.
- `INFO` por etapa atende ao caráter didático/interativo da persona do PRD (seção 4): o usuário vê o que está acontecendo em tempo real, sem precisar abrir o relatório para descobrir se algo travou.

**Alternativas descartadas:**
- *Barra de progresso ou output colorido/rico no terminal.* Rejeitado: complexidade de UI de terminal sem necessidade demonstrada — texto simples com níveis de log já atende ao objetivo didático.

### 2.4 Fatalidade assimétrica por etapa

**Decisão:**
- **Scanner** — se `ScannerService.scan(path)` lançar `InvalidProjectPathException` (caminho não existe ou não é diretório — Spec do Scanner, seção 4), a execução é interrompida com `ERROR`.
- **Analyzer** — qualquer exceção de `DependencyAnalyzer.analyze(...)` interrompe a execução com `ERROR`. O Analyzer é um componente puro e determinístico (Spec do Analyzer, seção 1) — não há um caso de erro "esperado" documentado para ele; qualquer exceção aqui é tratada como bug interno, e não há um modo degradado sensato para continuar sem um `AnalysisResult`.
- **Persistence** — `PersistenceService.save(...)` nunca lança exceção, por contrato (Spec do Persistence, 2.1). O CLI apenas chama o método e segue; nenhum `try/catch` adicional é necessário nem adicionado no CLI para essa chamada (ver 2.8).
- **Exporter** — a geração do `report.html` nunca é fatal (Spec do Exporter, 2.3). A geração do `report.json`, por outro lado, **é** tratada como fatal se falhar: sem `report.json`, nenhum artefato de saída foi produzido, e a execução não cumpriu sua promessa mínima ao usuário (ver 2.7).

**Justificativa:**
- Essa assimetria reflete o que é indispensável para o resultado mínimo do ArqSync: sem `ProjectScan` ou `AnalysisResult`, não há nada para persistir, exportar, ou mostrar ao usuário — a execução perdeu seu propósito. Persistence e a metade "HTML" do Exporter são, por definição das próprias Specs anteriores, camadas opcionais sobre um resultado que já existe.

**Alternativas descartadas:**
- *Tratar toda e qualquer falha como fatal, uniformemente.* Rejeitado: contraria diretamente a resiliência já especificada em Persistence (2.1) e Exporter (2.3) — essas Specs foram desenhadas exatamente para não interromper o fluxo.
- *Tratar toda falha como não fatal (logar e seguir sempre).* Rejeitado: um `AnalysisResult` ausente não tem como virar relatório algum — seguir adiante geraria uma exceção mais confusa (ex.: `NullPointerException`) em vez de uma mensagem clara de que o scan falhou.

### 2.5 Mensagem final decidida por checagem de arquivo, não por valor de retorno

**Decisão:** `ReportExporter.export(...)` é `void` (Spec do Exporter, seção 3) — o CLI não recebe nenhuma indicação direta de se o `report.html` foi gerado. Por isso, após a chamada, o CLI verifica com `Files.exists(outputDir.resolve("report.html"))` para decidir a mensagem final: se existir, exibe "Report generated successfully at: `.../report.html`"; se não existir, exibe uma mensagem equivalente apontando para `report.json` (o artefato garantido), sem alegar sucesso do HTML.

**Justificativa:**
- Não há necessidade de alterar o contrato já fechado do `ReportExporter` (retorno `void`) só para o CLI saber compor a mensagem final — os avisos específicos de por que o HTML não foi gerado (Python ausente vs. script falhou, ver 2.6) já são logados pelo próprio `HtmlReportGenerator` no momento em que acontecem (Spec do Exporter, 2.3). O CLI só precisa saber "o arquivo existe ou não" para a mensagem de encerramento — uma checagem simples de filesystem resolve isso sem acoplar as duas Specs mais do que o necessário.

**Alternativas descartadas:**
- *Mudar `ReportExporter.export(...)` para retornar um valor indicando sucesso do HTML.* Rejeitado: reabriria uma interface já especificada e commitada na Spec do Exporter por um ganho pequeno — uma checagem de arquivo no CLI resolve o mesmo problema sem essa mudança.

### 2.6 Distinção WARN/ERROR na ausência vs. falha do Python (refinamento do Exporter)

**Decisão:** dentro do `HtmlReportGenerator` (Spec do Exporter), a ausência do interpretador Python (`python3`/`python` não encontrados no `PATH`) é logada em nível `WARN`; uma falha do script já em execução (código de saída diferente de zero) é logada em nível `ERROR`. Essa distinção de nível pertence à implementação do Exporter (que já sabe qual dos dois casos ocorreu), não ao CLI.

**Justificativa:**
- A Spec do Exporter (2.3) já definia que ambos os casos "logam o erro e continuam", mas não distinguia o nível. Ausência de Python é um problema de ambiente esperado e documentado (ver Pendências, seção 6) — mais brando; um script que já rodou e falhou é mais provável de indicar um bug real (JSON malformado, erro de template) — mais sério. Essa distinção ajuda o usuário e o desenvolvedor a priorizar o que investigar.
- Isso é um refinamento de um comportamento já especificado (o "logar o erro" da Spec do Exporter), não uma mudança de contrato — a interface pública do Exporter não muda.

**Alternativas descartadas:**
- *Manter os dois casos no mesmo nível de log.* Rejeitado: perde a informação de qual dos dois é mais provável de ser um problema de ambiente vs. um bug — informação barata de preservar, já que o `HtmlReportGenerator` sabe a diferença no momento em que loga.

### 2.7 Falha na geração do `report.json` é fatal

**Decisão:** se `ReportExporter.export(...)` propagar uma exceção (o que só pode vir da etapa de geração do JSON — a etapa de HTML já é fire-and-forget internamente, 2.5/2.6), essa exceção é tratada como fatal pelo CLI, com o mesmo tratamento de `ERROR` de 2.4.

**Justificativa:**
- A Spec do Exporter não define a geração do `report.json` como resiliente a falhas (ao contrário do HTML) — e faz sentido que não seja: se nem o `report.json` (o artefato garantido, "primário" segundo a própria Spec do Exporter) puder ser escrito, nenhum artefato de saída existe, e não há razão para a execução "ter sucesso silenciosamente".

**Alternativas descartadas:**
- *Tratar qualquer falha do `ReportExporter` como não fatal, como o HTML.* Rejeitado: mascararia uma execução que não produziu nenhum artefato como se tivesse sucesso — pior do que simplesmente reportar o erro.

### 2.8 Sem `try/catch` redundante ao redor do Persistence

**Decisão:** o CLI chama `PersistenceService.save(...)` diretamente, sem envolver a chamada em `try/catch`.

**Justificativa:**
- A Spec do Persistence (2.1) já garante, como contrato de interface, que `save(...)` nunca lança exceção. Adicionar um `try/catch` no CLI duplicaria uma responsabilidade que já pertence inteiramente ao Persistence, e esconderia — em vez de expor — uma eventual violação futura desse contrato (se o Persistence um dia lançar uma exceção por engano, é melhor que ela quebre um teste do próprio Persistence do que seja silenciosamente engolida em uma segunda camada de proteção no CLI).

**Alternativas descartadas:**
- *Adicionar um `try/catch` defensivo mesmo assim, "por segurança".* Rejeitado: viola a confiança no contrato já especificado e testado (Spec do Persistence, seção 6) e duplica responsabilidade sem necessidade.

### 2.9 Saída limpa em erro: captura + log amigável + `System.exit`, sem stack trace bruto

**Decisão:** toda exceção fatal (2.4) é capturada no nível do orquestrador, logada em `ERROR` com uma mensagem amigável de uma linha (sem stack trace completo), e a execução termina com um código de saída diferente de zero via uma abstração `ProcessExiter` (não `System.exit(...)` chamado diretamente no meio do código de orquestração).

```java
public interface ProcessExiter {
    void exit(int code);
}
```

A implementação padrão (`SystemProcessExiter`) chama `System.exit(code)`. Em testes, uma implementação de teste apenas registra o código recebido, sem encerrar a JVM.

**Justificativa:**
- Deixar a exceção propagar sem captura e confiar no comportamento padrão do Spring Boot geraria um stack trace completo e o banner "Application run failed" no terminal — verboso e pouco didático para a persona do PRD (desenvolvedor em formação, seção 4), que se beneficia de uma mensagem de erro clara e curta.
- Chamar `System.exit(...)` diretamente espalhado pelo código de orquestração tornaria esse código impossível de testar sem encerrar a JVM do próprio teste — encapsular atrás de `ProcessExiter` permite testar "o código de saída correto foi solicitado" sem matar o processo de teste (ver seção 5).

**Alternativas descartadas:**
- *Deixar a exceção propagar e confiar no tratamento padrão do Spring Boot.* Rejeitado: saída ruidosa (stack trace completo), pouco alinhada ao objetivo didático do relatório e do próprio uso da ferramenta.
- *Chamar `System.exit(...)` diretamente, sem abstração.* Rejeitado: impede testar o código de saída de forma isolada e determinística.

### 2.10 Nota sobre nomenclatura: colisão com `org.springframework.boot.CommandLineRunner`

**Observação (não uma decisão fechada):** a assinatura pedida —

```java
@Component
public class CommandLineRunner implements ApplicationRunner {
```

— nomeia a classe `CommandLineRunner`, que é exatamente o nome de uma interface do próprio Spring Boot (`org.springframework.boot.CommandLineRunner`, uma alternativa a `ApplicationRunner` para o mesmo propósito). A classe aqui *implementa* `ApplicationRunner`, não `CommandLineRunner` — então não há erro de compilação, mas o nome é confuso: qualquer import de `org.springframework.boot.CommandLineRunner` no mesmo arquivo colidiria, e o nome sugere estar implementando uma interface diferente da que de fato implementa.

**Recomendação:** renomear para algo como `ArqSyncPipelineRunner` ou `ArqSyncApplicationRunner` na implementação. Mantida a assinatura como especificada nesta Spec (ver seção 3) por ser a decisão já fechada; o rename é uma sugestão de nomenclatura para a fase de implementação, registrada aqui para não ser esquecida.

---

## 3. Interface Pública

```java
@Component
public class CommandLineRunner implements ApplicationRunner {

    private final ScannerService scannerService;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final PersistenceService persistenceService;
    private final ReportExporter reportExporter;
    private final ProcessExiter processExiter;

    // construtor com injeção das cinco dependências acima

    @Override
    public void run(ApplicationArguments args) {
        // ver Fluxo de Execução, seção 4
    }
}

public interface ProcessExiter {
    void exit(int code);
}
```

> Ver 2.10 sobre a recomendação de renomear a classe na implementação.

---

## 4. Fluxo de Execução

1. Valida os argumentos: exatamente um argumento não vazio (o caminho do projeto). Se ausente/vazio, loga `ERROR` ("Uso: java -jar arqsync.jar <caminho-do-projeto>") e sai via `ProcessExiter.exit(1)` — sem chamar nenhum componente do pipeline.
2. Loga `INFO` ("Scanning project...") e chama `ScannerService.scan(path)`.
   - Se lançar `InvalidProjectPathException`: loga `ERROR` com a mensagem da exceção, `ProcessExiter.exit(1)`, execução encerrada (2.4).
3. Loga `INFO` ("Analyzing dependencies...") e chama `DependencyAnalyzer.analyze(projectScan)`.
   - Se lançar qualquer exceção: loga `ERROR`, `ProcessExiter.exit(1)`, execução encerrada (2.4).
4. Loga `INFO` ("Saving analysis...") e chama `PersistenceService.save(projectScan, analysisResult)` — sem `try/catch` (2.8); por contrato, nunca lança.
5. Calcula `outputDir = Paths.get("arqsync-reports", timestamp)`, com `timestamp` formatado como `yyyy-MM-dd-HH-mm-ss`.
6. Loga `INFO` ("Generating report...") e chama `ReportExporter.export(projectScan, analysisResult, outputDir)`.
   - Se lançar exceção (falha ao gerar o `report.json` — 2.7): loga `ERROR`, `ProcessExiter.exit(1)`, execução encerrada.
7. Verifica `Files.exists(outputDir.resolve("report.html"))` (2.5):
   - Se existir: loga mensagem final de sucesso — `"Report generated successfully at: arqsync-reports/.../report.html"`.
   - Se não existir: loga mensagem final apontando para o `report.json` gerado, sem alegar sucesso do HTML (os avisos específicos de por que o HTML não foi gerado já foram logados internamente pelo Exporter, 2.6).
8. Execução termina normalmente (sem chamar `ProcessExiter.exit(...)` — código de saída `0` implícito).

---

## 5. Estratégia de Testes

- **Framework:** JUnit 5 + AssertJ, com o contexto Spring Boot de teste para os casos de integração (`@SpringBootTest`).
- Em todos os testes, as dependências de componente (`ScannerService`, `DependencyAnalyzer`, `PersistenceService`, `ReportExporter`) são substituídas por *test doubles* (mocks/stubs), exceto no teste de pipeline completo, que usa implementações reais com fixtures — o CLI em si não tem lógica de domínio própria para testar além da orquestração e da decisão de fatalidade por etapa.
- `ProcessExiter` é sempre substituído por uma implementação de teste que registra o código de saída solicitado, para nunca encerrar a JVM de teste (2.9).

### Casos de teste

- **Caminho válido, pipeline completo** (fixture real do Scanner, ponta a ponta): todas as etapas chamadas na ordem correta; `report.json` gerado; mensagem final de sucesso; `ProcessExiter.exit(...)` nunca chamado.
- **Caminho inválido** (`ScannerService.scan(...)` lança `InvalidProjectPathException`): log `ERROR` emitido; `Analyzer`/`Persistence`/`Exporter` **nunca** chamados; `ProcessExiter.exit(1)` chamado.
- **Analyzer lança exceção** (mock de `DependencyAnalyzer` lançando `RuntimeException`): log `ERROR` emitido; `Persistence`/`Exporter` **nunca** chamados; `ProcessExiter.exit(1)` chamado.
- **Banco indisponível** (mock de `PersistenceService.save(...)` — como o contrato garante que não lança, o teste apenas verifica que o CLI chama `save(...)` e segue adiante para o Exporter normalmente, independentemente do que aconteça dentro do Persistence).
- **Python indisponível** (mock de `ReportExporter` cujo `export(...)` não gera `report.html` no `outputDir`, mas retorna normalmente): `report.json` presente; mensagem final aponta para o JSON, não alega sucesso do HTML; `ProcessExiter.exit(...)` **não** é chamado (não é fatal).
- **`ReportExporter.export(...)` lança exceção** (falha ao gerar o `report.json`): log `ERROR` emitido; `ProcessExiter.exit(1)` chamado.
- **Nenhum argumento / argumento vazio**: log `ERROR` de uso emitido; nenhum componente do pipeline é chamado; `ProcessExiter.exit(1)` chamado.
- **Teste de integração com o contexto Spring** (`@SpringBootTest`): o contexto sobe corretamente com todos os beans (`ScannerService`, `DependencyAnalyzer`, `PersistenceService`, `ReportExporter`, `ProcessExiter`) injetados na classe orquestradora, confirmando que a configuração de `@Component`/`ApplicationRunner` está correta.

---

## 6. Pendências

- **Flags de linha de comando** (`--help`, `--verbose`, `--output-dir`): fora do v1 (2.1) — adicionar apenas se houver fricção real de uso.
- **Modo silencioso** (sem logs): fora do v1.
- **Saída para stdout** (em vez de arquivo): fora do v1 — os artefatos são sempre gravados em `arqsync-reports/[timestamp]/`.
- **Paralelismo entre etapas**: fora do v1 (2.2) — revisitar apenas com evidência real de que a soma sequencial das quatro etapas é lenta o suficiente para incomodar o usuário.
- **Execução em modo "apenas JSON" ou "apenas HTML"**: fora do v1 — o pipeline sempre tenta gerar os dois artefatos, com o HTML sendo best-effort (Spec do Exporter).
- **Rename de `CommandLineRunner`**: ver 2.10 — recomendado renomear na implementação para evitar colisão de nome com `org.springframework.boot.CommandLineRunner`.

---

## 7. Sinalização: atualização do PRD (Python como restrição de ambiente)

A Spec do Exporter já havia registrado que Python 3 + Jinja2 são exigidos em runtime para gerar o `report.html`, sem que isso estivesse refletido nas Restrições do PRD (seção 7, que hoje só cita Java 21). Esta Spec do CLI é o ponto natural para fechar essa lacuna, já que é o componente que efetivamente decide o comportamento de fallback (2.6): **esta atualização foi aplicada diretamente ao PRD** (`docs/prd/PRD-arqsync.md`, seção 7), adicionando:

> Python 3.8+ com Jinja2 instalado é necessário para gerar o `report.html` — se não disponível, o v1 gera apenas o `report.json` (fallback).
