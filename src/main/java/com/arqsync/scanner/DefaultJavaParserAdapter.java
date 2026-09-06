package com.arqsync.scanner;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithExtends;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.nodeTypes.NodeWithMembers;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
                    .map((TypeDeclaration<?> type) -> new ClassScan(
                            type.getNameAsString(), packageName, imports,
                            superTypesOf(type), isInterface(type), fieldTypesOf(type)))
                    .toList();

            return new ParseOutcome.Success(classes);
        } catch (ParseProblemException | IOException e) {
            return new ParseOutcome.Failure(new ScanError(file.toString(), e.getMessage()));
        }
    }

    /**
     * Names declared in {@code extends}/{@code implements} for this type
     * (ADENDO-SPEC-scanner-supertypes.md, 2.1) — simple names, exactly as
     * written in the source (not resolved to a fully-qualified type).
     * {@code NodeWithExtends}/{@code NodeWithImplements} cover
     * classes/interfaces (both clauses), enums and records (implements only) —
     * annotation declarations have neither and yield an empty list.
     */
    private List<String> superTypesOf(TypeDeclaration<?> type) {
        List<String> superTypes = new ArrayList<>();
        if (type instanceof NodeWithExtends<?> withExtends) {
            for (ClassOrInterfaceType extended : withExtends.getExtendedTypes()) {
                superTypes.add(extended.getNameAsString());
            }
        }
        if (type instanceof NodeWithImplements<?> withImplements) {
            for (ClassOrInterfaceType implemented : withImplements.getImplementedTypes()) {
                superTypes.add(implemented.getNameAsString());
            }
        }
        return superTypes;
    }

    private boolean isInterface(TypeDeclaration<?> type) {
        return type instanceof ClassOrInterfaceDeclaration classOrInterface && classOrInterface.isInterface();
    }

    /**
     * Declared types (simple names) of this type's non-static instance fields
     * (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 2.5) — static fields (constants,
     * loggers) are never DI-injected dependencies, so they're excluded. Only
     * {@code ClassOrInterfaceType} fields are captured, the same restriction
     * {@code superTypesOf} implicitly has — a primitive or array field can never name a
     * port.
     */
    private List<String> fieldTypesOf(TypeDeclaration<?> type) {
        List<String> fieldTypes = new ArrayList<>();
        if (type instanceof NodeWithMembers<?> withMembers) {
            for (var member : withMembers.getMembers()) {
                if (member instanceof FieldDeclaration field && !field.isStatic()) {
                    for (VariableDeclarator variable : field.getVariables()) {
                        if (variable.getType() instanceof ClassOrInterfaceType classType) {
                            fieldTypes.add(classType.getNameAsString());
                        }
                    }
                }
            }
        }
        return fieldTypes;
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
