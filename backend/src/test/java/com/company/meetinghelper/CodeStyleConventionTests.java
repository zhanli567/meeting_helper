package com.company.meetinghelper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CodeStyleConventionTests {

    private static final Path MAIN_SOURCE = Path.of("src/main/java");
    private static final Pattern IF_PATTERN = Pattern.compile("\\bif\\s*\\(");
    private static final Pattern SWITCH_PATTERN = Pattern.compile("\\bswitch\\s*\\(");
    private static final Pattern PUBLIC_OR_PROTECTED_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]*(?:@[\\w.()={} ,\"\\-]+[ \\t]*\\R[ \\t]*)*"
                    + "(public|protected)\\s+"
                    + "(?:static\\s+|final\\s+|abstract\\s+|synchronized\\s+|native\\s+|strictfp\\s+)*"
                    + "(class|interface|enum|record|[\\w<>\\[\\], ?]+\\s+[A-Za-z_]\\w*\\s*\\()");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]*(?!"
                    + "(?:@[\\w.()={} ,\"\\-]+[ \\t]*\\R[ \\t]*)*"
                    + "(?:public|protected|private)?\\s*"
                    + "(?:static\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(?:class|interface|enum|record)\\b)"
                    + "(?:@[\\w.()={} ,\"\\-]+[ \\t]*\\R[ \\t]*)*"
                    + "(?:(public|protected|private)\\s+)?"
                    + "(?:static\\s+|final\\s+|abstract\\s+|synchronized\\s+|native\\s+|strictfp\\s+)*"
                    + "(?!class\\b|interface\\b|enum\\b|record\\b)"
                    + "([\\w<>\\[\\], ?]+)\\s+([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("\\bdefault\\s*(?::|->)");

    @Test
    void backendCodeStyleFollowsProjectConventions() throws IOException {
        List<String> issues = new ArrayList<>();
        for (Path file : javaFiles()) {
            String source = Files.readString(file);
            String code = stripCommentsAndText(source);
            issues.addAll(ifBraceIssues(file, code));
            issues.addAll(elseIfIssues(file, code));
            issues.addAll(switchDefaultIssues(file, code));
            issues.addAll(javadocIssues(file, source, code));
            issues.addAll(methodSizeIssues(file, code));
        }

        assertTrue(issues.isEmpty(), String.join(System.lineSeparator(), issues));
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(MAIN_SOURCE)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static List<String> ifBraceIssues(Path file, String code) {
        List<String> issues = new ArrayList<>();
        Matcher matcher = IF_PATTERN.matcher(code);
        while (matcher.find()) {
            int open = code.indexOf('(', matcher.start());
            int close = matching(code, open, '(', ')');
            int bodyStart = nextCodeIndex(code, close + 1);
            if (bodyStart >= code.length() || code.charAt(bodyStart) != '{') {
                issues.add(location(file, code, matcher.start(), "if statement must use braces"));
            }
        }
        return issues;
    }

    private static List<String> elseIfIssues(Path file, String code) {
        List<String> issues = new ArrayList<>();
        int cursor = 0;
        while ((cursor = code.indexOf("else if", cursor)) >= 0) {
            int chainCursor = cursor;
            int blockEnd = -1;
            while (code.startsWith("else if", chainCursor)) {
                int open = code.indexOf('(', chainCursor);
                int close = matching(code, open, '(', ')');
                int bodyStart = nextCodeIndex(code, close + 1);
                if (bodyStart >= code.length() || code.charAt(bodyStart) != '{') {
                    break;
                }
                blockEnd = matching(code, bodyStart, '{', '}') + 1;
                chainCursor = nextCodeIndex(code, blockEnd);
            }
            if (blockEnd > 0 && !code.startsWith("else", chainCursor)) {
                issues.add(location(file, code, cursor, "else-if chain must end with else"));
            }
            cursor += "else if".length();
        }
        return issues;
    }

    private static List<String> switchDefaultIssues(Path file, String code) {
        List<String> issues = new ArrayList<>();
        Matcher matcher = SWITCH_PATTERN.matcher(code);
        while (matcher.find()) {
            int open = code.indexOf('(', matcher.start());
            int close = matching(code, open, '(', ')');
            int bodyStart = nextCodeIndex(code, close + 1);
            int bodyEnd = matching(code, bodyStart, '{', '}');
            if (bodyStart >= 0 && bodyEnd >= 0 && !DEFAULT_PATTERN.matcher(code.substring(bodyStart, bodyEnd)).find()) {
                issues.add(location(file, code, matcher.start(), "switch statement must include default"));
            }
        }
        return issues;
    }

    private static List<String> javadocIssues(Path file, String source, String code) {
        List<String> issues = new ArrayList<>();
        Matcher matcher = PUBLIC_OR_PROTECTED_DECLARATION.matcher(code);
        while (matcher.find()) {
            int declarationStart = matcher.start(1);
            if (source.substring(matcher.start(), declarationStart).contains("@Override")) {
                continue;
            }
            if (!hasJavadocBefore(source, declarationStart)) {
                issues.add(location(file, source, declarationStart, "public/protected element is missing Javadoc"));
                continue;
            }
            String javadoc = javadocBefore(source, declarationStart);
            if (matcher.group(2).startsWith("record")) {
                issues.addAll(recordJavadocIssues(file, source, declarationStart, javadoc));
            } else if (matcher.group(2).endsWith("(")) {
                issues.addAll(methodJavadocIssues(file, source, declarationStart, javadoc));
            }
        }
        return issues;
    }

    private static List<String> methodSizeIssues(Path file, String code) {
        List<String> issues = new ArrayList<>();
        Matcher matcher = METHOD_DECLARATION.matcher(code);
        while (matcher.find()) {
            String visibility = matcher.group(1);
            if (!"public".equals(visibility) && !"protected".equals(visibility)) {
                continue;
            }
            if (code.substring(matcher.start(), matcher.end()).contains("@Override")
                    || hasAnnotationBefore(code, matcher.start(), "@Override")) {
                continue;
            }
            int declarationStart = matcher.start(2);
            int openParen = code.indexOf('(', declarationStart);
            if (isTypeDeclarationLine(code, declarationStart, openParen)) {
                continue;
            }
            if (isConstructor(file, matcher.group(3))) {
                continue;
            }
            if (isControlKeyword(matcher.group(3))) {
                continue;
            }
            int closeParen = matching(code, openParen, '(', ')');
            int bodyStart = nextCodeIndex(code, closeParen + 1);
            if (bodyStart < code.length() && code.charAt(bodyStart) == '{') {
                int paramCount = parameters(code.substring(openParen + 1, closeParen)).size();
                if (paramCount > 5) {
                    issues.add(location(file, code, declarationStart, "method parameter count must not exceed 5"));
                }
                int bodyEnd = matching(code, bodyStart, '{', '}');
                int lineCount = effectiveBodyLineCount(code.substring(bodyStart + 1, bodyEnd));
                if (lineCount > 50) {
                    issues.add(location(file, code, declarationStart, "method length must not exceed 50 lines"));
                }
            }
        }
        return issues;
    }

    private static boolean isTypeDeclarationLine(String code, int declarationStart, int openParen) {
        int lineStart = code.lastIndexOf('\n', Math.max(0, declarationStart - 1)) + 1;
        String declaration = code.substring(lineStart, openParen);
        return Pattern.compile("\\b(class|interface|enum|record)\\b").matcher(declaration).find();
    }

    private static boolean isConstructor(Path file, String methodName) {
        String fileName = file.getFileName().toString();
        String className = fileName.substring(0, fileName.length() - ".java".length());
        return className.equals(methodName);
    }

    private static boolean isControlKeyword(String methodName) {
        return List.of("catch", "for", "if", "switch", "try", "while").contains(methodName);
    }

    private static int effectiveBodyLineCount(String methodBody) {
        int count = 0;
        for (String line : methodBody.split("\\R")) {
            if (!line.trim().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasAnnotationBefore(String source, int declarationStart, String annotation) {
        String before = source.substring(0, declarationStart);
        int cursor = before.length();
        while (cursor > 0 && Character.isWhitespace(before.charAt(cursor - 1))) {
            cursor--;
        }
        while (cursor > 0 && annotationLineBefore(before, cursor)) {
            int lineStart = annotationStartBefore(before, cursor);
            if (before.substring(lineStart, cursor).trim().startsWith(annotation)) {
                return true;
            }
            cursor = lineStart;
            while (cursor > 0 && Character.isWhitespace(before.charAt(cursor - 1))) {
                cursor--;
            }
        }
        return false;
    }

    private static List<String> recordJavadocIssues(Path file, String source, int declarationStart, String javadoc) {
        int open = source.indexOf('(', declarationStart);
        int close = matching(source, open, '(', ')');
        return parameterJavadocIssues(file, source, declarationStart, javadoc, source.substring(open + 1, close));
    }

    private static List<String> methodJavadocIssues(Path file, String source, int declarationStart, String javadoc) {
        int open = source.indexOf('(', declarationStart);
        int close = matching(source, open, '(', ')');
        List<String> issues = parameterJavadocIssues(
                file,
                source,
                declarationStart,
                javadoc,
                source.substring(open + 1, close));
        String declaration = source.substring(declarationStart, open);
        if (!declaration.contains(" void ") && !declaration.matches("(?s).*\\s+[A-Z][A-Za-z0-9_]*$")) {
            if (!javadoc.contains("@return")) {
                issues.add(location(file, source, declarationStart, "non-void method Javadoc is missing @return"));
            }
        }
        return issues;
    }

    private static List<String> parameterJavadocIssues(
            Path file,
            String source,
            int declarationStart,
            String javadoc,
            String rawParameters) {
        List<String> issues = new ArrayList<>();
        for (String parameter : parameters(rawParameters)) {
            String name = parameterName(parameter);
            if (!name.isBlank() && !javadoc.contains("@param " + name)) {
                issues.add(location(file, source, declarationStart, "Javadoc is missing @param " + name));
            }
        }
        return issues;
    }

    private static List<String> parameters(String rawParameters) {
        List<String> parameters = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int index = 0; index < rawParameters.length(); index++) {
            char current = rawParameters.charAt(index);
            if (current == '<' || current == '(' || current == '[') {
                depth++;
            } else if (current == '>' || current == ')' || current == ']') {
                depth--;
            } else if (current == ',' && depth == 0) {
                addParameter(parameters, rawParameters.substring(start, index));
                start = index + 1;
            }
        }
        addParameter(parameters, rawParameters.substring(start));
        return parameters;
    }

    private static void addParameter(List<String> parameters, String rawParameter) {
        String trimmed = rawParameter.trim();
        if (!trimmed.isBlank()) {
            parameters.add(trimmed);
        }
    }

    private static String parameterName(String rawParameter) {
        String cleaned = rawParameter
                .replaceAll("@\\w+(?:\\([^)]*\\))?\\s*", "")
                .replace("final ", "")
                .replace("...", " ")
                .trim();
        String[] parts = cleaned.split("\\s+");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private static boolean hasJavadocBefore(String source, int declarationStart) {
        return !javadocBefore(source, declarationStart).isBlank();
    }

    private static String javadocBefore(String source, int declarationStart) {
        String before = source.substring(0, declarationStart);
        int cursor = before.length();
        while (cursor > 0 && Character.isWhitespace(before.charAt(cursor - 1))) {
            cursor--;
        }
        while (cursor > 0 && annotationLineBefore(before, cursor)) {
            cursor = annotationStartBefore(before, cursor);
            while (cursor > 0 && Character.isWhitespace(before.charAt(cursor - 1))) {
                cursor--;
            }
        }
        if (cursor <= 1 || before.charAt(cursor - 1) != '/' || before.charAt(cursor - 2) != '*') {
            return "";
        }
        int start = before.lastIndexOf("/**", cursor - 2);
        return start < 0 ? "" : before.substring(start, cursor);
    }

    private static boolean annotationLineBefore(String source, int cursor) {
        int lineStart = source.lastIndexOf('\n', Math.max(0, cursor - 1)) + 1;
        return source.substring(lineStart, cursor).trim().startsWith("@");
    }

    private static int annotationStartBefore(String source, int cursor) {
        return source.lastIndexOf('\n', Math.max(0, cursor - 1)) + 1;
    }

    private static String stripCommentsAndText(String source) {
        StringBuilder result = new StringBuilder(source.length());
        String state = "code";
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if ("code".equals(state)) {
                if (current == '/' && next == '/') {
                    state = "line";
                    result.append("  ");
                    index++;
                } else if (current == '/' && next == '*') {
                    state = "block";
                    result.append("  ");
                    index++;
                } else if (current == '"' || current == '\'') {
                    state = String.valueOf(current);
                    result.append(' ');
                } else {
                    result.append(current);
                }
            } else if ("line".equals(state)) {
                if (current == '\n') {
                    state = "code";
                    result.append('\n');
                } else {
                    result.append(' ');
                }
            } else if ("block".equals(state)) {
                if (current == '*' && next == '/') {
                    state = "code";
                    result.append("  ");
                    index++;
                } else {
                    result.append(current == '\n' ? '\n' : ' ');
                }
            } else if (current == '\\') {
                result.append("  ");
                index++;
            } else if (String.valueOf(current).equals(state)) {
                state = "code";
                result.append(' ');
            } else {
                result.append(current == '\n' ? '\n' : ' ');
            }
        }
        return result.toString();
    }

    private static int matching(String source, int start, char open, char close) {
        int depth = 0;
        for (int index = start; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int nextCodeIndex(String source, int index) {
        int cursor = index;
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static int lineOf(String source, int index) {
        int line = 1;
        for (int cursor = 0; cursor < Math.max(0, index); cursor++) {
            if (source.charAt(cursor) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String location(Path file, String source, int index, String message) {
        return MAIN_SOURCE.relativize(file) + ":" + lineOf(source, index) + " " + message;
    }
}
