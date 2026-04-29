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
            ensureSpace(spaceRepository, sharedSpaceId, "Yingshi Demo Space");
            ensureSpace(spaceRepository, HIDDEN_SPACE_ID, "Private Hidden Space");

            seedSharedSpace(sharedSpaceId, albumRepository, postRepository, mediaRepository, postMediaRepository, postAlbumRepository);
            seedHiddenSpace(HIDDEN_SPACE_ID, albumRepository, postRepository, mediaRepository, postMediaRepository, postAlbumRepository);
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
        mediaRepository.save(createImageMedia(spaceId, "media_001", "media_001", 1440, 1920, 1777412800000L));
        mediaRepository.save(createImageMedia(spaceId, "media_002", "media_002", 1600, 1200, 1777412600000L));
        mediaRepository.save(createImageMedia(spaceId, "media_003", "media_003", 1280, 1706, 1777412400000L));
        mediaRepository.save(createVideoMedia(spaceId, "media_004", "media_004", 1920, 1080, 1777412200000L));
        mediaRepository.save(createImageMedia(spaceId, "media_005", "media_005", 1536, 2048, 1777412000000L));
        mediaRepository.save(createImageMedia(spaceId, "media_006", "media_006", 1440, 1440, 1777411800000L));

        albumRepository.save(createAlbum(spaceId, "album_001", "Spring Window", "Light and slow daily fragments", "media_001"));
        albumRepository.save(createAlbum(spaceId, "album_002", "Weekend Notes", "Walks, trains, and tiny stops", "media_004"));
        albumRepository.save(createAlbum(spaceId, "album_003", "Gear Edit Picks", "Reusable frames for edit flows", "media_005"));

        postRepository.save(createPost(spaceId, "post_001", "Night Walk", "A quiet walk home", "Demo A and Demo B", 1777412800000L, "media_001"));
        postRepository.save(createPost(spaceId, "post_002", "Desk Light", "Small objects, warm lamp, late hour", "Demo A and Demo B", 1777412400000L, "media_005"));
        postRepository.save(createPost(spaceId, "post_003", "Train Window", "Passing light and station blur", "Demo A and Demo B", 1777412000000L, "media_006"));

        postMediaRepository.save(createPostMedia(spaceId, "post_media_001", "post_001", "media_001", 1));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_002", "post_001", "media_002", 2));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_003", "post_001", "media_004", 3));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_004", "post_002", "media_005", 1));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_005", "post_002", "media_003", 2));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_006", "post_002", "media_001", 3));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_007", "post_003", "media_006", 1));
        postMediaRepository.save(createPostMedia(spaceId, "post_media_008", "post_003", "media_002", 2));

        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_001", "post_001", "album_001"));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_002", "post_001", "album_002"));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_003", "post_002", "album_001"));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_004", "post_002", "album_003"));
        postAlbumRepository.save(createPostAlbum(spaceId, "post_album_005", "post_003", "album_002"));
    }

    private void seedHiddenSpace(
            String spaceId,
            AlbumRepository albumRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            PostMediaRepository postMediaRepository,
            PostAlbumRepository postAlbumRepository
    ) {
        mediaRepository.save(createImageMedia(spaceId, "media_other_secret", "media_other_secret", 1200, 1200, 1777410000000L));
        albumRepository.save(createAlbum(spaceId, "album_other_secret", "Hidden Album", "Should not be visible", "media_other_secret"));
        postRepository.save(createPost(spaceId, "post_other_secret", "Hidden Post", "Should not be visible", "Hidden", 1777410000000L, "media_other_secret"));
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

    private MediaEntity createImageMedia(String spaceId, String id, String slug, int width, int height, long displayTimeMillis) {
        MediaEntity media = new MediaEntity();
        media.setId(id);
        media.setSpaceId(spaceId);
        media.setMediaType(MediaType.IMAGE);
        media.setPreviewUrl("https://demo.yingshi.local/" + slug + "_preview.jpg");
        media.setOriginalUrl("https://demo.yingshi.local/" + slug + "_original.jpg");
        media.setVideoUrl(null);
        media.setCoverUrl(null);
        media.setWidth(width);
        media.setHeight(height);
        media.setAspectRatio(((double) width) / height);
        media.setDisplayTimeMillis(displayTimeMillis);
        return media;
    }

    private MediaEntity createVideoMedia(String spaceId, String id, String slug, int width, int height, long displayTimeMillis) {
        MediaEntity media = new MediaEntity();
        media.setId(id);
        media.setSpaceId(spaceId);
        media.setMediaType(MediaType.VIDEO);
        media.setPreviewUrl("https://demo.yingshi.local/" + slug + "_cover.jpg");
        media.setOriginalUrl(null);
        media.setVideoUrl("https://demo.yingshi.local/" + slug + ".mp4");
        media.setCoverUrl("https://demo.yingshi.local/" + slug + "_cover.jpg");
        media.setWidth(width);
        media.setHeight(height);
        media.setAspectRatio(((double) width) / height);
        media.setDisplayTimeMillis(displayTimeMillis);
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
