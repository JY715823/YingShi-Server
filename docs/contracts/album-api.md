# Album API Contract

## Status

- unified with current `yingshi-server` code
- outwardly this API represents large albums

## Base Rules

- base path: `/api/albums`
- bearer auth required
- album APIs currently do not paginate

## Album DTO

```json
{
  "albumId": "album_001",
  "title": "Spring Window",
  "subtitle": "Light and slow daily fragments",
  "coverMediaId": "media_001",
  "smallAlbumCount": 2,
  "systemKey": null,
  "includeInPhotoFeed": true
}
```

字段说明：

- `systemKey`
  - 非空表示这是系统维护的大相册
  - 系统相册只允许读取，不允许重命名或删除
- `includeInPhotoFeed`
  - 表示该大相册是否参与照片流相关展示语义

## Small Album Summary DTO

```json
{
  "smallAlbumId": "post_001",
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "eventStartedAtMillis": 1777412800000,
  "eventEndedAtMillis": null,
  "displayTimeSource": "MANUAL",
  "albumId": "album_001",
  "coverMediaId": "media_001",
  "mediaCount": 3
}
```

## Endpoints

### `GET /api/albums`

Response:

- returns `List<AlbumDto>`

### `GET /api/albums/{albumId}/small-albums`

Response:

- returns `List<PostSummaryDto>`
- each item belongs to exactly one parent `albumId`

### `PATCH /api/albums/{albumId}`

Request:

```json
{
  "title": "Renamed Album"
}
```

Response:

- returns updated `AlbumDto`

Behavior:

- only updates the large album title
- if the target album is system-managed, returns `DELETE_CONFLICT`

### `DELETE /api/albums/{albumId}`

Response:

- returns the created `TrashItemDto`
- `itemType = "largeAlbumDeleted"`

Behavior:

- soft-deletes the large album itself
- moves its active child small albums into the same trash operation
- does not delete media entities or media files
- if the target album is system-managed, returns `DELETE_CONFLICT`

## Notes

- large albums expose summary information only
- small-album detail still belongs to `/api/small-albums/{smallAlbumId}`
- parent reassignment is handled by `PATCH /api/small-albums/{smallAlbumId}`
- active large-album queries exclude rows where `deletedAt` is non-null

## Error Codes

- `ALBUM_NOT_FOUND`
- `SMALL_ALBUM_NOT_FOUND`
- `DELETE_CONFLICT`
- `AUTH_UNAUTHORIZED`
