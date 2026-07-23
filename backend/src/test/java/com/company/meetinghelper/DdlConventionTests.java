package com.company.meetinghelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DdlConventionTests {

    private static final Pattern CREATE_TABLE_PATTERN =
            Pattern.compile(
                    "^create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-zA-Z0-9_]+)",
                    Pattern.CASE_INSENSITIVE);

    @Test
    void sqlFilesStayInRootDdlAndOnlyCreatePrefixedTables() throws IOException {
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
            for (String rawStatement : sql.split(";")) {
                String statement = rawStatement.trim();
                if (statement.isEmpty()) {
                    continue;
                }
                Matcher matcher = CREATE_TABLE_PATTERN.matcher(statement);
                assertTrue(matcher.find(), () -> sqlFile + " 只允许包含 CREATE TABLE 语句");
                assertTrue(
                        matcher.group(1).toLowerCase(Locale.ROOT).startsWith("t_"),
                        () -> sqlFile + " 中的表名必须以 t_ 开头");
            }
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
