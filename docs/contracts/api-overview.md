# API Overview Draft

## Status
- Stage 11.1 draft only
- no live backend required
- no final auth or pagination behavior locked yet

## Purpose
This document defines the first client-side API boundary for future backend integration.
The goal is to keep UI models separate from transport models while preserving the current fake-first app flow.

## Base URL
- placeholder only: `https://api-placeholder.yingshi.local/`
- must be configurable
- must not hardcode a production server

## Auth
- bearer token placeholder
- request header draft:
  - `Authorization: Bearer <token>`
- auth contract details now live in `auth-api.md`
- token acquisition and refresh remain placeholder-only in Stage 11.2

## Envelope Draft

Successful response draft:

```json
{
  "requestId": "req_123",
  "data": {},
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

Error response draft:

```json
{
  "requestId": "req_123",
  "error": {
    "code": "NOT_IMPLEMENTED",
    "message": "Placeholder error",
    "details": null
  }
}
```

## Naming Rules
- JSON fields use `camelCase`
- IDs use string form such as `mediaId`, `postId`, `commentId`
- timestamps use UTC milliseconds or ISO-8601 string
- booleans use explicit names such as `isDeleted`, `isRead`, `hasMore`

## Pagination Draft
- page-number and cursor styles are both reserved
- list endpoints in Stage 11.1 expose placeholder params:
  - `page`
  - `pageSize`
  - `cursor`
- final backend can choose one style later, but the contract docs should mention the placeholder path now

## Error Code Placeholders
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `RATE_LIMITED`
- `SERVER_ERROR`
- `NOT_IMPLEMENTED`

## Stage 11.1 Draft-Only APIs
- upload token issue and upload completion
- delete / restore mutations
- comment create / update / delete
- notification APIs are not part of this contract pass yet
