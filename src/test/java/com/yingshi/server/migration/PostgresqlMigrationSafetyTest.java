package com.yingshi.server.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresqlMigrationSafetyTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration/postgresql");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.*\\.sql$");

    @Test
    void migrationVersionsAreUnique() throws IOException {
        HashSet<String> versions = new HashSet<>();
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            files.map(path -> path.getFileName().toString())
                    .map(VERSION_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .forEach(version -> assertThat(versions.add(version))
                            .as("duplicate Flyway migration version V%s", version)
                            .isTrue());
        }
    }

    @Test
    void smallAlbumMigrationDoesNotClearRuntimeTables() throws IOException {
        String sql = Files.readString(
                MIGRATION_DIR.resolve("V5__hard_cut_small_albums.sql"),
                StandardCharsets.UTF_8
        ).toLowerCase();

        assertThat(sql).doesNotContain("truncate table");
        assertThat(sql).contains("update small_albums");
        assertThat(sql).contains("drop table if exists post_albums");
    }
}
