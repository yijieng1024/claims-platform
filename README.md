# Claims Platform

Claims handling platform: claimants report incidents, officers handle claims, managers see exposure.

Design: [docs/DESIGN.md](docs/DESIGN.md). This README is a placeholder; the full version comes later.

## Run locally (current state)

Needs Docker and JDK 25.

```bash
docker compose up -d                 # Postgres (host port 5433) + Kafka (localhost:9092) + topic init
./mvnw -pl services/auth-service spring-boot:run       # :8081
./mvnw -pl services/claims-service spring-boot:run     # :8082
./mvnw -pl services/reporting-service spring-boot:run  # :8083
```

Swagger UI: `http://localhost:{8081,8082,8083}/swagger-ui.html`.

auth-service creates its RSA signing key on first start at `~/.claims-platform/auth-jwt-key.pem`
(override with `AUTH_JWT_KEY_PATH`). Never commit it.

## Seeded accounts (demo data only)

| Role | Email | Password |
|---|---|---|
| Manager (all markets) | `manager@example.com` | `Manager123!` |
| Officer | `officer.<market><n>@example.com`, market = `my sg th id ph vn`, n = `1 2` (e.g. `officer.my1@example.com`) | `Officer123!` |
| Claimant | `claimant1@example.com`, `claimant2@example.com` | `Claimant123!` |
