# Current Task: Preview Quality And Cover Generation

## Background

The Android photo feed now depends on stable preview-sized media URLs for fast browsing. Existing local previews were small and lazily generated, while video media often exposed the original video URL as its cover. This pass improves local-dev preview generation without introducing object storage, transcoding, HLS, or a new media pipeline.

## Goals

1. Generate clearer image previews while preserving the original aspect ratio.
2. Handle vertical, horizontal, and long images without stretching or accidental cropping.
3. Generate video cover JPEGs when local `ffmpeg` is available.
4. Keep upload and recovery flows tolerant: preview generation failures must not fail media ingestion.
5. Backfill missing or outdated preview/cover URLs during dev originals recovery.

## Scope

- Local storage preview file naming and image resize quality.
- EXIF orientation handling for image preview generation.
- Best-effort video cover extraction through the local `ffmpeg` command.
- Upload and dev recovery preview/cover warm-up.
- Media DTO URL mapping for `previewUrl` and `coverUrl`.

## Non Goals

- No Android list loading or cache-cleaning redesign.
- No OSS, HLS, transcoding queue, or cloud storage integration.
- No upload center, trash, post detail, pagination, or Viewer playback-control changes.

## Acceptance

1. Uploaded/imported images use clearer preview URLs and keep correct proportions.
2. Long, vertical, and horizontal images are not stretched or cropped by the server preview.
3. Uploaded/imported videos expose a cover URL when a local cover can be generated.
4. Recovery can repair missing preview/cover metadata and generate missing files best-effort.
5. Android `assembleDebug` and Server `mvnw test` pass.
