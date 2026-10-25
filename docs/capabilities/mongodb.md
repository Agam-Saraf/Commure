# mongodb

**Status:** Verified

## Purpose

CRUD demo against MongoDB (`/api/demo/mongo`).

## Default

Off. Mongo actuator health is disabled until profile `mongo` is active.

## Activation

```bash
./scripts/compose-up.sh
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,mongo
```

Connection: `spring.mongodb.uri` / `MONGODB_URI` (default `mongodb://127.0.0.1:27017/commure_demo`).
