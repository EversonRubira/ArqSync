# Spec Técnica — Scanner (v1)

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira (com Claude Code)
> **Última atualização:** 2026-08-27

---

## 1. Visão Geral

O Scanner é o primeiro componente do pipeline do ArqSync. Ele recebe o caminho de um diretório via CLI, escaneia recursivamente os arquivos `.java` dentro dele, parseia cada um com o JavaParser e produz um `ProjectScan`: um retrato estrutural bruto do projeto — pacotes, classes e imports. Não depende de build tool (Maven/Gradle).

O Scanner **não interpreta** arquitetura — não sabe o que é um ciclo ou uma violação de camada; isso é responsabilidade do Analyzer (`docs/specs/SPEC-analyzer.md`), que consome o `ProjectScan` produzido aqui. A responsabilidade do Scanner é extrair fatos estruturais de forma resiliente: um arquivo com erro de sintaxe nunca interrompe o scan do restante do projeto.

---

## 2. Decisões de Design

Cada decisão segue o princípio Hashimoto: a decisão, a justificativa, e as alternativas descartadas.

### 2.1 Descoberta de arquivos: caminhada manual, não `SourceRoot`

**Decisão:** os arquivos `.java` são descobertos via `Files.walk` manual sobre o diretório informado, não via a utilidade `SourceRoot` do próprio JavaParser. O pacote de cada classe vem da declaração `package` no arquivo (AST), nunca da estrutura de diretórios.

**Justificativa:**
- `SourceRoot` do JavaParser é pensado para projetos com uma raiz de source convencional (ex.: `src/main/java`) e amarra implicitamente a ideia de "pacote" à posição do arquivo na árvore — o que conflita diretamente com a premissa do ArqSync de funcionar sem build tool e com a decisão de extrair o pacote da declaração no código-fonte, não do caminho do arquivo.
- Uma caminhada manual dá controle total sobre a travessia: aplicar a denylist de diretórios (2.3) durante o walk, sem depender do comportamento interno de uma abstração de terceiros.

**Alternativas descartadas:**
- *Usar `SourceRoot` do JavaParser.* Rejeitado: assume convenção de source root incompatível com "sem build tool", e não expõe um ponto de extensão limpo para a denylist de diretórios que o ArqSync precisa.

### 2.2 Falha de parsing: valor de retorno, nunca exceção

**Decisão:** o resultado de parsear um arquivo é modelado como `ParseOutcome`, uma sealed interface com duas variantes — sucesso (com os `ClassScan` extraídos) e falha (com o `ScanError` correspondente). Nenhuma exceção de parsing escapa do `JavaParserAdapter`; o `ScannerService` nunca usa `try/catch` para controlar o fluxo de arquivo a arquivo.

**Justificativa:**
- Uma sealed interface com switch exaustivo obriga o código chamador, em tempo de compilação, a tratar os dois casos — sucesso e falha — sem depender de disciplina do desenvolvedor para lembrar de um `catch`. O contrato de resiliência fica expresso na assinatura do tipo, não em um comentário ou convenção.
- Usar exceção para representar "arquivo com erro de sintaxe" mistura um resultado de negócio esperado (parsing pode falhar; é o caso comum, não excepcional, em código real) com o mecanismo reservado para erros verdadeiramente inesperados — um `catch` genérico correria o risco de engolir silenciosamente um bug não relacionado a parsing (ex.: `NullPointerException` em outro ponto) como se fosse um erro de sintaxe legítimo.

**Alternativas descartadas:**
- *`try/catch` de uma `ParseException` (checked ou unchecked) ao redor de cada arquivo.* Rejeitado: torna fácil capturar exceções demais por engano (mascarando bugs reais como "arquivo inválido"), e não força o chamador a lidar com o caso de falha de forma explícita e verificável em compile-time.

### 2.3 Denylist fixa de diretórios excluídos

**Decisão:** diretórios com os nomes `target`, `build`, `.git`, `.idea`, `node_modules`, `out` são podados durante a caminhada — o Scanner nem desce para dentro deles. A lista é fixa no código, não configurável no v1.

**Justificativa:**
- Esses diretórios contêm artefatos de build, controle de versão, metadados de IDE ou dependências de outra stack (ex.: `node_modules` em um projeto full-stack) — nenhum deles representa código-fonte da arquitetura que o usuário quer diagnosticar. Escaneá-los poluiria as métricas (ex.: classes duplicadas em `target/classes` geradas por annotation processors) e o diagrama.
- Podar durante a própria caminhada (em vez de filtrar depois de coletar todos os arquivos) evita custo de I/O desnecessário em árvores potencialmente grandes (ex.: `node_modules`, `.git`).
- Manter a lista fixa (não configurável) é consistente com a filosofia do PRD de convenções fixas no v1 (mesma decisão já tomada para os nomes de camada na Spec do Analyzer) — evita superfície de configuração antes de haver evidência de necessidade real.

**Alternativas descartadas:**
- *Denylist configurável via CLI/arquivo de config.* Rejeitado: fora de escopo do v1; os seis diretórios cobrem os casos reais mais comuns, e configurabilidade é complexidade adiável.
- *Filtrar depois de coletar todos os arquivos (sem podar a travessia).* Rejeitado: gasta I/O andando por árvores que sabemos de antemão que serão descartadas inteiras.

### 2.4 Modelos como records imutáveis com `List.copyOf`

**Decisão:** `ProjectScan`, `PackageScan`, `ClassScan` e `ScanError` são records, e todo campo de lista é copiado defensivamente no construtor compacto via `List.copyOf(...)`.

**Justificativa:**
- O `ProjectScan` atravessa fronteiras de componente (Scanner → Analyzer, Persistence, Exporter). Sem cópia defensiva, um consumidor downstream poderia mutar a lista original recebida do Scanner (se o código de construção do Scanner reaproveitasse referências mutáveis), corrompendo o resultado do scan para os demais consumidores que compartilham a mesma instância.
- Fazer isso no construtor compacto do record garante a garantia de imutabilidade de forma uniforme e automática para qualquer forma de construção, sem depender de cada callsite lembrar de copiar.

**Alternativas descartadas:**
- *Records simples, sem cópia defensiva, confiando na disciplina do código chamador.* Rejeitado: frágil — um único ponto do código que reutiliza uma lista mutável compromete a garantia de imutabilidade silenciosamente.

### 2.5 Pacote default mapeado para `""`

**Decisão:** uma classe sem declaração `package` no arquivo tem `packageName = ""`.

**Justificativa:**
- É a mesma convenção usada pela própria API reflection do JDK (`Class.getPackageName()` retorna `""` para o pacote default) — reaproveitar uma convenção já familiar evita inventar um valor sentinela novo.
- String vazia é um valor de primeira classe simples de comparar e de usar em lógica de prefixo/substring no Analyzer, sem exigir tratamento especial de `null`.

**Alternativas descartadas:**
- *`null`.* Rejeitado: obriga checagem de nulidade em todo consumidor downstream, incluindo o Analyzer — risco de `NullPointerException` espalhado.
- *Sentinela textual como `"(default)"`.* Rejeitado: valor mágico arbitrário, sem motivo para ser mais claro que a string vazia, e diverge da convenção já usada pelo próprio Java.

### 2.6 Múltiplos tipos top-level por arquivo geram múltiplos `ClassScan`; tipos aninhados não são escaneados

**Decisão:** cada tipo top-level (`class`, `interface`, `enum`, `record`, `@interface`) declarado em um arquivo gera seu próprio `ClassScan`, todos compartilhando a mesma lista de imports do arquivo (imports são escopados por arquivo em Java, não por classe). Tipos aninhados (nested/inner) não geram `ClassScan` próprio no v1.

**Justificativa:**
- A métrica "total de classes" do PRD exige contar corretamente todo tipo top-level declarado, já que um arquivo pode conter mais de um (mesmo que a convenção comum seja um por arquivo).
- Tipos aninhados não representam uma unidade de pacote distinta do tipo que os contém, e o Analyzer opera em granularidade de pacote (Spec do Analyzer, 2.1) — incluí-los infla contagem sem adicionar sinal arquitetural.

**Alternativas descartadas:**
- *Um `ClassScan` por arquivo.* Rejeitado: subconta classes em arquivos com múltiplos tipos top-level.
- *`ClassScan` também para tipos aninhados.* Rejeitado: não se alinha à granularidade de pacote que o Analyzer usa; adiado explicitamente para v1 (ver Pendências).

### 2.7 Sem timeout de parsing

**Decisão:** nenhum mecanismo de timeout é aplicado ao parsing de um arquivo individual.

**Justificativa:**
- O parsing opera inteiramente sobre I/O local (sistema de arquivos), sem chamadas de rede — a classe de falha que um timeout normalmente mitiga (uma chamada externa que nunca retorna) não se aplica aqui. Adicionar um mecanismo de timeout (ex.: envolver cada parse em uma `Future` com prazo) é complexidade sem um modo de falha real conhecido a mitigar no v1.

**Alternativas descartadas:**
- *Envolver cada parse em um executor com prazo limite.* Rejeitado como engenharia prematura: não há evidência, no escopo do v1, de que o parser trave indefinidamente sobre arquivos locais. Revisitar apenas se isso for observado na prática.

### 2.8 Sem paralelismo

**Decisão:** os arquivos são processados sequencialmente, um por vez.

**Justificativa:**
- O PRD define o alvo de performance do v1 como projetos de 50–500 classes (seção 7), para os quais o processamento sequencial completa em tempo trivial. Paralelizar (ex.: stream paralela, `ExecutorService`) adicionaria complexidade real — sincronização na agregação de resultados, ordem de coleta de erros, superfície de teste maior — sem um problema de performance demonstrado a resolver.
- O próprio PRD já reconhece que projetos muito grandes (>10k classes) podem ter desempenho degradado no v1 e trata isso como otimização explicitamente adiada ("otimização fica para depois", seção 7).

**Alternativas descartadas:**
- *Caminhada e parsing paralelos desde o v1.* Rejeitado como otimização prematura — sem dados de performance real que justifiquem a complexidade adicional na escala alvo do v1.

### 2.9 Logging via SLF4J: WARN para arquivos pulados, INFO para o resumo

**Decisão:** cada arquivo pulado por falha de parsing gera um log em nível `WARN` (caminho do arquivo + mensagem). Ao final do scan, um log em nível `INFO` resume o resultado (total de arquivos processados, pacotes, classes, erros).

**Justificativa:**
- A persona do PRD é um desenvolvedor em formação rodando a ferramenta interativamente no terminal (seção 4) — feedback imediato de que um arquivo foi pulado, no momento em que acontece, é mais didático do que exigir que o usuário abra o `report.json`/`report.html` para descobrir isso depois. Isso também endereça o risco de "falsos positivos minando a confiança" (PRD, seção 8): arquivos pulados silenciosamente poderiam ser confundidos com ausência real de código.
- `WARN` (não `ERROR`) reflete corretamente que a falha é esperada e não interrompe a execução; `INFO` para o resumo dá uma checagem rápida de sanidade sem exigir nível `DEBUG`.

**Alternativas descartadas:**
- *Nenhum log; comunicar falhas apenas via `ScanError` no `ProjectScan`.* Rejeitado: o usuário só veria isso ao abrir o relatório final, perdendo o feedback imediato no terminal que o caso de uso interativo do PRD pede.

### 2.10 Imports estáticos/wildcard como texto bruto

**Decisão:** `ClassScan.imports` contém a representação textual de cada `import` exatamente como aparece no código-fonte (incluindo `.* ` para wildcard e `static ` para import estático), sem qualquer resolução ou normalização adicional.

**Justificativa:**
- Mantém o Scanner focado em extração estrutural bruta ("fatos"), deixando a interpretação (o que é interno ao projeto vs. externo, como tratar wildcard/static) inteiramente a cargo do Analyzer — que já assume e documenta esse formato (Spec do Analyzer, seção 2.6).

**Alternativas descartadas:**
- *Resolver/filtrar imports externos já no Scanner.* Rejeitado: acoplaria o Scanner ao conjunto completo de pacotes do projeto (que só existe depois que o scan termina) e violaria a separação de responsabilidades entre Scanner (fatos) e Analyzer (interpretação).

---

## 3. Modelos de Dados

Contrato de saída do Scanner e de entrada do Analyzer (confirmado em `docs/specs/SPEC-analyzer.md`, seção 3.1). Records imutáveis, com `List.copyOf(...)` no construtor compacto de cada um.

```java
record ProjectScan(
    String rootPath,
    List<PackageScan> packages,
    List<ScanError> errors
) {
    ProjectScan {
        packages = List.copyOf(packages);
        errors = List.copyOf(errors);
    }
}

record PackageScan(
    String name,               // nome totalmente qualificado, ex: "com.acme.orders.controller"
    List<ClassScan> classes
) {
    PackageScan {
        classes = List.copyOf(classes);
    }
}

record ClassScan(
    String name,                // nome simples do tipo top-level, ex: "OrderController"
    String packageName,         // pacote declarado no arquivo; "" se ausente (pacote default)
    List<String> imports        // texto bruto de cada import, ex: "com.acme.x.Y",
                                 // "com.acme.x.*", "static com.acme.x.Y.metodo"
) {
    ClassScan {
        imports = List.copyOf(imports);
    }
}

record ScanError(
    String filePath,
    String message               // mensagem de erro de parsing/leitura
) {}
```

---

## 4. Interface Pública

```java
public interface ScannerService {
    ProjectScan scan(Path path);
    // lança InvalidProjectPathException se o caminho não existir ou não for um diretório
}

public interface JavaParserAdapter {
    ParseOutcome parse(Path file);
}

public sealed interface ParseOutcome permits ParseOutcome.Success, ParseOutcome.Failure {
    record Success(List<ClassScan> classes) implements ParseOutcome {}
    record Failure(ScanError error) implements ParseOutcome {}
}

public class InvalidProjectPathException extends RuntimeException {
    public InvalidProjectPathException(String message) {
        super(message);
    }
}
```

`ScannerService.scan(...)` é o único ponto de entrada usado pela CLI. `JavaParserAdapter` é o colaborador interno responsável por envolver o JavaParser e nunca deixar uma exceção de parsing escapar — sua interface pública (em vez de uma classe concreta acoplada) existe principalmente para permitir substituição/stub em testes do `ScannerService` sem depender de arquivos reais em disco.

---

## 5. Fluxo de Execução

1. `ScannerService.scan(path)` é chamado pela CLI com o diretório informado pelo usuário.
2. Validação de precondição: se `path` não existe ou não é um diretório, lança `InvalidProjectPathException` imediatamente — essa é uma falha de configuração da execução como um todo, não um caso de resiliência por arquivo (diferente de uma falha de parsing individual, que nunca aborta o scan).
3. Caminhada manual (`Files.walk`) a partir de `path`, coletando arquivos com extensão `.java` e podando qualquer subárvore cujo diretório bata com a denylist (`target`, `build`, `.git`, `.idea`, `node_modules`, `out`) — a poda acontece durante a travessia, não depois.
4. Para cada arquivo `.java` encontrado, sequencialmente (sem paralelismo):
   a. `JavaParserAdapter.parse(file)` retorna um `ParseOutcome`.
   b. `ParseOutcome.Success(classes)`: os `ClassScan` extraídos (um por tipo top-level do arquivo) são acumulados.
   c. `ParseOutcome.Failure(error)`: um log `WARN` é emitido com o caminho e a mensagem; o `ScanError` é acumulado. O scan continua para o próximo arquivo.
5. Ao final da travessia, todos os `ClassScan` bem-sucedidos são agrupados por `packageName` em `PackageScan` — apenas pacotes com pelo menos uma classe geram uma entrada (nenhuma entrada vazia é criada para pacotes "pai" implícitos na hierarquia de nomes).
6. Um log `INFO` resume o scan (total de arquivos processados, pacotes, classes, erros).
7. `ScannerService` monta e retorna `ProjectScan(rootPath, packages, errors)`.

O Scanner é o único componente do pipeline do ArqSync que faz I/O real (leitura de arquivos) — Analyzer, Persistence e Exporter operam inteiramente sobre o modelo em memória resultante.

---

## 6. Estratégia de Testes

- **Framework:** JUnit 5 + AssertJ.
- **Fixtures:** projetos de exemplo versionados em `src/test/resources/fixtures/scanner/`, um subdiretório por cenário — este é o único componente do pipeline cujos testes fazem parsing real sobre arquivos `.java` reais (diferente do Analyzer, que usa apenas modelos em memória).

### Cenários de fixture

| Fixture | Cobre |
|---|---|
| `valid-project/` | Múltiplos pacotes e classes válidos, imports simples |
| `syntax-error/` | Um ou mais arquivos com erro de sintaxe, misturados com arquivos válidos |
| `multiple-classes-per-file/` | Arquivo com mais de um tipo top-level |
| `default-package/` | Arquivo sem declaração `package` |
| `empty-project/` | Diretório existente sem nenhum arquivo `.java` |

O cenário de denylist (diretórios `target`/`build`/`.git`/`.idea`/`node_modules`/`out`, com `.java` "plantado" dentro para provar que são ignorados) **não** é uma fixture versionada — um diretório literal `.git` não pode ser commitado normalmente (o Git o trata como fronteira de outro repositório, não como um diretório comum). Esse cenário é montado programaticamente dentro do próprio teste, via `@TempDir`.

### Casos de teste por componente

**`JavaParserAdapterTest`**
- Arquivo válido com pacote e imports → `ParseOutcome.Success` com os `ClassScan` corretos.
- Arquivo com erro de sintaxe → `ParseOutcome.Failure` com `ScanError` preenchido, sem lançar exceção.
- Arquivo sem declaração `package` → `ClassScan.packageName() == ""`.
- Arquivo com múltiplos tipos top-level → `Success` com múltiplos `ClassScan`, todos com a mesma lista de imports.
- Tipo aninhado dentro de uma classe → não gera `ClassScan` próprio.
- Import wildcard e import estático → preservados como texto bruto (`"com.acme.x.*"`, `"static com.acme.x.Y.metodo"`).
- Sintaxe moderna (records, sealed classes, Java 17+) → parseado com sucesso.

**`ScannerServiceTest`** (integração, sobre as fixtures em disco)
- `valid-project/` → `ProjectScan` completo e correto, `errors` vazio.
- `syntax-error/` → `ProjectScan` contém pacotes/classes dos arquivos válidos e um `ScanError` por arquivo inválido; scan não interrompido.
- `multiple-classes-per-file/` → todas as classes do arquivo aparecem no `PackageScan` correto.
- `default-package/` → uma `PackageScan` com `name = ""` contendo a(s) classe(s) correspondente(s).
- Denylist (`target`/`build`/`.git`/`.idea`/`node_modules`/`out`, montado via `@TempDir`) → nenhuma classe "plantada" dentro desses diretórios aparece no resultado.
- `empty-project/` → `ProjectScan` com `packages` e `errors` vazios, sem exceção.
- Caminho inexistente ou que não é diretório → `InvalidProjectPathException`.

---

## 7. Fora de Escopo (v1)

- Symbol solving / resolução de tipos (`JavaSymbolSolver`) — desnecessário; o Analyzer trabalha apenas com nomes de import textuais.
- Suporte a múltiplos módulos/source roots — o v1 escaneia a árvore inteira a partir de um único `path`.
- Qualquer dependência de Maven/Gradle (leitura de `pom.xml`/`build.gradle`, classpath).
- Paralelização do scan (ver 2.8).
- Denylist configurável de diretórios (ver 2.3).

---

## 8. Pendências

- **Interpretação de imports estáticos/wildcard:** capturados como texto bruto pelo Scanner (2.10); a resolução (o que é interno ao projeto, como tratar `.*`/`static `) é responsabilidade do Analyzer — já documentada na Spec do Analyzer, seção 2.6, mas vale revalidar com casos reais assim que houver implementação de ambos os componentes.
- **Colisão de nomes de pacote em projeto multi-módulo:** se dois módulos distintos declararem o mesmo nome de pacote totalmente qualificado, o Scanner os funde em uma única `PackageScan` (o modelo não tem noção de módulo) — comportamento aceito no v1, mas não validado contra um projeto multi-módulo real. Pode gerar métricas enganosas nesse cenário específico.
- **Classes aninhadas:** deliberadamente fora do v1 (2.6) — se no futuro o Analyzer precisar delas (ex.: para alguma métrica de coesão, hoje fora de escopo), esta decisão precisa ser revisitada.
- **Paralelização futura:** adiada por decisão (2.8); revisitar apenas se houver evidência real de gargalo de performance, especialmente para projetos fora da faixa alvo (>500 classes), consistente com o risco de performance degradada já reconhecido no PRD (seção 7).
