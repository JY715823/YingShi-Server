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
    @Order(3)
    ApplicationRunner commentSeedRunner(CommentRepository commentRepository) {
        return args -> {
            if (commentRepository.count() > 0) {
                return;
            }

            commentRepository.save(createPostComment(
                    "comment_post_001",
                    "space_demo_shared",
                    "user_demo_a",
                    "post_001",
                    "The night colors feel warm."
            ));
            commentRepository.save(createPostComment(
                    "comment_post_002",
                    "space_demo_shared",
                    "user_demo_b",
                    "post_001",
                    "The walking rhythm fits the cover choice."
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_001",
                    "space_demo_shared",
                    "user_demo_b",
                    "media_001",
                    "This frame should stay in the shared media flow."
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_002",
                    "space_demo_shared",
                    "user_demo_a",
                    "media_001",
                    "Agreed, the crop still works in both posts."
            ));
            commentRepository.save(createPostComment(
                    "comment_other_secret",
                    "space_private_other",
                    "user_demo_b",
                    "post_other_secret",
                    "Hidden space comment"
            ));
        };
    }

    private CommentEntity createPostComment(
            String id,
            String spaceId,
            String authorId,
            String postId,
            String content
    ) {
        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setSpaceId(spaceId);
        comment.setAuthorId(authorId);
        comment.setTargetType(CommentTargetType.POST);
        comment.setPostId(postId);
        comment.setContent(content);
        return comment;
    }

    private CommentEntity createMediaComment(
            String id,
            String spaceId,
            String authorId,
            String mediaId,
            String content
    ) {
        CommentEntity comment = new CommentEntity();
        comment.setId(id);
        comment.setSpaceId(spaceId);
        comment.setAuthorId(authorId);
        comment.setTargetType(CommentTargetType.MEDIA);
        comment.setMediaId(mediaId);
        comment.setContent(content);
        return comment;
    }
}
