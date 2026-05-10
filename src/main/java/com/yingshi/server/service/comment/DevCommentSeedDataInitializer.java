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
                    "这条帖子用于检查照片流、相册封面和帖子详情。"
            ));
            commentRepository.save(createPostComment(
                    "comment_post_002",
                    "library_shared",
                    "user_demo_b",
                    "post_001",
                    "长图预览要稳定，只有真正加载原文件后才切换。"
            ));
            commentRepository.save(createPostComment(
                    "comment_post_003",
                    "library_shared",
                    "user_demo_a",
                    "post_003",
                    "视频帖子用于检查播放和封面稳定性。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_001",
                    "library_shared",
                    "user_demo_b",
                    "media_001",
                    "同一个媒体可以出现在照片流和多个帖子里，媒体评论仍按 mediaId 隔离。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_002",
                    "library_shared",
                    "user_demo_a",
                    "media_001",
                    "原文件加载和评论状态都跟随当前媒体。"
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
                    "用于共享图库隔离检查的内部评论。"
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
