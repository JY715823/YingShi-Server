package com.yingshi.server.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the behaviour of {@code V24__foreign_key_constraints.sql}.
 * <p>
 * V24 introduces referential integrity (FOREIGN KEY constraints) for the most
 * critical entity relationships. It uses {@code ON DELETE CASCADE} for strong
 * ownership (library-scoped tables, small_album_media, ledger internals) and
 * {@code ON DELETE SET NULL} for optional references (e.g.
 * {@code comments.small_album_id}, {@code comments.media_id},
 * {@code ledger_transactions.category_id/account_id/to_account_id},
 * {@code ledger_deleted_items.book_id}, {@code upload_tasks.media_id}).
 * <p>
 * <b>Important:</b> V24 only issues {@code ALTER TABLE ADD CONSTRAINT}. It
 * performs <em>no</em> orphan-data cleanup, so any pre-existing row whose FK
 * column points at a non-existent parent will cause the constraint creation to
 * fail with a {@link FlywayException} (PostgreSQL validates existing rows when
 * a constraint is added, regardless of the {@code ON DELETE} action). These
 * tests document that behaviour and verify the CASCADE / SET NULL semantics on
 * post-migration deletes.
 * <p>
 * A real PostgreSQL 16 container is used together with Flyway's programmatic
 * API ({@code target} version) so migrations can be paused at V23, test data
 * inserted, and V24 then applied. This deliberately does <em>not</em> extend
 * {@code AbstractPostgresIntegrationTest}, which boots the full Spring context
 * and auto-runs every migration up to the latest version — incompatible with
 * the need to stop at V23 and inject data before running V24.
 */
@Testcontainers
class V24OrphanDataMigrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration/postgresql";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("yingshi_v24_test")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void resetSchema() {
        // Reset state between tests so the shared container starts each test clean.
        try (Connection conn = openConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS public CASCADE");
            st.execute("CREATE SCHEMA public");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset schema", e);
        }
    }

    // ---- Clean-data scenarios: V24 must succeed ----

    @Test
    void v24MigrationSucceedsWithCleanReferencedData() {
        migrateTo("23");
        insertCleanReferencedRows();

        assertThatCode(() -> migrateTo("24")).doesNotThrowAnyException();

        // Spot-check a representative constraint from each strategy was created.
        assertThat(constraintExists("fk_albums_library")).isTrue();         // CASCADE
        assertThat(constraintExists("fk_comments_small_album")).isTrue();    // SET NULL
        assertThat(constraintExists("fk_small_album_media_media")).isTrue(); // CASCADE
        assertThat(appliedMigrationVersion("24")).isTrue();
    }

    @Test
    void v24CascadeDeletesLibraryOwnedRowsWhenLibraryDeleted() {
        migrateTo("23");
        // lib-1 owns album-1, sa-1, media-1, sam-1 (sam-1 references sa-1 + media-1).
        insertCleanReferencedRows();
        migrateTo("24");

        // Deleting the library should CASCADE to albums, small_albums and media,
        // and consequently to small_album_media (via small_albums/media cascade).
        exec("DELETE FROM shared_libraries WHERE id = 'lib-1'");

        assertThat(rowExists("albums", "album-1")).isFalse();
        assertThat(rowExists("small_albums", "sa-1")).isFalse();
        assertThat(rowExists("media", "media-1")).isFalse();
        assertThat(rowExists("small_album_media", "sam-1")).isFalse();
    }

    @Test
    void v24SetsNullOnOptionalCommentReferencesWhenTargetDeleted() {
        migrateTo("23");
        insertLibrary("lib-1");
        insertAlbum("album-1", "lib-1");
        insertSmallAlbum("sa-1", "lib-1", "album-1");
        insertMedia("media-1", "lib-1");
        insertComment("comment-1", "lib-1", "sa-1", "media-1");
        migrateTo("24");

        // Delete the referenced small_album -> comments.small_album_id SET NULL.
        exec("DELETE FROM small_albums WHERE id = 'sa-1'");
        assertThat(columnValue("comments", "comment-1", "small_album_id")).isNull();

        // Delete the referenced media -> comments.media_id SET NULL.
        exec("DELETE FROM media WHERE id = 'media-1'");
        assertThat(columnValue("comments", "comment-1", "media_id")).isNull();

        // The comment row itself must survive (SET NULL, not CASCADE), and its
        // library link (a separate CASCADE FK) is untouched because we never
        // deleted the library.
        assertThat(rowExists("comments", "comment-1")).isTrue();
        assertThat(columnValue("comments", "comment-1", "library_id")).isEqualTo("lib-1");
    }

    // ---- Orphan-data scenarios: V24 does NOT clean orphans, so it fails ----

    @Test
    void v24MigrationFailsWhenOrphanLibraryIdExists() {
        migrateTo("23");
        // albums.library_id points at a library that does not exist. V24's first
        // constraint (fk_albums_library) validates this row and fails.
        insertAlbum("album-orphan", "nonexistent-library");

        assertThatThrownBy(() -> migrateTo("24"))
                .isInstanceOf(FlywayException.class);

        // V24 should not have been recorded as applied.
        assertThat(appliedMigrationVersion("24")).isFalse();
    }

    @Test
    void v24MigrationFailsWhenOrphanCommentSmallAlbumIdExists() {
        migrateTo("23");
        insertLibrary("lib-1");
        // comments.library_id is valid, but comments.small_album_id points at a
        // small_album that does not exist. Even though the FK is ON DELETE SET
        // NULL, constraint creation still validates existing rows.
        insertComment("comment-orphan", "lib-1", "ghost-small-album", null);

        assertThatThrownBy(() -> migrateTo("24"))
                .isInstanceOf(FlywayException.class);

        assertThat(appliedMigrationVersion("24")).isFalse();
    }

    // ---- Flyway helpers ----

    private void migrateTo(String targetVersion) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(MIGRATION_LOCATION)
                .target(MigrationVersion.fromVersion(targetVersion))
                .load()
                .migrate();
    }

    // ---- JDBC helpers ----

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void exec(String sql, Object... params) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("SQL failed: " + sql, e);
        }
    }

    private boolean constraintExists(String constraintName) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM pg_constraint WHERE conname = ?")) {
            ps.setString(1, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean rowExists(String table, String id) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM " + table + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Object columnValue(String table, String id, String column) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + column + " FROM " + table + " WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean appliedMigrationVersion(String version) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM flyway_schema_history WHERE version = ? AND success = true")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- Insert helpers (only NOT NULL columns are set) ----

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private void insertCleanReferencedRows() {
        insertLibrary("lib-1");
        insertAlbum("album-1", "lib-1");
        insertSmallAlbum("sa-1", "lib-1", "album-1");
        insertMedia("media-1", "lib-1");
        insertSmallAlbumMedia("sam-1", "lib-1", "sa-1", "media-1");
    }

    private void insertLibrary(String id) {
        exec("INSERT INTO shared_libraries (id, created_at, updated_at, display_name) VALUES (?, ?, ?, ?)",
                id, now(), now(), "Library " + id);
    }

    private void insertAlbum(String id, String libraryId) {
        exec("INSERT INTO albums (id, created_at, updated_at, library_id, title) VALUES (?, ?, ?, ?, ?)",
                id, now(), now(), libraryId, "Album " + id);
    }

    private void insertSmallAlbum(String id, String libraryId, String albumId) {
        exec("INSERT INTO small_albums (id, created_at, updated_at, library_id, title, album_id, "
                        + "display_time_millis, display_time_source) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, now(), now(), libraryId, "SmallAlbum " + id, albumId, 0L, "MANUAL");
    }

    private void insertMedia(String id, String libraryId) {
        exec("INSERT INTO media (id, created_at, updated_at, library_id, aspect_ratio, "
                        + "display_time_millis, display_time_source, height, imported_at_millis, "
                        + "media_type, mime_type, preview_url, size_bytes, storage_path, url, width) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, now(), now(), libraryId, 1.0, 0L, "MANUAL", 1, 0L,
                "IMAGE", "image/jpeg", "http://preview", 1L, "storage/path", "http://url", 1);
    }

    private void insertSmallAlbumMedia(String id, String libraryId, String smallAlbumId, String mediaId) {
        exec("INSERT INTO small_album_media (id, created_at, updated_at, library_id, small_album_id, "
                        + "media_id, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, now(), now(), libraryId, smallAlbumId, mediaId, 0);
    }

    private void insertComment(String id, String libraryId, String smallAlbumId, String mediaId) {
        exec("INSERT INTO comments (id, created_at, updated_at, library_id, author_id, target_type, "
                        + "small_album_id, media_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, now(), now(), libraryId, "author-1", "SMALL_ALBUM", smallAlbumId, mediaId);
    }
}
