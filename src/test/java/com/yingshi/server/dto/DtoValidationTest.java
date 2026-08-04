package com.yingshi.server.dto;

import com.yingshi.server.dto.auth.AuthLoginRequest;
import com.yingshi.server.dto.comment.CreateCommentRequest;
import com.yingshi.server.dto.comment.UpdateCommentRequest;
import com.yingshi.server.dto.content.CreateAlbumRequest;
import com.yingshi.server.dto.content.CreatePostRequest;
import com.yingshi.server.dto.upload.UploadTokenRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3-D-3: DTO validation tests.
 *
 * Verifies jakarta.validation annotations on request DTOs.
 * Pure unit test: builds a default ValidatorFactory, no Spring context needed.
 *
 * Covered DTOs:
 * - AuthLoginRequest (account/password @NotBlank + @Size)
 * - CreateAlbumRequest (title @NotBlank + @Size, subtitle @Size)
 * - CreatePostRequest (title @NotBlank, participantUserIds @NotNull + @Size(min=1))
 * - CreateCommentRequest / UpdateCommentRequest (content @NotBlank + @Size)
 * - UploadTokenRequest (fileName/mimeType/mediaType @NotBlank, fileSizeBytes/width/height @NotNull + @Positive)
 */
class DtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private void assertNoViolations(Object value) {
        assertThat(validator.validate(value)).isEmpty();
    }

    private void assertHasViolationOnPath(Object value, String path) {
        var violations = validator.validate(value);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals(path));
    }

    // ---- AuthLoginRequest ----

    @Test
    void authLoginRequestValidHasNoViolations() {
        assertNoViolations(new AuthLoginRequest("1085060329@qq.com", "123456"));
    }

    @Test
    void authLoginRequestBlankAccountHasViolation() {
        assertHasViolationOnPath(new AuthLoginRequest("", "123456"), "account");
    }

    @Test
    void authLoginRequestNullAccountHasViolation() {
        assertHasViolationOnPath(new AuthLoginRequest(null, "123456"), "account");
    }

    @Test
    void authLoginRequestBlankPasswordHasViolation() {
        assertHasViolationOnPath(new AuthLoginRequest("a@b.com", ""), "password");
    }

    @Test
    void authLoginRequestNullPasswordHasViolation() {
        assertHasViolationOnPath(new AuthLoginRequest("a@b.com", null), "password");
    }

    @Test
    void authLoginRequestOversizedAccountHasViolation() {
        String longAccount = "a@b.com".repeat(200);
        assertHasViolationOnPath(new AuthLoginRequest(longAccount, "123456"), "account");
    }

    // ---- CreateAlbumRequest ----

    @Test
    void createAlbumRequestValidHasNoViolations() {
        assertNoViolations(new CreateAlbumRequest("Trip", "Summer 2026"));
    }

    @Test
    void createAlbumRequestValidWithNullSubtitleHasNoViolations() {
        assertNoViolations(new CreateAlbumRequest("Trip", null));
    }

    @Test
    void createAlbumRequestBlankTitleHasViolation() {
        assertHasViolationOnPath(new CreateAlbumRequest("", "sub"), "title");
    }

    @Test
    void createAlbumRequestNullTitleHasViolation() {
        assertHasViolationOnPath(new CreateAlbumRequest(null, "sub"), "title");
    }

    @Test
    void createAlbumRequestOversizedTitleHasViolation() {
        String longTitle = "x".repeat(200);
        assertHasViolationOnPath(new CreateAlbumRequest(longTitle, null), "title");
    }

    // ---- CreatePostRequest ----

    @Test
    void createPostRequestValidHasNoViolations() {
        assertNoViolations(new CreatePostRequest(
                "Title", null, null, List.of("u1"), 1L, null, null, null,
                "album-1", List.of("m1"), null));
    }

    @Test
    void createPostRequestBlankTitleHasViolation() {
        assertHasViolationOnPath(new CreatePostRequest(
                "", null, null, List.of("u1"), 1L, null, null, null,
                "album-1", List.of("m1"), null), "title");
    }

    @Test
    void createPostRequestNullTitleHasViolation() {
        assertHasViolationOnPath(new CreatePostRequest(
                null, null, null, List.of("u1"), 1L, null, null, null,
                "album-1", List.of("m1"), null), "title");
    }

    @Test
    void createPostRequestNullParticipantUserIdsHasViolation() {
        assertHasViolationOnPath(new CreatePostRequest(
                "Title", null, null, null, 1L, null, null, null,
                "album-1", List.of("m1"), null), "participantUserIds");
    }

    @Test
    void createPostRequestEmptyParticipantUserIdsHasViolation() {
        assertHasViolationOnPath(new CreatePostRequest(
                "Title", null, null, List.of(), 1L, null, null, null,
                "album-1", List.of("m1"), null), "participantUserIds");
    }

    @Test
    void createPostRequestNullDisplayTimeMillisHasViolation() {
        assertHasViolationOnPath(new CreatePostRequest(
                "Title", null, null, List.of("u1"), null, null, null, null,
                "album-1", List.of("m1"), null), "displayTimeMillis");
    }

    @Test
    void createPostRequestBlankAlbumIdHasViolation() {
        assertHasViolationOnPath(new CreatePostRequest(
                "Title", null, null, List.of("u1"), 1L, null, null, null,
                "", List.of("m1"), null), "albumId");
    }

    @Test
    void createPostRequestNullInitialMediaIdsHasViolation() {
        assertHasViolationOnPath(new CreatePostRequest(
                "Title", null, null, List.of("u1"), 1L, null, null, null,
                "album-1", null, null), "initialMediaIds");
    }

    // ---- CreateCommentRequest / UpdateCommentRequest ----

    @Test
    void createCommentRequestValidHasNoViolations() {
        assertNoViolations(new CreateCommentRequest("Nice photo!"));
    }

    @Test
    void createCommentRequestBlankContentHasViolation() {
        assertHasViolationOnPath(new CreateCommentRequest(""), "content");
    }

    @Test
    void createCommentRequestNullContentHasViolation() {
        assertHasViolationOnPath(new CreateCommentRequest(null), "content");
    }

    @Test
    void createCommentRequestOversizedContentHasViolation() {
        String longContent = "x".repeat(2001);
        assertHasViolationOnPath(new CreateCommentRequest(longContent), "content");
    }

    @Test
    void updateCommentRequestValidHasNoViolations() {
        assertNoViolations(new UpdateCommentRequest("Edited content"));
    }

    @Test
    void updateCommentRequestBlankContentHasViolation() {
        assertHasViolationOnPath(new UpdateCommentRequest(""), "content");
    }

    @Test
    void updateCommentRequestNullContentHasViolation() {
        assertHasViolationOnPath(new UpdateCommentRequest(null), "content");
    }

    // ---- UploadTokenRequest ----

    private UploadTokenRequest validUploadTokenRequest() {
        return new UploadTokenRequest(
                "test.jpg", "image/jpeg", 1024L, "IMAGE", 1920, 1080,
                null, System.currentTimeMillis(), null, null,
                null, "fp-xyz", null, "IMPORT_TO_APP", null, null,
                null, null, null, null, null, null, null, null, null);
    }

    @Test
    void uploadTokenRequestValidHasNoViolations() {
        assertNoViolations(validUploadTokenRequest());
    }

    @Test
    void uploadTokenRequestBlankFileNameHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                "", req.mimeType(), req.fileSizeBytes(), req.mediaType(), req.width(), req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "fileName");
    }

    @Test
    void uploadTokenRequestNullFileNameHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                null, req.mimeType(), req.fileSizeBytes(), req.mediaType(), req.width(), req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "fileName");
    }

    @Test
    void uploadTokenRequestNullFileSizeBytesHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                req.fileName(), req.mimeType(), null, req.mediaType(), req.width(), req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "fileSizeBytes");
    }

    @Test
    void uploadTokenRequestZeroFileSizeBytesHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                req.fileName(), req.mimeType(), 0L, req.mediaType(), req.width(), req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "fileSizeBytes");
    }

    @Test
    void uploadTokenRequestNegativeFileSizeBytesHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                req.fileName(), req.mimeType(), -1L, req.mediaType(), req.width(), req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "fileSizeBytes");
    }

    @Test
    void uploadTokenRequestNullWidthHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                req.fileName(), req.mimeType(), req.fileSizeBytes(), req.mediaType(), null, req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "width");
    }

    @Test
    void uploadTokenRequestZeroWidthHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                req.fileName(), req.mimeType(), req.fileSizeBytes(), req.mediaType(), 0, req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "width");
    }

    @Test
    void uploadTokenRequestBlankMediaTypeHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                req.fileName(), req.mimeType(), req.fileSizeBytes(), "", req.width(), req.height(),
                req.durationMillis(), req.displayTimeMillis(), req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "mediaType");
    }

    @Test
    void uploadTokenRequestNullDisplayTimeMillisHasViolation() {
        var req = validUploadTokenRequest();
        var modified = new UploadTokenRequest(
                req.fileName(), req.mimeType(), req.fileSizeBytes(), req.mediaType(), req.width(), req.height(),
                req.durationMillis(), null, req.capturedAtMillis(), req.importedAtMillis(),
                req.displayTimeSource(), req.sourceFingerprint(), req.operationId(), req.operationType(),
                req.operationTitle(), req.operationMediaCount(), req.domain(), req.lifeCategory(),
                req.sourceItemId(), req.latitude(), req.longitude(), req.locationLabel(), req.locationSource(), req.idempotencyKey(), req.exifMetadata());
        assertHasViolationOnPath(modified, "displayTimeMillis");
    }
}
