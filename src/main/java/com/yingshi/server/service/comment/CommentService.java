package com.yingshi.server.service.comment;

import com.yingshi.server.common.IdGenerator;
import com.yingshi.server.common.auth.AuthenticatedUser;
import com.yingshi.server.common.exception.ApiException;
import com.yingshi.server.common.exception.ErrorCode;
import com.yingshi.server.domain.CommentEntity;
import com.yingshi.server.domain.CommentTargetType;
import com.yingshi.server.domain.UserEntity;
import com.yingshi.server.dto.comment.CommentDto;
import com.yingshi.server.dto.comment.CommentPageResponse;
import com.yingshi.server.dto.comment.CreateCommentRequest;
import com.yingshi.server.dto.comment.UpdateCommentRequest;
import com.yingshi.server.mapper.CommentMapper;
import com.yingshi.server.repository.CommentRepository;
import com.yingshi.server.repository.MediaRepository;
import com.yingshi.server.repository.PostRepository;
import com.yingshi.server.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            MediaRepository mediaRepository,
            UserRepository userRepository,
            CommentMapper commentMapper
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
        this.commentMapper = commentMapper;
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getPostComments(String postId, Integer page, Integer size, AuthenticatedUser currentUser) {
        requirePost(postId, currentUser.spaceId());
        Page<CommentEntity> comments = commentRepository.findBySpaceIdAndTargetTypeAndPostId(
                currentUser.spaceId(),
                CommentTargetType.POST,
                postId,
                buildPageRequest(page, size)
        );
        return toPageResponse(comments, normalizePage(page), normalizeSize(size));
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getMediaComments(String mediaId, Integer page, Integer size, AuthenticatedUser currentUser) {
        requireMedia(mediaId, currentUser.spaceId());
        Page<CommentEntity> comments = commentRepository.findBySpaceIdAndTargetTypeAndMediaId(
                currentUser.spaceId(),
                CommentTargetType.MEDIA,
                mediaId,
                buildPageRequest(page, size)
        );
        return toPageResponse(comments, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public CommentDto createPostComment(String postId, CreateCommentRequest request, AuthenticatedUser currentUser) {
        requirePost(postId, currentUser.spaceId());
        CommentEntity comment = new CommentEntity();
        comment.setId(IdGenerator.newId("comment"));
        comment.setSpaceId(currentUser.spaceId());
        comment.setAuthorId(currentUser.userId());
        comment.setTargetType(CommentTargetType.POST);
        comment.setPostId(postId);
        comment.setContent(request.content().trim());
        return toCommentDto(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto createMediaComment(String mediaId, CreateCommentRequest request, AuthenticatedUser currentUser) {
        requireMedia(mediaId, currentUser.spaceId());
        CommentEntity comment = new CommentEntity();
        comment.setId(IdGenerator.newId("comment"));
        comment.setSpaceId(currentUser.spaceId());
        comment.setAuthorId(currentUser.userId());
        comment.setTargetType(CommentTargetType.MEDIA);
        comment.setMediaId(mediaId);
        comment.setContent(request.content().trim());
        return toCommentDto(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto updateComment(String commentId, UpdateCommentRequest request, AuthenticatedUser currentUser) {
        CommentEntity comment = requireComment(commentId, currentUser.spaceId());
        if (comment.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Deleted comment cannot be edited.");
        }
        // TODO: notify the original author when another member in the same space edits this comment.
        comment.setContent(request.content().trim());
        return toCommentDto(commentRepository.save(comment));
    }

    @Transactional
    public CommentDto deleteComment(String commentId, AuthenticatedUser currentUser) {
        CommentEntity comment = requireComment(commentId, currentUser.spaceId());
        if (comment.getDeletedAt() == null) {
            // TODO: notify the original author when another member in the same space deletes this comment.
            comment.setDeletedAt(Instant.now());
            comment.setContent(null);
            comment = commentRepository.save(comment);
        }
        return toCommentDto(comment);
    }

    private CommentPageResponse toPageResponse(Page<CommentEntity> comments, int page, int size) {
        Set<String> authorIds = comments.getContent().stream().map(CommentEntity::getAuthorId).collect(Collectors.toSet());
        Map<String, UserEntity> authors = userRepository.findByIdIn(authorIds)
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, user -> user));

        return new CommentPageResponse(
                comments.getContent().stream()
                        .map(comment -> commentMapper.toCommentDto(comment, authors.get(comment.getAuthorId())))
                        .toList(),
                page,
                size,
                comments.getTotalElements(),
                comments.hasNext()
        );
    }

    private CommentDto toCommentDto(CommentEntity comment) {
        UserEntity author = userRepository.findById(comment.getAuthorId()).orElse(null);
        return commentMapper.toCommentDto(comment, author);
    }

    private PageRequest buildPageRequest(Integer page, Integer size) {
        return PageRequest.of(
                normalizePage(page) - 1,
                normalizeSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private CommentEntity requireComment(String commentId, String spaceId) {
        return commentRepository.findByIdAndSpaceId(commentId, spaceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.COMMENT_NOT_FOUND, "Comment was not found."));
    }

    private void requirePost(String postId, String spaceId) {
        if (postRepository.findByIdAndSpaceIdAndDeletedAtIsNull(postId, spaceId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.COMMENT_TARGET_NOT_FOUND, "Post target was not found.");
        }
    }

    private void requireMedia(String mediaId, String spaceId) {
        if (mediaRepository.findByIdAndSpaceIdAndDeletedAtIsNull(mediaId, spaceId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.COMMENT_TARGET_NOT_FOUND, "Media target was not found.");
        }
    }
}
