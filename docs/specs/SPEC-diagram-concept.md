# SPEC — Diagrama Arquitetural Conceitual (v1)

> **Status:** Rascunho para revisão — **não implementada nesta fase** (ver seção 6)
> **Metodologia:** Spec-Driven Development (SDD)
> **Extensão de:** `SPEC-analyzer.md` (novo classificador de papel) e
> `SPEC-exporter.md` (novo gerador visual)
> **Depende de:** ADENDO-SPEC-analyzer-classificador-papel.md (diretamente — os anéis
> e as faixas usam o classificador) e `SPEC-adapter-port-violation.md` (Spec A — a
> linha Adapter→Núcleo no Hexagonal usa a verificação real produzida por essa spec).
> Ordem de entrega: ADENDO-SPEC-scanner-supertypes.md +
> ADENDO-SPEC-analyzer-classificador-papel.md → Spec A → Spec B (esta).

## 1. Motivação

O Analyzer já detecta o estilo arquitetural do projeto (`DefaultArchitectureStyleDetector`:
Layered, Hexagonal, Clean Architecture, DDD), mas o relatório atual não usa esse dado —
o grafo Mermaid/Graphviz é sempre o mesmo tipo de visualização (dependências reais entre
pacotes, com ciclos destacados), independente do estilo identificado.

Esta spec adiciona um segundo diagrama, **complementar** ao grafo atual, que desenha a
*forma conceitual* do estilo detectado, preenchida com os pacotes reais do projeto
analisado. Objetivo: valor didático para quem estuda arquitetura de software — ver a
própria base de código encaixada no padrão teórico, não só uma lista de violações.

## 2. Decisões de design

### 2.1 Complementa, não substitui
O grafo de dependências (Mermaid HTML / Graphviz PDF) continua existindo — ele responde
"quem depende de quem de fato" (dado de import, capturado pelo Analyzer hoje). O novo
diagrama responde "como o projeto se encaixa no padrão teórico" — uma leitura
arquitetural, não de dependência literal.

**Alternativa descartada**: substituir o grafo atual. Descartado porque o grafo mostra
ciclos e violações — informação funcional que o diagrama de forma não captura.

### 2.2 Duas famílias de layout, não quatro

| Estilo | Estrutura | Forma |
|---|---|---|
| Hexagonal | Núcleo → Portas → Adapters | Hexágonos concêntricos (topo achatado) |
| Clean Architecture | Entidades → Casos de uso → Adapters → Frameworks | Círculos concêntricos |
| DDD | Domínio → Aplicação → Infraestrutura | Círculos concêntricos |
| Layered | Controller → Service → Repository → Domain | Faixas empilhadas |

Hexagonal, Clean e DDD compartilham a mesma estrutura de dados (N camadas ordenadas,
top-N por contagem, dependência sempre apontando pra dentro) e o mesmo **gerador de
anéis concêntricos** — a única diferença entre eles é a função de vértice do polígono:
hexágono de topo achatado para Hexagonal (forma que dá nome ao estilo, mantida por
valor didático — ver alternativa descartada abaixo), círculo para Clean e DDD. Ambas
as funções recebem os mesmos parâmetros (`N camadas`, raio por camada, rótulos) — só
o cálculo de vértices muda. Não é um gerador dedicado por forma, é um parâmetro
`shape: hexagon | circle` do mesmo gerador.

Layered é estruturalmente diferente (direcional, não concêntrico) — precisa de um
segundo gerador, faixas horizontais empilhadas.

**Alternativa descartada**: um gerador dedicado por estilo (4 no total). Descartado
por Hashimoto — Hexagonal/Clean/DDD têm a mesma estrutura de anéis, só a forma final
difere.

**Alternativa descartada (Hexagonal, revisão anterior)**: usar círculo também para
Hexagonal, por uniformidade total com Clean/DDD. Testado em mockup e revertido — sem
o hexágono literal, o diagrama perde o gancho didático que dá nome ao próprio estilo
(a "Arquitetura Hexagonal" sem hexágono não comunica nada por si só). O custo de manter
uma segunda função de vértice (hexágono de topo achatado, mesma lógica de "encolher por
camada" do círculo) é baixo o suficiente para valer a pena.

**Alternativa descartada (Hexagonal, primeira versão)**: hexágono único com caixas de
adapter presas à borda externa por linhas. Descartada em favor de hexágonos concêntricos
— mais consistente com o gerador de anéis usado pelos outros dois estilos, sem exigir
tratamento à parte para caixas externas conectadas por linha.

**Alternativa descartada (Clean Architecture)**: desenhar como cone (fatias
trapezoidais), fiel à imagem clássica do "Clean Architecture Cone". Descartado porque
exigiria um terceiro gerador geométrico (cálculo de fatia, não de anel) sem ganho de
generalização sobre círculos concêntricos.

### 2.3 Layered: tiers (template) + faixa de Infraestrutura (dado novo)

O diagrama de faixas empilhadas agrupa as camadas em tiers (1st/2nd/3rd) — puramente
visual, um template fixo no Exporter sobre as categorias que o `Layer` enum já resolve
(`CONTROLLER`, `SERVICE`, `REPOSITORY`, `DOMAIN`). Não exige mudança no Analyzer.

Uma faixa lateral de **Infraestrutura** (framework, logging, config) também faz parte
do MVP. Diferente dos tiers, isso exige uma quinta categoria no `Layer` enum e uma
heurística nova de classificação — hoje nenhum pacote é reconhecido como "código
transversal". Ver seção 4.

**Nome da categoria: `Layer.CROSS_CUTTING`, não `INFRASTRUCTURE`.** O ArqSync já tem
um pacote `com.arqsync.persistence` — a infraestrutura do próprio ArqSync. Um valor
`Layer.INFRASTRUCTURE` colidiria semanticamente: um classifica pacotes do projeto
*analisado*, o outro é o código do próprio ArqSync. `CROSS_CUTTING` evita a
ambiguidade sem perder precisão — o conceito é "código que atravessa camadas", não
"infraestrutura" no sentido genérico.

### 2.4 Granularidade: por pacote, agregado
Dentro de cada camada/região, os itens listados são pacotes (não classes individuais),
com contagem de classes ao lado. Evita listas de dezenas/centenas de classes dentro de
uma forma geométrica de espaço fixo.

### 2.5 Anti-explosão: top-N + overflow

**Faixas empilhadas (Layered)**: cada faixa mostra até N pacotes (ordenados por
quantidade de classes, decrescente) + um indicador agregado `"+X outros"`.

**Anéis concêntricos (Hexagonal/Clean/DDD)**: o espaço horizontal disponível dentro de
uma faixa anelar é bem mais estreito que dentro de uma faixa retangular — um nome de
pacote a mais já estoura a largura em vários anéis, especialmente nos mais externos e
finos. Por isso os anéis mostram **só título da camada + contagem agregada** (ex.:
"6 pacotes · 34 classes"), sem listar nomes de pacote. `N` efetivo é zero para essa
família de layout — a lista de nomes fica só nas faixas empilhadas.

Isso garante que a forma nunca cresce com o tamanho do projeto — funciona igual para
um monólito pequeno ou um projeto com centenas de pacotes.

### 2.6 Linha Adapter → Núcleo: verificada, não ilustrativa

**Decisão:** no diagrama Hexagonal, a relação visual entre o anel de Adapters e o
Núcleo reflete a verificação real de `SPEC-adapter-port-violation.md` (Spec A) — um
adapter que não implementa nenhuma porta do domínio é uma violação detectada por essa
spec, não apenas uma linha decorativa entre anéis vizinhos.

**Justificativa:**
- Uma linha puramente ilustrativa (anéis vizinhos, sem checagem) entregaria o
  diagrama mais rápido, mas viraria decoração sem valor de verificação — um projeto
  que viola o princípio de Ports & Adapters seria desenhado como se estivesse correto.
- Como a Spec A entrega valor sozinha (nova violação no relatório, independente do
  diagrama), o sequenciamento Spec A → Spec B não atrasa nada que não seria feito de
  qualquer forma — só ordena o trabalho.

**Alternativa descartada**: manter ilustrativa no MVP deste diagrama, adiando a
verificação real para uma spec futura sem compromisso de quando. Descartada porque o
custo de fazer a verificação de verdade (Spec A) é baixo o suficiente pra não valer a
pena entregar uma versão sabidamente incorreta primeiro.

## 3. Fora de escopo do MVP

- **Inferência de protocolo externo** (ex.: rotular um adapter como "fala HTTP" ou
  "fala SQL"). Exigiria nova heurística de análise de imports por adapter — não existe
  hoje. Fica como evolução futura, não nesta spec.
- **Melhorias visuais no grafo de dependências existente** (Mermaid/Graphviz — layout,
  cores por camada, legenda interativa). Escopo novo, não relacionado ao diagrama
  conceitual desta spec. Tratado como issue separada, fora do fluxo de specs.

## 4. Trabalho novo necessário

**Analyzer**: o classificador de papel por pacote e a categoria `Layer.CROSS_CUTTING`
são especificados em `ADENDO-SPEC-analyzer-classificador-papel.md`, não nesta spec —
é peça compartilhada com a Spec A, extraída pra um documento próprio depois que a
revisão cruzada entre as duas expôs uma dependência circular de documentação. Esta
spec só consome o resultado.

**Exporter**: dois geradores SVG novos (fora do Mermaid/Graphviz, que fazem layout
automático de grafo — não servem pra forma geométrica fixa):
- `build_concentric_diagram_svg(shape, layers)` — `shape` é `hexagon` (topo achatado)
  ou `circle`; `layers` é a lista ordenada de camadas com rótulo e contagem agregada.
  Usado por Hexagonal, Clean Architecture e DDD.
- `build_layered_diagram_svg(tiers, layers, infrastructure)` — faixas empilhadas
  agrupadas em tiers (template fixo), com a faixa lateral de Infraestrutura
  (`Layer.CROSS_CUTTING`).

Ambos seguem o padrão de dependência opcional já estabelecido no projeto (fallback
gracioso se o estilo detectado for `UNKNOWN`: mostra só o grafo atual, sem o diagrama
de forma).

## 5. Riscos

- Estilo `UNKNOWN` ou sinais mistos: diagrama de forma não se aplica — precisa de
  fallback claro (omitir a seção, não forçar uma forma errada).
- `N` do top-N pode não servir bem pra todo tamanho de projeto — validar com o próprio
  ArqSync analisando a si mesmo antes de fechar o número definitivo.

## 6. Nota de status (registrada durante a Fase 3)

A Fase 3 implementou o adendo do classificador de papel (apenas a heurística Hexagonal,
consumida pela Spec A) e a própria Spec A (`AdapterSemPortaViolation`), mas **não**
implementou esta spec — decisão explícita do usuário para faseamento do trabalho, não
um problema desta spec. Pré-requisitos ainda pendentes antes de implementar esta spec:
heurísticas de classificação para Clean Architecture e DDD, e a categoria
`Layer.CROSS_CUTTING` (ambas deixadas de fora do adendo do classificador por falta de
consumidor até aqui), além dos dois geradores SVG descritos na seção 4.
