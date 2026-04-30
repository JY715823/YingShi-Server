# Current Task - Stage 11B-3 Backend Support for Real Feed Upload Trash

## Goal
Support Android real-backend integration for media feed, upload/import, trash, Chinese seed data, and same-space comment operations.

## Scope
- Chinese dev seed data
- comment edit/delete permission policy update
- media feed stability check
- upload local-dev API stability check
- trash API stability check
- minimal contracts/docs update

## Product intent
- Dev seed data should be readable and natural in Chinese.
- Yingshi is a shared-space app.
- Members in the same space may edit/delete comments; future notification should inform the original author.
- Backend APIs should support Android REAL mode without breaking existing fake/local development.

## Do not do
- no real OSS
- no real Android system media delete
- no complex role system
- no real notification implementation
- no large unrelated refactor

## Done when
- Server starts
- Tests pass
- Seed albums/posts/comments are Chinese
- Same-space comment edit/delete is allowed
- Media feed API supports Android REAL mode
- Upload/trash APIs remain compatible with Android contracts

## Stage Notes
- keep demo accounts unchanged: `demo.a@yingshi.local` / `demo123456`, `demo.b@yingshi.local` / `demo123456`
- comment update/delete is allowed for any member inside the same space
- add a TODO only for future "notify original author after non-author edit/delete"
