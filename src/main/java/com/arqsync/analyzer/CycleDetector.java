package com.arqsync.analyzer;

import java.util.List;

public interface CycleDetector {

    List<Cycle> detect(DependencyGraph graph);
}
