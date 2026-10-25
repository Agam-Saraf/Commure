# oauth2-keycloak

**Status:** Verified

## Purpose

Keycloak-backed JWT resource server plus optional Vite SPA PKCE login.

## Default

Off. Default security keeps `/api/hello` and `/api/demo/**` public without JWT.

## Activation

```bash
./scripts/compose-up.sh keycloak
# wait until http://127.0.0.1:8081 is up, realm "commure" imported
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,oauth2
```

Demo user: `demo` / `demo`. Public client: `commure-spa` (PKCE).

### Backend smoke (password grant for quick curl)

```bash
TOKEN=$(curl -s -X POST 'http://127.0.0.1:8081/realms/commure/protocol/openid-connect/token' \
  -d 'client_id=commure-spa' \
  -d 'username=demo' \
  -d 'password=demo' \
  -d 'grant_type=password' | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')
curl -sf http://127.0.0.1:8080/api/secure/me -H "Authorization: Bearer $TOKEN"
```

### Frontend PKCE

```bash
cd frontend
VITE_OAUTH_ENABLED=true npm run dev
```

Maven: `DemoOAuth2Test`.
