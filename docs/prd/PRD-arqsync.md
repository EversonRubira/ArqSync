# PRD — ArqSync (v1)

> **Status:** Rascunho para revisão final
> **Metodologia:** Spec-Driven Development (SDD)
> **Autor:** Everson Rubira
> **Última atualização:** 2026-08-26

---

## 1. Contexto

Desenvolvedores em formação (e times em geral) frequentemente não conseguem enxergar o estado real da arquitetura de um projeto Java. Padrões como arquitetura hexagonal são difíceis de identificar visualmente pela nomenclatura e estrutura de pacotes, e violações sutis — como um `controller` chamando diretamente um `repository`, pulando a camada de serviço, ou o surgimento de ciclos entre camadas — passam despercebidas até virarem dívida técnica consolidada.

Hoje, a única visão de arquitetura que existe é a "arquitetura ideal" na cabeça de quem projetou o sistema — não há um retrato objetivo e atualizado do estado real. Ferramentas existentes cobrem parte do problema, mas nenhuma resolve o essencial: o IntelliJ mostra dependências mas não guarda histórico; o SonarQube tem métricas mas é pesado e não é focado em arquitetura; o ArchUnit valida regras em testes, mas não serve como ferramenta de diagnóstico contínuo e visual.

O ArqSync nasce da necessidade pessoal do autor — desenvolvedor em formação — de aprender a enxergar arquitetura na prática, escaneando projetos Java reais, detectando violações estruturais e acompanhando a evolução da arquitetura ao longo do tempo (não só uma foto do estado atual). É também o projeto-veículo para aplicar Harness Engineering e Spec-Driven Development (SDD) na prática, servindo como peça de portfólio que demonstra visão arquitetural, não apenas capacidade de código.

---

## 2. Objetivos

**Objetivo principal do v1:** Permitir que o desenvolvedor rode o ArqSync sobre um projeto Java e receba um relatório visual que revele violações estruturais da arquitetura — em especial dependências cíclicas entre pacotes — que não seriam perceptíveis a olho nu na leitura do código.

**Critério de sucesso:** Ao visualizar o relatório (com diagrama Mermaid), o usuário consegue reconhecer visualmente um ciclo de dependência ou uma violação de camada, entender por que aquilo é um problema arquitetural, e levar esse aprendizado para reconhecer o mesmo padrão em outros projetos.

**Fora do objetivo do v1:** Acompanhamento de evolução ao longo do tempo (diffs entre execuções, gráficos históricos) não é objetivo do v1 — é uma ambição de longo prazo. O v1 entrega uma "foto" do estado atual da arquitetura. Porém, a persistência em banco de dados já é implementada desde o v1, para não bloquear essa evolução futura.

---

## 3. Escopo (o que está dentro e fora do v1)

### Dentro do v1
- Análise de dependências cíclicas entre pacotes Java
- Detecção de violação de camadas por convenção de nomenclatura de pacotes (`controller`, `service`, `repository`, `domain`)
- Entrada via CLI: `java -jar arqsync.jar /caminho/do/projeto`, escaneando arquivos `.java` diretamente, sem depender de build tool (Maven/Gradle)
- Persistência em banco de dados desde o v1 (schema pronto para suportar histórico futuro, mas sem interface de consulta histórica)
- Saída em `arqsync-reports/[timestamp]/`:
  - `report.json` — dados estruturados
  - `report.html` — diagrama Mermaid, lista de ciclos detectados, lista de violações de camada, e métricas descritivas (total de pacotes, total de classes, número de ciclos, número de violações, dependências incoming/outgoing por pacote)
- Geração de `report.pdf` (via WeasyPrint, dependência opcional com fallback)

### Fora do v1
- Métricas de qualidade estrutural (coesão/LCOM, instabilidade/abstração de Robert Martin, distância da sequência principal) ou qualquer métrica que exija análise semântica/ponderação
- Interface de histórico/evolução (diffs entre execuções, gráficos de evolução) — mesmo com dados já persistidos
- Suporte a linguagens além de Java
- Interface web/dashboard
- Plugins de IDE
- Integração com CI/CD
- Configuração customizável de regras de camadas (arquivo de config) — convenção de nomenclatura fixa por enquanto

---

## 4. Personas / Usuários

**Usuário único do v1: o próprio autor** — desenvolvedor backend em formação (Java/Spring), que quer aprender a enxergar arquitetura na prática. Usa IntelliJ, Git e terminal no dia a dia.

**Nível de arquitetura:** Básico/intermediário — conhece os conceitos (ciclos, camadas, hexagonal) teoricamente, mas tem dificuldade em identificá-los lendo código.

**Momentos de uso:**
- Estudando projetos open source (ex: Spring Boot) para aprender arquitetura na prática
- Auto-diagnóstico em projetos pessoais (ex: Boardly), após cada feature

**Implicação de design:** o relatório precisa ser didático — o diagrama Mermaid é a entrega principal, e as violações vêm acompanhadas de explicação em texto, não só "linha X viola regra Y".

**Fora do v1:** personas de time/empresa, uso multiusuário, CI/CD. Se o ArqSync ganhar outros usuários no futuro, essa seção é revisitada.

---

## 5. Funcionalidades (com prioridades)

Esquema de prioridade: **P0** (essencial — sem isso não há v1) / **P1** (desejável — v1 sobrevive sem).

### P0 — essencial

| # | Funcionalidade | Descrição |
|---|---|---|
| 1 | CLI de scan | `java -jar arqsync.jar /caminho/do/projeto` — ponto de entrada único |
| 2 | Parser de código Java | Escaneia arquivos `.java` do projeto e extrai estrutura de pacotes, classes e imports (sem depender de Maven/Gradle) |
| 3 | Detecção de ciclos | Identifica dependências cíclicas entre pacotes |
| 4 | Detecção de violação de camadas | Identifica violações por convenção de nomenclatura (`controller`, `service`, `repository`, `domain`) — ex: controller → repository direto |
| 5 | Métricas descritivas | Total de pacotes, total de classes, número de ciclos, número de violações, dependências incoming/outgoing por pacote |
| 6 | Geração de `report.json` | Saída estruturada dos dados de análise |
| 7 | Geração de `report.html` | Diagrama Mermaid + lista de ciclos + lista de violações (com explicação didática) + métricas |
| 8 | Resiliência sem banco | Se o banco não estiver disponível, o scan ainda roda e gera os relatórios normalmente |

### P1 — desejável, mas o v1 sobrevive sem

> Nota: a funcionalidade abaixo é **P1 em prioridade de valor** (o relatório funciona sem ela), mas **implementada com prioridade de execução equivalente a P0**, por decisão do autor — a estrutura de banco já está planejada (schema, entidades, `docker-compose.yml`), e adiar a implementação geraria retrabalho maior do que implementá-la agora.

| # | Funcionalidade | Descrição |
|---|---|---|
| 9 | Persistência em banco de dados | Salva o resultado de cada scan (entidades/schema já planejados), preparando terreno para histórico futuro — sem interface de consulta no v1 |

---

## 6. Consumes/Provides (dependências com outras features/sistemas)

### Consumes (dependências externas)

| Dependência | Tipo | Papel |
|---|---|---|
| Sistema de arquivos local | Entrada | Projeto Java a ser escaneado (via CLI) |
| JavaParser 3.26.2 | Biblioteca | Parsing do código-fonte Java (extração de pacotes, classes, imports) |
| PostgreSQL | Banco de dados | Persistência dos resultados de scan (produção/desenvolvimento) — via Docker Compose ou variáveis de ambiente |
| H2 | Banco de dados | Persistência em memória para testes automatizados |
| Mermaid.js (via CDN) | Biblioteca JS | Renderização do diagrama no navegador, carregado no `report.html` |
| Java 21 (JRE/JDK) | Runtime | Ambiente de execução do JAR |

### Provides (o que o ArqSync fornece)

| Artefato | Consumidor no v1 | Observação |
|---|---|---|
| `report.html` | O próprio usuário (visualização no navegador) | Entrega principal do v1 — diagrama Mermaid, ciclos, violações, métricas |
| `report.json` | Nenhum no v1 | Desenhado como contrato de dados para consumo futuro (ex: interface de histórico, outra ferramenta) — sem consumidor formal ainda |

**Nota de restrição:** como o Mermaid é carregado via CDN, a visualização do `report.html` depende de conexão com internet no momento em que é aberto no navegador.

---

## 7. Restrições e Premissas

### Restrições
- Java 21 obrigatório no ambiente de execução
- Python 3.8+ com Jinja2 instalado é necessário para gerar o `report.html` — se não disponível, o v1 gera apenas o `report.json` (fallback)
- PostgreSQL como banco de produção/desenvolvimento (via Docker Compose ou variáveis de ambiente); H2 em memória para testes — banco é dependência opcional, com fallback gracioso (o scan roda e gera os relatórios mesmo sem banco disponível)
- Conexão com internet necessária para visualizar o `report.html`, pois o Mermaid.js é carregado via CDN
- Alvo de performance: projetos de porte pequeno/médio (50–500 classes). Projetos muito grandes (>10k classes) podem ter desempenho degradado no v1 — otimização fica para depois
- Sem prazo fixo, mas com meta de manter momentum: v1 funcional em algumas semanas

### Premissas
- A convenção de nomenclatura de pacotes (`controller`, `service`, `repository`, `domain`) é suficiente para detectar violações de camada na maioria dos projetos-alvo; se a convenção não for seguida pelo projeto escaneado, a detecção de violações pode falhar silenciosamente (não detectar), mas a detecção de ciclos continua funcionando normalmente, pois não depende de nomenclatura
- Os projetos analisados são de porte pequeno/médio (50–500 classes)
- O código-fonte Java está disponível localmente (sem necessidade de acesso a repositório remoto)
- A análise não depende de build tool (Maven/Gradle) — funciona só com estrutura de pastas `.java`
- O usuário tem Java 21 instalado no ambiente

---

## 8. Riscos e Mitigações

| Risco | Mitigação |
|---|---|
| **JavaParser pode falhar** com sintaxes modernas (records, sealed classes) ou código com erros de compilação | Foco em suporte a features padrão Java 17+; arquivos que falham no parsing são pulados e logados (não interrompem o scan); versão do JavaParser é atualizada gradualmente conforme necessário |
| **Falsos positivos/negativos na detecção de violação de camadas**, por depender de convenção de nomenclatura | Relatório deixa explícito que a detecção de violações é baseada em convenção de nomes (não é uma verdade absoluta); a detecção de ciclos é destacada como mais robusta, por não depender de nomenclatura |
| **Abandono do projeto ou featuritis**, por ser projeto solo, sem prazo fixo | Critério de pronto claro para o v1 ("rodar em projeto real e abrir o `report.html` com o diagrama"); entregas pequenas e frequentes; `STATUS.md` para visibilidade de progresso; compromisso público via posts de marco no LinkedIn |
| **Falsos positivos minando a confiança no diagnóstico** (maior risco percebido pelo autor, por comprometer o próprio propósito educacional da ferramenta) | Validação cruzada com múltiplas LLMs (Claude Pro, Claude Code) durante o desenvolvimento; linguagem não-dogmática no relatório ("isso parece ser um ciclo", não uma afirmação absoluta); testes em projetos com ciclos conhecidos e em projetos bem estruturados; limitações da detecção documentadas explicitamente no próprio relatório |

---

## 9. Próximos Passos

Com o PRD aprovado, o próximo artefato é a **Especificação Técnica (Spec)**, seguindo a metodologia SDD. Ordem prevista (sujeita a ajuste durante a Spec):

1. Spec do Scanner (base — parsing do código Java)
2. Spec do Analyzer (detecção de ciclos e violações)
3. Spec do Persistence (schema e entidades)
4. Spec do Exporter (geração de `report.json`/`report.html`)
5. Spec do CLI (ponto de entrada)

**Ação imediata:** criar `STATUS.md` assim que o PRD for finalizado, registrando fase atual, próximo passo, decisões fechadas e checklist de Specs pendentes.

**Decisões explicitamente adiadas** (não bloqueiam o PRD, resolvidas durante as Specs):
- Ordem exata de execução das Specs
- Modo verbose vs. silencioso da CLI
- Tema visual do `report.html` — decidido como neutro no v1
- Nome final dos artefatos gerados (`report.html` vs. `arqsync-report.html`)
