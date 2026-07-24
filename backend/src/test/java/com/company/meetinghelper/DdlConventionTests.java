package com.company.meetinghelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DdlConventionTests {

    private static final Pattern CREATE_TABLE_PATTERN =
            Pattern.compile(
                    "^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-zA-Z0-9_]+)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN =
            Pattern.compile(
                    "^comment\\s+on\\s+(table|column)\\s+([a-zA-Z0-9_]+)"
                            + "(?:\\.([a-zA-Z0-9_]+))?\\s+is\\s+'(?:''|[^'])+'$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void sqlFilesStayInRootDdlAndOnlyCreatePrefixedTablesWithCompleteComments() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path ddlDirectory = repositoryRoot.resolve("DDL").normalize();

        List<Path> sqlFiles;
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            sqlFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .filter(path -> !isGeneratedDirectory(repositoryRoot.relativize(path)))
                    .toList();
        }

        assertFalse(sqlFiles.isEmpty(), "根目录 DDL 文件夹至少应包含一个 SQL 文件");
        assertEquals(1, sqlFiles.size(), "仓库只能保留一份数据库建表 SQL");
        assertEquals(
                ddlDirectory.resolve("meeting_helper.sql"),
                sqlFiles.getFirst().normalize(),
                "数据库建表脚本必须命名为 DDL/meeting_helper.sql");
        assertTrue(
                sqlFiles.stream().allMatch(path -> path.normalize().startsWith(ddlDirectory)),
                "所有 SQL 源文件必须统一放在根目录 DDL 文件夹中");

        for (Path sqlFile : sqlFiles) {
            String sql = Files.readString(sqlFile);
            Set<String> createdTables = new HashSet<>();
            Set<String> expectedColumnComments = new HashSet<>();
            Set<String> tableComments = new HashSet<>();
            Set<String> columnComments = new HashSet<>();
            for (String rawStatement : sql.split(";")) {
                String statement = rawStatement.trim();
                if (statement.isEmpty()) {
                    continue;
                }
                Matcher createMatcher = CREATE_TABLE_PATTERN.matcher(statement);
                if (createMatcher.find()) {
                    String tableName = createMatcher.group(1).toLowerCase(Locale.ROOT);
                    assertTrue(
                            tableName.startsWith("t_"),
                            () -> sqlFile + " 中的表名必须以 t_ 开头");
                    createdTables.add(tableName);
                    collectExpectedColumnComments(statement, tableName, expectedColumnComments);
                    continue;
                }

                Matcher commentMatcher = COMMENT_PATTERN.matcher(statement);
                assertTrue(
                        commentMatcher.matches(),
                        () -> sqlFile + " 只允许包含 CREATE TABLE、COMMENT ON TABLE 和 COMMENT ON COLUMN 语句");
                String objectType = commentMatcher.group(1).toLowerCase(Locale.ROOT);
                String tableName = commentMatcher.group(2).toLowerCase(Locale.ROOT);
                String columnName = commentMatcher.group(3);
                assertTrue(
                        tableName.startsWith("t_"),
                        () -> sqlFile + " 注释引用的表名必须以 t_ 开头");
                if (objectType.equals("table")) {
                    assertTrue(columnName == null, "表注释不能包含字段名");
                    tableComments.add(tableName);
                } else {
                    assertTrue(columnName != null, "字段注释必须包含字段名");
                    columnComments.add(tableName + "." + columnName.toLowerCase(Locale.ROOT));
                }
            }
            assertEquals(createdTables, tableComments, "每张表都必须包含 COMMENT ON TABLE");
            assertEquals(expectedColumnComments, columnComments, "每个字段都必须包含 COMMENT ON COLUMN");
        }
    }

    private void collectExpectedColumnComments(
            String createStatement,
            String tableName,
            Set<String> expectedColumnComments
    ) {
        int openingParenthesis = createStatement.indexOf('(');
        int closingParenthesis = createStatement.lastIndexOf(')');
        assertTrue(openingParenthesis > 0 && closingParenthesis > openingParenthesis, "CREATE TABLE 结构不完整");
        String columnBlock = createStatement.substring(openingParenthesis + 1, closingParenthesis);
        for (String rawLine : columnBlock.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.toLowerCase(Locale.ROOT).startsWith("constraint ")) {
                continue;
            }
            String columnName = line.split("\\s+", 2)[0].replace(",", "");
            expectedColumnComments.add(tableName + "." + columnName.toLowerCase(Locale.ROOT));
        }
    }

    private Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("DDL"))) {
            return current;
        }
        if (Files.isDirectory(current.resolve("../DDL"))) {
            return current.resolve("..").normalize();
        }
        throw new IllegalStateException("无法定位仓库根目录 DDL 文件夹");
    }

    private boolean isGeneratedDirectory(Path relativePath) {
        for (Path part : relativePath) {
            String name = part.toString();
            if (name.equals(".git")
                    || name.equals("target")
                    || name.equals("node_modules")
                    || name.equals("dist")) {
                return true;
            }
        }
        return false;
    }
}
