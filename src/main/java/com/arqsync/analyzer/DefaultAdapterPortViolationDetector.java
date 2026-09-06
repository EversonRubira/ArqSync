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
 * Only runs for the Hexagonal style (SPEC-adapter-port-violation.md, 2.1). The
 * obligation checked depends on the adapter's direction
 * (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 2.4):
 *
 * <ul>
 *   <li>{@link PackageRole#DRIVEN_ADAPTER} (adapter de saída): must <b>implement</b> an
 *       output port — some {@code superTypes} must match an interface declared in a
 *       package classified {@link PackageRole#OUTPUT_PORT}. Unchanged from the original
 *       Spec A rule.</li>
 *   <li>{@link PackageRole#DRIVING_ADAPTER} (adapter de entrada): must <b>depend on</b>
 *       an input port — some {@code fieldTypes} must match an interface declared in a
 *       package classified {@link PackageRole#INPUT_PORT}. A driving adapter is not
 *       expected to implement anything; the application core does that
 *       (e.g. {@code CreateProjectService implements CreateProjectUseCase}).</li>
 *   <li>{@link PackageRole#ADAPTER} (direction couldn't be determined): the rule
 *       doesn't run for that class — same "not applicable is not a miss" philosophy as
 *       {@link PackageRole#UNKNOWN}.</li>
 * </ul>
 *
 * <p>A supertype/field type naming an external type (e.g. {@code Serializable}) can
 * never match: {@code outputPortNames}/{@code inputPortNames} below are built purely
 * from interfaces actually scanned in this project's port packages, so external names
 * are never candidates in the first place.
 */
@Component
public class DefaultAdapterPortViolationDetector implements AdapterPortViolationDetector {

    @Override
    public List<AdapterSemPortaViolation> detect(
            ProjectScan projectScan, ArchitectureStyle style, Map<PackageName, PackageRole> packageRoles) {
        if (!DefaultArchitectureStyleDetector.HEXAGONAL.equals(style)) {
            return List.of();
        }

        Set<String> outputPortNames = portInterfaceNames(projectScan, packageRoles, PackageRole.OUTPUT_PORT);
        Set<String> inputPortNames = portInterfaceNames(projectScan, packageRoles, PackageRole.INPUT_PORT);

        List<AdapterSemPortaViolation> violations = new ArrayList<>();
        for (PackageScan pkg : projectScan.packages()) {
            PackageName packageName = new PackageName(pkg.name());
            PackageRole role = packageRoles.getOrDefault(packageName, PackageRole.UNKNOWN);

            for (ClassScan cls : pkg.classes()) {
                if (role == PackageRole.DRIVEN_ADAPTER) {
                    if (cls.superTypes().stream().noneMatch(outputPortNames::contains)) {
                        violations.add(new AdapterSemPortaViolation(packageName, cls.name(), role));
                    }
                } else if (role == PackageRole.DRIVING_ADAPTER) {
                    if (cls.fieldTypes().stream().noneMatch(inputPortNames::contains)) {
                        violations.add(new AdapterSemPortaViolation(packageName, cls.name(), role));
                    }
                }
            }
        }
        return violations;
    }

    private Set<String> portInterfaceNames(
            ProjectScan projectScan, Map<PackageName, PackageRole> packageRoles, PackageRole portRole) {
        Set<String> names = new HashSet<>();
        for (PackageScan pkg : projectScan.packages()) {
            PackageName packageName = new PackageName(pkg.name());
            if (packageRoles.getOrDefault(packageName, PackageRole.UNKNOWN) != portRole) {
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
