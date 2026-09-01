package com.arqsync.analyzer;

import com.arqsync.scanner.ClassScan;
import com.arqsync.scanner.PackageScan;
import com.arqsync.scanner.ProjectScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Only runs for the Hexagonal style (SPEC-adapter-port-violation.md, 2.1). For
 * every class in a package classified {@link PackageRole#ADAPTER}, checks
 * whether any of its {@code superTypes} (simple names) matches an interface
 * declared in a package classified {@link PackageRole#CORE} — a "port". No
 * match means no port, which is the violation (2.3).
 *
 * <p>A supertype naming an external type (e.g. {@code Serializable}) can
 * never match: {@code corePortNames} below is built purely from interfaces
 * actually scanned in this project's core packages, so external names are
 * never candidates in the first place — no separate "is this internal"
 * check is needed to honor 2.3's "ignore external supertypes" rule.
 */
@Component
public class DefaultAdapterPortViolationDetector implements AdapterPortViolationDetector {

    @Override
    public List<AdapterSemPortaViolation> detect(
            ProjectScan projectScan, ArchitectureStyle style, Map<PackageName, PackageRole> packageRoles) {
        if (!DefaultArchitectureStyleDetector.HEXAGONAL.equals(style)) {
            return List.of();
        }

        Set<String> corePortNames = corePortInterfaceNames(projectScan, packageRoles);

        List<AdapterSemPortaViolation> violations = new ArrayList<>();
        for (PackageScan pkg : projectScan.packages()) {
            PackageName packageName = new PackageName(pkg.name());
            if (packageRoles.getOrDefault(packageName, PackageRole.UNKNOWN) != PackageRole.ADAPTER) {
                continue;
            }
            for (ClassScan cls : pkg.classes()) {
                boolean hasPort = cls.superTypes().stream().anyMatch(corePortNames::contains);
                if (!hasPort) {
                    violations.add(new AdapterSemPortaViolation(packageName, cls.name()));
                }
            }
        }
        return violations;
    }

    private Set<String> corePortInterfaceNames(ProjectScan projectScan, Map<PackageName, PackageRole> packageRoles) {
        Set<String> names = new HashSet<>();
        for (PackageScan pkg : projectScan.packages()) {
            PackageName packageName = new PackageName(pkg.name());
            if (packageRoles.getOrDefault(packageName, PackageRole.UNKNOWN) != PackageRole.CORE) {
                continue;
            }
            for (ClassScan cls : pkg.classes()) {
                if (cls.isInterface()) {
                    names.add(cls.name());
                }
            }
        }
        return names;
    }
}
