# frontend

**Status:** Verified

## Purpose

React + Vite app that proxies `/api` to the Spring Boot backend and optionally runs Keycloak PKCE.

## Default

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 — loads `GET /api/hello` through the Vite proxy.

## Optional OAuth UI

```bash
VITE_OAUTH_ENABLED=true npm run dev
```

Requires backend `oauth2` profile + Keycloak. See [oauth2-keycloak.md](oauth2-keycloak.md).

## Checks

```bash
npm run lint
npm run build
```
