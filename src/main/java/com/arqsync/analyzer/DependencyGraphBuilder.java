package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;

public interface DependencyGraphBuilder {

    DependencyGraph build(ProjectScan projectScan);
}
