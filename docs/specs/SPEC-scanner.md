# Spec Técnica — Scanner (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-27

---

## 1. Visão Geral

O Scanner é o primeiro componente do pipeline do ArqSync. Ele recebe o caminho de um projeto Java na máquina local, percorre a árvore de arquivos `.java` (sem depender de Maven/Gradle) e produz um `ProjectScan`: um retrato estrutural bruto do projeto — pacotes, classes e seus imports — que alimenta o Analyzer.

O Scanner **não interpreta** arquitetura (não sabe o que é um ciclo ou uma violação de camada — isso é responsabilidade do Analyzer, ver `docs/specs/SPEC-analyzer.md`). Sua única responsabilidade é extrair fatos estruturais do código-fonte de forma resiliente: um arquivo com erro de sintaxe não deve interromper o scan do restante do projeto.

Esta Spec confirma formalmente o contrato de dados (`ProjectScan`, `PackageScan`, `ClassScan`, `ScanError`) que a Spec do Analyzer já assumia — os dois documentos agora estão alinhados.

---

## 2. Decisões de Design

### 2.1 Uso do JavaParser, não um parser próprio

**Decisão:** o parsing de arquivos `.java` usa a biblioteca **JavaParser 3.26.2** (já listada como dependência no PRD, seção 6), não uma implementação própria baseada em regex/heurística.

**Justificativa:**
- O PRD já define o JavaParser como dependência de consumo (seção 6) e cita explicitamente o risco de sintaxes modernas (records, sealed classes) e código com erro de compilação (seção 8) — uma biblioteca madura e mantida é a forma correta de mitigar isso, em vez de reimplementar um parser Java.
- Extrair `package` e `import` de forma confiável via regex é frágil (comentários, strings literais, ou código multi-linha podem conter texto parecido com `import`), enquanto um parser real trabalha sobre a AST.

**Alternativas descartadas:**
- *Parsing via regex/linha a linha.* Rejeitado: falso-positivo/negativo em casos comuns (comentários, imports comentados, formatação atípica).
- *Compilar o projeto (via `javac`) e usar reflection/bytecode.* Rejeitado: contraria a premissa central do PRD de não depender de build tool nem de o projeto compilar (código com erro pode e deve ainda ser parcialmente escaneado).

### 2.2 Descoberta de arquivos sem build tool

**Decisão:** percorrer recursivamente o `rootPath` (`Files.walk`) coletando todo arquivo `*.java`, ignorando diretórios de saída de build comuns (`target/`, `build/`, `out/`, `.git/`).

**Justificativa:**
- Consistente com a premissa do PRD ("a análise não depende de build tool... funciona só com estrutura de pastas `.java`", seção 7).
- Ignorar `target/`/`build/`/`out/` evita escanear artefatos gerados/copiados (ex.: fontes anotadas geradas por annotation processors, ou cópias de sources em diretórios de build), que poderiam duplicar classes e distorcer as métricas.

**Alternativas descartadas:**
- *Detectar e usar `src/main/java` como raiz padrão (convenção Maven/Gradle).* Rejeitado: o PRD pede independência de build tool; assumir essa convenção quebraria projetos com layout diferente. O `rootPath` fornecido pelo usuário via CLI é escaneado por completo.

### 2.3 Pacote de uma classe: declaração `package`, não estrutura de diretório

**Decisão:** o pacote de cada classe é lido da declaração `package` no topo do arquivo (via AST do JavaParser). Se o arquivo não tiver declaração de pacote, `packageName = ""` (pacote default). A estrutura de diretórios **não** é usada para inferir ou corrigir o pacote.

**Justificativa:**
- É a fonte de verdade mais simples e sempre correta por definição — não exige detectar "qual pasta é a raiz de source" (problema não-trivial em projetos multi-módulo ou com layout não convencional), o que estaria fora do escopo do v1.
- Evita lidar com o caso ambíguo de diretório e `package` divergentes — a declaração no arquivo é o que o compilador Java também usa como verdade.

**Alternativas descartadas:**
- *Inferir pacote pela estrutura de diretórios relativa a `rootPath`.* Rejeitado: exigiria heurística para achar a raiz de source correta (`src/main/java`, `src`, ou a própria raiz do projeto variam), o que adiciona complexidade sem necessidade — a declaração `package` já resolve isso diretamente.

### 2.4 Um `ClassScan` por tipo top-level declarado, não por arquivo

**Decisão:** cada arquivo `.java` pode gerar **um ou mais** `ClassScan` — um para cada declaração de tipo top-level (`class`, `interface`, `enum`, `record`, `@interface`) presente no arquivo. Todos os `ClassScan` originados do mesmo arquivo compartilham a mesma lista de imports (imports são escopados por arquivo em Java, não por classe). Classes/tipos aninhados (nested/inner) **não** geram `ClassScan` próprio.

**Justificativa:**
- O PRD pede a métrica "total de classes" (seção 3 e 5) — contar corretamente exige capturar todo tipo top-level, já que Java permite mais de um por arquivo (embora a convenção comum seja um único tipo público por arquivo).
- Tipos aninhados não têm um "pacote" próprio distinto do tipo que os contém, e o Analyzer opera em nível de pacote (Spec do Analyzer, decisão 2.1) — incluí-los infla a contagem de classes sem agregar sinal à análise de arquitetura.

**Alternativas descartadas:**
- *Um `ClassScan` por arquivo, ignorando múltiplos tipos top-level.* Rejeitado: subconta classes em arquivos com mais de um tipo top-level (raro, mas válido em Java).
- *Gerar `ClassScan` também para tipos aninhados.* Rejeitado: não se alinha à granularidade de pacote usada pelo Analyzer e distorce métricas sem trazer valor arquitetural.

### 2.5 Imports extraídos como texto bruto, sem resolução

**Decisão:** `ClassScan.imports` contém a representação textual de cada `import` exatamente como aparece no código-fonte, sem resolver se é interno ou externo ao projeto. Wildcard (`import com.acme.x.*`) é mantido com o sufixo `.*`; import estático (`import static com.acme.X.metodo`) é mantido com o prefixo `static ` — a mesma sintaxe do Java, apenas como string.

**Justificativa:**
- Mantém o Scanner focado em extração estrutural bruta ("fatos"), deixando a interpretação ("isso é um pacote interno? é uma classe ou um método estático?") como responsabilidade exclusiva do Analyzer — que já documenta e assume exatamente esse formato (Spec do Analyzer, seção 2.6 e pendência da seção 8). Esta Spec fecha essa pendência: o formato está confirmado.
- Evita duplicar, no Scanner, lógica de resolução que só o Analyzer tem contexto suficiente para fazer (ele conhece o conjunto completo de pacotes do projeto).

**Alternativas descartadas:**
- *Resolver e filtrar imports externos já no Scanner.* Rejeitado: acoplaria o Scanner ao conjunto de pacotes conhecidos do projeto (que só existe depois que o scan termina) e violaria a separação de responsabilidades Scanner (fatos) / Analyzer (interpretação).

### 2.6 Resiliência: falha de parsing não interrompe o scan

**Decisão:** cada arquivo é parseado individualmente e de forma isolada. Se o JavaParser lançar um erro de parsing (`ParseProblemException`) ou o arquivo não puder ser lido (`IOException`), o Scanner registra um `ScanError(filePath, message)` e segue para o próximo arquivo — o scan do projeto como um todo nunca é abortado por um único arquivo problemático.

**Justificativa:**
- Requisito explícito do PRD: "arquivos que falham no parsing são pulados e logados (não interrompem o scan)" (seção 8, mitigação de risco). É o mesmo espírito de resiliência do requisito P0 #8 (scan funciona mesmo sem banco disponível) — degradação graciosa em vez de falha total.

**Alternativas descartadas:**
- *Abortar o scan no primeiro erro de parsing.* Rejeitado: contraria diretamente o requisito do PRD e tornaria a ferramenta inutilizável em projetos reais com algum arquivo problemático (comum em código legado ou em transição).

### 2.7 Agrupamento em `PackageScan`: só pacotes com classes reais

**Decisão:** um `PackageScan` é criado apenas para pacotes que contêm diretamente pelo menos um `ClassScan`. Pacotes "pai" que só existem como prefixo de outros pacotes (sem classes próprias) não geram uma entrada de `PackageScan` vazia.

**Justificativa:**
- O Analyzer trata cada pacote como um nó atômico do grafo, identificado pelo nome completo (Spec do Analyzer, seção 2.1) — ele não modela hierarquia pai-filho entre pacotes. Criar entradas vazias para pacotes intermediários adicionaria nós sem sentido ao grafo e às métricas ("total de pacotes" ficaria inflado sem motivo).

**Alternativas descartadas:**
- *Criar uma entrada de `PackageScan` para todo pacote implícito na hierarquia de nomes.* Rejeitado: não agrega valor à análise (o Analyzer não usa relação pai-filho) e infla a métrica de total de pacotes de forma enganosa.

---

## 3. Modelos de Dados

Estes modelos são o contrato de saída do Scanner e o contrato de entrada do Analyzer — idênticos aos já documentados em `docs/specs/SPEC-analyzer.md`, seção 3.1.

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
    String name,               // nome simples do tipo top-level, ex: "OrderController"
    String packageName,        // pacote a que pertence (declaração `package`; "" se ausente)
    List<String> imports       // texto bruto de cada import, ex: "com.acme.x.Y",
                                // "com.acme.x.*", "static com.acme.x.Y.metodo"
) {}

record ScanError(
    String filePath,
    String message              // mensagem de erro de parsing/leitura
) {}
```

---

## 4. Interface Pública

```java
public interface ProjectScanner {
    ProjectScan scan(Path rootPath);
}

public interface JavaFileFinder {
    List<Path> find(Path rootPath); // localiza *.java recursivamente, ignorando build/target/out/.git
}

public interface ClassScanExtractor {
    // parseia um arquivo e extrai um ClassScan por tipo top-level;
    // lança uma exceção de parsing que o ProjectScanner converte em ScanError
    List<ClassScan> extract(Path javaFile);
}

public interface PackageAggregator {
    List<PackageScan> aggregate(List<ClassScan> classes);
}
```

A implementação padrão (`DefaultProjectScanner`) orquestra as três colaboradoras via composição simples, sem framework — mesma filosofia de composição do Analyzer.

---

## 5. Fluxo de Execução

1. `ProjectScanner.scan(rootPath)` é chamado pela CLI com o caminho informado pelo usuário.
2. `JavaFileFinder.find(rootPath)` retorna a lista de caminhos `*.java` a processar (diretórios de build já filtrados).
3. Para cada arquivo:
   a. `ClassScanExtractor.extract(file)` tenta parsear o arquivo com JavaParser.
   b. Em caso de sucesso: retorna a lista de `ClassScan` (um por tipo top-level, com pacote e imports do arquivo).
   c. Em caso de falha (parsing ou leitura): a falha é capturada pelo `ProjectScanner`, que registra `ScanError(filePath, message)` e segue para o próximo arquivo — nenhum `ClassScan` é gerado para esse arquivo.
4. Todos os `ClassScan` bem-sucedidos (de todos os arquivos) são agregados por `PackageAggregator.aggregate(...)` em `List<PackageScan>`.
5. `ProjectScanner` monta e retorna `ProjectScan(rootPath, packages, errors)`.

O fluxo é o único ponto do pipeline do ArqSync que faz I/O real (leitura de arquivos) — Analyzer, Persistence e Exporter operam sobre o modelo em memória resultante.

---

## 6. Estratégia de Testes

- **Framework:** JUnit 5 + AssertJ.
- **Diferença em relação ao Analyzer:** o Scanner é o único componente que precisa de testes com I/O real e parsing real — os testes usam arquivos `.java` de fixture em `src/test/resources/fixtures/...`, montando pequenos "projetos" de exemplo em disco (via `@TempDir` ou fixtures versionadas), não apenas modelos em memória.

### Casos de teste

**`JavaFileFinderTest`**
- Encontra todos os `.java` em uma árvore de diretórios aninhada.
- Ignora `target/`, `build/`, `out/`, `.git/`.
- Projeto sem nenhum arquivo `.java` → lista vazia.

**`ClassScanExtractorTest`**
- Classe simples com pacote e imports → `ClassScan` correto.
- Arquivo sem declaração `package` → `packageName = ""`.
- Arquivo com múltiplos tipos top-level (ex.: uma `class` pública + uma `class` package-private no mesmo arquivo) → múltiplos `ClassScan`, mesma lista de imports em ambos.
- Tipo aninhado dentro de uma classe → não gera `ClassScan` próprio.
- Import wildcard (`import com.acme.x.*`) → preservado com `.*` na string.
- Import estático (`import static com.acme.x.Y.metodo`) → preservado com prefixo `static `.
- Arquivo com erro de sintaxe → lança exceção de parsing (capturada no nível do `ProjectScanner`, não aqui).
- Record/sealed class (sintaxe Java 17+) → parseado com sucesso (cobre o risco citado no PRD).

**`PackageAggregatorTest`**
- Classes de pacotes diferentes → uma `PackageScan` por pacote, com as classes corretas agrupadas.
- Nenhuma entrada de `PackageScan` vazia é criada para pacotes "pai" implícitos.

**`DefaultProjectScannerTest` (integração dos componentes, com projeto de fixture em disco)**
- Projeto com múltiplos pacotes/classes válidos → `ProjectScan` completo e correto, `errors` vazio.
- Projeto com um arquivo válido e um arquivo com erro de sintaxe → `ProjectScan` contém os pacotes/classes do arquivo válido, e um `ScanError` para o arquivo inválido — scan não interrompido.
- Projeto vazio (diretório sem `.java`) → `ProjectScan` com `packages` e `errors` vazios, sem exceção lançada.
- Arquivo ilegível (ex.: sem permissão de leitura, quando testável no ambiente de CI) → gera `ScanError`, não interrompe o scan.

---

## 7. Fora de Escopo (v1)

Consistente com o PRD (seção 3 e 7):
- Symbol solving / resolução de tipos (ex.: `JavaSymbolSolver` do JavaParser) — não é necessário; o Analyzer trabalha apenas com nomes de import textuais, não com resolução completa de tipos.
- Suporte a múltiplos módulos/source roots (ex.: projeto Maven multi-módulo com vários `src/main/java`) — o v1 escaneia a árvore inteira a partir de um único `rootPath`.
- Qualquer dependência de Maven/Gradle (leitura de `pom.xml`/`build.gradle`, classpath, etc.).

---

## 8. Pendências

- **Permissão de leitura/arquivos ilegíveis:** o comportamento exato ao encontrar um arquivo sem permissão de leitura (gera `ScanError` como falha de parsing) está definido aqui, mas ainda não foi validado em ambiente real — revisitar durante a implementação.
- **Encoding de arquivos:** assume-se UTF-8 (ou o encoding padrão do JavaParser) para leitura dos arquivos `.java`; projetos com encoding diferente podem gerar `ScanError` — não é uma premissa validada com o autor, mas é aceitável para o v1 dado o público-alvo (projetos próprios/open source comuns).
