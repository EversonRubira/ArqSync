# Spec Técnica — Analyzer (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-27
> **Depende de:** Spec do Scanner (ainda não commitada neste repositório — ver seção 8, Pendências)

---

## 1. Visão Geral

O Analyzer é o segundo componente do pipeline do ArqSync. Ele recebe um `ProjectScan` (produzido pelo Scanner) e produz uma `AnalysisResult` contendo:

1. Um grafo de dependências **entre pacotes**.
2. Os ciclos de dependência detectados nesse grafo, com o caminho completo de cada um.
3. As violações de camada detectadas, por convenção de nomenclatura (`controller`, `service`, `repository`, `domain`).
4. Métricas descritivas (contagens e dependências incoming/outgoing por pacote).

O Analyzer não lê arquivos, não conhece caminhos de disco e não conhece Mermaid/JSON — ele opera inteiramente sobre os modelos em memória entregues pelo Scanner e entrega um modelo em memória para o Exporter. É um componente puro (sem I/O), o que simplifica testes e mantém o pipeline desacoplado (Scanner → Analyzer → Persistence/Exporter).

---

## 2. Decisões de Design

Cada decisão segue o princípio Hashimoto: a decisão, a justificativa, e as alternativas descartadas.

### 2.1 Grafo por pacote, não por classe

**Decisão:** o `DependencyGraph` tem pacotes como nós e arestas pacote→pacote. É construído agregando os imports de cada classe, mas o nó atômico da análise é o pacote.

**Justificativa:**
- O PRD define o escopo do v1 em termos de pacotes ("análise de dependências cíclicas entre pacotes", seção 3) e a convenção de camadas é aplicada a nomes de pacote, não de classe.
- O critério de sucesso do PRD (seção 2) é visual e didático: o usuário precisa "reconhecer visualmente um ciclo... no diagrama Mermaid". Um grafo de classes para um projeto de 500 classes teria potencialmente milhares de arestas — ilegível como diagrama e contraproducente para o objetivo pedagógico.
- Pacotes já carregam o sinal necessário para a segunda análise (violação de camada), então usar a mesma granularity para as duas análises simplifica o modelo (`DependencyGraph` é insumo único para `CycleDetector` e `LayerViolationDetector`).

**Alternativas descartadas:**
- *Grafo por classe.* Rejeitado: fora do escopo declarado no PRD, gera um grafo denso demais para renderização didática, e exigiria uma etapa extra de agregação para chegar às métricas por pacote de qualquer forma — ou seja, a agregação por pacote é inevitável, só que feita depois ao invés de na origem.
- *Grafo por classe com opção de "recolher" para pacote na exportação.* Rejeitado por complexidade desnecessária no v1 — não há requisito de visualizar o grafo de classes em nenhum lugar do PRD; adicionar essa opção seria featuritis (risco explícito no PRD, seção 8).

### 2.2 Representação da aresta de dependência

**Decisão:** `DependencyEdge(from: PackageName, to: PackageName, classPairs: List<ClassDependency>)`, onde `ClassDependency(fromClass, toClass)` guarda uma amostra (limitada, ex.: até 5) dos pares de classe que originaram aquela aresta agregada.

**Justificativa:**
- O relatório precisa ser didático (PRD, seção 4): dizer apenas "`controller` depende de `repository`" é menos útil do que poder mostrar "`OrderController` importa `OrderRepository` diretamente". Guardar amostras de classes de origem dá ao Exporter material concreto para a explicação textual, sem precisar manter o grafo completo por classe.
- Um limite de amostras (ex.: 5) evita que pacotes com muitas classes acopladas gerem uma lista gigante — informação suficiente para exemplificar, sem virar ruído.
- Arestas duplicadas entre o mesmo par de pacotes são deduplicadas (aresta única, múltiplas amostras), o que mantém o grafo enxuto para detecção de ciclo e para o diagrama Mermaid.

**Alternativas descartadas:**
- *Aresta simples sem rastreabilidade (`from → to` apenas, sem amostras).* Rejeitado: perde a capacidade de gerar explicação didática concreta, que é o diferencial pedido no PRD frente a ferramentas como o IntelliJ (que mostra dependências mas sem contexto).
- *Guardar todos os pares de classe, sem limite.* Rejeitado: para pacotes muito acoplados (ex.: 50 classes de `service` todas importando o mesmo `repository`), isso é redundante — a primeira ou segunda amostra já ilustra o padrão.

### 2.3 Regras de violação de camada

**Decisão:** camadas têm uma ordem fixa de dependência permitida:

```
controller → service → repository → domain
```

Uma aresta pacote→pacote é avaliada assim:
1. Detecta-se a camada de origem e destino pelo nome do pacote (ver 2.4).
2. Se origem ou destino não tiverem camada reconhecida → aresta ignorada pela análise de camadas (mas continua no grafo para detecção de ciclo).
3. `domain` como **destino** é sempre permitido, de qualquer camada (`controller → domain`, `service → domain`, `repository → domain`) — domain é tratado como o núcleo de modelos/entidades compartilhado, que qualquer camada pode referenciar (alinhado com Clean/Hexagonal Architecture, onde o domínio não tem dependências de saída e é o centro que todos podem importar).
4. Qualquer aresta na **direção contrária** à ordem (`repository → service`, `repository → controller`, `service → controller`, `domain → controller/service/repository`) é uma violação do tipo `LAYER_INVERSION`.
5. Qualquer aresta que **pula um nível** na direção correta, exceto para `domain` (coberto pela regra 3) — isto é, `controller → repository` — é uma violação do tipo `LAYER_SKIP`. Esse é exatamente o exemplo citado no PRD.
6. `controller → service` e `service → repository` são permitidas (fluxo normal de chamada em arquitetura em camadas).

**Justificativa:**
- O PRD dá um exemplo concreto e único de violação: "controller não deve chamar repository diretamente" (seção 3, item 2). A regra de `LAYER_SKIP` cobre exatamente isso.
- A pergunta em aberto "service não deve chamar repository?" — a resposta é **não é violação**: é o fluxo esperado. Se o service não pudesse chamar o repository, a camada de service não teria razão de existir nessa convenção. Só o "pulo" (controller direto ao repository) e qualquer inversão são sinais de problema arquitetural.
- Tratar `domain` como destino sempre permitido reduz falsos positivos — que o próprio PRD identifica como o maior risco percebido pelo autor ("Falsos positivos minando a confiança no diagnóstico", seção 8). Em arquiteturas em camada/hexagonal, é comum e saudável que controller, service e repository referenciem entidades/VOs de `domain` diretamente; marcar isso como violação geraria ruído sem valor didático.
- Inversões (`repository → service`, etc.) são sempre um sinal arquitetural genuíno de problema — não há caso legítimo de uma camada mais interna depender de uma mais externa nessa convenção — por isso são sinalizadas sem exceção.

**Alternativas descartadas:**
- *Aplicar a mesma regra estrita de adjacência para `domain` (ou seja, `X → domain` só permitido se `X = repository`).* Rejeitado: geraria um volume alto de violações em qualquer projeto real onde controllers/services usam DTOs/entities do domínio (comum), indo contra o objetivo de manter a confiança do usuário no relatório.
- *Permitir qualquer pulo na direção correta (ex.: `controller → domain`, `controller → repository` tudo permitido, só flagar inversões.* Rejeitado: ignoraria o exemplo explícito do PRD (controller → repository é citado como a violação a detectar).
- *Tornar as regras de camada configuráveis (arquivo de config).* Explicitamente fora de escopo do v1 pelo PRD (seção 3, "Fora do v1") — convenção fixa por enquanto.

### 2.4 Detecção da camada de um pacote

**Decisão:** a camada de um pacote é determinada pelo **último segmento** do nome do pacote (separado por `.`) que corresponda (case-insensitive) a um dos quatro nomes de camada (`controller`, `service`, `repository`, `domain`). Se nenhum segmento corresponder, a camada é `UNKNOWN`.

**Justificativa:**
- Usar o último segmento correspondente (o mais próximo da folha) é o que melhor reflete a intenção de pacotes como `com.acme.orders.web.controller` (camada = `controller`, não `web`) ou pacotes aninhados incomuns.
- `UNKNOWN` é um valor de primeira classe, não um erro — o PRD já assume que a convenção pode não ser seguida ("a detecção de violações pode falhar silenciosamente... mas a detecção de ciclos continua funcionando", seção 7, Premissas). Pacotes `UNKNOWN` continuam participando do grafo e da detecção de ciclos; só ficam de fora da análise de camadas.

**Alternativas descartadas:**
- *Exigir que o pacote inteiro (não apenas um segmento) seja um dos quatro nomes.* Rejeitado: não reflete como projetos Java reais nomeiam pacotes (`com.empresa.projeto.modulo.controller`).
- *Falhar ou logar erro quando a camada não é reconhecida.* Rejeitado: contraria a premissa explícita do PRD de degradação graciosa — a ausência de convenção não deve impedir a análise de ciclos.

### 2.5 Algoritmo de detecção de ciclos

**Decisão:** usar Tarjan (componentes fortemente conexos) para identificar quais pacotes participam de algum ciclo, e então, dentro de cada componente com mais de um nó (ou com self-loop), rodar uma busca em profundidade para extrair caminhos de ciclo concretos, com um limite superior de ciclos reportados por componente (proposto: 10) para evitar explosão combinatória em componentes muito densos.

**Justificativa:**
- O PRD pede o caminho completo de cada ciclo ("para cada ciclo, registrar o caminho completo"), não apenas a existência de um ciclo — por isso não basta rodar só Tarjan (que identifica agrupamento, não o caminho).
- Enumerar *todos* os ciclos elementares de um grafo (algoritmo de Johnson) é overkill para o alvo de escala do v1 (50–500 classes, portanto tipicamente dezenas de pacotes) e tem custo combinatório que não se justifica — Tarjan é O(V+E) para achar os componentes, e a extração de caminho dentro de um componente pequeno é barata.
- O limite por componente é uma salvaguarda, não uma característica central: para os tamanhos de projeto alvo do v1, é extremamente improvável que um único componente tenha dezenas de ciclos distintos: pacotes normalmente formam ciclos pequenos (2–4 nós).

**Alternativas descartadas:**
- *Algoritmo de Johnson (enumeração completa de ciclos elementares).* Rejeitado por complexidade de implementação desproporcional ao valor entregue no v1, dado o tamanho alvo do projeto.
- *Reportar apenas os componentes fortemente conexos, sem caminho explícito.* Rejeitado: não atende ao requisito do PRD de mostrar o caminho do ciclo no relatório/diagrama.

### 2.6 Resolução de imports para pacotes internos

**Decisão:** para cada `ClassScan`, cada import é resolvido removendo o último segmento (nome da classe) para obter o pacote candidato. O import só vira uma aresta se o pacote candidato estiver entre os pacotes do próprio `ProjectScan` (ou seja, é um pacote interno ao projeto escaneado). Imports para pacotes fora do projeto (JDK, bibliotecas de terceiros) são ignorados na construção do grafo. Self-imports (pacote candidato == pacote da própria classe) também são ignorados.

**Justificativa:**
- O objetivo é o grafo de arquitetura *interna* do projeto — incluir `java.util.List` ou `org.springframework.*` como nós poluiria o grafo sem nenhum valor de diagnóstico arquitetural.
- Usar o conjunto de pacotes já presente no `ProjectScan` como "lista de pacotes internos" evita que o Analyzer precise de qualquer configuração externa (classpath, etc.) — decisão consistente com a premissa do PRD de não depender de build tool.

**Alternativas descartadas:**
- *Whitelist/blacklist de prefixos de pacote (ex.: tudo que começa com `java.`, `org.` é externo).* Rejeitado: frágil e incorreto — o pacote raiz do projeto pode coincidir com prefixos comuns, e a abordagem "é um pacote que o Scanner encontrou" é mais simples e sempre correta por construção.

---

## 3. Modelos de Dados

### 3.1 Entrada (assumida do Scanner — ver Pendências, seção 8)

```java
record ProjectScan(
    String rootPath,
    List<PackageScan> packages,
    List<ScanError> errors
) {}

record PackageScan(
    String name,              // nome totalmente qualificado, ex: "com.acme.orders.controller"
    List<ClassScan> classes
) {}

record ClassScan(
    String name,               // nome simples da classe, ex: "OrderController"
    String packageName,        // pacote a que pertence
    List<String> imports       // imports totalmente qualificados
) {}

record ScanError(String filePath, String message) {}
```

### 3.2 Saída do Analyzer

```java
record PackageName(String value) {}

enum Layer { CONTROLLER, SERVICE, REPOSITORY, DOMAIN, UNKNOWN }

record ClassDependency(String fromClass, String toClass) {}

record DependencyEdge(
    PackageName from,
    PackageName to,
    int occurrences,                    // total de imports agregados nessa aresta
    List<ClassDependency> classSamples  // amostra limitada (ex.: até 5)
) {}

record DependencyGraph(
    Set<PackageName> nodes,
    List<DependencyEdge> edges
) {
    List<DependencyEdge> outgoingFrom(PackageName pkg);
    List<DependencyEdge> incomingTo(PackageName pkg);
}

record Cycle(List<PackageName> path) {
    // path é a sequência ordenada do ciclo, com o primeiro nó repetido no fim
    // ex.: [A, B, C, A]
}

enum ViolationType { LAYER_SKIP, LAYER_INVERSION }

record LayerViolation(
    PackageName from,
    PackageName to,
    Layer fromLayer,
    Layer toLayer,
    ViolationType type,
    List<ClassDependency> classSamples,
    String explanation   // texto didático, ex.: "OrderController depende diretamente de
                          // OrderRepository, pulando a camada de service."
) {}

record PackageDependencyCount(PackageName pkg, int incoming, int outgoing) {}

record AnalysisMetrics(
    int totalPackages,
    int totalClasses,
    int cycleCount,
    int violationCount,
    List<PackageDependencyCount> dependencyCounts
) {}

record AnalysisResult(
    DependencyGraph dependencyGraph,
    List<Cycle> cycles,
    List<LayerViolation> violations,
    AnalysisMetrics metrics
) {}
```

---

## 4. Interface Pública

```java
public interface DependencyAnalyzer {
    AnalysisResult analyze(ProjectScan projectScan);
}

public interface DependencyGraphBuilder {
    DependencyGraph build(ProjectScan projectScan);
}

public interface CycleDetector {
    List<Cycle> detect(DependencyGraph graph);
}

public interface LayerViolationDetector {
    List<LayerViolation> detect(DependencyGraph graph);
}

public interface MetricsCalculator {
    AnalysisMetrics calculate(
        ProjectScan projectScan,
        DependencyGraph graph,
        List<Cycle> cycles,
        List<LayerViolation> violations
    );
}
```

A implementação padrão de `DependencyAnalyzer` (`DefaultDependencyAnalyzer`) orquestra as quatro colaboradoras acima via composição (injeção de dependência simples, sem framework — consistente com o Analyzer sendo uma biblioteca pura sem I/O). Cada colaboradora é testável isoladamente.

---

## 5. Fluxo de Execução

1. `DependencyAnalyzer.analyze(projectScan)` é chamado com a saída do Scanner.
2. `DependencyGraphBuilder.build(projectScan)`:
   a. Monta o conjunto de nomes de pacote internos a partir de `projectScan.packages()`.
   b. Para cada `PackageScan`, para cada `ClassScan`, para cada import: resolve o pacote candidato (remove último segmento); ignora se for externo ou self-import.
   c. Agrupa por par `(from, to)`, agregando `occurrences` e limitando `classSamples`.
   d. Retorna o `DependencyGraph` (nós = todos os pacotes do `ProjectScan`; arestas = agregadas acima).
3. `CycleDetector.detect(graph)`:
   a. Roda Tarjan para achar componentes fortemente conexos.
   b. Para cada componente com >1 nó (ou self-loop), extrai caminho(s) de ciclo via DFS, respeitando o limite por componente.
   c. Retorna a lista de `Cycle`.
4. `LayerViolationDetector.detect(graph)`:
   a. Para cada aresta, resolve `fromLayer`/`toLayer` (2.4).
   b. Aplica as regras da seção 2.3; gera `LayerViolation` com texto explicativo quando aplicável.
5. `MetricsCalculator.calculate(...)`:
   a. Conta pacotes e classes a partir do `ProjectScan`.
   b. `cycleCount`/`violationCount` a partir das listas already computadas.
   c. Para cada pacote, calcula incoming/outgoing a partir do grafo.
6. `DefaultDependencyAnalyzer` monta e retorna o `AnalysisResult` final.

O fluxo é inteiramente síncrono e sem efeitos colaterais — dado o mesmo `ProjectScan`, o `AnalysisResult` é determinístico (importante para reprodutibilidade e para os testes).

---

## 6. Estratégia de Testes

- **Framework:** JUnit 5 + AssertJ, seguindo o padrão já estabelecido no Scanner.
- **Sem I/O:** todos os testes do Analyzer operam sobre `ProjectScan` construídos em memória via fixtures — não dependem de arquivos `.java` reais nem do Scanner de fato (desacoplamento de camada).
- **Fixtures:** um `ProjectScanFixtures` (test-scoped, builder-style) para montar cenários com poucas linhas, ex.: `ProjectScanFixtures.withCycle("a.controller", "a.service", "a.repository")`.

### Casos por componente

**`DependencyGraphBuilderTest`**
- Import interno gera aresta pacote→pacote.
- Import externo (JDK/terceiros, fora dos pacotes do `ProjectScan`) é ignorado.
- Self-import (mesmo pacote) é ignorado.
- Múltiplos imports entre o mesmo par de pacotes agregam em uma única aresta, com `occurrences` correto e amostras limitadas.
- Projeto sem nenhum import interno gera grafo sem arestas (nós isolados).

**`CycleDetectorTest`**
- Sem ciclo → lista vazia.
- Ciclo simples de 2 nós (`A → B → A`).
- Ciclo de 3+ nós (`A → B → C → A`).
- Self-loop (`A → A`) — caso degenerado, deve ser tratado como ciclo de tamanho 1.
- Dependência em diamante sem ciclo (`A → B`, `A → C`, `B → D`, `C → D`) — não deve ser reportado como ciclo (evitar falso positivo).
- Dois componentes cíclicos independentes no mesmo grafo — ambos detectados.

**`LayerViolationDetectorTest`**
- `controller → service` — sem violação.
- `service → repository` — sem violação.
- `controller → repository` — `LAYER_SKIP`.
- `repository → service` — `LAYER_INVERSION`.
- `repository → controller` — `LAYER_INVERSION`.
- `domain → service` — `LAYER_INVERSION`.
- `controller → domain`, `service → domain`, `repository → domain` — sem violação (domain como alvo sempre permitido).
- Pacote com camada `UNKNOWN` em qualquer ponta — sem violação reportada (mas presente no grafo).

**`MetricsCalculatorTest`**
- Contagens de pacotes/classes batem com o `ProjectScan` de entrada.
- `cycleCount`/`violationCount` refletem exatamente o tamanho das listas fornecidas.
- Incoming/outgoing por pacote calculados corretamente em um grafo com múltiplas arestas, incluindo pacotes sem nenhuma dependência (0/0).

**`DefaultDependencyAnalyzerTest` (integração dos componentes)**
- Cenário composto: projeto com um ciclo, uma violação de camada, e pacotes bem formados sem problema — `AnalysisResult` final reflete todas as partes corretamente montadas.
- Projeto vazio (`ProjectScan` sem pacotes) — `AnalysisResult` com listas vazias e métricas zeradas, sem exceção.

---

## 7. Fora de Escopo (v1)

Consistente com o PRD (seção 3):
- Métricas de qualidade estrutural (coesão/LCOM, instabilidade de Robert Martin, distância da sequência principal).
- Configuração customizável de nomes de camada — convenção fixa (`controller`, `service`, `repository`, `domain`).
- Qualquer análise comparativa entre execuções (histórico/evolução) — o Analyzer produz uma "foto" única por execução.

---

## 8. Pendências

- ~~**Contrato de entrada não confirmado**~~ — **Resolvido:** a Spec do Scanner (`docs/specs/SPEC-scanner.md`) confirma exatamente os modelos `ProjectScan`/`PackageScan`/`ClassScan`/`ScanError` assumidos aqui.
- **Imports estáticos e wildcard:** confirmado pela Spec do Scanner (seção 2.5) — `imports` contém o texto bruto do import, com `import com.acme.x.*` preservando o sufixo `.*` e `import static com.acme.x.Y.method` preservando o prefixo `static `. A resolução (remover último segmento para achar o pacote candidato) permanece responsabilidade do Analyzer, como descrito em 2.6.
- **Limite de ciclos reportados por componente (proposto: 10):** valor não validado empiricamente; ajustar com base em projetos reais de teste.
- **Regra de `domain` como alvo sempre permitido:** decisão tomada para reduzir falsos positivos (risco prioritário do PRD), mas vale validar com o autor se reflete a intenção real de "domain" nesses projetos, ou se em algum caso `domain` deveria ser mais restrito.
- **Criação de `STATUS.md`:** o PRD (seção 8 e 9) trata `STATUS.md` como ação imediata após o PRD e pré-requisito de visibilidade de progresso — ainda não existe neste repositório. Recomendo criá-lo (fora do escopo desta Spec) antes de iniciar a implementação do Analyzer.
