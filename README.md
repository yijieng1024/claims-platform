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

Register body: `{ "email", "password", "policyNumber" }`. The account name is the policy's holder
name (a `name` field, if sent, is ignored).

Cover periods are one year long and **relative to the day the database was first migrated**
("today" = database date, UTC), so the demo data doesn't expire.

| Policy number | Holder email | Type | Market | Status | Cover |
|---|---|---|---|---|---|
| `POL-MY-MTR-0001` | `claimant1@example.com` | MOTOR | MY | ACTIVE | started 6 months ago |
| `POL-SG-PRP-0002` | `claimant1@example.com` | PROPERTY | SG | ACTIVE | started 2 months ago |
| `POL-TH-MTR-0003` | `claimant1@example.com` | MOTOR | TH | ACTIVE, **expired** | started 30 months ago, ended 18 months ago |
| `POL-ID-MTR-0004` | `claimant2@example.com` | MOTOR | ID | ACTIVE | started 10 months ago |
| `POL-PH-PRP-0005` | `claimant2@example.com` | PROPERTY | PH | ACTIVE | started 1 month ago |
| `POL-VN-PRP-0006` | `claimant2@example.com` | PROPERTY | VN | ACTIVE, **expired** | started 3 years ago, ended 2 years ago |
| `POL-MY-PRP-0007` | `holder3@example.com` | PROPERTY | MY | ACTIVE — no account yet | started 8 months ago |
| `POL-SG-MTR-0008` | `holder4@example.com` | MOTOR | SG | ACTIVE — no account yet | started 4 months ago |
| `POL-TH-PRP-0009` | `holder5@example.com` | PROPERTY | TH | ACTIVE — no account yet | started 5 months ago |
| `POL-VN-MTR-0010` | `holder6@example.com` | MOTOR | VN | **CANCELLED** (register fails) | started 3 months ago |

Sum insured and insured item (fake plate / address) are in
`services/claims-service/src/main/resources/db/migration/V3__seed_policies.sql`; dates in `V4__relative_policy_dates.sql`.
