# Stage 16 Cloudflare Tunnel Access

## Purpose

This document describes the first quasi-online HTTPS entry for the local or cloudlike Server environment.

The target shape is:

```text
Android REAL -> https://api.your-domain.com/ -> Cloudflare Tunnel -> Yingshi Server API
```

Cloudflare Tunnel is only an ingress path for the backend API. It does not change the Android contract, upload flow, media file endpoint, PostgreSQL schema, MinIO object layout, or storage provider abstraction.

References:

- Cloudflare Tunnel setup: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/get-started/
- Cloudflare Tunnel dashboard flow: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/get-started/create-remote-tunnel/index.md
- Cloudflare Tunnel run parameters: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/configure-tunnels/cloudflared-parameters/run-parameters/
- Cloudflare Tunnel service mode: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/configure-tunnels/local-management/as-a-service/
- Cloudflare Tunnel configuration: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/configure-tunnels/

## Recommended Mode For Current Windows Development

For the current daily workflow, use Windows `cloudflared`:

```text
PostgreSQL and MinIO: Docker Desktop
Server backend: Windows IDEA, active profile docker-local
cloudflared: Windows process or Windows service
Android REAL: https://api.your-domain.com/
```

This is the cleanest mode while the backend is launched from IDEA. The Cloudflare public hostname should map to:

```text
http://127.0.0.1:8080
```

Do not use the Docker `cloudflared` service to reach a Server process running in Windows IDEA unless you intentionally configure a host-gateway path. Inside a container, `localhost` means the container itself, not the Windows IDEA process.

## Public Exposure Boundary

Only publish one public hostname for the backend API, for example:

```text
https://api.your-domain.com/
```

Do not publish these services:

- PostgreSQL
- MinIO S3 API
- MinIO Console
- Navicat, pgAdmin, or any database management UI
- Object storage browser URLs or presigned provider URLs in Android

Android must continue to access only the Server API. Android must not direct-connect to MinIO or OSS and must not store object storage access keys or secret keys.

The current `/api/health` endpoint is public and is acceptable for smoke testing. Private APIs such as media files, media feed, posts, comments, uploads, and trash still require bearer auth. Seeing `AUTH_UNAUTHORIZED` from `/api/media/files/{mediaId}?variant=original` in a browser without a token is expected.

## Create The Tunnel In Cloudflare Dashboard

Use the Cloudflare Dashboard / Zero Trust Tunnels flow:

1. Open the Cloudflare Dashboard.
2. Go to the Tunnels page. In current Cloudflare docs this is shown as `Networking > Tunnels`; in Zero Trust UI it may appear under Networks / Tunnels.
3. Select `Create Tunnel`.
4. Choose `cloudflared`.
5. Name it, for example `yingshi-api-local`.
6. Select the host OS and copy the generated install/run command or token.
7. Add a public hostname:
   - Subdomain: `api`
   - Domain: your own Cloudflare-managed domain
   - Service type: `HTTP`
   - Service URL for Windows IDEA mode: `http://127.0.0.1:8080`
8. Wait for the connector status to become healthy.

Do not paste the tunnel token into committed files. Keep it in `.env`, Windows service configuration, or your shell history only if acceptable for your local machine.

## Windows cloudflared

For a no-domain temporary test, use Cloudflare Quick Tunnel:

```powershell
cloudflared tunnel --url http://127.0.0.1:8080
```

Cloudflare prints a temporary URL like:

```text
https://your-temporary.trycloudflare.com
```

Keep that PowerShell window open. If the command stops or you restart it, the temporary URL may change. In Android REAL, set:

```text
https://your-temporary.trycloudflare.com/
```

This quick URL is good for local app testing, but it is not a stable production hostname and has no uptime guarantee.

For a quick foreground test, install `cloudflared` on Windows and run:

```powershell
cloudflared.exe tunnel run --token <TUNNEL_TOKEN>
```

For service mode, open Command Prompt or PowerShell as Administrator and run the command Cloudflare shows. It is usually shaped like:

```powershell
cloudflared.exe service install <TUNNEL_TOKEN>
```

With Windows cloudflared, configure the public hostname service URL in Cloudflare Dashboard as:

```text
http://127.0.0.1:8080
```

Use this when Server runs from IDEA with the `docker-local` profile.

## Docker cloudflared

This repository includes an optional Compose override:

```text
docker-compose.cloudflare.yml
```

This mode is for running the full backend stack in Docker Compose:

```text
PostgreSQL container
MinIO container
Server container
cloudflared container
```

Copy the example environment file and fill only your local `.env`:

```powershell
Copy-Item .env.example .env
notepad .env
```

Set:

```text
CLOUDFLARE_TUNNEL_TOKEN=<token copied from Cloudflare Dashboard>
```

Then start the full stack:

```powershell
docker compose -f docker-compose.yml -f docker-compose.cloudflare.yml up -d --build
```

When `cloudflared` runs inside Compose, configure the Cloudflare public hostname service URL as:

```text
http://server:8080
```

Do not configure it as `http://127.0.0.1:8080` in this mode. That would point to the `cloudflared` container itself.

## IDEA docker-local Mode

This is the mode most useful while coding:

1. Start PostgreSQL and MinIO only:

```powershell
docker compose up -d postgres minio minio-init
```

2. In IDEA, run the Spring Boot application with active profile:

```text
docker-local
```

3. Confirm local health:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

4. Start Windows `cloudflared` with the token.
5. Confirm public health from a phone on mobile data:

```text
https://api.your-domain.com/api/health
```

For a temporary Quick Tunnel, run:

```powershell
cloudflared tunnel --url http://127.0.0.1:8080
```

Then use the printed `https://*.trycloudflare.com` URL for `/api/health` and Android REAL `baseUrl`.

The `docker-local` profile connects to PostgreSQL through `127.0.0.1:15432` and MinIO through `http://127.0.0.1:9000`.

## Server Proxy Header Notes

The `docker` and `docker-local` profiles set:

```yaml
server:
  forward-headers-strategy: framework
```

This lets Spring understand forwarded proxy headers such as `X-Forwarded-Proto` when future code needs the original public scheme or host. Current Android media URLs are still backend API paths and do not require Android to know object storage URLs.

Native Android API calls are not browser CORS requests. No CORS allowlist is required for Android REAL in this stage. If a browser admin console is added later, configure CORS explicitly for that web origin instead of opening all origins by default.

Do not change auth for Tunnel. Keep `/api/health` public and keep private media/post/comment/upload/trash APIs protected by bearer token.

## Android REAL Base URL

After the tunnel is healthy, Android REAL should use:

```text
https://api.your-domain.com/
```

Keep the trailing slash. In the app diagnostics page:

1. Open backend integration diagnostics.
2. Set repository mode to `REAL`.
3. Set `baseUrl` to `https://api.your-domain.com/`.
4. Log in with:
   - Account: `demo.a@yingshi.local`
   - Password: `demo123456`

Android still fetches media through backend endpoints such as:

```text
/api/media/files/{mediaId}?variant=original
/api/media/files/{mediaId}?variant=preview
/api/media/files/{mediaId}?variant=cover
```

Android must not use `http://127.0.0.1:9000`, MinIO Console URLs, OSS endpoints, bucket names, or object keys.

If you want to smoke test the current backend through either local HTTP or Quick Tunnel HTTPS without opening the app, run:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl https://your-temporary.trycloudflare.com
```

## True Device Smoke Test

Use this order:

1. Start PostgreSQL and MinIO:

```powershell
docker compose up -d postgres minio minio-init
```

2. Start Server:
   - Recommended while coding: IDEA with active profile `docker-local`
   - Full Docker stack: `docker compose up -d --build server`
3. Start `cloudflared`:
   - Windows IDEA mode: Windows `cloudflared.exe tunnel run --token <TUNNEL_TOKEN>` or Windows service
   - Full Docker mode: `docker compose -f docker-compose.yml -f docker-compose.cloudflare.yml up -d cloudflared`
4. On a phone using mobile network, open:

```text
https://api.your-domain.com/api/health
```

5. In Android diagnostics, set REAL `baseUrl` to:

```text
https://api.your-domain.com/
```

6. Log in with `demo.a@yingshi.local` / `demo123456`.
7. Pull the photo feed.
8. Upload one image.
9. Open original, preview, and cover through the app or through authenticated backend media requests.
10. Open MinIO Console locally at `http://127.0.0.1:9001` and confirm the object exists under bucket `yingshi-media`.
11. Use Navicat locally against PostgreSQL:
    - Host: `127.0.0.1`
    - Port: `15432`
    - Database: `yingshi`
    - User: `yingshi`
    - Password: `yingshi_dev_password`
12. Confirm media rows contain relative object keys, not URLs:

```sql
select id, storage_provider, bucket, original_object_key, preview_object_key, cover_object_key
from media
order by imported_at_millis desc
limit 20;
```

## Troubleshooting

`502`, `1033`, or Cloudflare tunnel page:

- Check `cloudflared` is running and shows a healthy connector in Cloudflare Dashboard.
- Check the public hostname is bound to the correct tunnel.
- Check the service URL:
  - Windows IDEA mode: `http://127.0.0.1:8080`
  - Docker cloudflared mode: `http://server:8080`
- Check Server is actually listening on port `8080`.
- For Quick Tunnel, keep the same PowerShell `cloudflared tunnel --url ...` process running; a stopped process means the temporary URL is dead.
- If you restart Quick Tunnel, copy the new `https://*.trycloudflare.com` URL into Android REAL again.

Android login fails:

- Confirm `baseUrl` has a trailing slash.
- Confirm the URL is HTTPS and the domain certificate is valid.
- Confirm `/api/health` works from the phone on mobile network.
- Check Server logs for auth or request errors.

Media does not open:

- Remember media endpoints require bearer auth. Browser access without `Authorization` should return `AUTH_UNAUTHORIZED`.
- Check `original_object_key`, `preview_object_key`, or `cover_object_key` in PostgreSQL.
- Check the object exists in MinIO bucket `yingshi-media`.
- Check Server logs around `/api/media/files/{mediaId}`.

Docker cloudflared cannot reach Server:

- Do not use `localhost` or `127.0.0.1` for the Cloudflare service URL when `cloudflared` is a container.
- Use `http://server:8080` with the provided Compose stack.

Navicat or MinIO Console from another device fails:

- This is expected after the Compose hardening. PostgreSQL and MinIO host ports are bound to `127.0.0.1` for local inspection only.
- Do not create public Cloudflare hostnames for PostgreSQL, MinIO API, or MinIO Console.
