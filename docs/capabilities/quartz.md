# quartz

**Status:** Verified

## Purpose

In-memory Quartz job that increments an execution counter every second.

## Activation

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,quartz
curl -sf http://127.0.0.1:8080/api/demo/quartz
```

Maven: `DemoQuartzTest`.
