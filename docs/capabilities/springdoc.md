# springdoc

**Status:** Verified

## Purpose

OpenAPI 3 document and Swagger UI for the starter REST API (disabled by default).

## Default

`springdoc.api-docs.enabled=false` and Swagger UI disabled in `application.properties`.

## Activation

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,docs
```

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: http://localhost:8080/swagger-ui.html

## Smoke test

```bash
curl -sf http://127.0.0.1:8080/v3/api-docs | grep -q 'Commure Interview Starter'
```

Maven: `DemoDocsTest`.
