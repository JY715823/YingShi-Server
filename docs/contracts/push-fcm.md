# Push FCM Contract

YingShi uses Firebase Cloud Messaging only as a lightweight invalidation channel for the life console widgets.

## Device Token Registration

`POST /api/push/device-tokens`

Authenticated request body:

```json
{
  "platform": "android",
  "token": "fcm-registration-token"
}
```

Response:

```json
{
  "tokenId": "push_token_xxx",
  "platform": "android",
  "lastSeenAtMillis": 1760000000000,
  "enabled": true
}
```

Registering the same token again reassigns it to the current user and library, marks it enabled, and updates `lastSeenAtMillis`.

## Data Message

When today-life-console media or bowel records change, the backend sends to other users' enabled devices in the same library:

```json
{
  "type": "life_console.changed",
  "event": "life_console.changed",
  "libraryId": "library_shared",
  "actorUserId": "user_demo_a",
  "reason": "media_added",
  "occurredAtMillis": "1760000000000"
}
```

The Android client treats this as an invalidation signal and fetches the latest widget snapshot.

## Server Configuration

```yaml
app:
  push:
    fcm:
      enabled: true
      dry-run: false
      project-id: your-firebase-project-id
      service-account-path: E:\Secrets\yingshi-firebase-adminsdk.json
```

`service-account-json-base64` may be used instead of a file path for deployment secret managers.
