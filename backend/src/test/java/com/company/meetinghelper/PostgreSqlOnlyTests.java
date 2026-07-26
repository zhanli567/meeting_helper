package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostgreSqlOnlyTests {

    @Test
    void automatedTestsUseDedicatedPostgreSqlDatabase() throws IOException {
        Path backendDirectory = Path.of("").toAbsolutePath();
        String pom = Files.readString(backendDirectory.resolve("pom.xml"));
        String testConfiguration = Files.readString(
                backendDirectory.resolve("src/test/resources/application.yml")
        );

        assertThat(pom).doesNotContain("com.h2database");
        assertThat(testConfiguration)
                .contains("jdbc:postgresql://localhost:5432/meeting_helper_test")
                .doesNotContain("jdbc:h2:");
    }
}
