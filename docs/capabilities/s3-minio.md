# s3-minio

**Status:** Verified

## Purpose

AWS SDK v2 S3 client pointed at MinIO (path-style). Put/get/list text objects.

## Activation

```bash
./scripts/compose-up.sh minio
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,s3
curl -sf -X PUT http://127.0.0.1:8080/api/demo/s3/notes.txt \
  -H 'Content-Type: application/json' \
  -d '{"content":"hello-minio"}'
curl -sf http://127.0.0.1:8080/api/demo/s3/notes.txt
```

MinIO console: http://127.0.0.1:9001 (`minioadmin` / `minioadmin`).

Maven: `DemoS3Test` (service mocked).
