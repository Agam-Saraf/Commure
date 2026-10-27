# Commure Interview Starter

Reusable full-stack interview baseline: Spring Boot 4.1.2 (Java 27) + React/Vite.

Default startup is intentionally lean (**H2** + `GET /api/hello`). Optional capabilities are preconfigured behind Spring and Docker Compose profiles—not running unless you activate them.

## Quick start

```bash
# Backend only (default profile: h2) — no Docker required
cd backend && ./mvnw spring-boot:run

# Frontend (proxies /api → :8080)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 — you should see the hello message from the backend.

Optional shared infra (MongoDB, Redis, RabbitMQ) for demos:

```bash
./scripts/compose-up.sh
```

Spring Boot Docker Compose integration is **disabled by default** (`spring.docker.compose.enabled=false`), so hello/H2 starts without Docker. Start infrastructure explicitly with `./scripts/compose-up.sh`. To let Spring Boot manage Compose instead, set `SPRING_DOCKER_COMPOSE_ENABLED=true`.

Mongo/Redis/Rabbit actuator health checks are off by default and turn on only with profiles `mongo` / `cache` / `rabbit`.

Only one practice workspace can own the shared host ports at a time. If another practice is running, stop its infrastructure before starting the starter infrastructure. If a running shared container has lost its published ports, repair it with `docker compose -f infrastructure/compose.yaml up -d --force-recreate mongodb redis rabbitmq`. This briefly restarts these services and preserves their existing volumes; do not use `down -v`.

## Grayscale video transform

Team setup, commands and test results: [docs/video-transform.md](docs/video-transform.md).

Upload an MP4 (50 MB, 2 minutes, 1920x1080 max), preview it, and convert it to grayscale through the third-party transform API. Both videos are kept in backend memory (latest 20 uploads or 500 MB; lost on restart).

Clips within the API limits (3 MB, 10 s, 600 frames) are sent as-is. Anything larger is split with ffmpeg into equal parts under 10 s (re-encoded to H.264, at most 1080p/60 fps and ~1.5 Mbit/s so each part stays under 3 MB), sent in parallel, and joined back in timeline order. If any part fails, the whole conversion fails with a message naming the part. Long videos need `ffmpeg`/`ffprobe` on the backend's `PATH` (`brew install ffmpeg`, or set `FFMPEG_PATH`/`FFPROBE_PATH`); without it only API-sized clips are accepted. Output has no audio, because the transform API returns video only.

Put credentials in a repo-root `.env` (see `.env.example`; `KEY=value` or `export KEY='value'`). Real environment variables override it. The token stays on the backend.

```bash
BASE_URL=https://...
TRANSFORM_SERVICE_TOKEN=...
```

| Endpoint | Purpose |
|----------|---------|
| `POST /api/videos` (multipart `file`) | Validate and store the original; `201` with `id` |
| `POST /api/videos/{id}/transform` | Call the transform API and store the grayscale result |
| `GET /api/videos/{id}/transformed` | Stream the grayscale MP4 (supports Range) |

Errors are JSON `{code, message, requestId, retryAfterSeconds}`; `429`/`503` also carry `Retry-After`. The backend caps upstream calls at 6 concurrent and 12 starts per second, and retries short `Retry-After` waits (`transform.*` and `video.*` in `application.properties`).

Tests: `./mvnw test -Dtest='Video*Test,Transform*Test,LongVideo*Test'` (includes a fake upstream on a real socket; ffmpeg tests are skipped when ffmpeg is missing) and `npm test` in `frontend/`. With the backend running, `./scripts/simulate-video.sh [upload|transform|long|all]` exercises the live workflow over HTTP. The `transform` and `long` steps call the real API and write `transformed.mp4` / `transformed-long.mp4`.

## Verification

```bash
./scripts/verify-quick.sh   # fast: unit tests, FE build/lint, hello/health when up
./scripts/verify-full.sh    # sequential capability matrix (starts only what it needs)
./scripts/check-ports.sh    # fail if host ports are busy
./scripts/setup.sh          # npm ci + docker compose pull (incl. optional images)
./scripts/start.sh          # shared compose up; prints how to run app
```

Scripts load `.env.practice` when present (via `scripts/lib/env.sh`). They never delete Docker volumes.

## Practice workspaces

After the starter is tagged (`starter-v1.0.1` by default):

```bash
./scripts/new-practice.sh my-session
# or: STARTER_TAG=starter-v1.0.0 ./scripts/new-practice.sh my-session
```

Creates a **local** clone (no GitHub required) on branch `practice/my-session` with its own Compose project name and named volumes. One practice at a time on shared host ports; use `./scripts/check-ports.sh` first.

## Capability matrix

Status legend: **Verified** (smoke exists) · **Preconfigured** (activation path documented; smoke pending) · **Installed** (on classpath / in compose; not yet wired)

| Capability | Status | Activate |
|------------|--------|----------|
| REST `/api/hello` | Verified | default |
| Spring Security (hello + health public) | Verified | default |
| JPA + H2 | Verified | profiles `h2,jpa` — `POST/GET /api/demo/jpa` |
| JPA + MySQL | Verified | spring `mysql,jpa` + compose `--profile mysql` |
| JPA + PostgreSQL | Verified | spring `postgres,jpa` + compose `--profile postgres` |
| Flyway | Verified | profiles `h2,jpa,flyway` (H2 IT) |
| MongoDB | Verified | profile `mongo` — CRUD `/api/demo/mongo` |
| Redis cache + TTL | Verified | profile `cache` — `/api/demo/cache/{key}` |
| Hazelcast cache | Verified | profile `hazelcast` — `/api/demo/hazelcast/{key}` |
| RabbitMQ | Verified | profile `rabbit` — `/api/demo/rabbit` |
| Kafka | Verified | compose `--profile kafka` + profile `kafka` |
| GraphQL | Verified | profile `graphql` — `{ demoMessage }` / GraphiQL |
| WebSocket | Verified | profile `websocket` — STOMP `/ws`, status `/api/demo/websocket` |
| Mail | Verified | compose `--profile mailhog` + profile `mail` |
| `@Scheduled` | Verified | profile `scheduling` — `/api/demo/scheduling` |
| Quartz | Verified | profile `quartz` — `/api/demo/quartz` |
| Spring Batch | Verified | profile `batch` — `POST /api/demo/batch/run` |
| Bean Validation | Verified | profile `validation` — `POST /api/demo/validation` |
| Actuator / Prometheus | Verified | health default; profile `metrics` — `/actuator/prometheus` |
| springdoc OpenAPI | Verified | profile `docs` — `/v3/api-docs`, `/swagger-ui.html` |
| OAuth2 / Keycloak PKCE | Verified | compose `--profile keycloak` + profile `oauth2`; FE `VITE_OAUTH_ENABLED=true` |
| S3 / MinIO | Verified | compose `--profile minio` + profile `s3` — `/api/demo/s3` |
| Testcontainers | Verified (context) | `./mvnw test` |
| Frontend build / lint / API | Verified | `npm run build` / `lint`; API via proxy; optional PKCE |

Profile reference: [docs/profiles.md](docs/profiles.md).  
Capability pages: [docs/capabilities/](docs/capabilities/).

## Safety

- Do not run `docker compose down -v` unless you intend to destroy data.
- Existing volumes (`commure-infra_mongo_data`, `commure-infra_rabbit_data`) are preserved by starter scripts.
- Localdev credentials in Compose are disposable; never use them in production.
