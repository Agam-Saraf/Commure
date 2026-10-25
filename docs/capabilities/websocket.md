# websocket

**Status:** Verified

## Purpose

STOMP-over-WebSocket echo demo plus a small HTTP readiness endpoint for curl-based smoke tests.

## Default

Off until profile `websocket` is active.

## Activation

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,websocket
```

- STOMP endpoint: `/ws`
- Send: `/app/demo` with `{"message":"..."}`
- Subscribe: `/topic/demo`
- Status: `GET /api/demo/websocket`

## Smoke test

```bash
curl -sf http://127.0.0.1:8080/api/demo/websocket
```

Expect `"status":"ready"`. Maven: `DemoWebSocketTest`.
