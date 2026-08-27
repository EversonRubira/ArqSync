package com.arqsync.analyzer;

import java.util.List;

public interface LayerViolationDetector {

    List<LayerViolation> detect(DependencyGraph graph);
}
