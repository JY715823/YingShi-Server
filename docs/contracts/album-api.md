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
  "smallAlbumCount": 2
}
```

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

## Notes

- large albums expose summary information only
- small-album detail still belongs to `/api/small-albums/{smallAlbumId}`
- parent reassignment is handled by `PATCH /api/small-albums/{smallAlbumId}`

## Error Codes

- `ALBUM_NOT_FOUND`
- `SMALL_ALBUM_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
