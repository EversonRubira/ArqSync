# Spec Técnica — Persistence (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-27

---

## 1. Visão Geral

O Persistence é o terceiro componente do pipeline do ArqSync. Ele recebe o `ProjectScan` (Scanner) e o `AnalysisResult` (Analyzer) de uma execução e salva um retrato desse resultado no PostgreSQL, via JPA/Hibernate, com schema gerenciado por Flyway.

O Persistence é **opcional em runtime**: se o banco não estiver disponível ou qualquer erro ocorrer durante o salvamento, o componente absorve a falha internamente — loga e segue em frente — e nunca impede a geração dos relatórios (`report.json`/`report.html`). Essa é a mesma filosofia de resiliência já aplicada no Scanner (um arquivo com erro de sintaxe não interrompe o scan do projeto): aqui, um erro de persistência não interrompe o pipeline como um todo.

O schema do v1 não tem interface de consulta — ele existe para não bloquear evolução futura (histórico entre execuções), mas essa evolução está fora do escopo desta Spec (ver seção 7).

---

## 2. Decisões de Design

Cada decisão segue o princípio Hashimoto: a decisão, a justificativa, e as alternativas descartadas.

### 2.1 Persistência opcional, fire-and-forget

**Decisão:** `PersistenceService.save(...)` nunca lança exceção para o chamador. Se o salvamento falhar por qualquer motivo (banco indisponível, violação de constraint, timeout de conexão), o erro é capturado, logado, e o método retorna normalmente (`void`).

**Justificativa:**
- Requisito explícito do PRD: "resiliência sem banco — se o banco não estiver disponível, o scan ainda roda e gera os relatórios normalmente" (P0, item 8). A persistência não pode ser um ponto único de falha para a entrega principal do v1 (o relatório).

**Alternativas descartadas:**
- *Propagar a exceção para o CLI e abortar a execução.* Rejeitado: contraria diretamente o requisito de resiliência do PRD.
- *Modo estrito configurável (falhar se o banco estiver indisponível).* Rejeitado: adiciona superfície de configuração sem necessidade demonstrada no v1 — consistente com a filosofia de convenções fixas já adotada nas Specs do Scanner e do Analyzer (sem arquivo de config no v1).

### 2.2 Separação entre orquestração transacional e captura de erro

**Decisão:** o método público `save(...)` **não** é anotado `@Transactional`. Ele delega para um método interno anotado `@Transactional` que executa todo o trabalho de mapeamento e gravação; o `try/catch` fica no método público, fora da fronteira transacional.

**Justificativa:**
- Se o `catch` estivesse dentro do método `@Transactional`, o Spring não veria a exceção propagar e não acionaria o rollback automático (rollback declarativo do Spring depende da exceção escapar do método transacional). Separar as duas responsabilidades — "uma unidade de trabalho atômica" (método interno) e "nunca deixar o chamador ver uma falha" (método público) — garante que o rollback aconteça corretamente e, ao mesmo tempo, que o fire-and-forget (2.1) seja respeitado.

**Alternativas descartadas:**
- *Capturar a exceção dentro do próprio método `@Transactional`.* Rejeitado: quebraria o rollback automático do Spring, deixando a transação em estado indefinido em vez de desfeita corretamente.

### 2.3 Estratégia de ID: sequência, não UUID nem chave natural

**Decisão:** todas as entidades usam `Long id` gerado via `GenerationType.SEQUENCE` (uma sequência PostgreSQL por tabela, com `allocationSize` configurado para permitir batching do Hibernate).

**Justificativa:**
- É uma ferramenta local, single-user, sem necessidade de geração de ID distribuída ou offline — UUID adicionaria overhead (16 bytes vs. 8, pior localidade de índice B-tree) sem nenhum benefício real no v1.
- `SEQUENCE` (em vez de `IDENTITY`) permite que o Hibernate agrupe (`batch`) múltiplos `INSERT` em uma única viagem ao banco, porque o ID pode ser obtido da sequência antes do insert. Isso importa porque uma única `Analysis` pode gerar dezenas de linhas de `PackageMetric`/`Cycle` — com `IDENTITY`, o Hibernate desativa o batching e faz um round-trip por linha, o que adicionaria latência real ao caminho fire-and-forget que já queremos manter rápido e discreto.
- Chave natural (ex.: usar `path` como PK de `Project`) foi descartada em favor de uma chave técnica — mantém as foreign keys das demais tabelas como inteiros simples, em vez de strings potencialmente longas.

**Alternativas descartadas:**
- *UUID.* Rejeitado: sem caso de uso real no v1 (sem múltiplas instâncias gravando concorrentemente em bancos distintos que depois precisam ser mesclados).
- *`GenerationType.IDENTITY`.* Rejeitado: desativa o batching de insert do Hibernate, o que penaliza justamente o cenário mais comum (uma `Analysis` com várias `PackageMetric`/`Cycle`).
- *Chave natural (`path` como PK de `Project`).* Rejeitado: strings como chave primária/estrangeira são mais custosas para índices e joins do que um `Long`.

### 2.4 Transação por scan

**Decisão:** cada chamada a `save(projectScan, analysisResult)` executa em uma única transação, que grava `Project` (se novo), `Analysis`, todos os `PackageMetric` e todos os `Cycle` daquela execução. Não há transação que abranja múltiplos scans, e não há commit parcial dentro de um mesmo scan.

**Justificativa:**
- A unidade de trabalho natural é "o resultado de uma execução do ArqSync" — ou tudo é salvo (um retrato consistente daquele scan), ou nada é (e o erro é absorvido conforme 2.1). Isso evita o estado inconsistente de, por exemplo, uma `Analysis` salva sem seus `PackageMetric` correspondentes.
- Não há caso de uso no v1 para múltiplos scans em uma única execução da CLI (a CLI processa um projeto por vez) — então não há razão para uma transação mais ampla.

**Alternativas descartadas:**
- *Commit individual por entidade (`Project`, depois `Analysis`, depois cada `PackageMetric`/`Cycle` separadamente).* Rejeitado: permite estado parcial visível no banco se uma falha ocorrer no meio do processo (ex.: `Analysis` salva, mas os `PackageMetric` não) — contrário à garantia de "tudo ou nada" que se espera de um retrato de scan.

### 2.5 Resiliência: rollback automático em erro no meio do salvamento

**Decisão:** qualquer exceção lançada durante o método transacional interno (violação de constraint, erro de mapeamento, perda de conexão no meio da operação) propaga para fora dele, acionando o rollback automático do Spring; o método público `save(...)` então captura essa exceção, loga com contexto (`path` do projeto, causa), e retorna normalmente.

**Justificativa:**
- Consequência direta de 2.2 e 2.4: rollback automático via propagação de exceção é o mecanismo padrão e correto do Spring, sem necessidade de controle manual de transação (`TransactionTemplate` ou gerenciamento manual do `EntityManager`).

**Alternativas descartadas:**
- *Rollback manual via `TransactionTemplate` ou `EntityManager` gerenciado à mão.* Rejeitado: mais código e mais superfície de erro do que o suporte declarativo padrão do Spring (`@Transactional`), sem benefício adicional no v1.

### 2.6 Sem sincronização/merge — histórico puramente aditivo

**Decisão:** cada scan gera uma nova `Analysis`, novos `PackageMetric` e novos `Cycle`. Não há `UPDATE` de registros existentes — mesmo que o mesmo projeto (mesmo `path`) seja escaneado várias vezes, cada execução é uma linha nova em `analyses` (e suas tabelas filhas), nunca uma sobrescrita.

**Justificativa:**
- O PRD é explícito: a persistência "prepara terreno para histórico futuro" (P1, item 9) — um modelo append-only é o único que preserva a possibilidade de, no futuro, comparar execuções ao longo do tempo. Fazer upsert destruiria essa capacidade antes mesmo dela existir.

**Alternativas descartadas:**
- *Upsert de `PackageMetric`/`Cycle` por `(project, packageName)` ou similar, mantendo só o estado mais recente.* Rejeitado: contraria diretamente o propósito de longo prazo do schema (histórico), que o PRD já definiu como razão de a persistência existir desde o v1.

### 2.7 `Project` identificado pelo caminho (`path`)

**Decisão:** `path` tem uma constraint de unicidade em `projects`. Ao salvar, o Persistence busca um `Project` existente por `path` (`findByPath`); se existir, reaproveita esse `Project` e cria apenas uma nova `Analysis` vinculada a ele; se não existir, cria um novo `Project`. Não há atualização de `name`/`repositoryUrl` de um `Project` já existente no v1 — se o caminho de um projeto mudar, isso é tratado como um projeto novo (linha nova em `projects`).

**Justificativa:**
- O v1 é uma ferramenta de linha de comando single-user sem conceito de autenticação/multiusuário — o caminho local já é o identificador natural e suficiente de "qual projeto é esse" (a CLI só recebe um argumento: o caminho, conforme PRD seção 5, item 1). Pedir ao usuário para nomear/identificar o projeto explicitamente adicionaria uma etapa sem necessidade demonstrada.
- Não atualizar `name`/`repositoryUrl` de um `Project` existente é uma simplificação deliberada: complica menos a Spec e o schema do v1, às custas de um caso de uso raro (mover um projeto de pasta) — documentado explicitamente como pendência (seção 8).

**Alternativas descartadas:**
- *Pedir ao usuário um identificador de projeto explícito via CLI (flag).* Rejeitado: fora do escopo do v1 — a CLI tem um único ponto de entrada (`java -jar arqsync.jar /caminho/do/projeto`), sem flags adicionais definidas até aqui.
- *Atualizar `name`/`repositoryUrl` do `Project` a cada novo scan.* Rejeitado por ora: adiciona lógica de "merge" de metadados de projeto que não tem requisito claro no PRD; registrado como pendência para revisitar se necessário.

### 2.8 Schema 100% gerenciado por Flyway — Hibernate nunca gera DDL

**Decisão:** Hibernate é configurado com `ddl-auto=validate` (nunca `update` ou `create`). O schema real do banco é definido exclusivamente pelas migrations Flyway; o Hibernate apenas valida, na inicialização, que as entidades mapeadas correspondem ao schema existente.

**Justificativa:**
- Deixar o Hibernate gerar/alterar DDL automaticamente pode divergir silenciosamente do schema versionado pelo Flyway entre ambientes (dev local com H2, CI, produção com PostgreSQL) — o requisito do PRD de "schema gerenciado por Flyway" só é cumprido de fato se o Flyway for a única fonte de verdade, não uma entre duas.

**Alternativas descartadas:**
- *`ddl-auto=update`.* Rejeitado: pode mascarar uma migration Flyway ausente ou incorreta, gerando schema diferente entre ambientes — exatamente o tipo de inconsistência silenciosa que a persistência opcional/resiliente (2.1) não deveria esconder ainda mais.

### 2.9 Spring Data JPA para os repositórios

**Decisão:** `ProjectRepository`, `AnalysisRepository`, `PackageMetricRepository` e `CycleRepository` são interfaces Spring Data JPA (`extends JpaRepository<T, Long>`), sem implementação manual de DAO.

**Justificativa:**
- Reduz boilerplate para as operações CRUD simples que o v1 precisa (`save`, `findByPath`); é o padrão idiomático em um projeto Java/Spring, consistente com a stack já definida no PRD (Spring, PostgreSQL, H2 para testes).

**Alternativas descartadas:**
- *`EntityManager` gerenciado manualmente.* Rejeitado: mais código para o mesmo resultado, sem necessidade de queries complexas no v1 que justifiquem abrir mão do Spring Data JPA.

---

## 3. Modelos de Dados

### 3.1 Entidades JPA

```java
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "project_seq")
    @SequenceGenerator(name = "project_seq", sequenceName = "projects_id_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true)
    private String path;               // caminho absoluto — identidade do projeto (2.7)

    @Column(nullable = false)
    private String name;               // derivado do último segmento do path

    @Column
    private String repositoryUrl;      // opcional

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Analysis> analyses = new ArrayList<>();
}

@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_seq")
    @SequenceGenerator(name = "analysis_seq", sequenceName = "analyses_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    @Column(nullable = false)
    private int totalPackages;

    @Column(nullable = false)
    private int totalClasses;

    @Column(nullable = false)
    private int totalDependencies;

    @Column(nullable = false)
    private int cyclicDependencies;    // número de ciclos detectados

    @Column(nullable = false)
    private int violationCount;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PackageMetric> packageMetrics = new ArrayList<>();

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Cycle> cycles = new ArrayList<>();
}

@Entity
@Table(name = "package_metrics")
public class PackageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "package_metric_seq")
    @SequenceGenerator(name = "package_metric_seq", sequenceName = "package_metrics_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(nullable = false)
    private String packageName;

    @Column(nullable = false)
    private int classCount;

    @Column(nullable = false)
    private int outgoingDependencies;

    @Column(nullable = false)
    private int incomingDependencies;
}

@Entity
@Table(name = "cycles")
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cycle_seq")
    @SequenceGenerator(name = "cycle_seq", sequenceName = "cycles_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @Column(nullable = false, columnDefinition = "text")
    private String cyclePath;          // ex: "com.acme.A -> com.acme.B -> com.acme.A"

    @Column(nullable = false)
    private int length;                // número de pacotes distintos no ciclo
}
```

Getters/setters/construtores omitidos por brevidade (JPA exige construtor sem argumentos; encapsulamento padrão de entidade).

### 3.2 Migration Flyway — `V1__init.sql`

```sql
CREATE TABLE projects (
    id              BIGINT PRIMARY KEY,
    path            VARCHAR(1024) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    repository_url  VARCHAR(1024),
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_projects_path UNIQUE (path)
);

CREATE SEQUENCE projects_id_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE analyses (
    id                   BIGINT PRIMARY KEY,
    project_id           BIGINT NOT NULL REFERENCES projects (id),
    analyzed_at          TIMESTAMP NOT NULL,
    total_packages       INTEGER NOT NULL,
    total_classes        INTEGER NOT NULL,
    total_dependencies   INTEGER NOT NULL,
    cyclic_dependencies  INTEGER NOT NULL,
    violation_count      INTEGER NOT NULL
);

CREATE SEQUENCE analyses_id_seq START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_analyses_project_id ON analyses (project_id);

CREATE TABLE package_metrics (
    id                     BIGINT PRIMARY KEY,
    analysis_id            BIGINT NOT NULL REFERENCES analyses (id),
    package_name           VARCHAR(512) NOT NULL,
    class_count            INTEGER NOT NULL,
    outgoing_dependencies  INTEGER NOT NULL,
    incoming_dependencies  INTEGER NOT NULL
);

CREATE SEQUENCE package_metrics_id_seq START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_package_metrics_analysis_id ON package_metrics (analysis_id);

CREATE TABLE cycles (
    id            BIGINT PRIMARY KEY,
    analysis_id   BIGINT NOT NULL REFERENCES analyses (id),
    cycle_path    TEXT NOT NULL,
    length        INTEGER NOT NULL
);

CREATE SEQUENCE cycles_id_seq START WITH 1 INCREMENT BY 50;
CREATE INDEX idx_cycles_analysis_id ON cycles (analysis_id);
```

`allocationSize = 50` nas entidades casa com `INCREMENT BY 50` nas sequências — permite ao Hibernate reservar um lote de IDs em memória e fazer batching de insert (2.3), sem gerar buracos inesperados por configuração desalinhada entre entidade e schema.

---

## 4. Interface Pública

```java
public interface PersistenceService {
    void save(ProjectScan projectScan, AnalysisResult analysisResult);
}

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByPath(String path);
}

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {}

public interface PackageMetricRepository extends JpaRepository<PackageMetric, Long> {}

public interface CycleRepository extends JpaRepository<Cycle, Long> {}
```

A implementação padrão (`DefaultPersistenceService`) usa um colaborador interno de mapeamento (`AnalysisResultMapper`, não anotado com Spring/JPA) que converte `ProjectScan` + `AnalysisResult` em instâncias de `Project`/`Analysis`/`PackageMetric`/`Cycle` — mantido como uma função pura, sem I/O, para ser testável isoladamente sem precisar de banco (ver seção 6).

---

## 5. Fluxo de Execução

1. Após o Analyzer produzir o `AnalysisResult`, o orquestrador do pipeline (CLI) chama `PersistenceService.save(projectScan, analysisResult)` — independentemente de o Exporter já ter rodado ou não; a ordem entre Persistence e Exporter não é uma dependência entre si (cada um consome os mesmos dois objetos em memória).
2. `save(...)` (método público, sem `@Transactional`) delega para um método interno `@Transactional`.
3. Dentro do método transacional:
   a. `AnalysisResultMapper` monta as entidades a partir de `projectScan`/`analysisResult` (nome do projeto derivado do último segmento do `path`, `analyzedAt = now()`, métricas agregadas copiadas de `AnalysisMetrics`, um `PackageMetric` por `PackageDependencyCount`, um `Cycle` por `Cycle` do Analyzer com `cyclePath` formatado como texto e `length` calculado).
   b. `projectRepository.findByPath(projectScan.rootPath())` — se presente, reaproveita o `Project`; caso contrário, associa o novo `Project` recém-mapeado.
   c. A `Analysis` mapeada é associada ao `Project` (existente ou novo), e seus `PackageMetric`/`Cycle` são associados a ela.
   d. `analysisRepository.save(analysis)` persiste a `Analysis`; o cascade (`CascadeType.ALL`) grava `PackageMetric`/`Cycle` na mesma operação. Se o `Project` for novo, ele é salvo antes (ou via cascade a partir de `Analysis.project`, a decidir na implementação — ambos válidos dado o mapeamento bidirecional).
4. Se qualquer etapa falhar, a exceção propaga para fora do método transacional, o Spring executa rollback, e a exceção chega ao método público `save(...)`.
5. `save(...)` captura a exceção, loga em nível apropriado (ex.: `ERROR`, com o `path` do projeto e a causa raiz), e retorna — nenhuma exceção alcança o chamador.
6. O restante do pipeline (Exporter, geração de `report.json`/`report.html`) prossegue de forma totalmente independente do resultado da persistência.

---

## 6. Estratégia de Testes

- **Framework:** JUnit 5 + AssertJ.
- **Duas camadas de teste**, por causa da natureza deste componente (o único, junto com o Scanner, que faz I/O real — aqui, com um banco):
  - **Rápida, com H2 em memória (`MODE=PostgreSQL`)**: testes de repositório e de fluxo (`@DataJpaTest`/contexto Spring mínimo), para o dia a dia de desenvolvimento.
  - **Integração real, com Testcontainers (PostgreSQL)**: valida o schema Flyway real contra as entidades mapeadas (garante que `ddl-auto=validate` não vai falhar em produção por divergência não capturada pelo H2).

### Casos de teste

**`AnalysisResultMapperTest`** (unitário, sem banco)
- `ProjectScan` + `AnalysisResult` válidos → `Project`/`Analysis`/`PackageMetric`/`Cycle` mapeados corretamente (contagens, nomes, `cyclePath` formatado).
- `AnalysisResult` sem ciclos/violações → `Analysis` com `cyclicDependencies = 0`/`violationCount = 0` e listas vazias de `Cycle`.
- Nome do projeto derivado corretamente do último segmento do `path` (incluindo casos com barra final).

**`ProjectRepositoryTest`** (H2)
- `findByPath` retorna o `Project` correto quando existe.
- `findByPath` retorna vazio quando não existe.
- Tentar salvar dois `Project` com o mesmo `path` viola a constraint de unicidade.

**`DefaultPersistenceServiceTest`** (H2, fluxo completo)
- Primeiro scan de um projeto novo → cria `Project`, `Analysis`, `PackageMetric`(s), `Cycle`(s).
- Segundo scan do **mesmo** `path` → reaproveita o `Project` existente (mesmo `id`), cria uma **nova** `Analysis` (histórico aditivo, 2.6) — nenhum registro anterior é sobrescrito.
- Falha simulada durante o mapeamento/gravação (ex.: mock de repositório lançando exceção) → `save(...)` não lança exceção, e nenhum dado parcial fica persistido (rollback, 2.4/2.5).
- Banco indisponível (ex.: `DataSource` apontando para porta fechada) → `save(...)` retorna normalmente, sem lançar exceção.

**`PersistenceIntegrationTest`** (Testcontainers + PostgreSQL real + Flyway)
- Sobe um container PostgreSQL, aplica a migration `V1__init.sql` via Flyway, inicializa o contexto Spring com `ddl-auto=validate` — o contexto deve subir sem erro de validação de schema.
- Fluxo completo de `save(...)` contra o Postgres real, incluindo verificação das foreign keys e da constraint de unicidade em `projects.path`.

---

## 7. Fora de Escopo (v1)

- Interface de consulta ao histórico persistido (diffs entre execuções, gráficos de evolução) — o schema existe para não bloquear isso no futuro, mas nenhuma consulta além da gravação é implementada nesta Spec; depende de uma futura extensão do Exporter/CLI.
- Deleção de `Project`/`Analysis` (não há caso de uso de limpeza de histórico no v1).
- Qualquer configuração de retry/backoff de conexão com o banco — uma falha de conexão é tratada como qualquer outra falha (2.1), sem tentativa de reconexão automática.

---

## 8. Pendências

- **Atualização de `Project` (nome, URL) se o mesmo projeto for escaneado a partir de um caminho diferente:** fora do v1 (2.7) — hoje isso cria um `Project` novo, sem vínculo com o anterior. Revisitar se o caso de uso de "mover pasta do projeto sem perder histórico" se mostrar relevante.
- **Estratégia de deduplicação de `Analysis`** (ex.: o mesmo commit/estado de código escaneado duas vezes seguidas gerando duas `Analysis` idênticas): fora do v1 — o modelo é puramente aditivo (2.6), sem detecção de "nada mudou desde o último scan".
- **Histórico com interface de consulta** (diffs, gráficos de evolução): fora do v1, aguardando uma futura Spec (Exporter estendido ou um novo componente) — o schema atual já é compatível com essa evolução, mas nenhuma consulta é implementada aqui.
