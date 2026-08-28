package com.arqsync.analyzer;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies the fixed layer ordering (controller -> service -> repository ->
 * domain) to every edge of the dependency graph (SPEC-analyzer.md, 2.3).
 */
@Component
public class DefaultLayerViolationDetector implements LayerViolationDetector {

    @Override
    public List<LayerViolation> detect(DependencyGraph graph) {
        List<LayerViolation> violations = new ArrayList<>();

        for (DependencyEdge edge : graph.edges()) {
            Layer fromLayer = LayerResolver.resolve(edge.from());
            Layer toLayer = LayerResolver.resolve(edge.to());

            if (fromLayer == Layer.UNKNOWN || toLayer == Layer.UNKNOWN) {
                continue;
            }
            if (toLayer == Layer.DOMAIN) {
                continue; // domain as a target is always allowed, from any layer
            }

            if (fromLayer.ordinal() > toLayer.ordinal()) {
                violations.add(violation(edge, fromLayer, toLayer, ViolationType.LAYER_INVERSION));
            } else if (toLayer.ordinal() - fromLayer.ordinal() > 1) {
                violations.add(violation(edge, fromLayer, toLayer, ViolationType.LAYER_SKIP));
            }
        }

        return violations;
    }

    private LayerViolation violation(DependencyEdge edge, Layer fromLayer, Layer toLayer, ViolationType type) {
        return new LayerViolation(
                edge.from(),
                edge.to(),
                fromLayer,
                toLayer,
                type,
                edge.classSamples(),
                explanationFor(edge, fromLayer, toLayer, type),
                suggestionFor(fromLayer, toLayer, type)
        );
    }

    private String explanationFor(DependencyEdge edge, Layer fromLayer, Layer toLayer, ViolationType type) {
        String sourceSample = edge.classSamples().isEmpty()
                ? edge.from().value()
                : edge.classSamples().get(0).fromClass();
        String targetSample = edge.classSamples().isEmpty()
                ? edge.to().value()
                : edge.classSamples().get(0).toClass();

        return switch (type) {
            case LAYER_SKIP -> "%s depende diretamente de %s, pulando a camada de %s."
                    .formatted(sourceSample, targetSample, skippedLayerNames(fromLayer, toLayer));
            case LAYER_INVERSION -> "%s (camada %s) depende de %s (camada %s), invertendo o fluxo esperado de dependência entre camadas."
                    .formatted(sourceSample, layerName(fromLayer), targetSample, layerName(toLayer));
        };
    }

    private String suggestionFor(Layer fromLayer, Layer toLayer, ViolationType type) {
        return switch (type) {
            case LAYER_SKIP -> "Introduza a chamada através de %s em vez de acessar %s diretamente, mantendo cada camada dependente apenas da camada imediatamente abaixo."
                    .formatted(layerName(nextLayerAfter(fromLayer)), layerName(toLayer));
            case LAYER_INVERSION -> "Essa dependência aponta no sentido contrário ao fluxo esperado (o correto é %s depender de %s, não o inverso). Mova a lógica que precisa de %s para dentro de %s, ou passe os dados necessários como parâmetro em vez de %s chamar %s diretamente."
                    .formatted(layerName(toLayer), layerName(fromLayer), layerName(fromLayer), layerName(toLayer), layerName(fromLayer), layerName(toLayer));
        };
    }

    private Layer nextLayerAfter(Layer layer) {
        Layer[] values = Layer.values();
        return values[layer.ordinal() + 1];
    }

    private String skippedLayerNames(Layer fromLayer, Layer toLayer) {
        Layer[] values = Layer.values();
        List<String> skipped = new ArrayList<>();
        for (int i = fromLayer.ordinal() + 1; i < toLayer.ordinal(); i++) {
            skipped.add(layerName(values[i]));
        }
        return String.join(", ", skipped);
    }

    private String layerName(Layer layer) {
        return layer.name().toLowerCase(Locale.ROOT);
    }
}
