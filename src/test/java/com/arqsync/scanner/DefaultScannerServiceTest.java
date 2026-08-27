package com.arqsync.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultScannerServiceTest {

    private final ScannerService scannerService = new DefaultScannerService(new DefaultJavaParserAdapter());

    private Path fixture(String name) throws URISyntaxException {
        URL resource = Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/scanner/" + name),
                "Fixture not found: " + name
        );
        return Paths.get(resource.toURI());
    }

    @Test
    void scansValidProjectWithMultiplePackages() throws URISyntaxException {
        ProjectScan result = scannerService.scan(fixture("valid-project"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.packages()).hasSize(3);
        assertThat(result.packages())
                .extracting(PackageScan::name)
                .containsExactlyInAnyOrder("com.acme.controller", "com.acme.service", "com.acme.repository");
    }

    @Test
    void continuesScanningAfterSyntaxError() throws URISyntaxException {
        ProjectScan result = scannerService.scan(fixture("syntax-error"));

        assertThat(result.errors()).hasSize(1);
        assertThat(result.packages()).hasSize(1);
        assertThat(result.packages().get(0).classes())
                .extracting(ClassScan::name)
                .containsExactly("Valid");
    }

    @Test
    void extractsAllTopLevelClassesFromAMultiClassFile() throws URISyntaxException {
        ProjectScan result = scannerService.scan(fixture("multiple-classes-per-file"));

        assertThat(result.packages()).hasSize(1);
        assertThat(result.packages().get(0).classes())
                .extracting(ClassScan::name)
                .containsExactlyInAnyOrder("Multi", "Helper");
    }

    @Test
    void mapsMissingPackageDeclarationToEmptyString() throws URISyntaxException {
        ProjectScan result = scannerService.scan(fixture("default-package"));

        assertThat(result.packages()).hasSize(1);
        assertThat(result.packages().get(0).name()).isEmpty();
    }

    @Test
    void ignoresFilesInDenylistedDirectories(@TempDir Path tempDir) throws IOException {
        // A literal ".git" directory can't be committed as a fixture (git treats it as a
        // repository boundary, not an ordinary directory), so this scenario is built here
        // instead of under src/test/resources/fixtures/scanner/.
        writeJavaFile(tempDir.resolve("src/com/acme/Real.java"), "com.acme", "Real");
        for (String excludedDir : List.of("target", "build", ".git", ".idea", "node_modules", "out")) {
            writeJavaFile(tempDir.resolve(excludedDir + "/com/acme/Generated.java"), "com.acme", "Generated");
        }

        ProjectScan result = scannerService.scan(tempDir);

        assertThat(result.packages()).hasSize(1);
        assertThat(result.packages().get(0).classes())
                .extracting(ClassScan::name)
                .containsExactly("Real");
    }

    private void writeJavaFile(Path file, String packageName, String className) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package %s;\n\npublic class %s {\n}\n".formatted(packageName, className));
    }

    @Test
    void handlesEmptyProjectWithoutError() throws URISyntaxException {
        ProjectScan result = scannerService.scan(fixture("empty-project"));

        assertThat(result.packages()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void throwsForNonExistentPath() {
        Path missing = Path.of("this/path/does/not/exist");

        assertThatThrownBy(() -> scannerService.scan(missing))
                .isInstanceOf(InvalidProjectPathException.class);
    }

    @Test
    void throwsWhenPathIsNotADirectory(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "not a directory");

        assertThatThrownBy(() -> scannerService.scan(file))
                .isInstanceOf(InvalidProjectPathException.class);
    }
}
