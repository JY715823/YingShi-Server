# Album API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- base path: `/api/albums`
- bearer auth required for all endpoints
- current backend does not paginate album APIs
- album membership mutation is handled by `PATCH /api/posts/{postId}`, not a standalone album endpoint

## Endpoints

### `GET /api/albums`

Response data:

```json
[
  {
    "albumId": "album_001",
    "title": "Spring Window",
    "subtitle": "Light and slow daily fragments",
    "coverMediaId": "media_001",
    "postCount": 2
  }
]
```

### `GET /api/albums/{albumId}/posts`

Response data:

```json
[
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
```

## Notes
- album APIs return summaries only
- album detail media lists still belong to post detail
- there is no `PATCH /api/posts/{postId}/albums` endpoint in current backend

## Error Codes
- `ALBUM_NOT_FOUND`
- `POST_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
