# Album API Draft

## Status
- Server 3 minimal implementation target
- local-dev usable

## Purpose
Define the first working backend contract for album directory browsing and album-scoped post lists.

## Endpoints

### `GET /api/albums`
- use case: fetch album directory in the current space
- auth: required

Response:

```json
{
  "requestId": "req_albums",
  "data": [
    {
      "albumId": "album_001",
      "title": "Spring Window",
      "subtitle": "Light and slow daily fragments",
      "coverMediaId": "media_001",
      "postCount": 2
    }
  ]
}
```

### `GET /api/albums/{albumId}/posts`
- use case: fetch posts under one album in the current space
- auth: required

Response:

```json
{
  "requestId": "req_album_posts",
  "data": [
    {
      "postId": "post_001",
      "title": "Night Walk",
      "summary": "A quiet walk home",
      "contributorLabel": "Demo A and Demo B",
      "displayTimeMillis": 1777412800000,
      "albumIds": ["album_001", "album_002"],
      "coverMediaId": "media_001",
      "mediaCount": 3
    }
  ]
}
```

## Field Notes
- every album row belongs to one `spaceId`
- album APIs return post summaries, not full post detail media lists
- post album membership is updated through `PATCH /api/posts/{postId}`

## Error Code Placeholders
- `ALBUM_NOT_FOUND`
- `POST_NOT_FOUND`
- `AUTH_UNAUTHORIZED`

## Server 3 Notes
- no album create, rename, delete, or cover mutation in this stage
