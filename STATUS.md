# STATUS — ArqSync

> Visibilidade de progresso do projeto, conforme mitigação de risco definida no PRD (seção 8: "abandono do projeto ou featuritis").
> **Última atualização:** 2026-08-27

---

## Fase atual

**Spec-Driven Development — fase de Specs técnicas.** Nenhum código de produção foi escrito ainda; o projeto está formalizando o desenho de cada componente antes de implementar.

## Próximo passo

Escrever a **Spec do Persistence** (schema e entidades), seguindo a ordem definida no PRD (seção 9):

1. ~~Spec do Scanner~~ ✅
2. ~~Spec do Analyzer~~ ✅
3. **Spec do Persistence** ← próximo
4. Spec do Exporter
5. Spec do CLI

## Checklist de Specs

| # | Spec | Status | Local |
|---|---|---|---|
| — | PRD v1 | ✅ Aprovado | `docs/prd/PRD-arqsync.md` |
| 1 | Scanner | ✅ Escrita | `docs/specs/SPEC-scanner.md` |
| 2 | Analyzer | ✅ Escrita | `docs/specs/SPEC-analyzer.md` |
| 3 | Persistence | ⏳ Pendente | — |
| 4 | Exporter | ⏳ Pendente | — |
| 5 | CLI | ⏳ Pendente | — |

Nenhuma Spec foi implementada em código ainda — todas estão na fase de design.

## Decisões fechadas (fora do PRD, definidas durante as Specs)

- **Grafo de dependências é por pacote**, não por classe (Spec do Analyzer, 2.1) — agregado a partir dos imports de cada classe, mas com pacote como unidade atômica de análise.
- **Regras de violação de camada:** `controller → repository` (pula `service`) é violação (`LAYER_SKIP`); qualquer aresta na direção contrária ao fluxo `controller → service → repository → domain` é violação (`LAYER_INVERSION`); `service → repository` é fluxo normal, não é violação; `domain` como destino é sempre permitido, de qualquer camada, para reduzir falsos positivos (Spec do Analyzer, 2.3).
- **Detecção de ciclos** via Tarjan (componentes fortemente conexos) + DFS para extrair caminhos concretos, com limite de ciclos reportados por componente — evita a complexidade de enumeração completa (algoritmo de Johnson), desnecessária na escala alvo do v1 (50–500 classes) (Spec do Analyzer, 2.5).
- **Scanner usa JavaParser 3.26.2**, sem parser próprio; pacote de uma classe vem da declaração `package` na AST, não da estrutura de diretórios; um arquivo `.java` pode gerar múltiplos `ClassScan` (um por tipo top-level); falha de parsing em um arquivo gera `ScanError` e não interrompe o scan do restante do projeto (Spec do Scanner, seções 2.1–2.6).
- **Contrato Scanner → Analyzer confirmado:** `ProjectScan(rootPath, packages, errors)`, `PackageScan(name, classes)`, `ClassScan(name, packageName, imports)`, `ScanError(filePath, message)` — igual nas duas Specs.
- **Imports** são passados como texto bruto do Scanner para o Analyzer (sem resolver interno/externo); a resolução (e o tratamento de wildcard `.*` e `static `) é responsabilidade do Analyzer, não do Scanner — separação entre "extrair fatos" (Scanner) e "interpretar arquitetura" (Analyzer).

## Riscos monitorados (do PRD, seção 8)

- **Falsos positivos minando confiança:** mitigado até aqui pela regra de `domain` como destino sempre permitido na análise de camadas (reduz ruído) e pela linguagem não-dogmática planejada para o relatório (ainda não implementada — depende do Exporter).
- **JavaParser falhando em sintaxes modernas:** endereçado no design (Spec do Scanner) via resiliência por arquivo, mas ainda não validado com código real, pois não há implementação.
- **Abandono do projeto / featuritis:** este `STATUS.md` existe para mitigar esse risco, conforme o próprio PRD prevê.

## Fora do escopo do v1 (lembrete)

- Métricas de qualidade estrutural (coesão/LCOM, instabilidade de Robert Martin, etc.).
- Interface de histórico/evolução (diffs entre execuções) — mesmo com persistência já implementada.
- Suporte a linguagens além de Java, IDE plugins, CI/CD, configuração customizável de camadas.

Ver `docs/prd/PRD-arqsync.md` para o documento completo de escopo, riscos e critérios de sucesso.
