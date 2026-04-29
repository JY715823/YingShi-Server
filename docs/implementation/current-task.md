# Current Task - Server 3 Album, Post, and Media Core

## Goal
Build the first real backend content foundation for album browsing, post detail, photo feed, and Gear Edit style post editing.

## In Scope
- `Album`, `Post`, `Media`, `PostMedia`, and `PostAlbum` data models
- space-scoped content ownership for every content table
- album directory API
- album post list API
- post detail API
- post create API
- post basic update API
- post cover update API
- post media order update API
- deduplicated global media feed API
- dev seed data for the shared demo space
- minimal contract sync for `post-api.md`, `media-api.md`, and `album-api.md`

## Product Intent
- all app content belongs to the authenticated user's shared `spaceId`
- one media item may appear in multiple posts
- global photo feed should be deduplicated by `mediaId`
- media order inside a post is controlled by `PostMedia.sortOrder`
- a post may belong to one or more albums
- a post cover points to one media item already attached to that post

## Server 3 API Shape
- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`
- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `GET /api/media/feed`

## Local Dev Assumptions
- authenticated requests use the Server 2 bearer token flow
- local profile uses H2 with JPA schema creation
- demo content should be rich enough to back album page, post detail, photo feed, and edit flows

## Non-Goals
- no upload
- no comments
- no delete or trash
- no OSS integration
- no real pagination
- no advanced search or filter system

## Done When
- authenticated user can list albums in the current space
- authenticated user can list posts under one album in the current space
- authenticated user can fetch post detail in the current space
- authenticated user can create and update posts in the current space
- authenticated user can update post cover and media order
- authenticated user can fetch a deduplicated media feed for the current space
- seeded content exists for the shared demo space
- project builds and tests pass
