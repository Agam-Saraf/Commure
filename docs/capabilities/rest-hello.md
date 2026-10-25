# rest-hello

**Status:** Verified

## Purpose

Minimal REST health of the stack: `GET /api/hello` returns JSON `{ "message": "..." }`.

## Default

Enabled.

## Activation

No special profile. Start backend and (optionally) frontend.

```bash
cd backend && ./mvnw spring-boot:run
# Frontend: cd frontend && npm run dev → http://localhost:5173
```

## Smoke test

```bash
curl -s http://localhost:8080/api/hello
# Proxied: curl -s http://localhost:5173/api/hello
cd backend && ./mvnw test -Dtest=HelloControllerTest
```
