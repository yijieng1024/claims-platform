# Claims Platform

Claims handling platform: claimants report incidents, officers handle claims, managers see exposure.

Design: [docs/DESIGN.md](docs/DESIGN.md). This README is a placeholder; the full version comes later.

## Run locally (current state)

Needs Docker and JDK 25. Start auth-service and claims-service: registration needs both.

```bash
docker compose up -d                 # Postgres 18 (host port 5433) + Kafka (localhost:9092) + topic init
./mvnw install -DskipTests           # once, so the services can resolve libs/web-common
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

## Seeded policies (fake demo data)

Claimants can only register with a policy number whose holder email matches the email they register
with (design section 4). Expired policies still allow registration; cancelled ones don't. Every
failure gives the same message.

| Policy number | Holder email | Type | Market | Status | Cover |
|---|---|---|---|---|---|
| `POL-MY-MTR-0001` | `claimant1@example.com` | MOTOR | MY | ACTIVE | 2026-03-01 – 2027-02-28 |
| `POL-SG-PRP-0002` | `claimant1@example.com` | PROPERTY | SG | ACTIVE | 2026-07-01 – 2027-06-30 |
| `POL-TH-MTR-0003` | `claimant1@example.com` | MOTOR | TH | ACTIVE, **expired** | 2024-05-01 – 2025-04-30 |
| `POL-ID-MTR-0004` | `claimant2@example.com` | MOTOR | ID | ACTIVE | 2026-01-15 – 2027-01-14 |
| `POL-PH-PRP-0005` | `claimant2@example.com` | PROPERTY | PH | ACTIVE | 2026-09-01 – 2027-08-31 |
| `POL-VN-PRP-0006` | `claimant2@example.com` | PROPERTY | VN | ACTIVE, **expired** | 2023-10-01 – 2024-09-30 |
| `POL-MY-PRP-0007` | `holder3@example.com` | PROPERTY | MY | ACTIVE — no account yet | 2026-04-01 – 2027-03-31 |
| `POL-SG-MTR-0008` | `holder4@example.com` | MOTOR | SG | ACTIVE — no account yet | 2026-02-01 – 2027-01-31 |
| `POL-TH-PRP-0009` | `holder5@example.com` | PROPERTY | TH | ACTIVE — no account yet | 2026-06-01 – 2027-05-31 |
| `POL-VN-MTR-0010` | `holder6@example.com` | MOTOR | VN | **CANCELLED** (register fails) | 2026-01-01 – 2026-12-31 |

Sum insured and insured item (fake plate / address) are in
`services/claims-service/src/main/resources/db/migration/V3__seed_policies.sql`.
