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
