# Adendo — SPEC Técnica Scanner: Captura de Supertipos

> **Status:** Rascunho para revisão
> **Metodologia:** Spec-Driven Development (SDD)
> **Reabre:** `docs/specs/SPEC-scanner.md` (aprovada)
> **Motivado por:** SPEC-adapter-port-violation.md (ver `docs/specs/`)

---

## 1. Contexto

`SPEC-scanner.md` já está aprovada. Este adendo documenta uma extensão pontual do
modelo de dados do Scanner, necessária para uma capacidade de análise nova (ver
`SPEC-adapter-port-violation.md`), sem reabrir o restante da spec aprovada.

## 2. Decisão de Design

### 2.1 `ClassScan` ganha supertipos e a marca de interface

**Decisão:** `ClassScan` passa a incluir `List<String> superTypes` — os nomes
declarados em `implements`/`extends` da classe — e `boolean isInterface` — se a
declaração é de fato uma `interface`, não uma classe/enum/record/annotation. Ambos
extraídos do mesmo AST já percorrido pelo `JavaParserAdapter` para capturar `imports`.

```java
public record ClassScan(String name, String packageName, List<String> imports,
                         List<String> superTypes, boolean isInterface) {
    public ClassScan {
        imports = List.copyOf(imports);
        superTypes = List.copyOf(superTypes);
    }
}
```

**Justificativa:**
- Para verificar relações de implementação (ex.: um adapter que implementa uma porta
  do domínio), o Scanner precisa capturar supertipos de cada classe — é um dado
  estrutural bruto, do mesmo tipo que `imports`, e cabe na mesma responsabilidade do
  Scanner (extrair fatos, não interpretar arquitetura).
- `isInterface` é necessário pela mesma razão: a regra "porta = uma `interface` cujo
  pacote foi classificado como núcleo" (`SPEC-adapter-port-violation.md`, 2.2) precisa
  distinguir uma interface de domínio de uma classe qualquer que também esteja em um
  pacote núcleo — sem essa marca, qualquer supertipo cujo nome bata com qualquer
  `ClassScan` do pacote núcleo contaria como porta, mesmo que fosse uma classe comum.
  Só ficou evidente durante a implementação da Spec A, não no desenho original deste
  adendo — daí entrar aqui como parte da mesma extensão, não como um adendo à parte.
- O JavaParser já expõe as duas informações na mesma passada de AST usada para
  `imports` (`superTypes` via `NodeWithExtends`/`NodeWithImplements`,
  `isInterface` via `ClassOrInterfaceDeclaration.isInterface()`) — não é uma segunda
  leitura do arquivo, só mais dois dados extraídos da mesma visita.
- Manter `superTypes` como `List<String>` (nomes simples, não resolvidos) segue a
  mesma convenção já usada em `imports`: resolução de nome pra pacote interno é
  responsabilidade do Analyzer (SPEC-analyzer.md, 2.6), não do Scanner.

**Alternativas descartadas:**
- *Criar um novo tipo `ClassScanWithSuperTypes` separado.* Rejeitado: duplicaria o
  record por uma extensão aditiva; nenhum consumidor existente do `ClassScan` quebra
  com campos novos.
- *Resolver o supertipo pra um pacote/porta já no Scanner.* Rejeitado pelo mesmo
  motivo da resolução de imports (2.6 do SPEC-analyzer.md): o Scanner não deveria
  precisar entender a estrutura de pacotes do projeto pra fazer seu trabalho.
- *Inferir "é porta" por convenção de nome (ex.: sufixo `Port`) em vez de checar
  `isInterface`.* Rejeitado: convenção de nome é frágil e opcional; `isInterface` é um
  fato estrutural do AST, sempre disponível, sem depender de nomenclatura do projeto
  analisado.

## 3. Impacto em consumidores existentes

`DefaultJavaParserAdapter` precisa de duas extrações adicionais (supertipos
declarados e a marca de interface) ao lado da já existente (imports), na mesma visita
ao AST. Nenhum consumidor atual do `ClassScan` (Analyzer) quebra — os campos são
aditivos.

## 4. Fora de escopo deste adendo

- Resolver `superTypes` para um pacote real do projeto (interno vs. biblioteca
  externa) — responsabilidade do Analyzer, tratada em `SPEC-adapter-port-violation.md`.
