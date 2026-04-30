# Current Task - Stage 11B-4 Backend Support for Real Feature Completion

## Goal
Support Android REAL-mode completion for Gear Edit, media management, upload/import, and trash refresh.

## Scope
- post basic info update stability
- post album relation update
- set cover
- media order update
- add uploaded media to post
- directory delete
- system delete
- trash refresh / restore / remove / undo stability
- Chinese seed data retention
- same-space comment edit/delete policy retention
- minimal docs update
- integration smoke should cover Gear Edit and media-management core paths where practical

## Product intent
- Backend should support Android REAL mode for core app-content flows.
- Same-space members may edit/delete comments.
- Fake/dev seed data should stay natural Chinese.
- Trash and delete semantics must remain consistent.

## Do not do
- no real OSS
- no real Android system delete
- no real notification implementation
- no complex role system
- no broad refactor

## Done when
- Server starts
- Tests pass
- Gear Edit related APIs support Android REAL mode
- Media management APIs support Android REAL mode
- Upload/import remains compatible
- Trash APIs remain compatible

## Implementation notes
- Android REAL Gear Edit depends on `PATCH /api/posts/{postId}` for title, summary, display time, and album membership.
- Android REAL media management depends on cover update, media-order update, post-media relation delete, and global media delete semantics.
- Smoke verification should cover post update, cover switch, media order save, directory delete, and trash restore with the current seeded data.
