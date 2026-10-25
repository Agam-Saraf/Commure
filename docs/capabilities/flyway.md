# flyway

**Status:** See [README capability matrix](../../README.md#capability-matrix).

## Purpose

Generic demo / verification path for this capability in the interview starter. No interview-specific business logic.

## Default

Disabled or unused unless noted in the README (hello, H2, and health are on by default).

## Activation

_To be filled when the capability is wired (Spring profile + Compose profile if needed)._

```bash
# Example (placeholder)
# docker compose -f infrastructure/compose.yaml --profile <name> up -d
# ./mvnw spring-boot:run -Dspring-boot.run.profiles=<name>
```

## Smoke test

_To be filled when verify scripts cover this capability._

## Notes

- Keep demos under `com.commure.backend.demo.*` and profile-gated.
- Never delete Docker volumes from verification scripts.
