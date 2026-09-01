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

### 2.1 `ClassScan` ganha a lista de supertipos

**Decisão:** `ClassScan` passa a incluir `List<String> superTypes` — os nomes
declarados em `implements`/`extends` da classe, extraídos do mesmo AST já percorrido
pelo `JavaParserAdapter` para capturar `imports`.

```java
public record ClassScan(String name, String packageName, List<String> imports,
                         List<String> superTypes) {
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
- O JavaParser já expõe essa informação na mesma passada de AST usada para
  `imports` — não é uma segunda leitura do arquivo, só mais um campo extraído da
  mesma visita.
- Manter como `List<String>` (nomes simples, não resolvidos) segue a mesma convenção
  já usada em `imports`: resolução de nome pra pacote interno é responsabilidade do
  Analyzer (SPEC-analyzer.md, 2.6), não do Scanner.

**Alternativas descartadas:**
- *Criar um novo tipo `ClassScanWithSuperTypes` separado.* Rejeitado: duplicaria o
  record por uma extensão aditiva; nenhum consumidor existente do `ClassScan` quebra
  com um campo novo.
- *Resolver o supertipo pra um pacote/porta já no Scanner.* Rejeitado pelo mesmo
  motivo da resolução de imports (2.6 do SPEC-analyzer.md): o Scanner não deveria
  precisar entender a estrutura de pacotes do projeto pra fazer seu trabalho.

## 3. Impacto em consumidores existentes

`DefaultJavaParserAdapter` precisa de uma extração adicional (supertipos declarados)
ao lado da já existente (imports), na mesma visita ao AST. Nenhum consumidor atual do
`ClassScan` (Analyzer) quebra — o campo é aditivo.

## 4. Fora de escopo deste adendo

- Resolver `superTypes` para um pacote real do projeto (interno vs. biblioteca
  externa) — responsabilidade do Analyzer, tratada em `SPEC-adapter-port-violation.md`.

## 5. Nota de implementação (registrada durante a Fase 3)

Ao implementar `SPEC-adapter-port-violation.md`, ficou claro que a regra "porta = uma
`interface` cujo pacote foi classificado como núcleo" (2.2 dessa spec) também exige
saber se um `ClassScan` é uma interface — informação que nem este adendo nem
`SPEC-scanner.md` capturavam. `ClassScan` ganhou também `boolean isInterface`,
extraído na mesma visita de AST (`ClassOrInterfaceDeclaration.isInterface()`), pelo
mesmo raciocínio de 2.1: dado estrutural bruto, sem custo de uma segunda leitura.
