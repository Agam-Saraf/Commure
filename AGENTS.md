# Commure Interview Project

## Stack
- Backend: Java 27, Spring Boot 4.1.2
- Frontend: React, JavaScript, Vite
- API: REST/JSON (optional GraphQL, WebSocket via profiles)

## Architecture
- Keep the implementation minimal.
- Separate business logic from controllers.
- Prefer composition and dependency injection.
- Follow DRY and SOLID where appropriate.
- Avoid unnecessary abstractions and patterns.
- Do not introduce infrastructure without justification.
- Demo capabilities stay behind Spring / Compose profiles; default boot remains H2 + `/api/hello`.
- Spring Boot Docker Compose integration stays off by default; scripts start infra via `./scripts/compose-up.sh`.
- Optional infra health indicators (mongo/redis/rabbit) enable only with their feature profiles.

## Workflow
- Before significant changes, propose a short plan.
- Work on one scoped feature at a time.
- Do not change unrelated files.
- Prefer existing dependencies.
- After implementation, run relevant tests.
- Report what changed, what was tested, and any remaining limitations.
- Never claim tests passed unless executed.
