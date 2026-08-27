package com.arqsync.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultJavaParserAdapterTest {

    private final JavaParserAdapter adapter = new DefaultJavaParserAdapter();

    @Test
    void parsesValidFileWithPackageAndImports(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("OrderController.java");
        Files.writeString(file, """
                package com.acme.controller;

                import com.acme.service.OrderService;

                public class OrderController {
                }
                """);

        ParseOutcome outcome = adapter.parse(file);

        assertThat(outcome).isInstanceOf(ParseOutcome.Success.class);
        List<ClassScan> classes = ((ParseOutcome.Success) outcome).classes();
        assertThat(classes).hasSize(1);
        ClassScan classScan = classes.get(0);
        assertThat(classScan.name()).isEqualTo("OrderController");
        assertThat(classScan.packageName()).isEqualTo("com.acme.controller");
        assertThat(classScan.imports()).containsExactly("com.acme.service.OrderService");
    }

    @Test
    void returnsFailureForSyntaxError(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Broken.java");
        Files.writeString(file, """
                package com.acme;

                public class Broken {
                    public void method(
                """);

        ParseOutcome outcome = adapter.parse(file);

        assertThat(outcome).isInstanceOf(ParseOutcome.Failure.class);
        ScanError error = ((ParseOutcome.Failure) outcome).error();
        assertThat(error.filePath()).isEqualTo(file.toString());
        assertThat(error.message()).isNotBlank();
    }

    @Test
    void defaultsToEmptyPackageWhenDeclarationMissing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("NoPackage.java");
        Files.writeString(file, """
                public class NoPackage {
                }
                """);

        ParseOutcome outcome = adapter.parse(file);

        assertThat(outcome).isInstanceOf(ParseOutcome.Success.class);
        ClassScan classScan = ((ParseOutcome.Success) outcome).classes().get(0);
        assertThat(classScan.packageName()).isEmpty();
    }

    @Test
    void extractsMultipleTopLevelTypesSharingTheSameImports(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Multi.java");
        Files.writeString(file, """
                package com.acme;

                import java.util.List;

                public class Multi {
                }

                class Helper {
                }
                """);

        ParseOutcome outcome = adapter.parse(file);

        List<ClassScan> classes = ((ParseOutcome.Success) outcome).classes();
        assertThat(classes).hasSize(2);
        assertThat(classes).extracting(ClassScan::name).containsExactlyInAnyOrder("Multi", "Helper");
        assertThat(classes).allSatisfy(c -> assertThat(c.imports()).containsExactly("java.util.List"));
    }

    @Test
    void doesNotExtractNestedTypesAsSeparateClassScans(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Outer.java");
        Files.writeString(file, """
                package com.acme;

                public class Outer {
                    static class Inner {
                    }
                }
                """);

        ParseOutcome outcome = adapter.parse(file);

        List<ClassScan> classes = ((ParseOutcome.Success) outcome).classes();
        assertThat(classes).hasSize(1);
        assertThat(classes.get(0).name()).isEqualTo("Outer");
    }

    @Test
    void capturesWildcardAndStaticImportsAsRawText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Imports.java");
        Files.writeString(file, """
                package com.acme;

                import com.acme.other.*;
                import static com.acme.util.Constants.MAX_VALUE;

                public class Imports {
                }
                """);

        ParseOutcome outcome = adapter.parse(file);

        List<String> imports = ((ParseOutcome.Success) outcome).classes().get(0).imports();
        assertThat(imports).containsExactlyInAnyOrder(
                "com.acme.other.*",
                "static com.acme.util.Constants.MAX_VALUE"
        );
    }

    @Test
    void parsesModernJavaSyntax(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Modern.java");
        Files.writeString(file, """
                package com.acme;

                public sealed interface Shape permits Circle {
                }

                record Circle(double radius) implements Shape {
                }
                """);

        ParseOutcome outcome = adapter.parse(file);

        assertThat(outcome).isInstanceOf(ParseOutcome.Success.class);
        assertThat(((ParseOutcome.Success) outcome).classes()).hasSize(2);
    }
}
