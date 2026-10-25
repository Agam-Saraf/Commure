# jpa-h2

**Status:** Preconfigured (datasource verified; CRUD demo pending)

## Purpose

Default JDBC database via in-memory H2. Hibernate `ddl-auto=update`. Flyway disabled on this profile.

## Default

Yes — `spring.profiles.default=h2`.

## Activation

```bash
cd backend && ./mvnw spring-boot:run
# Uses application-h2.properties
```

## Smoke test

Confirm logs show `jdbc:h2:mem:backend`. CRUD entity demo added in a later phase.
