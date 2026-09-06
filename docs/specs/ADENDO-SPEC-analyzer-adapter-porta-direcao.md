# Adendo — SPEC Técnica Analyzer: Direção do Adapter na Violação de Porta

> **Status:** Aprovado e implementado — decisão em aberto da seção 2.4 resolvida pela
> opção (a) (subdividir `CORE` em `INPUT_PORT`/`OUTPUT_PORT`)
> **Metodologia:** Spec-Driven Development (SDD)
> **Reabre:** `docs/specs/SPEC-adapter-port-violation.md` (Spec A, aprovada e já
> implementada — 140 testes)
> **Estende também:** `ADENDO-SPEC-analyzer-classificador-papel.md` (vocabulário do
> `PackageRoleClassifier` ganha `usecase`/`usecases` e a distinção
> driving/driven) e `SPEC-scanner.md` (`ClassScan` ganha `fieldTypes`, no mesmo
> espírito aditivo de `ADENDO-SPEC-scanner-supertypes.md`)
> **Motivado por:** dogfooding — ArqSync rodado contra o Boardly
> (`C:\Workspace\JAVA\Boardly`), um projeto Hexagonal real, não sintético

---

## 1. Contexto

### 1.1 O comportamento atual

`DefaultAdapterPortViolationDetector` (Spec A, 2.3) trata todo pacote classificado
`PackageRole.ADAPTER` da mesma forma: para cada classe do pacote, verifica se algum
`superTypes` (`implements`/`extends`) corresponde a uma interface declarada num pacote
`PackageRole.CORE`. Nenhum `superTypes` correspondente → `AdapterSemPortaViolation`.

Essa regra modela corretamente **um** dos dois papéis que "adapter" pode assumir na
Arquitetura Hexagonal — o **driven adapter** (adapter de saída, ex.: implementação de
repositório) — e nenhum dos dois é distinguido do outro hoje.

### 1.2 Evidência real: dogfooding contra o Boardly

Rodar o ArqSync contra o Boardly (backend Spring Boot, `com.boardly.*`) expõe o
problema com casos concretos, não hipotéticos:

| Classe | Pacote | Papel real | `superTypes` | Resultado hoje |
|---|---|---|---|---|
| `DevUserController` | `adapters.in.web.controller` | driving adapter que **fura a arquitetura** injetando direto um output port | (nenhum) | violação (correta, mas pelo motivo errado) |
| `AuthController` | `adapters.in.web.controller` | driving adapter correto (injeta `AuthService`) | (nenhum) | violação (**falso positivo**) |
| `ProjectController` | `adapters.in.web.controller` | driving adapter correto (injeta 7 use cases via construtor) | (nenhum) | violação (**falso positivo**) |
| `TaskController` | `adapters.in.web.controller` | driving adapter correto (injeta 2 use cases via construtor) | (nenhum) | violação (**falso positivo**) |
| `UserRepositoryAdapter` | `adapters.out.mongo.adapter` | driven adapter correto | `UserRepositoryPort` | sem violação (correto, hoje já funciona) |

Todo controller REST do Boardly é `@RequiredArgsConstructor` (Lombok) com um ou mais
campos `private final <UseCase>` — nenhum deles declara `implements` de propósito
alguma, porque **driving adapters não implementam a porta, o núcleo de aplicação
implementa** (`CreateProjectService implements CreateProjectUseCase`, em
`application.project`, não em `adapters.*`). O detector atual penaliza exatamente o
padrão correto.

O caso de `DevUserController` é instrutivo pelo motivo oposto: ele é uma violação real
— um driving adapter não deveria depender diretamente de `UserRepositoryPort` (um
output port), pulando a camada de aplicação — mas o detector atual não sabe disso; ele
acerta por acidente, porque `DevUserController` também não implementa nada. Trocar
"implementa" por "depende de", sem mais nada, faria `DevUserController` deixar de ser
detectado (ele *depende* de uma porta, só que da porta errada) — ver 2.4 e 4 para como
este adendo evita essa regressão.

### 1.3 Raiz do problema

Duas causas distintas, ambas necessárias para o falso positivo observado:

1. **A regra de violação é direction-blind.** "Implementar uma porta" é a obrigação
   estrutural do driven adapter; a obrigação estrutural do driving adapter é a oposta —
   **depender de** uma porta (via campo/parâmetro), não implementá-la. Aplicar a
   mesma checagem ("implementa?") aos dois papéis está estruturalmente errado para
   metade dos casos, não é um limiar mal calibrado.
2. **O vocabulário de `PackageRole.CORE` não reconhece `usecase`/`usecases`.** Mesmo
   corrigindo (1), a checagem de dependência do driving adapter não encontraria
   nenhuma porta pra combinar: as portas de entrada do Boardly (`CreateProjectUseCase`,
   `MoveTaskUseCase` etc.) vivem em `application.usecase`, um pacote que
   `DefaultPackageRoleClassifier` hoje resolve para `UNKNOWN` (só reconhece
   `port`/`ports`). As duas causas precisam ser corrigidas juntas — corrigir só (1)
   ainda deixaria todo controller do Boardly como falso positivo.

## 2. Decisão de Design

### 2.1 `PackageRole` ganha `DRIVING_ADAPTER` e `DRIVEN_ADAPTER`

**Decisão:** o enum `PackageRole` ganha duas variantes novas. `ADAPTER` é mantida
como categoria de fallback — "é um adapter, mas a direção não pôde ser determinada"
(ver 2.2) — não removida, porque `SPEC-diagram-concept.md` (consumidor futuro do
mesmo classificador) só precisa saber "isto é um adapter" pra desenhar o anel
correspondente, e não deveria quebrar por causa de uma distinção que só a regra de
violação (esta spec) precisa.

```java
public enum PackageRole {
    CORE,
    ADAPTER,          // adapter, direção não determinada — ver 2.2
    DRIVING_ADAPTER,   // adapter de entrada (ex.: controller REST)
    DRIVEN_ADAPTER,    // adapter de saída (ex.: implementação de repositório)
    UNKNOWN
}
```

**Justificativa:**
- A distinção driving/driven é conceitual, não um detalhe de implementação da regra
  de violação — faz sentido morar no classificador de papel (peça compartilhada,
  `ADENDO-SPEC-analyzer-classificador-papel.md`), não só dentro do detector.
- Reaproveita a mesma convenção já usada para `CROSS_CUTTING`
  (`ADENDO-SPEC-analyzer-classificador-papel.md`, 2.2): categoria nova quando a
  granularidade existente não é suficiente pra um consumidor real.

**Alternativas descartadas:**
- *Manter só `PackageRole.ADAPTER` e resolver a direção dentro do
  `DefaultAdapterPortViolationDetector`, olhando o nome do pacote de novo.*
  Rejeitado: duplicaria a leitura de vocabulário de pacote (`in`/`out`/`driving`/
  `driven`) que já é responsabilidade do classificador — o mesmo argumento que
  motivou extrair o classificador para um adendo próprio em primeiro lugar
  (`ADENDO-SPEC-analyzer-classificador-papel.md`, 2.1).
- *Substituir `ADAPTER` por só `DRIVING_ADAPTER`/`DRIVEN_ADAPTER`, sem fallback.*
  Rejeitado: quebraria a resolução de direção ambígua (2.2) — um projeto Hexagonal
  real pode ter um pacote `adapter`/`adapters` sem nenhum sinal de direção
  (`in`/`out`/`driving`/`driven`) na estrutura; forçar uma direção nesse caso seria
  inventar um fato que o pacote não declarou.

### 2.2 Detecção de direção: exige os dois sinais (papel + direção)

**Decisão:** `classifyHexagonal` deixa de parar no primeiro segmento reconhecido.
Ela percorre todos os segmentos do pacote coletando dois sinais independentes:

- **sinal de papel:** algum segmento é `port`/`ports` (→ candidato a `CORE`,
  ver 2.3) ou `adapter`/`adapters` (→ candidato a adapter);
- **sinal de direção** (só relevante se o sinal de papel foi `adapter`/`adapters`):
  algum segmento é `in`/`driving` (→ `DRIVING_ADAPTER`) ou `out`/`driven`
  (→ `DRIVEN_ADAPTER`).

Sinal de papel `adapter` **sem** sinal de direção → `PackageRole.ADAPTER` (2.1).
Sinal de direção sozinho, sem `adapter`/`adapters` em algum segmento → **não** basta;
resolve para `UNKNOWN`. `port`/`ports` continua resolvendo direto para `CORE`,
sem precisar de direção (portas não têm "direção" própria — quem tem é o adapter que
depende delas ou as implementa).

**Justificativa:**
- O caso real do Boardly exige isso: `adapters.in.web.controller` tem o segmento
  `adapter`(s) e o segmento `in` a três posições de distância um do outro, com dois
  segmentos (`web`, `controller`) entre eles. Um algoritmo "para no primeiro match"
  (o existente) nunca alcança `adapters` nesse caminho, porque teria que primeiro
  descartar `controller` e `web` — e mesmo que alcançasse, pararia aí sem checar
  `in`. `adapters.out.mongo.adapter` é pior ainda: o segmento mais à direita já é
  literalmente `adapter` (subpacote singular dentro de `mongo`), então o algoritmo
  atual para ali mesmo, sem nunca ver o `out` duas posições antes. Uma regra de
  adjacência (checar só o segmento vizinho de `adapter`) erraria os dois casos reais
  do Boardly — não é um caso extremo hipotético, é o projeto que motivou este adendo.
- Exigir os dois sinais (não só `in`/`out`/`driving`/`driven` isolado) evita que um
  pacote qualquer chamado `in` ou `out` por outro motivo (raro, mas `in`/`out` são
  palavras curtas e genéricas) seja classificado como adapter sem nenhum sinal de
  que o projeto pretende seguir a convenção de portas/adapters.

**Alternativas descartadas:**
- *Checar só o segmento imediatamente adjacente ao segmento `adapter`/`adapters`.*
  Rejeitado — ver justificativa acima: erra os dois exemplos reais do Boardly.
- *Reconhecer `in`/`out` isoladamente, sem exigir `adapter`/`adapters` em algum
  segmento.* Rejeitado: `in`/`out` sozinhos são sinal fraco demais (palavras muito
  genéricas) para carregar todo o peso da classificação; exigir os dois sinais
  reduz o risco de falso positivo em projetos que não seguem a convenção.

### 2.3 `PackageRole.CORE` passa a reconhecer `usecase`/`usecases`

**Decisão:** `fromSegment` ganha mais duas entradas resolvendo para `CORE`:
`usecase` e `usecases`, ao lado de `port`/`ports` já existentes.

```java
case "port", "ports", "usecase", "usecases" -> PackageRole.CORE;
```

**Justificativa:**
- "Porta" na Hexagonal cobre tanto porta de saída (output port — convenção mais
  comum: `port`/`ports`) quanto porta de entrada (input port / use case — convenção
  mais comum: `usecase`/`usecases`, como no Boardly:
  `application.usecase.CreateProjectUseCase`). Sem esse reconhecimento, a checagem
  de driving adapter (2.4) nunca teria nenhuma porta de entrada pra combinar, e a
  correção de direção (2.1–2.2) sozinha não resolveria o falso positivo — o
  controller continuaria sinalizado, só que por um motivo novo (nenhuma porta
  reconhecida) em vez do motivo antigo (não implementa nada).
- ArqSync deveria se adaptar à convenção de nomenclatura de um projeto Hexagonal
  real e existente, não o oposto — este é justamente o ponto do dogfooding: o
  Boardly não foi escrito para agradar o ArqSync.

**Alternativas descartadas:**
- *Não estender o vocabulário de `CORE`; documentar que projetos devem nomear
  portas de entrada como `port`/`ports` também.* Rejeitado: reescreveria a
  convenção de um projeto real e correto só para satisfazer uma limitação da
  ferramenta — o problema é do classificador, não do Boardly.

### 2.4 Regra de violação passa a ser direcional

**Decisão:** `DefaultAdapterPortViolationDetector.detect` passa a ramificar por
papel:

- **`DRIVEN_ADAPTER`:** regra inalterada (Spec A, 2.3) — algum `superTypes` deve
  corresponder a uma interface de um pacote `CORE`; senão, violação.
- **`DRIVING_ADAPTER`:** nova regra — a classe deve **depender** (via `fieldTypes`,
  ver 2.5) de pelo menos um tipo que corresponda a uma interface de um pacote
  `CORE`; senão, violação.
- **`ADAPTER`** (direção ambígua, 2.1–2.2): a regra não roda para essa classe —
  mesmo espírito de "não aplicável não é miss" já usado para `UNKNOWN`
  (`SPEC-analyzer.md`, 2.4; `SPEC-adapter-port-violation.md`, 2.1).

**Justificativa:**
- Resolve 1.3(1) diretamente: cada papel passa a ser checado pela obrigação que
  realmente tem, não pela obrigação do outro papel.
- Resolve o caso do `DevUserController` (1.2) sem reintroduzir o falso negativo
  temido: `DevUserController` **depende** de `UserRepositoryPort`, mas
  `UserRepositoryPort` é uma porta de **saída** (`application.ports`), não uma
  porta de **entrada** (`application.usecase`). A checagem de driving adapter não
  deveria aceitar qualquer porta — só uma porta de entrada. Ver a nota de
  refinamento abaixo.

**Nota de refinamento necessária:** a decisão acima, como redigida ("depende de
*algum* tipo que corresponda a uma interface `CORE`"), ainda erraria
`DevUserController` — `UserRepositoryPort` é `CORE` (via `port`/`ports`, 2.3), então
"depende de algo em `CORE`" seria satisfeito mesmo dependendo da porta errada. A
regra de driving adapter precisa distinguir *porta de entrada* de *porta de saída*
dentro de `CORE`, não só "é `CORE` ou não". Duas formas de resolver, ambas viáveis,
**a decidir na revisão deste adendo antes da implementação**:

- (a) sub-dividir `CORE` também (`INPUT_PORT`/`OUTPUT_PORT`) com o mesmo vocabulário
  `usecase`/`ports` que já os distingue por nome (2.3) — driving adapter exige
  `INPUT_PORT`, driven adapter exige `OUTPUT_PORT`; ou
- (b) tratar isso como uma violação **diferente**, fora do escopo desta spec (ex.:
  "driving adapter depende de porta de saída" — uma violação de camada, não de
  "adapter sem porta") e aceitar que `DevUserController` deixe de ser pego por
  `AdapterSemPortaViolation` especificamente, mas fique elegível a uma regra futura
  documentada aqui como fora de escopo (seção 6).

**Resolvido na revisão: opção (a).** `PackageRole.CORE` foi subdividido em
`PackageRole.INPUT_PORT` (convenção `usecase`/`usecases`) e
`PackageRole.OUTPUT_PORT` (convenção `port`/`ports`), com o mesmo vocabulário já
definido em 2.3. `DevUserController` agora é corretamente pego pela regra: ele
depende de `UserRepositoryPort`, mas esse pacote é `OUTPUT_PORT`, não
`INPUT_PORT` — não satisfaz a obrigação do driving adapter, que é depender de um
`INPUT_PORT`. Implementado em `DefaultAdapterPortViolationDetector` (seção 3) e
coberto por teste de regressão dedicado
(`drivingAdapterDependingOnlyOnAnOutputPortIsStillAViolation`,
`DefaultAdapterPortViolationDetectorTest`).

**Alternativas descartadas (para a bifurcação por papel em si, independente da nota
acima):**
- *Gerar um novo tipo de violação para driving adapter, mantendo
  `AdapterSemPortaViolation` só para driven.* Rejeitado por ora: mesmo espírito
  estrutural (um adapter, um papel esperado, papel não cumprido) — duas violações
  na mesma lista do relatório já é resolvido na camada de apresentação (Spec A,
  nota de implementação, seção 5) sem precisar de dois tipos Java; reavaliar se as
  mensagens/sugestões precisarem divergir tanto a ponto de um tipo único atrapalhar
  (ver 3, campo `role` no record).

### 2.5 Nova capacidade do Scanner: `fieldTypes`, não parâmetros de construtor

**Decisão:** `ClassScan` ganha `List<String> fieldTypes` — os nomes de tipo
(simples, não resolvidos) declarados nos **campos de instância** da classe, no
mesmo espírito aditivo de `superTypes`/`isInterface`
(`ADENDO-SPEC-scanner-supertypes.md`). Campos `static` são ignorados (constantes,
loggers — não são dependências injetadas). Parâmetros de construtor **não** são
capturados.

**Justificativa:**
- Todos os controllers do Boardly usam `@RequiredArgsConstructor` (Lombok) —
  `AuthController`, `DevUserController` — e Lombok gera o construtor via
  processamento de anotação, não como um nó real na AST que o `JavaParser` visita.
  Escanear parâmetros de construtor simplesmente não veria nenhum construtor
  nessas classes — um falso negativo silencioso, justamente no padrão mais comum
  de injeção de dependência em projetos Spring reais. Escanear campos funciona
  igual nos dois estilos (Lombok ou construtor explícito, como `ProjectController`
  e `TaskController`, que não usam Lombok) porque em ambos o resultado final é um
  campo `private final` — só o **caminho** até lá difere (gerado vs. escrito à
  mão), não o fato em si.
- Ignorar campos `static` evita ruído (um `private static final Logger LOG` nunca
  seria uma porta, mas não faz mal nenhum registrar isso explicitamente como
  decisão consciente em vez de um acidente de implementação).

**Alternativas descartadas:**
- *Escanear parâmetros de construtor (a modelagem "óbvia" para DI via
  construtor).* Rejeitado pelo motivo Lombok acima — erraria exatamente os casos
  reais que motivaram este adendo.
- *Escanear os dois (campos e parâmetros de construtor), unindo os dois
  conjuntos.* Rejeitado por ora: nenhum caso real observado precisa dessa união —
  todo campo injetado observado no Boardly (com ou sem Lombok) aparece como campo;
  adicionar parâmetros de construtor seria complexidade sem necessidade
  demonstrada (princípio Hashimoto). Reavaliar se aparecer um projeto real que
  injeta via parâmetro de método `@Autowired` sem armazenar em campo — padrão
  incomum, não observado até agora.

### 2.6 Casos de borda: pacotes de dado/tradução cortam a busca antes de `adapter`

Ver seção 4 (dedicada, por pedido explícito da revisão) para a decisão completa e os
exemplos reais do Boardly.

## 3. Modelo de Dados

```java
public enum PackageRole {
    CORE,
    ADAPTER,
    DRIVING_ADAPTER,
    DRIVEN_ADAPTER,
    UNKNOWN
}
```

```java
public record ClassScan(String name, String packageName, List<String> imports,
                         List<String> superTypes, boolean isInterface,
                         List<String> fieldTypes) {
    public ClassScan {
        imports = List.copyOf(imports);
        superTypes = List.copyOf(superTypes);
        fieldTypes = List.copyOf(fieldTypes);
    }
}
```

`AdapterSemPortaViolation` ganha um campo `PackageRole role` (`DRIVING_ADAPTER` ou
`DRIVEN_ADAPTER`) para que o relatório e o prompt de sugestão (Groq) possam
diferenciar a mensagem ("este adapter de entrada não depende de nenhuma porta" vs.
"este adapter de saída não implementa nenhuma porta") sem inspecionar de novo o
pacote:

```java
public record AdapterSemPortaViolation(PackageName adapterPackage, String className,
                                        PackageRole role) {
}
```

Esboço do detector (nível de spec, não implementação final — sujeito à decisão em
aberto da seção 2.4):

```java
switch (role) {
    case DRIVEN_ADAPTER -> {
        if (cls.superTypes().stream().noneMatch(corePortNames::contains)) {
            violations.add(new AdapterSemPortaViolation(pkg, cls.name(), DRIVEN_ADAPTER));
        }
    }
    case DRIVING_ADAPTER -> {
        if (cls.fieldTypes().stream().noneMatch(inputPortNames::contains)) {
            violations.add(new AdapterSemPortaViolation(pkg, cls.name(), DRIVING_ADAPTER));
        }
    }
    default -> { /* ADAPTER (direção ambígua) e UNKNOWN: regra não roda */ }
}
```

## 4. Casos de Borda: DTOs, mappers e exception handlers

**Decisão:** segmentos `dto`/`dtos`, `mapper`/`mappers`, `exception`/`exceptions` e
`document`/`documents` resolvem para `PackageRole.UNKNOWN` e **interrompem** a busca
de papel — mesmo que um segmento `adapter`/`adapters` exista mais à esquerda no
mesmo caminho de pacote. `fromSegment` ganha essas entradas com prioridade de
avaliação sobre `port`/`adapter`/direção (a busca right-to-left para nesses
segmentos antes de continuar procurando `adapter`).

Exemplos reais do Boardly, todos hoje classificados `ADAPTER` (e, portanto,
avaliados — incorretamente — pela regra atual):

| Classe | Pacote | Por que não deveria ser avaliada |
|---|---|---|
| `GlobalExceptionHandler` | `adapters.in.web.exception` | `@RestControllerAdvice`, não implementa nem depende de porta nenhuma — não participa do fluxo de entrada/saída, traduz exceção para resposta HTTP |
| `CreateProjectRequest` (e todo `dto.*`) | `adapters.in.web.dto.project` | `record` de dados puro — tem campos, mas nenhum é dependência injetada, é payload |
| `UserMongoMapper` | `adapters.out.mongo.mapper` | classe utilitária estática, zero campos de instância, traduz `User` ↔ `UserDocument` |
| `UserDocument` | `adapters.out.mongo.document` | modelo de persistência do MongoDB, campos são dados, não dependências |

**Justificativa:**
- Nenhuma dessas classes é um adapter no sentido *comportamental* que a regra
  desta spec verifica — elas não são o ponto de entrada nem o ponto de saída do
  núcleo de aplicação, são código de suporte (tradução de formato, modelagem de
  dado, tratamento cross-cutting de erro) que só *mora* dentro do pacote adapter
  por convenção de organização de código.
- Segue a mesma convenção já usada pelo classificador inteiro (vocabulário de
  segmento, right-to-left) — não introduz um mecanismo de exclusão novo, só
  estende o vocabulário existente com mais palavras-chave que têm o mesmo peso
  estrutural que `port`/`adapter`/`in`/`out` já têm.

**Alternativas descartadas:**
- *Excluir por sinal estrutural: classe com zero campos de instância não é
  avaliada.* Rejeitado como critério único — funciona para
  `GlobalExceptionHandler` e `UserMongoMapper` (ambos zero campos), mas não para
  `CreateProjectRequest` e `UserDocument`, que têm vários campos (são dados, não
  dependências) e seriam avaliados do mesmo jeito por esse critério.
- *Excluir por anotação (`@RestControllerAdvice` fica de fora, `@RestController`
  fica dentro; DTOs/documents não têm anotação de componente Spring nenhuma).*
  Mais preciso em tese, mas exigiria uma capacidade nova do Scanner (captura de
  anotações) que hoje não existe — o Scanner não lê anotações em lugar nenhum
  (`ClassScan` não tem esse campo). Complexidade maior por uma precisão marginal
  sobre a exclusão por palavra-chave de pacote, que já resolve os quatro casos
  reais observados. Mesmo critério de escopo já usado em
  `ADENDO-SPEC-analyzer-classificador-papel.md` (seção 5, "não construir sem
  necessidade demonstrada") — fica registrado aqui como refinamento futuro se a
  exclusão por palavra-chave se mostrar insuficiente em outro projeto real.

## 5. Impacto em consumidores existentes

- `DefaultPackageRoleClassifier`: `classifyHexagonal`/`fromSegment` reescritos
  (2.2–2.3, 4) — deixa de ser "para no primeiro match" para coletar sinais de
  papel e direção separadamente.
- `DefaultAdapterPortViolationDetector`: ramifica por `PackageRole` em vez de uma
  única checagem "implementa?" (2.4).
- `DefaultJavaParserAdapter`: precisa de uma extração adicional (`fieldTypes`) na
  mesma visita de AST já usada para `imports`/`superTypes` (2.5) — mesmo padrão de
  extensão aditiva do `ADENDO-SPEC-scanner-supertypes.md`.
- `AdapterSemPortaViolation`: ganha o campo `role` (3) — muda a assinatura do
  record; `DefaultGroqSuggestionService` (serialização JSON do resumo enviado ao
  Groq) e `ReportData`/`report.json`/`report.html` (Spec A, nota de implementação,
  seção 5) precisam incluir o novo campo.
- Testes existentes: os 140 testes da Spec A cobrem o comportamento antigo de
  `PackageRole.ADAPTER` tratado como um único papel — `DefaultPackageRoleClassifierTest`
  e `DefaultAdapterPortViolationDetectorTest` precisam de casos novos para
  `DRIVING_ADAPTER`/`DRIVEN_ADAPTER`/direção ambígua, e os casos existentes que
  assumem "todo pacote `adapter*` é `PackageRole.ADAPTER`" precisam ser revistos —
  alguns viram `DRIVING_ADAPTER`/`DRIVEN_ADAPTER` dependendo do fixture usado.

## 6. Fora de Escopo deste Adendo

- Exclusão por anotação (`@RestControllerAdvice`, `@Component`, etc.) em vez de
  palavra-chave de pacote (seção 4) — exigiria uma capacidade nova do Scanner,
  sem necessidade demonstrada pelos casos reais observados até agora.
- Aplicar a distinção driving/driven a Clean Architecture ou DDD — mesmo escopo
  já excluído por `SPEC-adapter-port-violation.md` (seção 4): esses estilos têm
  suas próprias variações (gateway/presenter, repository) que não mapeiam 1:1
  para driving/driven adapter.
- Verificação de porta de entrada órfã (use case sem nenhum controller
  dependendo dela) — mesma categoria já excluída em
  `SPEC-adapter-port-violation.md` (seção 4, "porta órfã").
- Escanear parâmetros de construtor além de campos (2.5, alternativa descartada)
  — sem caso real que demonstre necessidade.

## 7. Nota para a implementação (após aprovação)

Caso de teste de regressão obrigatório, baseado no padrão real do Boardly: um
driving adapter (`@RestController`-like, pacote `adapters.in.*` ou equivalente)
com uma porta de entrada injetada como campo via construtor (Lombok
`@RequiredArgsConstructor` **e** construtor explícito, os dois estilos — 2.5) **não
deve** gerar `AdapterSemPortaViolation`. Sem esse teste, uma regressão futura no
detector voltaria a marcar todo controller correto como violação, exatamente o bug
que motivou este adendo.

## 8. Nota de implementação

Implementado conforme as seções 2–5, com a decisão em aberto da seção 2.4
resolvida pela opção (a) (nota atualizada em 2.4). Sem desvios do plano.

- `PackageRole`, `ClassScan`, `AdapterSemPortaViolation`,
  `DefaultPackageRoleClassifier`, `DefaultJavaParserAdapter` e
  `DefaultAdapterPortViolationDetector` alterados conforme o modelo de dados da
  seção 3.
- `DefaultGroqSuggestionService` também passou a incluir `role` no resumo JSON
  enviado ao Groq (não só em `ReportData`/`report.json`, cobertos automaticamente
  pela serialização Jackson do record) — o próprio motivo de `role` existir no
  record (seção 3) é permitir que a mensagem/sugestão diferencie "não depende de
  porta" de "não implementa porta"; deixar de fora do prompt da IA teria anulado
  esse propósito pela metade.
- Teste de regressão da seção 7 implementado como
  `drivingAdapterDependingOnAnInputPortHasNoViolation` (padrão
  AuthController/ProjectController/TaskController) em
  `DefaultAdapterPortViolationDetectorTest`, ao lado de
  `drivingAdapterDependingOnlyOnAnOutputPortIsStillAViolation` — o teste simétrico
  baseado no `DevUserController`, confirmando que a resolução da nota de 2.4
  (opção a) não reintroduz o falso negativo que a nota descrevia.
- `DefaultJavaParserAdapterTest` ganhou
  `capturesInstanceFieldTypesEvenWithoutAnExplicitConstructor`, que escreve uma
  classe sem nenhum construtor (simulando o que o Scanner vê de uma classe
  `@RequiredArgsConstructor` do Lombok) — cobre diretamente a justificativa de
  2.5 no nível do Scanner, não só no nível do detector.
- Suíte completa: 163 testes, 0 falhas (era 140 antes deste adendo).
