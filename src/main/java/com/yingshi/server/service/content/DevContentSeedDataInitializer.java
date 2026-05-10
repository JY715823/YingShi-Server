package com.yingshi.server.service.content;

import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.PostAlbumEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.domain.SharedLibraryEntity;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostAlbumRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.SharedLibraryRepository;
import com.yingshi.server.service.auth.DevAuthSeedDataInitializer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("dev")
public class DevContentSeedDataInitializer {

    private static final String HIDDEN_LIBRARY_ID = "library_private_other";
    private static final String SAMPLE_LANDSCAPE_PATH = "test/photos/sample-landscape-2400x1600.jpg";
    private static final String SAMPLE_PORTRAIT_PATH = "test/photos/sample-portrait-1800x2400.jpg";
    private static final String SAMPLE_SQUARE_PATH = "test/photos/sample-square-2400.jpg";
    private static final String SAMPLE_LONG_PATH = "test/long/sample-long-1400x3200.jpg";
    private static final String SAMPLE_TALL_PATH = "test/long/sample-tall-1200x3600.jpg";
    private static final String SAMPLE_VIDEO_PATH = "test/videos/sample-video-15s-868KB.mp4";

    @Bean
    @Order(2)
    ApplicationRunner contentSeedRunner(
            SharedLibraryRepository libraryRepository,
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository
    ) {
        return args -> {
            if (albumRepository.count() > 0 || postRepository.count() > 0 || mediaRepository.count() > 0) {
                return;
            }

            String sharedLibraryId = DevAuthSeedDataInitializer.DEMO_LIBRARY_ID;
            ensureLibrary(libraryRepository, sharedLibraryId, "映时共享相册");
            ensureLibrary(libraryRepository, HIDDEN_LIBRARY_ID, "内部测试相册");

            seedSharedLibrary(
                    sharedLibraryId,
                    albumRepository,
                    postRepository,
                    mediaRepository,
                    postMediaRepository,
                    postAlbumRepository
            );
            seedHiddenLibrary(
                    HIDDEN_LIBRARY_ID,
                    albumRepository,
                    postRepository,
                    mediaRepository,
                    postMediaRepository,
                    postAlbumRepository
            );
        };
    }

    private void seedSharedLibrary(
            String libraryId,
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository
    ) {
        mediaRepository.save(createImageMedia(libraryId, "media_001", 2400, 1600, 1777412800000L, SAMPLE_LANDSCAPE_PATH, 505_464L));
        mediaRepository.save(createImageMedia(libraryId, "media_002", 1800, 2400, 1777412600000L, SAMPLE_PORTRAIT_PATH, 674_179L));
        mediaRepository.save(createImageMedia(libraryId, "media_003", 2400, 2400, 1777412400000L, SAMPLE_SQUARE_PATH, 383_089L));
        mediaRepository.save(createImageMedia(libraryId, "media_004", 1400, 3200, 1777412200000L, SAMPLE_LONG_PATH, 822_197L));
        mediaRepository.save(createImageMedia(libraryId, "media_005", 1200, 3600, 1777412000000L, SAMPLE_TALL_PATH, 744_380L));
        mediaRepository.save(createVideoMedia(libraryId, "media_006", 1080, 1920, 1777411800000L, SAMPLE_VIDEO_PATH, 887_988L, 15_000L));

        albumRepository.save(createAlbum(libraryId, "album_001", "示例照片", "横图、竖图、方图和长图的本地示例数据", "media_001"));
        albumRepository.save(createAlbum(libraryId, "album_002", "视频检查", "用于视频封面、播放和导入检查的本地示例数据", "media_006"));
        albumRepository.save(createAlbum(libraryId, "album_003", "长图检查", "用于长图预览、Viewer 阅读和原图加载检查", "media_005"));

        postRepository.save(createPost(
                libraryId,
                "post_001",
                "示例照片组",
                "用于照片流、相册封面和帖子详情检查的本地示例照片。",
                "演示用户 A 和 B",
                1777412800000L,
                "media_001"
        ));
        postRepository.save(createPost(
                libraryId,
                "post_002",
                "长图阅读检查",
                "用于检查长图预览、Viewer 滚动和原文件切换。",
                "演示用户 A 和 B",
                1777412200000L,
                "media_005"
        ));
        postRepository.save(createPost(
                libraryId,
                "post_003",
                "视频导入检查",
                "包含本地 mp4 示例和方图，用于视频封面和播放检查。",
                "演示用户 A 和 B",
                1777411800000L,
                "media_006"
        ));

        postMediaRepository.save(createPostMedia(libraryId, "post_media_001", "post_001", "media_001", 1));
        postMediaRepository.save(createPostMedia(libraryId, "post_media_002", "post_001", "media_002", 2));
        postMediaRepository.save(createPostMedia(libraryId, "post_media_003", "post_001", "media_004", 3));
        postMediaRepository.save(createPostMedia(libraryId, "post_media_004", "post_002", "media_005", 1));
        postMediaRepository.save(createPostMedia(libraryId, "post_media_005", "post_002", "media_003", 2));
        postMediaRepository.save(createPostMedia(libraryId, "post_media_006", "post_003", "media_006", 1));
        postMediaRepository.save(createPostMedia(libraryId, "post_media_007", "post_003", "media_001", 2));

        postAlbumRepository.save(createPostAlbum(libraryId, "post_album_001", "post_001", "album_001"));
        postAlbumRepository.save(createPostAlbum(libraryId, "post_album_002", "post_002", "album_001"));
        postAlbumRepository.save(createPostAlbum(libraryId, "post_album_003", "post_002", "album_003"));
        postAlbumRepository.save(createPostAlbum(libraryId, "post_album_004", "post_003", "album_002"));
    }

    private void seedHiddenLibrary(
            String libraryId,
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository
    ) {
        mediaRepository.save(createImageMedia(libraryId, "media_other_secret", 2400, 2400, 1777410000000L, SAMPLE_SQUARE_PATH, 383_089L));
        albumRepository.save(createAlbum(libraryId, "album_other_secret", "内部相册", "用于共享图库隔离检查的内部数据", "media_other_secret"));
        postRepository.save(createPost(libraryId, "post_other_secret", "内部帖子", "用于共享图库隔离检查的内部数据", "内部成员", 1777410000000L, "media_other_secret"));
        postMediaRepository.save(createPostMedia(libraryId, "post_media_other_secret", "post_other_secret", "media_other_secret", 1));
        postAlbumRepository.save(createPostAlbum(libraryId, "post_album_other_secret", "post_other_secret", "album_other_secret"));
    }

    private void ensureLibrary(SharedLibraryRepository libraryRepository, String id, String name) {
        if (libraryRepository.findById(id).isPresent()) {
            return;
        }
        SharedLibraryEntity library = new SharedLibraryEntity();
        library.setId(id);
        library.setDisplayName(name);
        libraryRepository.save(library);
    }

    private AlbumEntity createAlbum(String libraryId, String id, String title, String subtitle, String coverMediaId) {
        AlbumEntity album = new AlbumEntity();
        album.setId(id);
        album.setLibraryId(libraryId);
        album.setTitle(title);
        album.setSubtitle(subtitle);
        album.setCoverMediaId(coverMediaId);
        return album;
    }

    private PostEntity createPost(
            String libraryId,
            String id,
            String title,
            String summary,
            String contributorLabel,
            long displayTimeMillis,
            String coverMediaId
    ) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setLibraryId(libraryId);
        post.setTitle(title);
        post.setSummary(summary);
        post.setContributorLabel(contributorLabel);
        post.setDisplayTimeMillis(displayTimeMillis);
        post.setEventStartedAtMillis(displayTimeMillis);
        post.setEventEndedAtMillis(null);
        post.setDisplayTimeSource("MANUAL");
        post.setCoverMediaId(coverMediaId);
        return post;
    }

    private MediaEntity createImageMedia(
            String libraryId,
            String id,
            int width,
            int height,
            long displayTimeMillis,
            String storagePath,
            long sizeBytes
    ) {
        String mediaUrl = "/api/media/files/" + id;
        MediaEntity media = createBaseMedia(libraryId, id, width, height, displayTimeMillis, storagePath, sizeBytes);
        media.setMediaType(MediaType.IMAGE);
        media.setUrl(mediaUrl);
        media.setPreviewUrl(mediaUrl);
        media.setOriginalUrl(mediaUrl);
        media.setVideoUrl(null);
        media.setCoverUrl(null);
        media.setMimeType("image/jpeg");
        media.setDurationMillis(null);
        return media;
    }

    private MediaEntity createVideoMedia(
            String libraryId,
            String id,
            int width,
            int height,
            long displayTimeMillis,
            String storagePath,
            long sizeBytes,
            long durationMillis
    ) {
        String mediaUrl = "/api/media/files/" + id;
        MediaEntity media = createBaseMedia(libraryId, id, width, height, displayTimeMillis, storagePath, sizeBytes);
        media.setMediaType(MediaType.VIDEO);
        media.setUrl(mediaUrl);
        media.setPreviewUrl(mediaUrl);
        media.setOriginalUrl(null);
        media.setVideoUrl(mediaUrl);
        media.setCoverUrl(mediaUrl);
        media.setMimeType("video/mp4");
        media.setDurationMillis(durationMillis);
        return media;
    }

    private MediaEntity createBaseMedia(
            String libraryId,
            String id,
            int width,
            int height,
            long displayTimeMillis,
            String storagePath,
            long sizeBytes
    ) {
        MediaEntity media = new MediaEntity();
        media.setId(id);
        media.setLibraryId(libraryId);
        media.setSizeBytes(sizeBytes);
        media.setWidth(width);
        media.setHeight(height);
        media.setAspectRatio(((double) width) / height);
        media.setDisplayTimeMillis(displayTimeMillis);
        media.setCapturedAtMillis(displayTimeMillis);
        media.setImportedAtMillis(displayTimeMillis);
        media.setDisplayTimeSource("ORIGINAL");
        media.setStoragePath(storagePath);
        return media;
    }

    private PostMediaEntity createPostMedia(String libraryId, String id, String postId, String mediaId, int sortOrder) {
        PostMediaEntity relation = new PostMediaEntity();
        relation.setId(id);
        relation.setLibraryId(libraryId);
        relation.setPostId(postId);
        relation.setMediaId(mediaId);
        relation.setSortOrder(sortOrder);
        return relation;
    }

    private PostAlbumEntity createPostAlbum(String libraryId, String id, String postId, String albumId) {
        PostAlbumEntity relation = new PostAlbumEntity();
        relation.setId(id);
        relation.setLibraryId(libraryId);
        relation.setPostId(postId);
        relation.setAlbumId(albumId);
        return relation;
    }
}
