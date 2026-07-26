package com.company.meetinghelper.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 为集成测试创建并重建专用 PostgreSQL 数据库。
 */
public final class PostgreSqlTestDatabaseInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String TEST_URL = "jdbc:postgresql://localhost:5432/meeting_helper_test";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "123456";
    private static final String TEST_DATABASE = "meeting_helper_test";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        createTestDatabaseIfMissing();
        rebuildTestSchema();
        TestPropertyValues.of(
                "spring.datasource.url=" + TEST_URL,
                "spring.datasource.username=" + USERNAME,
                "spring.datasource.password=" + PASSWORD,
                "spring.datasource.driver-class-name=org.postgresql.Driver"
        ).applyTo(applicationContext);
    }

    private static void createTestDatabaseIfMissing() {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USERNAME, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "select 1 from pg_database where datname = ?"
             )) {
            statement.setString(1, TEST_DATABASE);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }
            try (Statement createStatement = connection.createStatement()) {
                createStatement.execute("create database " + TEST_DATABASE);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法创建 PostgreSQL 自动化测试数据库", exception);
        }
    }

    private static void rebuildTestSchema() {
        try (Connection connection = DriverManager.getConnection(TEST_URL, USERNAME, PASSWORD)) {
            verifyDedicatedTestDatabase(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute("drop schema public cascade");
                statement.execute("create schema public");
            }
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(new FileSystemResource(resolveDdlPath()), "UTF-8")
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("无法重建 PostgreSQL 自动化测试库", exception);
        }
    }

    private static void verifyDedicatedTestDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select current_database()")) {
            if (!resultSet.next() || !TEST_DATABASE.equals(resultSet.getString(1))) {
                throw new IllegalStateException("拒绝清理非专用测试数据库");
            }
        }
    }

    private static Path resolveDdlPath() {
        Path currentDirectory = Path.of("").toAbsolutePath();
        Path directPath = currentDirectory.resolve("DDL/meeting_helper.sql");
        if (Files.isRegularFile(directPath)) {
            return directPath;
        }
        Path parentPath = currentDirectory.resolve("../DDL/meeting_helper.sql").normalize();
        if (Files.isRegularFile(parentPath)) {
            return parentPath;
        }
        throw new IllegalStateException("未找到根目录 DDL/meeting_helper.sql");
    }
}
