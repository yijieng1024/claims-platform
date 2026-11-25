# Brief 05 — Brief 04 follow-ups, document upload (backend), Java PATH fix

## Context
`docs/DESIGN.md` is now **v0.7** — read it again. New: Q21–Q24 (section 14), `last_event_at`, the notification/version note and the multi-instance known limitation in §7.

Plan:
1. ~~Skeleton + infra + auth-service~~ ✅
2. ~~Docker check + shared module + policies + registration~~ ✅
3. ~~Claims core~~ ✅
4. ~~Outbox publisher + reporting-service~~ ✅
5. **Follow-ups + document upload (backend) + PATH fix** ← this brief
6. Angular frontend
7. README, DECISIONS.md, compose for all services, clean-start check

Same rules: small meaningful commits, no real company branding, don't edit `AI_JOURNAL.md` or `docs/DESIGN.md`, comment new dependencies, **report straight away**, don't silently change the design — ask.

## Part A — Follow-ups from brief 04 (one commit each)

1. **`asOf` = event time (Q21).** Add `claim_summary.last_event_at` (from the event's `occurredAt`). Every report's `asOf` = max `last_event_at` of the rows it's based on (null if none).
2. **Notifications don't depend on the version check (Q22).** An event older than the stored `claim_version` must **not** overwrite the summary but **must** still create its notification. Duplicate events still create nothing (`processed_events`). Add a test: deliver v2 then v1 → summary stays at v2, both notifications exist.
3. **Exposure always lists all six currencies (Q24):** MYR, SGD, THB, IDR, PHP, VND, with zeros when nothing is open. Keep a fixed order. Update the test.

## Part B — Document upload (claims-service)

Design §6.2 `claim_documents` and §9. Backend only; UI is brief 6.

### B1. Storage
- A small `DocumentStorage` interface (`store`, `open`, `delete`) with one implementation, `LocalDiskDocumentStorage`. Root folder configurable (default `./data/uploads`, ignored by git).
- Files saved as `{root}/{claimId}/{storedFilename}` where `storedFilename` is a **random UUID** — the original filename is never used in a path.
- The interface exists so object storage (S3) can replace local disk later without touching the rest. Don't build S3.

### B2. Table
Flyway migration for `claim_documents` exactly as design §6.2.

### B3. Endpoints
| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/claims/{id}/documents` (multipart: `file`, optional `informationRequestId`) | claim owner | Claim must not be `SETTLED`/`REJECTED`. If `informationRequestId` is given it must be an **`OPEN`** request of **this** claim. → 201 with document metadata |
| GET | `/api/claims/{id}/documents` | owner, officers of the claim's market, manager | metadata list, newest first, incl. which request it belongs to |
| GET | `/api/claims/{id}/documents/{docId}/content` | same | streams the file |

Uploading does **not** change claim status and does **not** write an outbox event (reporting doesn't need it). The claimant still uses the existing respond endpoint to answer an information request; the UI will upload files first, then send the response.

Permission check order as Q17: not allowed to see the claim → 404 before any other check.

### B4. Validation
- Max **10 MB** per file — configure Spring's multipart limit and turn "too large" into a **413** ProblemDetail (not Spring's default error).
- Allowed: **JPEG, PNG, PDF**. Check all three: file extension, declared content type, **and the first bytes of the file** (JPEG `FF D8 FF`, PNG `89 50 4E 47`, PDF `%PDF`). Mismatch → **415** ProblemDetail. No new library needed for this.
- Empty file → 400.
- Max **20 documents per claim** → 409 when exceeded.
- Original filename: strip any path parts, keep max 255 chars; it's only used for display and the download name.

### B5. Download response
- `Content-Type` = stored content type; `Content-Disposition: attachment` with the original filename (properly encoded for non-ASCII names); `X-Content-Type-Options: nosniff`.

### B6. Consistency between disk and DB
- Write the file first, then insert the row in a transaction. If the insert fails, delete the file. If the file write fails, no row is inserted.
- Say in the report what happens if the file is deleted from disk by hand (download should give 404/410 ProblemDetail, not 500).

### B7. Tests (Testcontainers, temp folder for storage)
- Owner uploads a PDF → 201; listed; download returns identical bytes and the right headers.
- Upload linked to an open request → works; linked to a `RESPONDED` request or another claim's request → 400/409.
- Another claimant → 404 on upload, list and download. Officer of another market → 404. Manager → can list and download.
- 11 MB file → 413. `.exe` renamed to `.pdf` (wrong first bytes) → 415. PNG bytes with `.pdf` extension → 415. Empty → 400.
- Upload on a `SETTLED` claim → 409. 21st document → 409.
- Filename `../../etc/passwd.pdf` (with valid PDF bytes) is stored under a UUID; download name has no path parts.

## Part C — Java PATH fix on my machine (not a repo change, no commit)

`java -version` on my PATH is still Java 8 because `C:\Program Files (x86)\Common Files\Oracle\Java\java8path` comes before Adoptium JDK 25.
- Remove that entry from PATH (or move it below the Adoptium entry). It's in the **System** PATH, so it may need admin rights.
- Only change the PATH entry. **Don't uninstall** Java 8.
- If you can't do it without admin rights, don't try workarounds — give me the exact steps (or one PowerShell command to run as administrator) and I'll do it.
- After the change, confirm in a **new** terminal that `java -version` shows 25.

## Done when
- `./mvnw clean verify` green, 0 skipped.
- Manually (Swagger): claimant uploads a PDF and a JPG to their claim, one linked to an open information request; MY officer lists and downloads both; a `.txt` renamed to `.pdf` is rejected.
- Exposure returns all six currencies.
- `java -version` → 25 in a new terminal (or you've told me the steps).

## Report back
- Test count run / passed / skipped
- Commit list
- Any design changes or questions
- What you did for the PATH (or the steps for me)
- Manual test steps
