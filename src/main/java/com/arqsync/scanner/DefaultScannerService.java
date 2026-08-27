package com.arqsync.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DefaultScannerService implements ScannerService {

    private static final Logger log = LoggerFactory.getLogger(DefaultScannerService.class);

    private static final Set<String> EXCLUDED_DIRECTORIES =
            Set.of("target", "build", ".git", ".idea", "node_modules", "out");

    private final JavaParserAdapter javaParserAdapter;

    public DefaultScannerService(JavaParserAdapter javaParserAdapter) {
        this.javaParserAdapter = javaParserAdapter;
    }

    @Override
    public ProjectScan scan(Path path) {
        if (!Files.isDirectory(path)) {
            throw new InvalidProjectPathException("Path does not exist or is not a directory: " + path);
        }

        List<Path> javaFiles = findJavaFiles(path);

        List<ClassScan> classes = new ArrayList<>();
        List<ScanError> errors = new ArrayList<>();

        for (Path file : javaFiles) {
            ParseOutcome outcome = javaParserAdapter.parse(file);
            switch (outcome) {
                case ParseOutcome.Success success -> classes.addAll(success.classes());
                case ParseOutcome.Failure failure -> {
                    log.warn("Skipped file due to parse error: {} - {}",
                            failure.error().filePath(), failure.error().message());
                    errors.add(failure.error());
                }
            }
        }

        List<PackageScan> packages = aggregateByPackage(classes);

        log.info("Scan complete: {} files processed, {} packages, {} classes, {} errors",
                javaFiles.size(), packages.size(), classes.size(), errors.size());

        return new ProjectScan(path.toString(), packages, errors);
    }

    private List<Path> findJavaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !isInExcludedDirectory(root, file))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk project directory: " + root, e);
        }
    }

    private boolean isInExcludedDirectory(Path root, Path file) {
        for (Path segment : root.relativize(file)) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private List<PackageScan> aggregateByPackage(List<ClassScan> classes) {
        return classes.stream()
                .collect(Collectors.groupingBy(ClassScan::packageName, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new PackageScan(entry.getKey(), entry.getValue()))
                .toList();
    }
}
