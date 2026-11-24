package com.example.claims.claims.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_status_history")
public class StatusHistory {

    @Id
    private UUID id;
    private UUID claimId;
    @Enumerated(EnumType.STRING)
    private ClaimStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private ClaimStatus toStatus;
    private UUID changedById;
    private String changedByName;
    private String changedByRole;
    private String note;
    private Instant changedAt;

    protected StatusHistory() {
    }

    static StatusHistory of(UUID claimId, ClaimStatus from, ClaimStatus to, Caller by, String note, Instant at) {
        var h = new StatusHistory();
        h.id = UUID.randomUUID();
        h.claimId = claimId;
        h.fromStatus = from;
        h.toStatus = to;
        h.changedById = by.id();
        h.changedByName = by.name();
        h.changedByRole = by.role();
        h.note = note;
        h.changedAt = at;
        return h;
    }

    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public UUID getChangedById() { return changedById; }
    public String getChangedByName() { return changedByName; }
    public String getChangedByRole() { return changedByRole; }
    public String getNote() { return note; }
    public Instant getChangedAt() { return changedAt; }
}
