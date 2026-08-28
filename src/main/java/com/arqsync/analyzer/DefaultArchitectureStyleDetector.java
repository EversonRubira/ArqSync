package com.arqsync.analyzer;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Infers an architectural style from the package names present in the
 * dependency graph, using keyword signals commonly associated with each
 * style. Purely a naming heuristic for reporting purposes — unlike layer
 * violation detection, a "miss" here has no effect on the rest of the
 * analysis (SPEC-analyzer.md, 2.4's UNKNOWN-is-first-class philosophy
 * extended to the whole-project style, not just one package).
 *
 * <p>Checked in order from most to least specific, so that a project using
 * an explicit style (e.g. {@code ports}/{@code adapters}) isn't mistaken
 * for the more generic layered convention just because it also happens to
 * have a {@code service} package.
 */
@Component
public class DefaultArchitectureStyleDetector implements ArchitectureStyleDetector {

    private static final ArchitectureStyle HEXAGONAL = new ArchitectureStyle(
            "Arquitetura Hexagonal (Ports & Adapters)",
            "Os pacotes organizam o projeto em torno de portas e adaptadores: o núcleo da aplicação "
                    + "define interfaces (ports) que são implementadas por adaptadores externos (banco de "
                    + "dados, web, mensageria), isolando as regras de negócio de detalhes de infraestrutura."
    );

    private static final ArchitectureStyle CLEAN = new ArchitectureStyle(
            "Clean Architecture",
            "Os pacotes refletem os círculos concêntricos da Clean Architecture (entidades, casos de uso, "
                    + "adaptadores/gateways, infraestrutura): as regras de negócio centrais não dependem de "
                    + "nenhum detalhe externo — é sempre o externo que depende do núcleo."
    );

    private static final ArchitectureStyle DDD = new ArchitectureStyle(
            "Domain-Driven Design (DDD)",
            "A estrutura de pacotes evidencia conceitos de Domain-Driven Design (domínio, agregados, "
                    + "objetos de valor, camadas de aplicação e infraestrutura) — o software é modelado em "
                    + "torno do domínio de negócio, não da tecnologia usada para implementá-lo."
    );

    private static final ArchitectureStyle LAYERED = new ArchitectureStyle(
            "Arquitetura em Camadas (Layered)",
            "Os pacotes seguem a convenção clássica de camadas horizontais (controller → service → "
                    + "repository → domain), onde cada camada só deveria depender da camada imediatamente "
                    + "abaixo dela."
    );

    private static final ArchitectureStyle UNKNOWN = new ArchitectureStyle(
            "Não identificado",
            "Não foi possível reconhecer um padrão arquitetural conhecido pelos nomes dos pacotes deste "
                    + "projeto. Isso não é necessariamente um problema — apenas significa que a convenção de "
                    + "nomenclatura utilizada foge dos padrões comuns reconhecidos pelo ArqSync (camadas, "
                    + "hexagonal, clean, DDD)."
    );

    @Override
    public ArchitectureStyle detect(DependencyGraph graph) {
        Set<String> segments = segmentsOf(graph);

        if (segments.isEmpty()) {
            return UNKNOWN;
        }
        if (segments.contains("hexagonal") || hasHexagonalSignals(segments)) {
            return HEXAGONAL;
        }
        if (segments.contains("clean") || hasCleanSignals(segments)) {
            return CLEAN;
        }
        if (segments.contains("ddd") || segments.contains("domaindriven") || hasDddSignals(segments)) {
            return DDD;
        }
        if (segments.contains("layered") || segments.contains("camadas") || hasLayeredSignals(segments)) {
            return LAYERED;
        }
        return UNKNOWN;
    }

    private boolean hasHexagonalSignals(Set<String> segments) {
        boolean hasPort = segments.contains("port") || segments.contains("ports");
        boolean hasAdapter = segments.contains("adapter") || segments.contains("adapters");
        return hasPort && hasAdapter;
    }

    private boolean hasCleanSignals(Set<String> segments) {
        boolean hasUseCase = segments.contains("usecase") || segments.contains("usecases")
                || segments.contains("interactor") || segments.contains("interactors");
        boolean hasEntities = segments.contains("entities");
        boolean hasGatewaysOrPresenters = segments.contains("gateways") || segments.contains("presenters")
                || segments.contains("adapters");
        return hasUseCase && (hasEntities || hasGatewaysOrPresenters);
    }

    private boolean hasDddSignals(Set<String> segments) {
        boolean explicit = segments.contains("aggregate") || segments.contains("aggregates")
                || segments.contains("valueobject") || segments.contains("valueobjects") || segments.contains("vo")
                || segments.contains("boundedcontext") || segments.contains("boundedcontexts");
        boolean triad = segments.contains("domain") && segments.contains("application") && segments.contains("infrastructure");
        return explicit || triad;
    }

    private boolean hasLayeredSignals(Set<String> segments) {
        int matches = 0;
        if (segments.contains("controller") || segments.contains("controllers")) matches++;
        if (segments.contains("service") || segments.contains("services")) matches++;
        if (segments.contains("repository") || segments.contains("repositories")) matches++;
        return matches >= 2;
    }

    private Set<String> segmentsOf(DependencyGraph graph) {
        Set<String> segments = new HashSet<>();
        for (PackageName pkg : graph.nodes()) {
            for (String segment : pkg.value().split("\\.")) {
                segments.add(segment.toLowerCase(Locale.ROOT));
            }
        }
        return segments;
    }
}
