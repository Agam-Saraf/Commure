# graphql

**Status:** Verified

## Purpose

Minimal GraphQL query demo (`demoMessage`) with optional GraphiQL UI.

## Default

Off. Schema is loaded only when the `graphql` profile is active (`classpath:graphql-demo/`).

## Activation

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2,graphql
```

- GraphQL HTTP: `POST /graphql`
- GraphiQL: http://localhost:8080/graphiql

## Smoke test

```bash
curl -s http://127.0.0.1:8080/graphql \
  -H 'Content-Type: application/json' \
  -d '{"query":"{ demoMessage }"}'
```

Expect `"Hello from GraphQL demo"`. Maven: `DemoGraphqlTest`.
