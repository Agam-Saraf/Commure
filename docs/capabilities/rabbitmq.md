# rabbitmq

**Status:** Verified

## Purpose

RabbitMQ pub/sub demo (`/api/demo/rabbit`).

## Default

Off. Rabbit actuator health is disabled until profile `rabbit` is active.

## Activation

```bash
./scripts/compose-up.sh
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,rabbit
```
