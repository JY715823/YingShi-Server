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
                    "这条帖子用了更新后的本地示例媒体，方便验证照片流和帖子详情的展示。"
            ));
            commentRepository.save(createPostComment(
                    "comment_post_002",
                    "library_shared",
                    "user_demo_b",
                    "post_001",
                    "长图应保持预览加载稳定，只在真实文件载入后才切换到原图。"
            ));
            commentRepository.save(createPostComment(
                    "comment_post_003",
                    "library_shared",
                    "user_demo_a",
                    "post_003",
                    "视频帖子主要验证播放和封面稳定性，不涉及图片原图加载逻辑。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_001",
                    "library_shared",
                    "user_demo_b",
                    "media_001",
                    "共享媒体可以出现在照片流和多个帖子中，但评论按 mediaId 隔离。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_002",
                    "library_shared",
                    "user_demo_a",
                    "media_001",
                    "原图加载和评论状态应跟随这条媒体本身。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_003",
                    "library_shared",
                    "user_demo_b",
                    "media_004",
                    "这张 1400x3200 的长图适合纵向浏览和原图加载验证。"
            ));
            commentRepository.save(createPostComment(
                    "comment_other_secret",
                    "library_private_other",
                    "user_demo_b",
                    "post_other_secret",
                    "内部隔离用例，用于共享库访问权限验证。"
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
