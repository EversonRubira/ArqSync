package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;

import java.util.List;
import java.util.Map;

/**
 * Detects adapters with no port implemented, only when the project's style
 * is Hexagonal (SPEC-adapter-port-violation.md, 2.1).
 */
public interface AdapterPortViolationDetector {

    List<AdapterSemPortaViolation> detect(
            ProjectScan projectScan, ArchitectureStyle style, Map<PackageName, PackageRole> packageRoles);
}
