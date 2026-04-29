# Current Task - Server 4 Comment Core

## Goal
Implement backend comment APIs for post comments and media comments while strictly keeping the two streams separated.

## In Scope
- `Comment` entity with post/media target split
- post comment list API
- media comment list API
- create comment API
- update comment API
- soft delete comment API
- page / size pagination
- latest-first ordering
- DTO and mapper
- minimal contract sync for `comment-api.md`
- dev seed comments for current shared space

## Product Intent
- post comments and media comments must never mix into one stream
- post comments are keyed by `postId`
- media comments are keyed by `mediaId`
- one `mediaId` keeps one shared media-comment flow across posts
- comments belong to the authenticated user's `spaceId`
- edit and delete should stay inside the author's own comment scope

## Server 4 API Shape
- `GET /api/posts/{postId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/posts/{postId}/comments`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

## Local Dev Assumptions
- authenticated requests use the existing Server 2 bearer token flow
- comments live on top of the current Server 3 post/media data
- soft delete is enough for this stage
- default page size is `10`

## Non-Goals
- no replies
- no nested threads
- no quotes
- no notifications
- no likes
- no rich text

## Done When
- authenticated user can fetch post comments in current space
- authenticated user can fetch media comments in current space
- authenticated user can create comments in current space
- authenticated user can edit own comments
- authenticated user can soft delete own comments
- post and media comment flows remain separated
- project builds and tests pass
