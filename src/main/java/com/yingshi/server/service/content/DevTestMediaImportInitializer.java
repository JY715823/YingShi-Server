package com.yingshi.server.service.content;

import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.PostAlbumEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostAlbumRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.service.auth.DevAuthSeedDataInitializer;
import com.yingshi.server.service.upload.LocalMediaStorageService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
@Profile("dev")
@ConditionalOnProperty(prefix = "yingshi.dev.test-import", name = "enabled", havingValue = "true")
public class DevTestMediaImportInitializer {

    private static final String TEST_IMPORT_ROOT = "test";
    private static final String TEST_IMPORT_ALBUM_ID = "album_import_local_test_media";
    private static final String TEST_IMPORT_ALBUM_TITLE = "测试导入";
    private static final String TEST_IMPORT_CONTRIBUTOR_LABEL = "本地测试导入";
    private static final int DEFAULT_VIDEO_WIDTH = 1080;
    private static final int DEFAULT_VIDEO_HEIGHT = 1920;
    private static final long DEFAULT_VIDEO_DURATION_MILLIS = 15_000L;

    @Bean
    @Order(3)
    ApplicationRunner testMediaImportRunner(
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository,
            LocalMediaStorageService localMediaStorageService
    ) {
        return args -> importTestMedia(
                DevAuthSeedDataInitializer.DEMO_SPACE_ID,
                albumRepository,
                postRepository,
                mediaRepository,
                postMediaRepository,
                postAlbumRepository,
                localMediaStorageService
        );
    }

    private void importTestMedia(
            String spaceId,
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository,
            LocalMediaStorageService localMediaStorageService
    ) {
        List<ImportCandidate> candidates = localMediaStorageService.listFilesRecursively(spaceId, TEST_IMPORT_ROOT)
                .stream()
                .filter(this::isSupportedMediaFile)
                .map(path -> toImportCandidate(spaceId, path, localMediaStorageService))
                .filter(candidate -> candidate != null)
                .filter(candidate -> candidate.sizeBytes() > 0L)
                .sorted(Comparator
                        .comparing(ImportCandidate::bucketKey)
                        .thenComparing(ImportCandidate::displayTimeMillis, Comparator.reverseOrder())
                        .thenComparing(ImportCandidate::storagePath))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }

        AlbumEntity album = albumRepository.findById(TEST_IMPORT_ALBUM_ID)
                .orElseGet(() -> createImportAlbum(spaceId));
        album.setSpaceId(spaceId);
        album.setTitle(TEST_IMPORT_ALBUM_TITLE);
        album.setSubtitle("自动导入 local-storage/" + spaceId + "/" + TEST_IMPORT_ROOT + " 下的真实媒体");

        String latestCoverMediaId = album.getCoverMediaId();
        long latestCoverTime = Long.MIN_VALUE;

        Map<String, List<ImportCandidate>> candidatesByBucket = new LinkedHashMap<>();
        for (ImportCandidate candidate : candidates) {
            candidatesByBucket.computeIfAbsent(candidate.bucketKey(), key -> new ArrayList<>()).add(candidate);
            if (candidate.displayTimeMillis() > latestCoverTime) {
                latestCoverTime = candidate.displayTimeMillis();
                latestCoverMediaId = mediaIdForStoragePath(candidate.storagePath());
            }
        }

        for (Map.Entry<String, List<ImportCandidate>> entry : candidatesByBucket.entrySet()) {
            String bucketKey = entry.getKey();
            List<ImportCandidate> bucketCandidates = entry.getValue();
            List<String> mediaIds = new ArrayList<>();
            long postDisplayTimeMillis = Long.MIN_VALUE;

            for (ImportCandidate candidate : bucketCandidates) {
                MediaEntity media = upsertMedia(spaceId, candidate);
                mediaRepository.save(media);
                mediaIds.add(media.getId());
                postDisplayTimeMillis = Math.max(postDisplayTimeMillis, candidate.displayTimeMillis());
            }

            if (mediaIds.isEmpty()) {
                continue;
            }

            String postId = postIdForBucket(bucketKey);
            PostEntity post = postRepository.findById(postId).orElseGet(PostEntity::new);
            post.setId(postId);
            post.setSpaceId(spaceId);
            post.setTitle(truncate("测试导入 · " + humanizeBucketKey(bucketKey), 120));
            post.setSummary(truncate("自动导入自 " + bucketKey, 1000));
            post.setContributorLabel(TEST_IMPORT_CONTRIBUTOR_LABEL);
            post.setDisplayTimeMillis(postDisplayTimeMillis);
            post.setCoverMediaId(mediaIds.get(0));
            post.setDeletedAt(null);
            postRepository.save(post);

            PostAlbumEntity postAlbum = postAlbumRepository.findById(postAlbumIdForPost(postId)).orElseGet(PostAlbumEntity::new);
            postAlbum.setId(postAlbumIdForPost(postId));
            postAlbum.setSpaceId(spaceId);
            postAlbum.setPostId(postId);
            postAlbum.setAlbumId(TEST_IMPORT_ALBUM_ID);
            postAlbumRepository.save(postAlbum);

            attachMediaToPost(spaceId, postId, mediaIds, postMediaRepository);
        }

        album.setCoverMediaId(latestCoverMediaId);
        albumRepository.save(album);
    }

    private void attachMediaToPost(
            String spaceId,
            String postId,
            List<String> mediaIds,
            PostMediaRepository postMediaRepository
    ) {
        List<PostMediaEntity> existingRelations = postMediaRepository.findBySpaceIdAndPostIdOrderBySortOrderAsc(spaceId, postId);
        Map<String, PostMediaEntity> existingByMediaId = new LinkedHashMap<>();
        int maxSortOrder = 0;
        for (PostMediaEntity relation : existingRelations) {
            existingByMediaId.put(relation.getMediaId(), relation);
            maxSortOrder = Math.max(maxSortOrder, relation.getSortOrder());
        }

        int nextSortOrder = maxSortOrder + 1;
        for (String mediaId : mediaIds) {
            if (existingByMediaId.containsKey(mediaId)) {
                continue;
            }
            PostMediaEntity relation = new PostMediaEntity();
            relation.setId(postMediaIdForRelation(postId, mediaId));
            relation.setSpaceId(spaceId);
            relation.setPostId(postId);
            relation.setMediaId(mediaId);
            relation.setSortOrder(nextSortOrder++);
            postMediaRepository.save(relation);
        }
    }

    private AlbumEntity createImportAlbum(String spaceId) {
        AlbumEntity album = new AlbumEntity();
        album.setId(TEST_IMPORT_ALBUM_ID);
        album.setSpaceId(spaceId);
        album.setTitle(TEST_IMPORT_ALBUM_TITLE);
        return album;
    }

    private MediaEntity upsertMedia(String spaceId, ImportCandidate candidate) {
        String mediaId = mediaIdForStoragePath(candidate.storagePath());
        String mediaUrl = "/api/media/files/" + mediaId;

        MediaEntity media = new MediaEntity();
        media.setId(mediaId);
        media.setSpaceId(spaceId);
        media.setMediaType(candidate.mediaType());
        media.setUrl(mediaUrl);
        media.setPreviewUrl(mediaUrl);
        media.setOriginalUrl(candidate.mediaType() == MediaType.IMAGE ? mediaUrl : null);
        media.setVideoUrl(candidate.mediaType() == MediaType.VIDEO ? mediaUrl : null);
        media.setCoverUrl(null);
        media.setMimeType(candidate.mimeType());
        media.setSizeBytes(candidate.sizeBytes());
        media.setWidth(candidate.width());
        media.setHeight(candidate.height());
        media.setAspectRatio(((double) candidate.width()) / candidate.height());
        media.setDurationMillis(candidate.durationMillis());
        media.setDisplayTimeMillis(candidate.displayTimeMillis());
        media.setStoragePath(candidate.storagePath());
        media.setDeletedAt(null);
        return media;
    }

    private ImportCandidate toImportCandidate(
            String spaceId,
            Path path,
            LocalMediaStorageService localMediaStorageService
    ) {
        String storagePath = localMediaStorageService.toRelativeStoragePath(path);
        String bucketKey = bucketKeyForStoragePath(spaceId, storagePath);
        MediaType mediaType = mediaTypeForPath(path);
        String mimeType = mimeTypeForPath(path, mediaType);
        long displayTimeMillis = lastModifiedTimeMillis(path);
        long sizeBytes = fileSize(path);
        if (sizeBytes <= 1024L) {
            return null;
        }
        MediaDimensions dimensions = readDimensions(path, mediaType);
        if (dimensions == null) {
            return null;
        }
        Long durationMillis = mediaType == MediaType.VIDEO ? DEFAULT_VIDEO_DURATION_MILLIS : null;

        return new ImportCandidate(
                storagePath,
                bucketKey,
                mediaType,
                mimeType,
                sizeBytes,
                dimensions.width(),
                dimensions.height(),
                durationMillis,
                displayTimeMillis
        );
    }

    private MediaDimensions readDimensions(Path path, MediaType mediaType) {
        if (mediaType == MediaType.VIDEO) {
            return new MediaDimensions(DEFAULT_VIDEO_WIDTH, DEFAULT_VIDEO_HEIGHT);
        }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                return new MediaDimensions(image.getWidth(), image.getHeight());
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private boolean isSupportedMediaFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".png")
                || fileName.endsWith(".webp")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".heic")
                || fileName.endsWith(".heif")
                || fileName.endsWith(".avif")
                || fileName.endsWith(".mp4")
                || fileName.endsWith(".mov")
                || fileName.endsWith(".m4v")
                || fileName.endsWith(".webm")
                || fileName.endsWith(".3gp")
                || fileName.endsWith(".mkv")
                || fileName.endsWith(".avi");
    }

    private MediaType mediaTypeForPath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".mp4")
                || fileName.endsWith(".mov")
                || fileName.endsWith(".m4v")
                || fileName.endsWith(".webm")
                || fileName.endsWith(".3gp")
                || fileName.endsWith(".mkv")
                || fileName.endsWith(".avi")) {
            return MediaType.VIDEO;
        }
        return MediaType.IMAGE;
    }

    private String mimeTypeForPath(Path path, MediaType mediaType) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (IOException ignored) {
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (mediaType == MediaType.VIDEO) {
            if (fileName.endsWith(".mov")) {
                return "video/quicktime";
            }
            if (fileName.endsWith(".webm")) {
                return "video/webm";
            }
            if (fileName.endsWith(".3gp")) {
                return "video/3gpp";
            }
            if (fileName.endsWith(".mkv")) {
                return "video/x-matroska";
            }
            if (fileName.endsWith(".avi")) {
                return "video/x-msvideo";
            }
            return "video/mp4";
        }

        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (fileName.endsWith(".heic")) {
            return "image/heic";
        }
        if (fileName.endsWith(".heif")) {
            return "image/heif";
        }
        if (fileName.endsWith(".avif")) {
            return "image/avif";
        }
        return "image/jpeg";
    }

    private long lastModifiedTimeMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return System.currentTimeMillis();
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private String bucketKeyForStoragePath(String spaceId, String storagePath) {
        Path path = Paths.get(storagePath.replace("/", java.io.File.separator));
        Path expectedRoot = Paths.get(spaceId, TEST_IMPORT_ROOT);
        Path parent = path.getParent();
        if (parent == null) {
            return TEST_IMPORT_ROOT;
        }
        if (parent.startsWith(expectedRoot)) {
            Path relative = expectedRoot.relativize(parent);
            return relative.toString().replace(java.io.File.separatorChar, '/');
        }
        return parent.toString().replace(java.io.File.separatorChar, '/');
    }

    private String humanizeBucketKey(String bucketKey) {
        String normalized = bucketKey.replace('\\', '/');
        if (normalized.isBlank() || TEST_IMPORT_ROOT.equals(normalized)) {
            return "test 根目录";
        }
        return normalized.replace("/", " / ");
    }

    private String mediaIdForStoragePath(String storagePath) {
        return "media_test_" + shortHash(storagePath);
    }

    private String postIdForBucket(String bucketKey) {
        return "post_test_" + shortHash(bucketKey);
    }

    private String postMediaIdForRelation(String postId, String mediaId) {
        return "post_media_test_" + shortHash(postId + "::" + mediaId);
    }

    private String postAlbumIdForPost(String postId) {
        return "post_album_test_" + shortHash(postId + "::" + TEST_IMPORT_ALBUM_ID);
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed).substring(0, 20);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 digest is not available.", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ImportCandidate(
            String storagePath,
            String bucketKey,
            MediaType mediaType,
            String mimeType,
            long sizeBytes,
            int width,
            int height,
            Long durationMillis,
            long displayTimeMillis
    ) {
    }

    private record MediaDimensions(int width, int height) {
    }
}
