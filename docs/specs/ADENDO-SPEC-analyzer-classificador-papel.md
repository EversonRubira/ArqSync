# Adendo — SPEC Técnica Analyzer: Classificador de Papel por Pacote

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Reabre:** `docs/specs/SPEC-analyzer.md` (aprovada)
> **Consumido por:** `SPEC-adapter-port-violation.md` (Spec A) e
> `SPEC-diagram-concept.md` (Spec B) — peça compartilhada entre as duas, não parte
> exclusiva de nenhuma delas. Extraído para este adendo próprio depois que a revisão
> cruzada entre A e B expôs uma dependência circular de documentação: Spec A citava o
> classificador como definido em Spec B, mas Spec B também dependia de Spec A. Nenhuma
> das duas specs deveria depender da outra pra uma peça que as duas só consomem.

---

## 1. Contexto

`SPEC-analyzer.md` já está aprovada e define `LayerResolver`, que classifica pacotes
em `Layer` (`CONTROLLER`, `SERVICE`, `REPOSITORY`, `DOMAIN`, `UNKNOWN`) só pra
convenção Layered. Este adendo generaliza essa ideia — um classificador de papel por
pacote — para os outros estilos que `DefaultArchitectureStyleDetector` já reconhece
(Hexagonal, Clean Architecture, DDD), mais uma categoria transversal nova.

## 2. Decisão de Design

### 2.1 Um classificador por estilo, reaproveitando o vocabulário existente

**Decisão:** análogo ao `LayerResolver` existente, cada estilo ganha um classificador
que atribui um papel a cada pacote do grafo. Reaproveita o vocabulário de keywords já
presente em `DefaultArchitectureStyleDetector` (`port`/`adapter`, `usecase`/`entities`,
`aggregate`/`domain`/`application`/`infrastructure`), só que aplicado por pacote
individual em vez de uma vez para o projeto inteiro.

**Justificativa:**
- O detector de estilo já teve que resolver "que palavras sinalizam qual conceito" pra
  decidir o estilo do projeto inteiro — a mesma pergunta, feita por pacote em vez de
  uma vez só, não é uma heurística nova, é a heurística existente aplicada em
  granularidade diferente.
- Manter o mesmo vocabulário evita que o classificador por pacote discorde do
  detector de estilo (ex.: o projeto ser detectado como Hexagonal, mas um pacote
  `adapter` não ser reconhecido como tal pelo classificador).

**Alternativas descartadas:**
- *Um classificador por estilo com vocabulário próprio, independente do detector.*
  Rejeitado: duplicaria conhecimento que já existe em
  `DefaultArchitectureStyleDetector`, com risco real de divergência entre "que estilo
  o projeto é" e "que papel cada pacote tem".

### 2.2 Quinta categoria: `Layer.CROSS_CUTTING`

**Decisão:** o enum `Layer` ganha uma quinta categoria, `CROSS_CUTTING`, para pacotes
como `config`, `logging`, `infrastructure`, `framework` — código transversal que não
pertence à sequência principal de camadas.

**Justificativa:**
- Necessária para popular a faixa lateral de Infraestrutura do diagrama Layered
  (`SPEC-diagram-concept.md`, 2.3) — hoje nenhum pacote é reconhecido como código
  transversal.
- Nome `CROSS_CUTTING`, não `INFRASTRUCTURE`: o ArqSync já tem um pacote
  `com.arqsync.persistence` — a infraestrutura do próprio ArqSync. Um valor
  `Layer.INFRASTRUCTURE` colidiria semanticamente entre "papel do pacote do projeto
  *analisado*" e "código do próprio ArqSync". `CROSS_CUTTING` evita a ambiguidade sem
  perder precisão.
- Essa categoria **não afeta detecção de violação de camada** (`SPEC-analyzer.md`,
  2.3) — é usada só para popular o diagrama e a regra de violação da Spec A, não para
  a regra de negócio já existente.

## 3. Consumidores

- **Spec A** (`SPEC-adapter-port-violation.md`): usa este classificador pra decidir o
  que conta como "núcleo" (porta) e o que conta como "adapter" na regra de violação —
  não é uma segunda heurística, é a mesma pergunta respondida uma vez, consumida pelos
  dois lugares.
- **Spec B** (`SPEC-diagram-concept.md`): usa este classificador para popular os anéis
  concêntricos (Hexagonal/Clean/DDD) e as faixas do Layered, incluindo a categoria
  `CROSS_CUTTING` na faixa de Infraestrutura.

## 4. Ordem de implementação

Este adendo é pré-requisito comum — nem Spec A nem Spec B começam antes dele existir.
Ordem completa do conjunto:

```
ADENDO-SPEC-scanner-supertypes.md ─┐
                                     ├─→ Spec A (violação) ─→ Spec B (diagrama)
ADENDO-SPEC-analyzer-classificador  ┘         ↑
                                          Spec B também depende
                                          deste adendo diretamente
                                          (não só via Spec A)
```

Scanner e este adendo não dependem um do outro — podem ser implementados em qualquer
ordem entre si, mas ambos precisam existir antes da Spec A.

## 5. Nota de implementação (registrada durante a Fase 3)

A Fase 3 implementou este adendo e a Spec A juntas, mas **deferiu Spec B** (diagrama
conceitual) para uma etapa futura — decisão do usuário, não desta spec. Consequência
prática: só a parte do classificador que a Spec A realmente consome foi implementada
agora — a heurística Hexagonal (`port`/`ports` → núcleo, `adapter`/`adapters` →
adapter). As heurísticas de Clean Architecture e DDD, e a categoria `Layer.CROSS_CUTTING`
(2.2 acima, usada só pelo diagrama Layered), **não têm nenhum consumidor ainda** e
foram deixadas de fora por Hashimoto — mesmo critério já usado em várias outras specs
deste projeto ("não construir sem necessidade demonstrada"). `PackageRoleClassifier`
foi desenhado como uma interface própria — sua assinatura (`classify(DependencyGraph,
ArchitectureStyle)`) já é genérica o bastante e não deve precisar mudar quando
Clean/DDD forem adicionados — mas a implementação atual não é uma estratégia por
estilo; ver seção 6 sobre o que isso significa na prática para quem for implementar
Spec B.

## 6. Riscos / Nota Técnica: `DefaultPackageRoleClassifier` é uma classe única, não strategy-per-style

`DefaultPackageRoleClassifier` hoje é uma classe única com `if/else` (`classify()`
decide `hexagonal ? classifyHexagonal(pkg) : UNKNOWN`), não um `strategy` com uma
implementação por estilo. Adicionar Clean/DDD vai exigir editar `classify()`/
`fromSegment()` diretamente, não só plugar uma implementação nova — uma segunda
`@Component` implementando `PackageRoleClassifier` causaria
`NoUniqueBeanDefinitionException` na injeção de `DefaultDependencyAnalyzer` (que
espera um único bean desse tipo), a menos que se introduza `@Qualifier`, uma lista de
classificadores com um dispatcher, ou se refatore para composição nesse momento.

Decisão consciente por Hashimoto — três implementações de estilo para dois
consumidores reais (Spec A só usa Hexagonal) seria complexidade antecipada sem
necessidade demonstrada — documentada aqui para quem for implementar Spec B não
presumir que é só "adicionar uma classe nova": o ponto de decisão real está dentro do
método `classify()` existente.
