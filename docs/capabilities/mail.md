# mail

**Status:** Verified

## Purpose

Send a simple email via `JavaMailSender` (MailHog for local SMTP capture).

## Default

Off until profile `mail` is active.

## Activation

```bash
./scripts/compose-up.sh mailhog
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,mail
```

- API: `POST /api/demo/mail` with `{"to","subject","text"}`
- MailHog UI: http://127.0.0.1:8025

## Smoke test

Maven: `DemoMailTest` (mocked sender). Live: post then check MailHog UI.
