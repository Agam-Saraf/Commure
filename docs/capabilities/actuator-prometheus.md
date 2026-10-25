# actuator-prometheus

**Status:** Verified

## Purpose

Expose Prometheus scrape format and a tiny demo counter (`commure.demo.hits`).

## Default

Only `/actuator/health` is exposed. Metrics export stays off until the `metrics` profile is active.

## Activation

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,metrics
```

- Counter: `GET /api/demo/metrics`
- Scrape: `GET /actuator/prometheus`

## Smoke test

```bash
curl -sf http://127.0.0.1:8080/api/demo/metrics
curl -sf http://127.0.0.1:8080/actuator/prometheus | grep commure_demo_hits
```

Maven: `DemoMetricsTest`.
