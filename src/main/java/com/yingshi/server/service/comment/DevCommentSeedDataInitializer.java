package com.yingshi.server.service.comment;

import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import com.yingshi.server.repository.CommentRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("dev")
public class DevCommentSeedDataInitializer {

    @Bean
    @Order(4)
    ApplicationRunner commentSeedRunner(CommentRepository commentRepository) {
        return args -> {
            if (commentRepository.count() > 0) {
                return;
            }

            commentRepository.save(createPostComment(
                    "comment_post_001",
                    "library_shared",
                    "user_demo_a",
                    "post_001",
                    "This post uses the refreshed local sample media for photo feed and post-detail checks."
            ));
            commentRepository.save(createPostComment(
                    "comment_post_002",
                    "library_shared",
                    "user_demo_b",
                    "post_001",
                    "Long images should keep preview safe and switch to original only when the real file loads."
            ));
            commentRepository.save(createPostComment(
                    "comment_post_003",
                    "library_shared",
                    "user_demo_a",
                    "post_003",
                    "Video posts validate playback and poster stability, not image-original state."
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_001",
                    "library_shared",
                    "user_demo_b",
                    "media_001",
                    "Shared media can appear in the feed and multiple posts while keeping comments isolated by mediaId."
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_002",
                    "library_shared",
                    "user_demo_a",
                    "media_001",
                    "Original loading and comment state should follow this media item itself."
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_003",
                    "library_shared",
                    "user_demo_b",
                    "media_004",
                    "This 1400x3200 long image is useful for vertical reading and original-loading checks."
            ));
            commentRepository.save(createPostComment(
                    "comment_other_secret",
                    "library_private_other",
                    "user_demo_b",
                    "post_other_secret",
                    "Internal isolation fixture for shared-library access checks."
            ));
        };
    }

    private CommentEntity createPostComment(
            String id,
            String libraryId,
            String authorId,
            String postId,
            String content
    ) {
        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setLibraryId(libraryId);
        comment.setAuthorId(authorId);
        comment.setTargetType(CommentTargetType.POST);
        comment.setPostId(postId);
        comment.setContent(content);
        return comment;
    }

    private CommentEntity createMediaComment(
            String id,
            String libraryId,
            String authorId,
            String mediaId,
            String content
    ) {
        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setLibraryId(libraryId);
        comment.setAuthorId(authorId);
        comment.setTargetType(CommentTargetType.MEDIA);
        comment.setMediaId(mediaId);
        comment.setContent(content);
        return comment;
    }
}
