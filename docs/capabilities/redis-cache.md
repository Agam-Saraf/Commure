# redis-cache

**Status:** Verified

## Purpose

Redis `@Cacheable` demo (`/api/demo/cache/{key}`).

## Default

Off. Redis actuator health is disabled until profile `cache` is active.

## Activation

```bash
./scripts/compose-up.sh
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,cache
```
