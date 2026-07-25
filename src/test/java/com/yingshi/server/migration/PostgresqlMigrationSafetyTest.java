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

    @Test
    void runtimeHardeningMigrationDropsLegacyTrashConstraintBeforeRenamingItemTypes() throws IOException {
        String sql = Files.readString(
                MIGRATION_DIR.resolve("V10__database_runtime_hardening.sql"),
                StandardCharsets.UTF_8
        ).toLowerCase();

        int dropConstraintIndex = sql.indexOf("drop constraint if exists trash_items_item_type_check");
        int updateItemTypeIndex = sql.indexOf("update trash_items");
        int addConstraintIndex = sql.indexOf("add constraint trash_items_item_type_check");

        assertThat(dropConstraintIndex).isGreaterThanOrEqualTo(0);
        assertThat(updateItemTypeIndex).isGreaterThan(dropConstraintIndex);
        assertThat(addConstraintIndex).isGreaterThan(updateItemTypeIndex);
        assertThat(sql).contains("'small_album_deleted'");
    }

    @Test
    void trashSnapshotNormalizationMigrationRepairsLegacyPostSnapshots() throws IOException {
        String sql = Files.readString(
                MIGRATION_DIR.resolve("V11__normalize_trash_snapshots.sql"),
                StandardCharsets.UTF_8
        ).toLowerCase();

        assertThat(sql).contains("json_build_object('smallalbumid', source_small_album_id)");
        assertThat(sql).contains("replace(snapshot_json, '\"postid\":', '\"smallalbumid\":')");
        assertThat(sql).contains("snapshot_json ~ '^[0-9]+$'");
    }

    @Test
    void largeAlbumDirectorySupportMigrationAddsAlbumSoftDeleteAndLargeAlbumTrashType() throws IOException {
        String sql = Files.readString(
                MIGRATION_DIR.resolve("V14__large_album_directory_support.sql"),
                StandardCharsets.UTF_8
        ).toLowerCase();

        assertThat(sql).contains("alter table albums");
        assertThat(sql).contains("add column if not exists deleted_at");
        assertThat(sql).contains("idx_albums_library_deleted_title");
        assertThat(sql).contains("'large_album_deleted'");
    }

    @Test
    void uploadIdempotencyIndexIsScopedToUploader() throws IOException {
        String sql = Files.readString(
                MIGRATION_DIR.resolve("V38__upload_idempotency_and_dedup.sql"),
                StandardCharsets.UTF_8
        ).toLowerCase();

        assertThat(sql).contains("(library_id, uploaded_by_user_id, idempotency_key)");
        assertThat(sql).contains("where idempotency_key is not null");
    }

    @Test
    void authSessionPartialIndexesDoNotUseVolatileCurrentTime() throws IOException {
        String sql = Files.readString(
                MIGRATION_DIR.resolve("V37__audit_log_and_constraints.sql"),
                StandardCharsets.UTF_8
        ).toLowerCase();

        assertThat(sql).doesNotContain("where refresh_expire_at is not null and refresh_expire_at < now()");
    }
}
