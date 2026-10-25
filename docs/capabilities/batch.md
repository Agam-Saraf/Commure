# batch

**Status:** Verified

## Purpose

Spring Batch 6 job that uppercases a short in-memory list. Jobs do **not** auto-run on startup (`spring.batch.job.enabled=false`).

## Activation

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,batch
curl -sf -X POST http://127.0.0.1:8080/api/demo/batch/run
```

Maven: `DemoBatchTest`.
