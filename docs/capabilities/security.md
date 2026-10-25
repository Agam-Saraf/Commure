# security

**Status:** Verified (baseline filter chain)

## Purpose

Spring Security filter chain: anonymous access to `GET /api/hello` and `/actuator/health/**`; other routes require authentication.

## Default

Enabled with the application (not profile-gated).

## Activation

See [`SecurityConfig`](../../backend/src/main/java/com/commure/backend/config/SecurityConfig.java).

OAuth2 / Keycloak (PKCE) is a separate profile — see [oauth2-keycloak.md](oauth2-keycloak.md).

## Smoke test

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/hello   # 200
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/secure  # 401/403
./mvnw test -Dtest=HelloControllerTest
```
