# Spec Técnica — Violação: Adapter sem Porta (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Depende de:** ADENDO-SPEC-scanner-supertypes.md e
> ADENDO-SPEC-analyzer-classificador-papel.md
> **Consumida por:** SPEC-diagram-concept.md (a linha adapter→núcleo deixa de ser
> ilustrativa e passa a refletir esta verificação real)

---

## 1. Visão Geral

Detecta adapters que não implementam nenhuma porta (interface) do núcleo do domínio —
uma violação estrutural específica da Arquitetura Hexagonal, no mesmo espírito da
detecção de violação de camada já existente (`SPEC-analyzer.md`, 2.3), mas com uma
regra própria: só se aplica quando o estilo arquitetural detectado é Hexagonal.

Entrega valor sozinha, como uma nova linha no relatório de violações — não depende do
diagrama conceitual (SPEC-diagram-concept.md) para existir.

## 2. Decisões de Design

### 2.1 Condicional ao estilo detectado

**Decisão:** a regra só executa quando `ArchitectureStyleDetector.detect(graph)`
retorna Hexagonal. Para qualquer outro estilo (Layered, Clean, DDD, UNKNOWN), a
verificação é pulada — não gera violação, nem "não aplicável", simplesmente não roda.

**Justificativa:**
- "Porta" é um conceito que só existe na Arquitetura Hexagonal. Aplicar essa regra a
  um projeto Layered, por exemplo, não tem correspondência conceitual válida — o
  detector reconheceria classes como "adapter" só por keyword de pacote, sem que o
  projeto sequer pretenda seguir esse padrão.
- Segue o mesmo espírito filosófico do `ArchitectureStyleDetector` (UNKNOWN como
  cidadão de primeira classe, SPEC-analyzer.md 2.4): uma regra que não se aplica não
  é um "miss", é esperado.

**Alternativas descartadas:**
- *Rodar sempre, independente do estilo.* Rejeitado: geraria falsos positivos em
  projetos que nunca pretenderam ter portas/adapters.

### 2.2 O que conta como "porta"

**Decisão:** uma porta é uma `interface` cujo pacote foi classificado como núcleo
(`Layer` equivalente de domínio/aplicação nesse contexto — ver classificador de papel
por pacote, também usado pelo diagrama conceitual). Um adapter é uma classe cujo
pacote foi classificado como adapter pelo mesmo classificador.

**Justificativa:**
- Reaproveita o classificador de papel por pacote descrito em
  `ADENDO-SPEC-analyzer-classificador-papel.md` em vez de criar uma segunda heurística
  de classificação — a mesma pergunta ("este pacote é núcleo ou adapter?") serve tanto
  pra desenhar o diagrama (Spec B) quanto pra checar a violação (esta spec). Nenhuma
  das duas specs "dona" desse classificador — ele é peça comum, extraída pra um
  adendo próprio.

### 2.3 Regra de violação

**Decisão:** para cada classe classificada como adapter, verificar se algum de seus
`superTypes` (ADENDO-SPEC-scanner-supertypes.md) corresponde a uma interface
declarada em um pacote classificado como núcleo. Se nenhum supertipo corresponder,
gerar violação `AdapterSemPortaViolation`.

**Justificativa:**
- Resolver `superTypes` (nomes simples, não qualificados) contra as interfaces
  conhecidas do próprio grafo de dependências segue a mesma resolução de nome pra
  pacote interno já usada para imports (SPEC-analyzer.md, 2.6) — reaproveita
  infraestrutura de resolução existente, não cria uma nova.
- Um adapter que implementa uma interface de biblioteca externa (ex.: `Serializable`)
  não deveria contar como "sem porta" nem como "com porta" — supertipos que não
  resolvem para um pacote interno do projeto são ignorados na checagem, não tratados
  como ausência de porta.

**Alternativas descartadas:**
- *Exigir que TODA interface do núcleo tenha ao menos um adapter implementando.*
  Rejeitado: essa é uma pergunta diferente (porta órfã, sem implementação) — fora de
  escopo desta spec, poderia ser uma violação futura separada se houver fricção real.

## 3. Modelo de Dados

Novo tipo, seguindo o padrão de `LayerViolation` (SPEC-analyzer.md, 3.2):

```java
public record AdapterSemPortaViolation(PackageName adapterPackage, String className) {
}
```

Incorporado em `AnalysisResult` ao lado das violações de camada já existentes —
mesma lista de violações do relatório, não uma seção separada.

## 4. Fora de Escopo desta Spec

- Diagrama visual (SPEC-diagram-concept.md, spec separada e sequencial a esta).
- Verificação de porta órfã (interface do núcleo sem nenhum adapter implementando).
- Aplicação da mesma lógica a Clean Architecture ou DDD — fica para uma spec futura
  se houver fricção real (Hashimoto): esses estilos têm suas próprias variações do
  conceito (gateways/presenters, repositories) que não mapeiam 1:1 pra "porta".

## 5. Nota de implementação (registrada durante a Fase 3)

`AnalysisResult` ganhou um campo próprio, `List<AdapterSemPortaViolation>
adapterPortViolations`, em vez de literalmente compartilhar o mesmo `List<LayerViolation>`
— unificar os dois tipos num único tipo `Violation` (ex.: interface `sealed`) exigiria
mudar a assinatura de `MetricsCalculator`, `DefaultGroqSuggestionService` e outros
consumidores que hoje dependem especificamente de `LayerViolation`, por um ganho que
não se justificava nesta fase. "Mesma lista de violações do relatório, não uma seção
separada" (seção 3 acima) foi satisfeita na camada de apresentação: `report.json` e
`report.html` combinam `violations` e `adapterPortViolations` numa única seção/lista
visual de "Violações", mesmo sendo dois tipos Java distintos por baixo. Revisitar a
unificação real se um terceiro tipo de violação aparecer e a duplicação de
código de apresentação começar a doer.
