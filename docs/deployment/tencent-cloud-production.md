# Tencent Cloud Production Notes

This note is for the planned small production deployment where Tencent Cloud CVM runs the backend and PostgreSQL, COS stores media objects, and CDN serves media traffic.

## Target Shape

- CVM: run `server`, `postgres`, and an HTTPS reverse proxy. Keep PostgreSQL bound to localhost or private network only.
- COS: replace MinIO for original media, previews, covers, avatars, and upload objects.
- CDN: accelerate COS object delivery. The backend still owns auth, media metadata, upload task creation, direct upload confirmation, signed media URLs, trash, and fallback `/api/media/files/{mediaId}` access.
- Android release builds must set `YINGSHI_RELEASE_API_BASE_URL=https://your-api-domain/`; release builds fail without it.

## Required Environment

Set production values with real secrets:

```env
SPRING_PROFILES_ACTIVE=docker,prod
APP_PRODUCTION_SAFETY_ENABLED=true
APP_AUTH_JWT_SECRET=replace-with-a-long-random-secret

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/yingshi
SPRING_DATASOURCE_USERNAME=yingshi
SPRING_DATASOURCE_PASSWORD=replace-with-a-strong-password

STORAGE_PROVIDER=cos
STORAGE_BUCKET=your-cos-bucket
STORAGE_ENDPOINT=https://your-bucket.cos.ap-guangzhou.myqcloud.com
STORAGE_REGION=ap-guangzhou
STORAGE_ACCESS_KEY=replace-with-cos-secret-id
STORAGE_SECRET_KEY=replace-with-cos-secret-key
STORAGE_FORCE_PATH_STYLE=false
STORAGE_DIRECT_UPLOAD_ENABLED=true

STORAGE_CDN_DOMAIN=https://media.example.com
STORAGE_CDN_AUTH_KEY=replace-with-cdn-type-d-key
STORAGE_CDN_SIGN_PARAM=sign
STORAGE_CDN_TIMESTAMP_PARAM=t
STORAGE_SIGNED_URL_TTL=PT15M
```

Use Tencent CDN TypeD URL authentication on the CDN domain. The backend generates `FileName?sign=md5hash&t=timestamp` URLs, with `md5hash = md5(cdnAuthKey + uri + timestamp)`.

## Flyway Baseline Warning

`V5__hard_cut_small_albums.sql` was changed from a destructive `truncate` migration to a conservative data-preserving migration. This is correct for a new Tencent Cloud database, but it changes the checksum for any database that already recorded the old V5.

For a fresh Tencent Cloud PostgreSQL database:

1. Start from an empty database.
2. Run the current Flyway migrations normally.
3. Keep `spring.jpa.hibernate.ddl-auto=validate`.

For an existing development database that already ran old V5:

1. Back up PostgreSQL first.
2. If the schema already matches the current code, run Flyway repair for the changed V5 checksum.
3. If the schema or data is uncertain, restore into a clean database and run the current migrations there before switching over.

Do not apply the old `truncate` V5 to any database containing real media metadata, comments, trash, upload tasks, sessions, or notifications.

## Acceptance Checks

- `/api/health` reports DB and storage checks.
- Upload flow returns `uploadMethod=presigned-put`, Android PUTs to COS, then backend confirm verifies object metadata and creates media.
- Media DTOs include stable `access[].cacheKey` and short-lived `access[].signedUrl`.
- Android image and video caches use stable keys, not full signed URLs.
- PostgreSQL daily backup and COS lifecycle/version protection are configured before real use.
