# testcontainers

**Status:** Verified (context load)

## Purpose

Integration-test containers for Kafka, MongoDB, RabbitMQ, and Redis via `@ServiceConnection` in [`TestcontainersConfiguration`](../../backend/src/test/java/com/commure/backend/TestcontainersConfiguration.java).

MySQL/PostgreSQL Testcontainers modules remain on the classpath for future ITs.

## Default

Used by tests only (`BackendApplicationTests`).

## Activation

```bash
cd backend && ./mvnw test -Dtest=BackendApplicationTests
```

## Smoke test

`contextLoads` must pass with Docker available.
