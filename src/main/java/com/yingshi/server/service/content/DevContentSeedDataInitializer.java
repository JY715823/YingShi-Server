package com.yingshi.server.service.content;

import com.yingshi.server.domain.AlbumEntity;
import com.yingshi.server.domain.MediaEntity;
import com.yingshi.server.domain.MediaType;
import com.yingshi.server.domain.PostAlbumEntity;
import com.yingshi.server.domain.PostEntity;
import com.yingshi.server.domain.PostMediaEntity;
import com.yingshi.server.domain.SpaceEntity;
import com.yingshi.server.repository.AlbumRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostAlbumRepository;
import com.yingshi.server.repository.PostMediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.SpaceRepository;
import com.yingshi.server.service.auth.DevAuthSeedDataInitializer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("dev")
public class DevContentSeedDataInitializer {

    private static final String HIDDEN_SPACE_ID = "space_private_other";
    private static final String SAMPLE_LANDSCAPE_PATH = "space_demo_shared/test/photos/sample-landscape-2400x1600.jpg";
    private static final String SAMPLE_PORTRAIT_PATH = "space_demo_shared/test/photos/sample-portrait-1800x2400.jpg";
    private static final String SAMPLE_SQUARE_PATH = "space_demo_shared/test/photos/sample-square-2400.jpg";
    private static final String SAMPLE_LONG_PATH = "space_demo_shared/test/long/sample-long-1400x3200.jpg";
    private static final String SAMPLE_TALL_PATH = "space_demo_shared/test/long/sample-tall-1200x3600.jpg";
    private static final String SAMPLE_VIDEO_PATH = "space_demo_shared/test/videos/sample-video-15s-868KB.mp4";

    @Bean
    @Order(2)
    ApplicationRunner contentSeedRunner(
            SpaceRepository spaceRepository,
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

            String sharedSpaceId = DevAuthSeedDataInitializer.DEMO_SPACE_ID;
            ensureSpace(spaceRepository, sharedSpaceId, "映时共享空间");
            ensureSpace(spaceRepository, HIDDEN_SPACE_ID, "隐藏测试空间");

            seedSharedSpace(
                    sharedSpaceId,
                    albumRepository,
                    postRepository,
                    mediaRepository,
                    postMediaRepository,
                    postAlbumRepository
            );
            seedHiddenSpace(
                    HIDDEN_SPACE_ID,
                    albumRepository,
                    postRepository,
                    mediaRepository,
                    postMediaRepository,
                    postAlbumRepository
            );
        };
    }

    private void seedSharedSpace(
            String spaceId,
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository
    ) {
        mediaRepository.save(createImageMedia(spaceId, "media_001", 2400, 1600, 1777412800000L, SAMPLE_LANDSCAPE_PATH, 505_464L));
        mediaRepository.save(createImageMedia(spaceId, "media_002", 1800, 2400, 1777412600000L, SAMPLE_PORTRAIT_PATH, 674_179L));
        mediaRepository.save(createImageMedia(spaceId, "media_003", 2400, 2400, 1777412400000L, SAMPLE_SQUARE_PATH, 383_089L));
        mediaRepository.save(createImageMedia(spaceId, "media_004", 1400, 3200, 1777412200000L, SAMPLE_LONG_PATH, 822_197L));
        mediaRepository.save(createImageMedia(spaceId, "media_005", 1200, 3600, 1777412000000L, SAMPLE_TALL_PATH, 744_380L));
        mediaRepository.save(createVideoMedia(spaceId, "media_006", 1080, 1920, 1777411800000L, SAMPLE_VIDEO_PATH, 887_988L, 15_000L));

        albumRepository.save(createAlbum(spaceId, "album_001", "样例合集", "横图、竖图、方图和长图的基础显示数据", "media_001"));
        albumRepository.save(createAlbum(spaceId, "album_002", "视频测试", "用于验证视频封面、播放和导入链路", "media_006"));
        albumRepository.save(createAlbum(spaceId, "album_003", "长图精选", "用于验证长图预览、查看态和原图加载", "media_005"));

        postRepository.save(createPost(
                spaceId,
                "post_001",
                "样例图片合集",
                "这组帖子引用新下载的横图、竖图和方图，用来验证照片流、相册封面和帖子详情的基础显示。",
                "小雨 和 阿泽",
                1777412800000L,
                "media_001"
        ));
        postRepository.save(createPost(
                spaceId,
                "post_002",
                "长图阅读测试",
                "这组帖子专门放长图和方图，用来检查列表裁切、查看态纵向浏览和加载原图是否真的切换资源。",
                "小雨 和 阿泽",
                1777412200000L,
                "media_005"
        ));
        postRepository.save(createPost(
                spaceId,
                "post_003",
                "视频导入测试",
                "这组帖子引用一个本地 mp4 示例，并带一张方图作为对照，方便检查视频媒体不再误走加载原图。",
                "小雨 和 阿泽",
                1777411800000L,
                "media_006"
        ));

        postMediaRepository.save(createPostMedia(spaceId, "post_media_001", "post_001", "media_001", 1));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_002", "post_001", "media_002", 2));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_003", "post_001", "media_004", 3));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_004", "post_002", "media_005", 1));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_005", "post_002", "media_003", 2));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_006", "post_003", "media_006", 1));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_007", "post_003", "media_001", 2));

        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_001", "post_001", "album_001"));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_002", "post_002", "album_001"));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_003", "post_002", "album_003"));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_004", "post_003", "album_002"));
    }

    private void seedHiddenSpace(
            String spaceId,
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository
    ) {
        mediaRepository.save(createImageMedia(spaceId, "media_other_secret", 2400, 2400, 1777410000000L, SAMPLE_SQUARE_PATH, 383_089L));
        albumRepository.save(createAlbum(spaceId, "album_other_secret", "隐藏相册", "用于跨空间可见性测试", "media_other_secret"));
        postRepository.save(createPost(spaceId, "post_other_secret", "隐藏帖子", "用于跨空间可见性测试", "隐藏成员", 1777410000000L, "media_other_secret"));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_other_secret", "post_other_secret", "media_other_secret", 1));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_other_secret", "post_other_secret", "album_other_secret"));
    }

    private void ensureSpace(SpaceRepository spaceRepository, String id, String name) {
        if (spaceRepository.findById(id).isPresent()) {
            return;
        }
        SpaceEntity space = new SpaceEntity();
        space.setId(id);
        space.setDisplayName(name);
        spaceRepository.save(space);
    }

    private AlbumEntity createAlbum(String spaceId, String id, String title, String subtitle, String coverMediaId) {
        AlbumEntity album = new AlbumEntity();
        album.setId(id);
        album.setSpaceId(spaceId);
        album.setTitle(title);
        album.setSubtitle(subtitle);
        album.setCoverMediaId(coverMediaId);
        return album;
    }

    private PostEntity createPost(
            String spaceId,
            String id,
            String title,
            String summary,
            String contributorLabel,
            long displayTimeMillis,
            String coverMediaId
    ) {
        PostEntity post = new PostEntity();
        post.setId(id);
        post.setSpaceId(spaceId);
        post.setTitle(title);
        post.setSummary(summary);
        post.setContributorLabel(contributorLabel);
        post.setDisplayTimeMillis(displayTimeMillis);
        post.setCoverMediaId(coverMediaId);
        return post;
    }

    private MediaEntity createImageMedia(
            String spaceId,
            String id,
            int width,
            int height,
            long displayTimeMillis,
            String storagePath,
            long sizeBytes
    ) {
        String mediaUrl = "/api/media/files/" + id;
        MediaEntity media = createBaseMedia(spaceId, id, width, height, displayTimeMillis, storagePath, sizeBytes);
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
            String spaceId,
            String id,
            int width,
            int height,
            long displayTimeMillis,
            String storagePath,
            long sizeBytes,
            long durationMillis
    ) {
        String mediaUrl = "/api/media/files/" + id;
        MediaEntity media = createBaseMedia(spaceId, id, width, height, displayTimeMillis, storagePath, sizeBytes);
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
            String spaceId,
            String id,
            int width,
            int height,
            long displayTimeMillis,
            String storagePath,
            long sizeBytes
    ) {
        MediaEntity media = new MediaEntity();
        media.setId(id);
        media.setSpaceId(spaceId);
        media.setSizeBytes(sizeBytes);
        media.setWidth(width);
        media.setHeight(height);
        media.setAspectRatio(((double) width) / height);
        media.setDisplayTimeMillis(displayTimeMillis);
        media.setStoragePath(storagePath);
        return media;
    }

    private PostMediaEntity createPostMedia(String spaceId, String id, String postId, String mediaId, int sortOrder) {
        PostMediaEntity relation = new PostMediaEntity();
        relation.setId(id);
        relation.setSpaceId(spaceId);
        relation.setPostId(postId);
        relation.setMediaId(mediaId);
        relation.setSortOrder(sortOrder);
        return relation;
    }

    private PostAlbumEntity createPostAlbum(String spaceId, String id, String postId, String albumId) {
        PostAlbumEntity relation = new PostAlbumEntity();
        relation.setId(id);
        relation.setSpaceId(spaceId);
        relation.setPostId(postId);
        relation.setAlbumId(albumId);
        return relation;
    }
}
