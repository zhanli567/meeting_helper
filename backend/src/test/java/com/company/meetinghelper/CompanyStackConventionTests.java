package com.company.meetinghelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CompanyStackConventionTests {

    @Test
    void backendUsesMyBatisPlusAndPostgreSqlWithoutJpaOrH2() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).contains("mybatis-plus-spring-boot3-starter");
        assertThat(pom).contains("org.postgresql");
        assertThat(pom).doesNotContain("spring-boot-starter-data-jpa");
        assertThat(pom).doesNotContain("com.h2database");
    }

    @Test
    void controllersOnlyDeclareGetAndPostRoutes() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            String controllerSources = files
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .map(this::read)
                    .reduce("", String::concat);

            assertThat(controllerSources).doesNotContain("@PutMapping");
            assertThat(controllerSources).doesNotContain("@DeleteMapping");
            assertThat(controllerSources).doesNotContain("@PatchMapping");
        }
    }

    @Test
    void productionCodeHasNoDemoInitializerOrManualUserHeader() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            String productionSources = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", String::concat);

            assertThat(productionSources).doesNotContain("DemoDataInitializer");
            assertThat(productionSources).doesNotContain("X-User-Id");
            assertThat(productionSources).doesNotContain("CurrentUserProvider");
            assertThat(productionSources).contains("CurrentUserHolder");
        }
    }

    @Test
    void javaSourcesUseExplicitLocalVariableTypes() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src"))) {
            String javaSources = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", String::concat);

            assertThat(javaSources).doesNotContainPattern("\\bvar\\s+");
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取源码：" + path, exception);
        }
    }
}
