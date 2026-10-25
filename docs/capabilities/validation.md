# validation

**Status:** Verified

## Purpose

Jakarta Bean Validation on a JSON body (`@NotBlank`, `@Email`, `@Size`).

## Activation

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,validation
curl -sf -X POST http://127.0.0.1:8080/api/demo/validation \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada","email":"ada@example.com"}'
```

Invalid payloads return HTTP 400 with field errors. Maven: `DemoValidationTest`.
