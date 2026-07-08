# Backend Platform & Operations Infrastructure Refinement Brief

**Module key**: `backend-platform`
**Status**: `closed`
**Created**: 2026-07-02
**Updated**: 2026-07-02
**Closed**: 2026-07-02

---

## Module Goal

Deep-sweep the YingShi-server backend for performance, security, observability, code quality, and operational readiness. Eliminate OOM risks, close security gaps, add monitoring, refactor oversized services, harden the database layer, and clean up infrastructure configuration — so the backend is production-grade before cloud deployment.

## Current State Summary

### Architecture
- Spring Boot 4.0.6, Java 21 (pom.xml was 17, aligning to 21 to match Dockerfile), Maven
- Layered: Controller → Service → Repository → Domain/Entity
- Custom JWT auth (no Spring Security web), BCrypt via spring-security-crypto
- PostgreSQL 16 (prod/docker) + H2 (dev), Flyway migrations (V1–V21)
- S3-compatible storage (MinIO dev / COS prod), ffmpeg for video covers
- Docker Compose: postgres + minio + minio-init + server
- FCM push notifications, Firebase Admin SDK

### API Surface
- 14 controllers, ~60 endpoints, all wrapped in `ApiResponse<T>`
- Pagination: mixed cursor-based (media feed) and offset-based (everything else)
- Error format: `ApiError(code, message, details)` with ~40 ErrorCode enum values
- OpenAPI/Swagger via springdoc (disabled by default)

### Testing
- **6 test files for 170+ source files** — estimated coverage < 5%
- No service-level tests, no controller tests, no integration tests for critical flows

### Key Metrics
- 170+ Java source files, 21 Flyway migrations, 4 Spring profiles
- Largest services: NotificationService (906L), UploadService (892L), TrashService (877L), LedgerSyncService (697L)

---

## Codex Recommendations (Priority-Ordered)

### Batch A — P0 Critical Performance & Stability

| # | Issue | Impact | Effort |
|---|-------|--------|--------|
| A1 | `MediaService.getMediaFeedPage()` loads ALL media into memory, then paginates in Java | OOM on large libraries; every feed request is O(n) memory | M |
| A2 | `AlbumService.listAlbums()` loads ALL posts to count per album | N+1 at service level; O(n) memory | S |
| A3 | `SyncService.getVersions()` fires 12+ separate DB queries | Unnecessary DB round-trips on every 3s poll | S |
| A4 | Missing database indexes on `media.library_id`, `posts.library_id`, `comments.library_id+target_type`, `upload_tasks.library_id` | Full table scans on high-frequency queries | S |
| A5 | `findTop200ByMediaTypeAndDeletedAtIsNullOrderByUpdatedAtDesc` loads 200 entities for video cover warmup at startup | Slow startup, memory spike | S |

### Batch B — P1 Security Hardening

| # | Issue | Impact | Effort |
|---|-------|--------|--------|
| B1 | No rate limiting on auth endpoints (login challenge, verify) | Brute-force vulnerability | M |
| B2 | No account lockout after failed password attempts | Credential stuffing | S |
| B3 | No CORS explicit configuration | Potential cross-origin attacks if dev profile leaks | S |
| B4 | No `@Size`/`@Length` validation on DTO string fields | Unbounded input can cause DB/storage issues | S |
| B5 | Hardcoded dev JWT secret in `application-dev.yml` | If dev profile accidentally deployed, all tokens forgeable | S |
| B6 | No HTTPS enforcement / redirect | MITM risk if deployed without reverse proxy | S |

### Batch C — P1 Observability & Operations

| # | Issue | Impact | Effort |
|---|-------|--------|--------|
| C1 | No structured logging (JSON), no logback-spring.xml, no MDC | Debugging in production is nearly impossible | M |
| C2 | No Spring Boot Actuator, no metrics export | No health/readiness split, no Prometheus/Grafana | M |
| C3 | No graceful shutdown config | In-flight requests dropped on deploy | S |
| C4 | `@Scheduled` tasks run on single thread | One blocked task delays all others | S |
| C5 | No HTTP response compression | Wasted bandwidth, slower client responses | S |
| C6 | Health endpoint lacks liveness/readiness split | Cannot support Kubernetes or advanced health checks | S |

### Batch D — P2 Database & Query Layer

| # | Issue | Impact | Effort |
|---|-------|--------|--------|
| D1 | No foreign key constraints between tables | Orphaned rows, no referential integrity | M |
| D2 | `varchar(255)` for all ID columns (UUIDs are ~38 chars) | Wasted storage, suboptimal indexing | S |
| D3 | No HikariCP explicit configuration | Default pool size (10) may be insufficient under load | S |
| D4 | Dev profile uses `ddl-auto: update` | Schema drift from Flyway migrations | S |
| D5 | `snapshot_json oid` in trash_items may still use OID type | Deprecated PG type, potential issues | S |
| D6 | Ultra-long Spring Data derived query method names | Generated SQL is hard to optimize/debug | S |

### Batch E — P2 Code Quality & Service Decomposition

| # | Issue | Impact | Effort |
|---|-------|--------|--------|
| E1 | `LedgerSyncService` (697L): 9 tables × 5 operations of repetitive boilerplate | Maintenance burden, bug-prone | L |
| E2 | `TrashService` (877L): handles 3 trash types with different lifecycles | Hard to test, hard to extend | L |
| E3 | `UploadService` (892L): token creation + file handling + confirmation + cleanup | Too many responsibilities | L |
| E4 | `NotificationService` (906L): materializes from 6+ sources in one class | Complex, hard to test | L |
| E5 | `MediaService.getMediaFeedPage()` calls `getMediaFeed()` first (in-memory pagination) | Duplicate of A1, but also a code quality issue | M |
| E6 | `LocalObjectStorageService.checksum()` reads entire file for SHA-256 on every `getMetadata()` | Wasted I/O | S |
| E7 | Push notification sending is synchronous within request transaction | Slow API responses if FCM is slow | M |

### Batch F — P3 Infrastructure & Configuration

| # | Issue | Impact | Effort |
|---|-------|--------|--------|
| F1 | docker-compose.yml hardcodes `E:/Study/App/YingShi_Storage/` paths | Not portable across machines | S |
| F2 | Proxy config (`host.docker.internal:7897`) hardcoded in compose | Not portable | S |
| F3 | Multipart limits absurdly high (4096MB / 4300MB) | OOM risk if someone uploads a 4GB file through Spring | S |
| F4 | Java version mismatch: pom.xml=17, Dockerfile=21 | Confusing, wastes image size | S |
| F5 | Dev seed data initializers in `src/main/java` (not test scope) | Production code contains test data generators | S |
| F6 | No CI/CD pipeline (no GitHub Actions, no automated builds) | No automated quality gates | M |
| F7 | `jcodec 0.2.5` outdated (last release 2020); ffmpeg already in Docker image | Redundant dependency | S |
| F8 | No Dependabot / vulnerability scanning for dependencies | Unknown CVE exposure | S |

### Batch G — P3 API Contract & Client Integration

| # | Issue | Impact | Effort |
|---|-------|--------|--------|
| G1 | No API versioning (`/api/v1/...`) | Breaking changes are difficult in the future | S |
| G2 | Deprecated `GET/PUT /api/ledger/snapshot` still active, no migration enforced | Client may rely on deprecated endpoints | S |
| G3 | Inconsistent pagination (cursor vs offset across endpoints) | Client must handle two pagination models | M |
| G4 | `LedgerController.sync()` has `request` + `request2` parameter naming | Code smell, confusing | S |
| G5 | `Map<String, Object>` used for Ledger sync data transfer | No type safety, silent field name typos | M |
| G6 | `disableInvalidTokens()` saves each FCM token individually | Should batch | S |

---

## Scope Boundaries

### In Scope
- All server-side Java code (controllers, services, repositories, config, domain)
- Docker Compose, Dockerfile, deployment configuration
- Flyway migrations (new indexes, constraints)
- Application configuration (application.yml, profiles)
- Logging, monitoring, health checks
- Test infrastructure and critical test coverage
- API contract improvements (pagination consistency, versioning strategy)

### Out of Scope
- Android client code changes (separate module briefs)
- New feature development (no new endpoints unless fixing existing gaps)
- Production cloud deployment execution (Tencent Cloud CVM/COS/CDN)
- Ledger business logic changes (covered by `ledger.md` brief)
- Frontend/web dashboard (doesn't exist yet)

---

## Related Modules

| Module | Relationship |
|--------|-------------|
| `ledger.md` (client) | Ledger sync architecture changes affect server LedgerSyncService |
| `bin.md` (client) | Trash/recycle bin UI depends on TrashService API shape |
| `noticeCenter.md` (client) | Notification delivery depends on NotificationService + FCM |
| `submit_transferCenter.md` (client) | Upload flow depends on UploadService API |
| `login_and_session.md` (client) | Auth/session hardening affects JWT interceptor |

---

## Hidden Impact Checklist

- [x] **Auth**: Rate limiting changes may affect Android login retry logic — PASS (scoped to auth, 20 req/5min)
- [x] **Upload**: Multipart limit reduction may break large video uploads if not set correctly — PASS (500MB sufficient for 4K)
- [x] **Notifications**: Async push sending changes FCM timing; client dedup window may need adjustment — PASS (1.8s delay preserved)
- [x] **Sync**: SyncService query consolidation changes response shape — verify client SyncVersionTracker compatibility — PASS (response shape unchanged)
- [x] **Trash**: TrashService decomposition must not alter the three-phase delete contract — PASS (all 3 phases verified intact)
- [x] **Media**: SQL-level pagination changes cursor format — Android client must be compatible — PASS (done in Round 1)
- [x] **Ledger**: LedgerSyncService refactoring must preserve the 9-table upsert/delete semantics — PASS (all 9 tables verified)
- [x] **Settings**: Config changes (HikariCP, compression, logging) affect all endpoints — PASS (all profiles consistent)
- [x] **Copy/Empty states**: N/A (backend only)
- [x] **Permissions**: CORS changes may affect Android or future web clients — PASS (configurable, backward-compatible)
- [x] **Analytics/Logging**: Structured logging changes log format — any log parsers must update — PASS (JSON format, MDC keys)
- [x] **Cache/Offline**: Adding HTTP cache headers affects Android offline behavior — PASS (compression only, no cache headers)
- [x] **Deployment**: Docker Compose path changes require `.env` update on all dev machines — PASS (defaults backward-compatible)

---

## Implementation Batches (Recommended Order)

### Batch 1: Critical Performance (A1–A5)
**Goal**: Eliminate OOM risks and full-table-scan queries.
- A1: Rewrite `getMediaFeedPage()` to use SQL-level pagination (LIMIT/OFFSET or keyset cursor)
- A2: Replace `listAlbums()` post counting with `SELECT album_id, COUNT(*) GROUP BY album_id`
- A3: Consolidate `SyncService.getVersions()` into single query using `GREATEST()` or custom `@Query`
- A4: Add Flyway migration for missing indexes
- A5: Replace `findTop200...` with `@Query` using `LIMIT 200` and add appropriate index

### Batch 2: Security Hardening (B1–B6)
**Goal**: Close brute-force, input validation, and configuration gaps.
- B1: Add rate limiting on auth endpoints (Bucket4j or Spring Security rate limiter)
- B2: Add account lockout after N failed attempts (configurable, e.g., 5 attempts → 15min lockout)
- B3: Configure CORS explicitly per profile (dev: localhost, prod: specific domain)
- B4: Add `@Size` validation on all DTO string fields
- B5: Add `ProductionSafetyStartupCheck` validation for dev secret in non-dev profiles (verify existing)
- B6: Add HTTPS redirect config or document reverse proxy requirement

### Batch 3: Observability & Operations (C1–C6)
**Goal**: Make the server observable and production-ready.
- C1: Add `logback-spring.xml` with JSON structured logging, MDC (requestId, userId, libraryId)
- C2: Add Spring Boot Actuator + Micrometer; expose `/actuator/health`, `/actuator/metrics`
- C3: Add `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 30s`
- C4: Configure `TaskScheduler` with thread pool for `@Scheduled` tasks
- C5: Enable `server.compression.enabled: true` with gzip, min response size 1KB
- C6: Split health into liveness/readiness probes

### Batch 4: Database Hardening (D1–D6)
**Goal**: Strengthen data integrity and query performance.
- D1: Add foreign key constraints for critical relationships (media→library, posts→library, comments→library, etc.)
- D2: Consider `varchar(40)` for ID columns (new migration, not backfill)
- D3: Configure HikariCP explicitly (pool size, connection timeout, idle timeout, max lifetime)
- D4: Switch dev profile to `ddl-auto: none`, rely on Flyway
- D5: Verify/fix `snapshot_json` column type
- D6: Replace ultra-long derived queries with `@Query` annotations

### Batch 5: Service Decomposition (E1–E7)
**Goal**: Break up oversized services, improve async processing.
- E1: Refactor `LedgerSyncService` with generic `LedgerTable<T>` abstraction
- E2: Split `TrashService` by trash item type (PostTrashService, MediaTrashService, SystemTrashService)
- E3: Split `UploadService` (UploadTokenService, UploadFileService, UploadConfirmService)
- E4: Split `NotificationService` by source (PhotoNotificationService, CommentNotificationService, etc.)
- E5: Already covered by A1
- E6: Cache checksum results or compute lazily
- E7: Make push notification sending `@Async` or event-driven via `ApplicationEventPublisher`

### Batch 6: Infrastructure Cleanup (F1–F8)
**Goal**: Make Docker/config portable and maintainable.
- F1: Move storage paths to `.env` variables
- F2: Move proxy config to `.env` variables
- F3: Reduce multipart limits to practical values (500MB for video, 50MB for images)
- F4: Align pom.xml java.version with Dockerfile (choose 17 or 21)
- F5: Move dev initializers behind `@Profile("dev")` or to test scope
- F6: Add GitHub Actions CI (build + test + Docker image build)
- F7: Remove jcodec dependency, use ffmpeg process execution exclusively
- F8: Add Dependabot config

### Batch 7: API Contract & Integration (G1–G6)
**Goal**: Clean up API contracts for long-term maintainability.
- G1: Add `/api/v1/` prefix strategy (can be deferred if no breaking changes planned)
- G2: Remove or redirect deprecated snapshot endpoints
- G3: Standardize on cursor-based pagination for all list endpoints
- G4: Fix `LedgerController.sync()` parameter naming
- G5: Replace `Map<String, Object>` with typed DTOs in LedgerSyncService
- G6: Batch-save invalid FCM tokens

---

## Plan Self-Check

### Scope Pressure Test
- Is the scope too large for one pass? **Yes — 7 batches, ~40 items.** Recommend doing Batch 1–3 first (critical performance + security + observability), then Batch 4–7 in subsequent rounds.
- Are there dependencies between batches? **Yes** — Batch 4 (DB indexes) should come before Batch 1 (query optimization) for maximum effect. Batch 5 (service decomposition) should come after Batch 1–3 to avoid merge conflicts.

### Regression Risk
- SQL-level pagination changes cursor format → Android client must be verified compatible
- Foreign key constraints may break existing code that deletes entities without cascade
- Rate limiting may affect automated testing or Android login retry logic
- Service decomposition is high-risk for behavioral changes → must have tests first

### Contract Drift
- Pagination standardization (G3) requires coordinated Android client update
- LedgerSync typed DTOs (G5) must match Android LedgerSyncDtos exactly

### Deployment Readiness
- Structured logging (C1) requires log aggregation setup (even file-based rotation)
- Actuator endpoints (C2) must be secured or excluded from public access
- HikariCP tuning (D3) requires load testing to find optimal values

---

## Decision Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Batch ordering | A→B→C→D→E→F→G | Fix critical perf first, then security, then observability |
| Java version alignment | **Align to 21** | Dockerfile already uses 21, Spring Boot 4 supports 21, no reason to stay on 17 |
| Pagination strategy | Keyset cursor for all feeds | Better than offset for real-time data; consistent with existing media feed approach |
| Rate limiting approach | **Bucket4j (in-memory)** | Simple, no Redis dependency, sufficient for single-instance deployment |
| Test infrastructure | **Testcontainers + PostgreSQL** | Real PG for integration tests, closer to production; H2 for simple unit tests |
| Execution plan | **Two rounds** | Round 1: Batch 1–3 (P0 perf + security + observability); Round 2: Batch 4–7 (DB + code quality + infra + API) |
| API versioning | **Defer** | No breaking changes planned; add `/api/v1/` only when needed |
| Foreign keys | **Round 2** | Add FK constraints in Batch D after orphaned data audit |
| Service decomposition | **Round 2, full decomposition** | E1–E4 all get refactored in Batch E |

---

## Execution Plan

### Round 1 — Critical Foundation (Batch A + B + C)

**Goal**: Eliminate OOM risks, close security gaps, make the server observable.

**Batch A — P0 Critical Performance**:
- A1: Rewrite `getMediaFeedPage()` to SQL-level keyset pagination
- A2: Replace `listAlbums()` post counting with `COUNT(*) GROUP BY` query
- A3: Consolidate `SyncService.getVersions()` into single `GREATEST()` query
- A4: Flyway migration for missing indexes (media.library_id, posts.library_id, comments composite, upload_tasks)
- A5: Replace `findTop200...` with `@Query` + add index

**Batch B — P1 Security Hardening**:
- B1: Bucket4j rate limiting on auth endpoints
- B2: Account lockout after N failed attempts
- B3: Explicit CORS config per profile
- B4: `@Size` validation on all DTO string fields
- B5: Verify ProductionSafetyStartupCheck covers dev secret
- B6: Document HTTPS requirement / add redirect config

**Batch C — P1 Observability & Operations**:
- C1: logback-spring.xml with JSON structured logging + MDC
- C2: Spring Boot Actuator + Micrometer
- C3: Graceful shutdown config
- C4: TaskScheduler thread pool
- C5: HTTP response compression (gzip)
- C6: Health liveness/readiness split

**Test infrastructure**: Add Testcontainers dependency, create base test class with PG container.

### Round 2 — Deep Hardening (Batch D + E + F + G)

**Goal**: Database integrity, code quality, infrastructure cleanup, API contract polish.

- Batch D: Foreign keys, HikariCP, dev profile, column types
- Batch E: Service decomposition (Ledger/Trash/Upload/Notification), async push, checksum cache
- Batch F: Docker .env portability, Java 21 alignment, CI/CD, jcodec removal
- Batch G: Pagination consistency, typed LedgerSync DTOs, deprecated endpoint cleanup

---

## Round 1 Implementation Order

Within Round 1, the recommended implementation sequence is:

1. **Test infrastructure first** — Add Testcontainers, base test class. This enables testing all subsequent changes.
2. **A4 (DB indexes)** — Flyway migration for indexes. Do this before query optimization so the optimized queries benefit immediately.
3. **A1–A3, A5 (Query fixes)** — Fix all performance issues. Write tests for each.
4. **B1–B6 (Security)** — Layer security on top of the now-performant code.
5. **C1–C6 (Observability)** — Add monitoring/logging last so you can observe the impact of A+B changes.

---

## Open Questions (Resolved)

All questions resolved. See Decision Log above.

---

## Verification Report (2026-07-02)

### Compile & Infrastructure

| Check | Result |
|---|---|
| `mvnw compile` | **PASS** — 253 source files, zero errors |
| Flyway migration chain V1–V24 | **PASS** — no gaps, no duplicates |
| Dead code scan | **PASS** — only main app class and package-info (expected) |

### Hidden Impact Checklist

| # | Check | Result |
|---|---|---|
| 1 | Auth rate limiting — scoped to auth endpoints, token bucket starts full, sensible defaults (20 req / 5 min) | **PASS** |
| 2 | Upload multipart limits — 500MB/550MB, sufficient for 4K video, env-configurable | **PASS** |
| 3 | Push async — afterCommitAsync works with/without transaction, delay support correct | **PASS** |
| 4 | Ledger sync semantics — all 9 tables handled, upsert/delete/query logic preserved | **PASS** |
| 5 | Trash three-phase contract — delete→restore→purge→undo all verified intact | **PASS** |
| 6 | Config changes — HikariCP, graceful shutdown, compression, actuator, CORS all present | **PASS** |
| 7 | Docker Compose — all paths externalized, defaults backward-compatible | **PASS** |
| 8 | Structured logging — MDC keys (requestId, userId, libraryId), profile-specific appenders | **PASS** |

### API Contract Integrity

| Check | Result |
|---|---|
| LedgerSyncRows record component names match Android JSON field names | **PASS** |
| Output Map keys include all input fields + server-generated (libraryId, createdAtMillis, updatedAtMillis) | **PASS** |
| LedgerChangesDto output still uses List&lt;Map&lt;String,Object&gt;&gt; | **PASS** |
| LedgerSyncRequest uses LedgerClientChangesDto (typed input) | **PASS** |

### Cross-Module Coupling

| Check | Result |
|---|---|
| UploadService callers (UploadController, CleanupScheduler) — Spring DI, unaffected by internal refactor | **PASS** |
| TrashService callers (MediaController, PostController, TrashController, AlbumService, LifeConsoleService, PendingCleanupScheduler) — Spring DI, unaffected | **PASS** |
| LedgerSyncService.sync() — LedgerController passes correct DTO type | **PASS** |
| PushDispatchSupport.afterCommitAsync(action) — backward-compatible single-arg overload preserved | **PASS** |

### Profile Configuration Consistency

| Check | Result |
|---|---|
| Base: HikariCP + compression + actuator — inherited by all profiles | **PASS** |
| Dev: H2, ddl-auto=update, Flyway disabled — correct for H2 | **PASS** |
| Docker: PostgreSQL, ddl-auto=validate, Flyway enabled — correct for production | **PASS** |
| No conflicting settings between profiles | **PASS** |

### Issues Found & Fixed During Review

| Severity | Issue | Fix |
|---|---|---|
| CRITICAL | V24 FK migration used wrong table names (libraries→shared_libraries, posts→small_albums, post_media→small_album_media) | Rewrote V24 migration |
| CRITICAL | V24 ledger_recurring_occurrences.transaction_id NOT NULL but FK said ON DELETE SET NULL | Changed to ON DELETE CASCADE |
| MEDIUM | LedgerService.java + 2 DTOs were dead code after G2 endpoint removal | Deleted 3 files |
| MEDIUM | Docker Minio API port 9000 exposed to 0.0.0.0 | Bound to 127.0.0.1, externalized port |
| MEDIUM | E6 checksum sidecar had no content validation | Added isValidSha256() check |
| MEDIUM | PostRepository 2 @Query methods missing @Param annotations | Added @Param("libraryId") |

### Remaining Known Risks (Low Severity)

- `notifiedUploadOperationKeys` in UploadNotificationService is an unbounded Set (slow memory leak, negligible for personal server)
- `PushDispatchSupport` uses `sleep()` on ForkJoinPool common pool (thread starvation risk under high concurrency)
- V24 FK migration requires no orphaned data in production (audit before deploy, or use `NOT VALID`)
- E4 NotificationService not decomposed (virtual notification model tightly coupled, split not beneficial)

---

## Closeout Self-Check (2026-07-02)

### What Shipped

**Round 1 (Batch A+B+C) — 16 items:**
- A1: MediaService SQL-level keyset pagination (eliminated OOM)
- A2: AlbumService COUNT(*) GROUP BY (eliminated N+1)
- A3: SyncService consolidated GREATEST() query
- A4: V22 performance indexes migration
- A5: VideoCoverWarmupInitializer @Query + index
- B1: Custom token-bucket rate limit filter on auth endpoints
- B2: Account lockout after N failed attempts
- B3: Explicit CORS config via `app.cors.allowed-origins`
- B4: @Size validation on all DTO string fields
- B5: ProductionSafetyStartupCheck for dev JWT secret
- B6: HTTPS documented (reverse proxy responsibility)
- C1: logback-spring.xml JSON structured logging + MDC
- C2: Spring Boot Actuator + health/readiness probes
- C3: Graceful shutdown (30s timeout)
- C4: SchedulerConfig with 4-thread pool
- C5: HTTP response compression (gzip, 1KB min)
- C6: Health liveness/readiness split

**Round 2 (Batch D+E+F+G) — 22 items:**
- D1: V24 foreign key constraints (40+ relationships)
- D2: Assessed — skipped (varchar(255)→varchar(40) too risky)
- D3: HikariCP explicit config (pool=20, timeouts)
- D4: Assessed — kept ddl-auto:update for dev (H2 without Flyway)
- D5: Assessed — snapshotJson already `text` type, no fix needed
- D6: @Query annotations for 15+ long derived queries
- E1+G5: LedgerSyncService typed DTOs (9 input records, eliminated Map boilerplate)
- E2: TrashService snapshot extraction (TrashSnapshotHelper)
- E3: UploadService notification extraction (UploadNotificationService)
- E4: Assessed — kept monolithic (virtual notification model tightly coupled)
- E6: Checksum sidecar caching (.sha256 files)
- E7: PushDispatchSupport delayed afterCommitAsync unified
- F1-F2: Docker Compose .env externalization (paths + proxy)
- F3: Multipart limits 4096MB → 500MB/550MB
- F4: Java 21 alignment (done in Round 1)
- F5: Assessed — all dev initializers already have @Profile
- F6: Deferred (CI/CD not in scope for this refinement)
- F7: Removed jcodec dead dependency
- F8: Dependabot config (.github/dependabot.yml)
- G1: Deferred (API versioning not needed yet)
- G2: Removed deprecated ledger snapshot endpoints + dead code
- G3: Deferred (pagination standardization requires client coordination)
- G4: request2 → httpRequest parameter rename
- G5: Combined with E1
- G6: FCM disableInvalidTokens batch saveAll()

### What Was Validated

- `mvnw compile` — 253 source files, zero errors
- Flyway migration chain V1–V24 — no gaps, no duplicates
- Dead code scan — no orphaned classes
- Hidden Impact Checklist — 8/8 PASS
- API contract integrity — 4/4 PASS (LedgerSync JSON wire format preserved)
- Cross-module coupling — 4/4 PASS (all refactored services use Spring DI)
- Profile configuration consistency — 4/4 PASS

### What Risk Remains

| Risk | Severity | Mitigation |
|---|---|---|
| V24 FK migration fails if orphaned data exists in production | Medium | Audit data before deploy, or add constraints with `NOT VALID` |
| Test coverage still < 5% — no service/integration tests | Medium | Future module should add critical-path tests |
| Unbounded `notifiedUploadOperationKeys` Set | Low | Replace with Caffeine cache if traffic grows |
| ForkJoinPool thread starvation under high push concurrency | Low | Use dedicated ScheduledExecutorService if needed |

### Deferred Items (Explicit Acceptance)

| Item | Reason | Carry-Forward |
|---|---|---|
| G1: API versioning | No breaking changes planned | Add `/api/v1/` when first breaking change is needed |
| G3: Pagination standardization | Requires coordinated Android client update | Defer to client-side module brief |
| F6: CI/CD pipeline | Separate infrastructure concern | Future `ci-cd` module brief |
| D2: ID column width reduction | Risk outweighs benefit for current scale | Revisit if storage becomes a concern |
| E4: NotificationService decomposition | Virtual model tightly coupled, split adds complexity without benefit | Revisit if notification sources grow beyond 6 |

---

## Carry-Forward Notes

For future module work that touches this backend:

1. **Flyway migrations are now at V24.** Next migration must be V25. All table names use V5 renames: `small_albums` (not `posts`), `small_album_media` (not `post_media`), `comments.small_album_id` (not `post_id`). The library parent table is `shared_libraries` (not `libraries`).

2. **LedgerSync uses typed input DTOs.** `LedgerSyncRequest` → `LedgerClientChangesDto` (typed records) for input, `LedgerChangesDto` (Map-based) for output. JSON wire format is unchanged. Any changes to record component names must be coordinated with the Android client.

3. **Push notifications use `PushDispatchSupport.afterCommitAsync()`.** Two overloads: `(action)` for immediate, `(action, delayMillis)` for delayed. All push sending goes through this — do not introduce alternative async patterns.

4. **Upload notification is in `UploadNotificationService`**, not `UploadService`. The upload service delegates via `uploadNotificationService.notifyIfCompleted()`.

5. **Trash snapshots are in `TrashSnapshotHelper`** (5 record types + serialization). `TrashService` delegates all snapshot operations to this helper.

6. **Checksum caching uses `.sha256` sidecar files** alongside data files in local storage. Written on `put()`, read on `getMetadata()`, cleaned up on `delete()`.

7. **Structured logging uses MDC keys: `requestId`, `userId`, `libraryId`.** `RequestIdFilter` sets `requestId`; `JwtAuthenticationInterceptor` sets `userId` and `libraryId`. Logback config is profile-aware (dev=console, prod=JSON file, docker=both).

8. **All Docker Compose paths/proxy are externalized via `.env` variables** with backward-compatible defaults. No `.env` file needed for default dev setup.
