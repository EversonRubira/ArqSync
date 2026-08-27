# ArqSync

Ferramenta CLI para análise e evolução arquitetural de projetos Java.

![Build](https://img.shields.io/badge/build-passing-brightgreen)
![Tests](https://img.shields.io/badge/tests-75%2F75-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-94%25-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

ArqSync escaneia o código-fonte de um projeto Java, analisa suas dependências arquiteturais e gera relatórios sobre ciclos e violações de camada.

## Funcionalidades (v1)

- Scan de código-fonte Java, sem dependência de build tool do projeto analisado (Maven/Gradle)
- Detecção de ciclos de dependência entre pacotes
- Detecção de violação de camadas por convenção de nomenclatura (`controller`, `service`, `repository`, `domain`)
- Geração de relatórios em `report.json` e `report.html`
- Persistência opcional em PostgreSQL, com fallback gracioso quando o banco está indisponível

## Pré-requisitos

- Java 21
- Python 3.8+ com Jinja2 (necessário para gerar `report.html`; sem ele, apenas `report.json` é gerado)
- PostgreSQL (opcional — necessário apenas para persistência)

## Instalação e uso

```bash
git clone https://github.com/EversonRubira/ArqSync.git
cd ArqSync
./mvnw clean package
java -jar target/arqsync.jar /caminho/do/projeto
```

Os relatórios são gerados em `arqsync-reports/`.

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
