# Spec Técnica — Suggest / Integração Groq (Fase 2, v1)

> **Status:** Implementado
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-31

---

## 1. Visão Geral

`--suggest` é a Fase 2 do ArqSync: uma flag opcional que, após a análise arquitetural completa, envia um resumo estruturado do `AnalysisResult` (métricas, ciclos, violações, estilo arquitetural — **nunca código-fonte**) para a API Groq (modelo Llama) e recebe de volta sugestões de melhoria arquitetural. As sugestões são exibidas no terminal e em uma seção adicional dos relatórios HTML/PDF já existentes.

A IA **nunca executa refatorações** — apenas sugere. `--suggest` é inteiramente opcional: sua ausência, a ausência de `GROQ_API_KEY`, ou qualquer falha na chamada à API nunca interrompem o pipeline nem alteram o exit code.

---

## 2. Decisões de Design

### 2.1 Resiliência: um retry, exit code sempre 0

**Decisão:** em qualquer falha de transporte ou status não-2xx (exceto 401), uma única tentativa adicional é feita após um backoff fixo de 2s. Se ainda assim falhar, `[WARN] Groq API indisponível, prosseguindo sem sugestões` é logado e o pipeline segue normalmente. Uma resposta 401 (chave inválida) nunca é reenviada — loga `[ERROR] GROQ_API_KEY inválida. Verifique sua configuração.` imediatamente. `GROQ_API_KEY` ausente é detectado antes de qualquer chamada de rede (`[WARN] GROQ_API_KEY não configurada...`).

**Justificativa:** `--suggest` é uma funcionalidade opcional (2. do PRD desta fase); nenhuma falha nela deveria produzir um exit code diferente de zero ou interromper a geração do relatório, que continua sendo a entrega principal.

### 2.2 Dados enviados: resumo estruturado, sem código-fonte

**Decisão:** o prompt enviado ao Groq é um JSON com o estilo arquitetural, métricas agregadas, e até 15 ciclos/violações (path/from/to/layers/tipo) — nunca nomes de classe ou trechos de código.

**Justificativa:** minimiza custo de tokens e risco de vazamento de propriedade intelectual do projeto analisado; o limite de 15 itens mantém o tamanho do prompt previsível independente do tamanho do projeto.

### 2.3 Saída: terminal + relatórios

**Decisão:** as sugestões (já ordenadas) são impressas no terminal logo após a resposta da IA, e persistidas em `report.json` (`aiSuggestions`), de onde o `generate-report.py` as renderiza em uma nova seção `#ai-suggestions` do `report.html`/`report.pdf`, visível apenas quando não vazia.

### 2.4 Consentimento: aviso a cada execução

**Decisão:** antes de cada chamada, um aviso é impresso: `[AVISO] --suggest enviará um resumo estruturado da análise (...) para a API Groq (https://api.groq.com)`. Não é interativo (não bloqueia pipelines/CI) — o próprio uso da flag já é o consentimento; o aviso só documenta o que está sendo enviado.

### 2.5 Priorização: severidade fixa por tipo

**Decisão:** o CLI ordena as sugestões recebidas por `SuggestionType` (ordem de declaração do enum): `CYCLE_BREAK` > `LAYER_VIOLATION` > `STYLE_MIGRATION` > `GENERAL`. A ordenação acontece em `DefaultGroqSuggestionService`, não depende da ordem em que a IA respondeu.

### 2.6 Parsing tolerante

**Decisão:** a API é chamada com `response_format: json_object` (Groq/OpenAI-compatible) para maximizar a chance de um JSON válido; mesmo assim, o parsing tenta extrair o primeiro bloco `{...}` via regex caso o conteúdo venha embrulhado em texto extra (ex.: cercas de markdown). Se ainda assim não for possível parsear, `[WARN] Resposta da IA em formato inesperado, prosseguindo sem sugestões` e a lista fica vazia.

### 2.7 Pipeline: síncrono, após a análise completa

**Decisão:** a chamada ao Groq acontece depois de `PersistenceService.save(...)` e antes de `ReportExporter.export(...)`, de forma síncrona/bloqueante — para que as sugestões já estejam disponíveis quando os relatórios forem gerados.

### 2.8 Configuração via variáveis de ambiente

**Decisão:** `GROQ_API_KEY` (obrigatória para a funcionalidade funcionar) e `GROQ_MODEL` (opcional, default `llama3.1-70b`) são lidas diretamente do ambiente (`System.getenv`), sem arquivo de configuração adicional.

---

## 3. Interface Pública

```java
public interface GroqSuggestionService {
    List<AiSuggestion> suggest(ProjectScan projectScan, AnalysisResult analysisResult);
}

public record AiSuggestion(
        SuggestionType type, String title, String description, String codeExample
) {}

public enum SuggestionType {
    CYCLE_BREAK, LAYER_VIOLATION, STYLE_MIGRATION, GENERAL
}
```

`ReportExporter.export(...)` e `JsonExporter.export(...)` ganharam um parâmetro `List<AiSuggestion> aiSuggestions` (vazio quando `--suggest` não foi passado); `ReportData` ganhou o campo `aiSuggestions`.

---

## 4. Pendências

- Sem cache de respostas entre execuções — cada `--suggest` sempre chama a API.
- Sem suporte a outros provedores de LLM além de Groq — fora do escopo desta fase.
- Sem truncamento inteligente do prompt para projetos com centenas de ciclos/violações além do limite de 15 por lista — revisitar apenas com evidência real de necessidade.
