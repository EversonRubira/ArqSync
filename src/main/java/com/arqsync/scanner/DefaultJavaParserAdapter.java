package com.arqsync.scanner;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Component
public class DefaultJavaParserAdapter implements JavaParserAdapter {

    static {
        // Target projects may use modern syntax (records, sealed classes) — PRD risk table.
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    @Override
    public ParseOutcome parse(Path file) {
        try {
            CompilationUnit compilationUnit = StaticJavaParser.parse(file);

            String packageName = compilationUnit.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            List<String> imports = compilationUnit.getImports().stream()
                    .map(this::formatImport)
                    .toList();

            List<ClassScan> classes = compilationUnit.getTypes().stream()
                    .map((TypeDeclaration<?> type) -> new ClassScan(type.getNameAsString(), packageName, imports))
                    .toList();

            return new ParseOutcome.Success(classes);
        } catch (ParseProblemException | IOException e) {
            return new ParseOutcome.Failure(new ScanError(file.toString(), e.getMessage()));
        }
    }

    private String formatImport(ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        if (importDeclaration.isAsterisk()) {
            name = name + ".*";
        }
        if (importDeclaration.isStatic()) {
            name = "static " + name;
        }
        return name;
    }
}
