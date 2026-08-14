package com.wangzimin.now.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CodeQualityRulesTest {

    private static final Path MAIN_SOURCE = Path.of("src", "main", "java");
    private static final double MINIMUM_COMMENT_RATIO = 0.30d;
    private static final List<String> FORBIDDEN_DOMAIN_LITERALS = List.of(
            "\"COMPLETED\"", "'COMPLETED'",
            "\"SKIPPED\"", "'SKIPPED'",
            "\"DELETED\"", "'DELETED'",
            "\"exercise-dataset\"",
            "\"free\"", "\"cycle\""
    );

    @Test
    void productionJavaCommentRatioStaysAtOrAboveThirtyPercent() throws IOException {
        List<String> lines = javaFiles()
                .flatMap(path -> readLines(path).stream())
                .filter(line -> !line.isBlank())
                .toList();
        long commentLines = countCommentLines(lines);
        double ratio = (double) commentLines / lines.size();

        assertTrue(ratio >= MINIMUM_COMMENT_RATIO,
                () -> "生产 Java 注释率为 %.2f%%，低于 30%%".formatted(ratio * 100));
    }

    @Test
    void everyProductionMethodHasAdjacentDocumentation() throws IOException {
        javaFiles().forEach(path -> {
            List<String> lines = readLines(path);
            List<String> undocumented = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (!isMethodDeclarationEnd(line)) continue;
                int declarationStart = findDeclarationStart(lines, index);
                if (declarationStart < 0 || !isMethodStart(lines.get(declarationStart).trim())) continue;
                if (!hasAdjacentComment(lines, declarationStart)) {
                    undocumented.add((index + 1) + ": " + line);
                }
            }
            assertTrue(undocumented.isEmpty(),
                    () -> path + " 存在无注释方法: " + undocumented);
        });
    }

    @Test
    void domainLiteralsOnlyExistInsideDomainEnums() throws IOException {
        javaFiles()
                .filter(path -> !path.toString().contains("domain"))
                .forEach(path -> {
                    String source = readSource(path);
                    FORBIDDEN_DOMAIN_LITERALS.forEach(literal ->
                            assertFalse(source.contains(literal),
                                    () -> path + " 仍包含未枚举化业务值 " + literal));
                });
    }

    private Stream<Path> javaFiles() throws IOException {
        return Files.walk(MAIN_SOURCE).filter(path -> path.toString().endsWith(".java"));
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取源码 " + path, exception);
        }
    }

    private String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取源码 " + path, exception);
        }
    }

    private long countCommentLines(List<String> lines) {
        boolean[] inBlock = { false };
        return lines.stream().filter(line -> {
            String value = line.trim();
            boolean comment = inBlock[0] || value.startsWith("//") || value.startsWith("/*") || value.startsWith("*");
            if (value.contains("/*")) inBlock[0] = true;
            if (value.contains("*/")) inBlock[0] = false;
            return comment;
        }).count();
    }

    private boolean isMethodDeclarationEnd(String line) {
        return line.endsWith("{") && line.contains(")")
                && !line.contains("->") && !line.contains(" catch ")
                && !line.contains(" record ") && !line.startsWith("record ");
    }

    private int findDeclarationStart(List<String> lines, int end) {
        for (int index = end; index >= Math.max(0, end - 8); index--) {
            String line = lines.get(index).trim();
            if (line.contains("(") && !line.startsWith("@")) return index;
            if (line.endsWith(";") || line.equals("}")) return -1;
        }
        return -1;
    }

    private boolean isControlStructure(String line) {
        return Stream.of("if ", "if(", "for ", "for(", "while ", "while(", "switch ", "switch(",
                        "catch ", "catch(", "try ", "else ", "do ")
                .anyMatch(line::startsWith);
    }

    private boolean isMethodStart(String line) {
        if (isControlStructure(line)) return false;
        return line.matches("^(?:(?:public|private|protected|static|final|synchronized)\\s+)*"
                + "(?:[\\w<>?,.\\[\\]]+\\s+)?[A-Za-z_$][\\w$]*\\s*\\(.*$");
    }

    private boolean hasAdjacentComment(List<String> lines, int declarationStart) {
        for (int index = declarationStart - 1; index >= 0; index--) {
            String line = lines.get(index).trim();
            if (line.isBlank() || line.startsWith("@")) continue;
            return line.endsWith("*/") || line.startsWith("//");
        }
        return false;
    }
}
