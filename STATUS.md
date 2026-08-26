# STATUS — ArqSync

> **Última atualização:** 2026-08-26

## Fase atual

**Spec do Scanner concluída e aprovada.** (`docs/specs/SPEC-scanner.md`)

## Próximo passo imediato

Escrever a **Spec do Analyzer** — o componente responsável por consumir o `ProjectScan` produzido pelo Scanner e detectar dependências cíclicas entre pacotes e violações de camada por convenção de nomenclatura.

## Decisões fechadas (não revisitar)

- **Escopo do v1:** detecção de ciclos entre pacotes + violação de camadas por convenção de nomenclatura (`controller`, `service`, `repository`, `domain`). Métricas de qualidade estrutural (coesão, instabilidade) ficam fora do v1.
- **Entrada:** CLI (`java -jar arqsync.jar /caminho/do/projeto`), escaneando `.java` diretamente.
- **Saída:** `arqsync-reports/[timestamp]/report.json` + `report.html` (diagrama Mermaid, listas de ciclos/violações, métricas descritivas).
- **Persistência:** PostgreSQL (produção/dev) + H2 (testes), com fallback gracioso — o scan roda mesmo sem banco disponível. Tecnicamente P1, implementada com prioridade de execução equivalente a P0.
- **Resiliência de parsing:** arquivos com erro de compilação são pulados e logados; o scan não é interrompido.
- **Runtime:** Java 21 obrigatório.
- **Persona:** uso pessoal do autor no v1 — sem generalização para times/CI/CD.
- **Progresso medido por marcos funcionais** (ex: "Scanner rodando", "Analyzer detectando ciclos"), não por tempo. Meta: uma feature por semana.
- **Scanner (Spec aprovada):** caminhada manual de diretório (não `SourceRoot` do JavaParser) — pacote lido da declaração `package` do arquivo, não da estrutura de pastas; falha de parsing modelada como `ParseOutcome` (sealed interface), nunca exceção; denylist fixa de diretórios de build/VCS (`target`, `build`, `.git`, `.idea`, `node_modules`, `out`); modelos (`ProjectScan`, `PackageScan`, `ClassScan`, `ScanError`) como records imutáveis; sem timeout e sem paralelismo no v1.

## Pendências (checklist de Specs)

- [x] Spec do Scanner
- [ ] Spec do Analyzer
- [ ] Spec do Persistence
- [ ] Spec do Exporter
- [ ] Spec do CLI

## Decisões explicitamente adiadas

- Ordem exata de execução das Specs
- Modo verbose vs. silencioso da CLI
- Tema visual do `report.html` (decidido como neutro no v1)
- Nome final dos artefatos gerados (`report.html` vs. `arqsync-report.html`)
- Interpretação de imports estáticos/wildcard (mapeamento pacote-a-pacote) — fica para a Spec do Analyzer
- Pacotes com mesmo nome em módulos diferentes (multi-módulo) — Scanner identifica pacotes só pelo nome no v1
- Classes aninhadas (inner/nested) como entidades separadas — não incluído no v1
- Paralelização do scan — otimização futura, fora do v1
