# ArqSync

Ferramenta CLI para análise e evolução arquitetural de projetos Java.

![Build](https://img.shields.io/badge/build-passing-brightgreen)
![Tests](https://img.shields.io/badge/tests-75%2F75-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-94%25-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

ArqSync escaneia o código-fonte de um projeto Java, analisa suas dependências arquiteturais e gera relatórios sobre ciclos e violações de camada.

## Funcionalidades (v1)

- Scan de código-fonte Java, sem dependência de build tool do projeto analisado (Maven/Gradle)
- Análise a partir de um caminho local ou de uma URL de repositório Git público (clone automático)
- Detecção de ciclos de dependência entre pacotes
- Detecção de violação de camadas por convenção de nomenclatura (`controller`, `service`, `repository`, `domain`)
- Geração de relatórios em `report.json` e `report.html`
- Geração opcional de `report.pdf` — via `--pdf` na CLI (WeasyPrint, dependência opcional com fallback gracioso) ou diretamente no navegador, com o botão "Gerar PDF" no `report.html`
- Persistência opcional em PostgreSQL, com fallback gracioso quando o banco está indisponível

## Pré-requisitos

- Java 21
- Python 3.8+ com Jinja2 (necessário para gerar `report.html`; sem ele, apenas `report.json` é gerado)
- PostgreSQL (opcional — necessário apenas para persistência)

## Instalação e uso

### Build

```bash
git clone https://github.com/EversonRubira/ArqSync.git
cd ArqSync
./mvnw clean package
```

### Uso

**Análise local** — escaneia um projeto Java existente no disco:

```bash
java -jar target/arqsync.jar /caminho/do/projeto
```

**Análise remota** — clona e escaneia um repositório público (GitHub, GitLab, Bitbucket):

```bash
java -jar target/arqsync.jar https://github.com/usuario/projeto.git
```

Para manter o clone após a análise (depuração), adicione a flag `--keep`:

```bash
java -jar target/arqsync.jar https://github.com/usuario/projeto.git --keep
```

Para gerar `report.pdf` já na análise (via WeasyPrint), adicione a flag `--pdf`:

```bash
java -jar target/arqsync.jar /caminho/do/projeto --pdf
```

Alternativamente, o `report.html` tem um botão "Gerar PDF" no cabeçalho que usa a impressão do próprio navegador — não depende do WeasyPrint.

**Limitações (análise remota):**
- Apenas repositórios públicos
- Tamanho máximo: 100 MB
- Máximo de 10.000 arquivos `.java`
- Timeout de clone: 5 minutos
- Timeout total da operação: 10 minutos

### Saída

Os relatórios são gerados em `arqsync-reports/`:

```
arqsync-reports/
└── 2026-08-27-15-30-00/
    ├── report.json   # dados estruturados
    └── report.html   # relatório visual com diagrama Mermaid
```

## Arquitetura

O pipeline é sequencial, sem framework de orquestração:

Scanner → Analyzer → Persistence (opcional) → Exporter → CLI

| Componente | Responsabilidade |
|---|---|
| Scanner | Lê o código-fonte Java e extrai classes, pacotes e imports |
| Analyzer | Constrói o grafo de dependências e detecta ciclos e violações de camada |
| Persistence | Grava o resultado da análise em PostgreSQL; opcional, com fallback automático se o banco estiver indisponível |
| Exporter | Gera `report.json` e `report.html` a partir do resultado da análise |
| CLI | Ponto de entrada único (`java -jar arqsync.jar <caminho>`), orquestra as etapas acima |

## Tecnologias

- Java 21
- Spring Boot 3.4.1
- Maven
- PostgreSQL
- Flyway
- JavaParser
- Python / Jinja2
- Mermaid.js

## Documentação

- [PRD](docs/prd/)
- [Specs técnicas](docs/specs/)

## Sobre este projeto

O ArqSync foi desenvolvido aplicando Spec-Driven Development (SDD) com o Claude Code Harness: cada componente foi especificado formalmente antes da implementação, com decisões de design documentadas e justificadas, seguindo o princípio de que a especificação — não o código — é a fonte de verdade do sistema. Este projeto é o resultado prático desse estudo, usado como exercício para aplicar SDD de ponta a ponta em um sistema real, do PRD à implementação.

## Licença

MIT

## Autor

Everson Rubira — [GitHub](https://github.com/EversonRubira)
