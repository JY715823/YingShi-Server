# Push FCM Contract

YingShi uses Firebase Cloud Messaging for visible partner notifications and lightweight life-console invalidation.

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

Local Docker Compose keeps the Firebase Admin SDK JSON outside git and mounts it into the Linux container:

```dotenv
FCM_ENABLED=true
FCM_DRY_RUN=false
FCM_PROJECT_ID=your-firebase-project-id
FCM_SERVICE_ACCOUNT_HOST_PATH=E:/Secrets/yingshi-firebase-adminsdk.json
FCM_SERVICE_ACCOUNT_PATH=/run/secrets/firebase-service-account.json
FCM_SERVICE_ACCOUNT_JSON_BASE64=
```

`FCM_SERVICE_ACCOUNT_JSON_BASE64` may be used instead of the mounted file path for deployment secret managers. Leave it empty for the local mounted-secret flow; if it contains invalid base64, Firebase initialization fails before the path fallback can be used.
