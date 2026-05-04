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
                    "space_demo_shared",
                    "user_demo_a",
                    "post_001",
                    "这组现在用的是新下载的横图、竖图和方图，适合先检查照片流和帖子详情是否都换成了新素材。"
            ));
            commentRepository.save(createPostComment(
                    "comment_post_002",
                    "space_demo_shared",
                    "user_demo_b",
                    "post_001",
                    "长图这一组要重点看查看态：默认预览不能糊成一团，加载原图后应该能明显切到完整资源。"
            ));
            commentRepository.save(createPostComment(
                    "comment_post_003",
                    "space_demo_shared",
                    "user_demo_a",
                    "post_003",
                    "视频帖子只验证视频播放和封面稳定，不应该再出现“已加载原图”这种图片专属状态。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_001",
                    "space_demo_shared",
                    "user_demo_b",
                    "media_001",
                    "这张横图会同时出现在照片流和多个帖子里，用来验证共享媒体的评论、引用和删除恢复链路。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_002",
                    "space_demo_shared",
                    "user_demo_a",
                    "media_001",
                    "同一张媒体被复用时，原图加载和评论状态都应该按媒体本身稳定显示。"
            ));
            commentRepository.save(createMediaComment(
                    "comment_media_003",
                    "space_demo_shared",
                    "user_demo_b",
                    "media_004",
                    "这张是 1400x3200 长图，适合专门验证长图阅读和原图加载。"
            ));
            commentRepository.save(createPostComment(
                    "comment_other_secret",
                    "space_private_other",
                    "user_demo_b",
                    "post_other_secret",
                    "隐藏空间里的测试评论，用于跨空间可见性验证。"
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
