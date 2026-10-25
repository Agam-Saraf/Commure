# Spring & Docker Compose profiles

Spring profiles control **application** features. Docker Compose profiles control **infrastructure**. Scripts load `.env.practice` when present (`scripts/lib/env.sh`).

Spring Boot does **not** auto-start Compose (`spring.docker.compose.enabled=false` by default). Use `./scripts/compose-up.sh` (or set `SPRING_DOCKER_COMPOSE_ENABLED=true`). Actuator health for mongo/redis/rabbit is disabled until the matching feature profile is active.

## Database profiles (exactly one required)

| Spring profile | Role | Compose |
|----------------|------|---------|
| `h2` (default) | In-memory JDBC | none |
| `mysql` | MySQL JDBC | `--profile mysql` |
| `postgres` | PostgreSQL JDBC | `--profile postgres` |

**Conflict:** `mysql` + `postgres` → startup fails fast.

When you set any Spring profiles, `spring.profiles.default=h2` is **not** applied. Always include a DB profile explicitly, e.g. `h2,cache` not `cache` alone.

## Feature profiles

| Spring profile | App effect | Required infrastructure |
|----------------|------------|-------------------------|
| `jpa` | `/api/demo/jpa` CRUD; schema update (or validate with flyway) | a DB profile |
| `flyway` | Flyway migrations enabled | a DB profile (+ usually `jpa`) |
| `mongo` | `/api/demo/mongo` CRUD | MongoDB (shared compose service) |
| `cache` | Redis `@Cacheable` demo | Redis (shared) |
| `hazelcast` | Embedded Hazelcast cache demo | none |
| `rabbit` | RabbitMQ pub/sub demo | RabbitMQ (shared) |
| `kafka` | Kafka pub/sub demo | Compose `--profile kafka` |
| `graphql` | GraphQL `demoMessage` + GraphiQL | none |
| `websocket` | STOMP `/ws` + status `/api/demo/websocket` | none |
| `docs` | springdoc OpenAPI + Swagger UI | none |
| `metrics` | Prometheus scrape + `/api/demo/metrics` counter | none |
| `mail` | `POST /api/demo/mail` via JavaMailSender | Compose `--profile mailhog` |
| `scheduling` | `@Scheduled` ticks — `GET /api/demo/scheduling` | none |
| `quartz` | Quartz job counter — `GET /api/demo/quartz` | none |
| `batch` | Spring Batch job — `POST /api/demo/batch/run` | none |
| `validation` | Bean Validation demo — `POST /api/demo/validation` | none |
| `oauth2` | JWT resource server — `GET /api/secure/me` | Compose `--profile keycloak` |
| `s3` | MinIO/S3 put/get/list — `/api/demo/s3` | Compose `--profile minio` |

**Conflict:** `cache` + `hazelcast` → startup fails fast (both define `CacheManager`).

## Shared Compose services (no profile)

Always available via `./scripts/compose-up.sh` (no extra args):

- MongoDB `127.0.0.1:27017`
- Redis `127.0.0.1:6379`
- RabbitMQ `127.0.0.1:5672` (management `15672`)

## Startup examples

```bash
# Shared infra
./scripts/compose-up.sh

# Optional infra (multiple profiles supported)
./scripts/compose-up.sh kafka
./scripts/compose-up.sh postgres kafka

# App — always include a DB profile
cd backend
./mvnw spring-boot:run                                                 # default h2
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,jpa
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,cache
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,kafka
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres,jpa,cache,kafka
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,graphql
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,websocket
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,docs
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,metrics
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,mail
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,scheduling
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,quartz
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,batch
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,validation
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,oauth2
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,s3
```

## Environment variables (optional overrides)

Disposable local defaults remain in properties/compose when unset.

| Variable | Used by |
|----------|---------|
| `H2_DATASOURCE_URL` / `H2_DATASOURCE_USERNAME` / `H2_DATASOURCE_PASSWORD` | `h2` |
| `MYSQL_DATASOURCE_URL` / `MYSQL_USER` / `MYSQL_PASSWORD` / `MYSQL_DATABASE` / `MYSQL_ROOT_PASSWORD` | `mysql` + compose |
| `POSTGRES_DATASOURCE_URL` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `postgres` + compose |
| `MONGODB_URI` | `mongo` (`spring.mongodb.uri`) |
| `REDIS_HOST` / `REDIS_PORT` | `cache` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `rabbit` + compose |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka` |
| `MAIL_HOST` / `MAIL_PORT` | `mail` + compose `mailhog` |
| `KEYCLOAK_ISSUER_URI` / `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | `oauth2` + compose `keycloak` |
| `S3_ENDPOINT` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` / `S3_REGION` | `s3` + compose `minio` |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | compose `minio` |
| `SPRING_DOCKER_COMPOSE_ENABLED` | opt-in Spring-managed Compose (`true` to enable) |
| `COMPOSE_PROJECT_NAME` | practice isolation (see `.env.practice`) |

## Schema management

- **jpa** without **flyway**: Hibernate `ddl-auto=update` (via `JpaSchemaConfiguration`, not profile file order).
- **jpa** + **flyway**: Hibernate `validate`; Flyway runs `classpath:db/migration`.
- DB profiles do **not** set `ddl-auto`.

## Safety

Scripts never run `docker compose down -v`. Existing named volumes are preserved.
