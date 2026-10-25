# scheduling

**Status:** Verified

## Purpose

`@Scheduled` fixed-rate tick counter.

## Activation

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,scheduling
curl -sf http://127.0.0.1:8080/api/demo/scheduling
```

Maven: `DemoSchedulingTest`.
