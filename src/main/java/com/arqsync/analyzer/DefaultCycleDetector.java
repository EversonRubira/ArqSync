package com.arqsync.analyzer;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects dependency cycles: Tarjan's algorithm finds the strongly connected
 * components, then a bounded DFS extracts concrete cycle paths within each
 * one (SPEC-analyzer.md, 2.5).
 */
@Component
public class DefaultCycleDetector implements CycleDetector {

    private static final int MAX_CYCLES_PER_COMPONENT = 10;

    @Override
    public List<Cycle> detect(DependencyGraph graph) {
        List<Set<PackageName>> components = findStronglyConnectedComponents(graph);

        List<Cycle> cycles = new ArrayList<>();
        for (Set<PackageName> component : components) {
            cycles.addAll(extractCycles(component, graph));
        }

        return cycles.stream()
                .sorted(Comparator.comparing(DefaultCycleDetector::pathKey))
                .toList();
    }

    private static String pathKey(Cycle cycle) {
        return cycle.path().stream().map(PackageName::value).collect(Collectors.joining(">"));
    }

    // --- Tarjan's strongly connected components ---

    private List<Set<PackageName>> findStronglyConnectedComponents(DependencyGraph graph) {
        TarjanState state = new TarjanState();
        for (PackageName node : graph.nodes()) {
            if (!state.index.containsKey(node)) {
                strongConnect(node, graph, state);
            }
        }
        return state.components;
    }

    private void strongConnect(PackageName node, DependencyGraph graph, TarjanState state) {
        state.index.put(node, state.counter);
        state.lowlink.put(node, state.counter);
        state.counter++;
        state.stack.push(node);
        state.onStack.add(node);

        for (DependencyEdge edge : graph.outgoingFrom(node)) {
            PackageName neighbor = edge.to();
            if (!state.index.containsKey(neighbor)) {
                strongConnect(neighbor, graph, state);
                state.lowlink.put(node, Math.min(state.lowlink.get(node), state.lowlink.get(neighbor)));
            } else if (state.onStack.contains(neighbor)) {
                state.lowlink.put(node, Math.min(state.lowlink.get(node), state.index.get(neighbor)));
            }
        }

        if (state.lowlink.get(node).equals(state.index.get(node))) {
            Set<PackageName> component = new LinkedHashSet<>();
            PackageName member;
            do {
                member = state.stack.pop();
                state.onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));
            state.components.add(component);
        }
    }

    private static final class TarjanState {
        final Map<PackageName, Integer> index = new HashMap<>();
        final Map<PackageName, Integer> lowlink = new HashMap<>();
        final Set<PackageName> onStack = new HashSet<>();
        final Deque<PackageName> stack = new ArrayDeque<>();
        final List<Set<PackageName>> components = new ArrayList<>();
        int counter = 0;
    }

    // --- Cycle path extraction within a component ---

    private List<Cycle> extractCycles(Set<PackageName> component, DependencyGraph graph) {
        if (component.size() == 1) {
            PackageName node = component.iterator().next();
            return hasSelfLoop(node, graph)
                    ? List.of(new Cycle(List.of(node, node)))
                    : List.of();
        }

        PackageName start = component.stream()
                .min(Comparator.comparing(PackageName::value))
                .orElseThrow();

        List<Cycle> cycles = new ArrayList<>();
        List<PackageName> path = new ArrayList<>(List.of(start));
        Set<PackageName> visited = new HashSet<>(List.of(start));
        searchCycles(start, start, component, graph, path, visited, cycles);
        return cycles;
    }

    private boolean hasSelfLoop(PackageName node, DependencyGraph graph) {
        return graph.outgoingFrom(node).stream().anyMatch(edge -> edge.to().equals(node));
    }

    private void searchCycles(
            PackageName start,
            PackageName current,
            Set<PackageName> component,
            DependencyGraph graph,
            List<PackageName> path,
            Set<PackageName> visited,
            List<Cycle> cycles
    ) {
        if (cycles.size() >= MAX_CYCLES_PER_COMPONENT) {
            return;
        }

        for (DependencyEdge edge : graph.outgoingFrom(current)) {
            PackageName next = edge.to();
            if (!component.contains(next)) {
                continue;
            }

            if (next.equals(start)) {
                List<PackageName> cyclePath = new ArrayList<>(path);
                cyclePath.add(start);
                cycles.add(new Cycle(List.copyOf(cyclePath)));
                if (cycles.size() >= MAX_CYCLES_PER_COMPONENT) {
                    return;
                }
            } else if (!visited.contains(next)) {
                visited.add(next);
                path.add(next);
                searchCycles(start, next, component, graph, path, visited, cycles);
                path.remove(path.size() - 1);
                visited.remove(next);
            }
        }
    }
}
