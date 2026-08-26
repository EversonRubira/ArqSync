# STATUS — ArqSync

> **Última atualização:** 2026-08-26

## Fase atual

**PRD aprovado.** Especificação técnica (Spec) pendente.

## Próximo passo imediato

Escrever a **Spec do Scanner** — o componente base responsável por escanear os arquivos `.java` de um projeto e extrair a estrutura de pacotes, classes e imports (via JavaParser 3.26.2), sem depender de build tool (Maven/Gradle).

## Decisões fechadas (não revisitar)

- **Escopo do v1:** detecção de ciclos entre pacotes + violação de camadas por convenção de nomenclatura (`controller`, `service`, `repository`, `domain`). Métricas de qualidade estrutural (coesão, instabilidade) ficam fora do v1.
- **Entrada:** CLI (`java -jar arqsync.jar /caminho/do/projeto`), escaneando `.java` diretamente.
- **Saída:** `arqsync-reports/[timestamp]/report.json` + `report.html` (diagrama Mermaid, listas de ciclos/violações, métricas descritivas).
- **Persistência:** PostgreSQL (produção/dev) + H2 (testes), com fallback gracioso — o scan roda mesmo sem banco disponível. Tecnicamente P1, implementada com prioridade de execução equivalente a P0.
- **Resiliência de parsing:** arquivos com erro de compilação são pulados e logados; o scan não é interrompido.
- **Runtime:** Java 21 obrigatório.
- **Persona:** uso pessoal do autor no v1 — sem generalização para times/CI/CD.
- **Progresso medido por marcos funcionais** (ex: "Scanner rodando", "Analyzer detectando ciclos"), não por tempo. Meta: uma feature por semana.

## Pendências (checklist de Specs)

- [ ] Spec do Scanner
- [ ] Spec do Analyzer
- [ ] Spec do Persistence
- [ ] Spec do Exporter
- [ ] Spec do CLI

## Decisões explicitamente adiadas

- Ordem exata de execução das Specs
- Modo verbose vs. silencioso da CLI
- Tema visual do `report.html` (decidido como neutro no v1)
- Nome final dos artefatos gerados (`report.html` vs. `arqsync-report.html`)
