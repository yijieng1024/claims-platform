package com.example.claims.claims.claim;

import com.example.claims.claims.claim.ClaimStateMachine.Action;
import com.example.claims.claims.policy.PolicyRepository.Policy;
import com.example.claims.events.ClaimSnapshot;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Every status change goes through {@link #move}, i.e. through {@link ClaimStateMachine}. */
@Entity
@Table(name = "claims")
public class Claim {

    @Id
    private UUID id;
    private String claimNumber;
    private UUID claimantId;
    private String claimantName;
    private UUID policyId;
    private String policyNumber;
    private String type;
    private String market;
    private String currency;
    private LocalDate incidentDate;
    private String incidentLocation;
    private String description;
    private BigDecimal claimedAmount;
    @Enumerated(EnumType.STRING)
    private ClaimStatus status;
    private UUID assignedOfficerId;
    private String assignedOfficerName;
    private BigDecimal reserveAmount;
    private BigDecimal settledAmount;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    @Version
    private Long version; // null until persisted, so Spring Data treats a new claim as new

    protected Claim() {
    }

    /** Type, market and currency come from the policy, never from the claimant (design section 5). */
    static Claim open(String claimNumber, Caller claimant, Policy policy, LocalDate incidentDate,
            String incidentLocation, String description, BigDecimal claimedAmount, Instant now) {
        var c = new Claim();
        c.id = UUID.randomUUID();
        c.claimNumber = claimNumber;
        c.claimantId = claimant.id();
        c.claimantName = claimant.name();
        c.policyId = policy.id();
        c.policyNumber = policy.policyNumber();
        c.type = policy.productType();
        c.market = policy.market();
        c.currency = policy.currency();
        c.incidentDate = incidentDate;
        c.incidentLocation = incidentLocation;
        c.description = description;
        c.claimedAmount = claimedAmount;
        c.status = ClaimStatus.SUBMITTED;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    void assign(Caller officer, Instant now) {
        move(Action.ASSIGN, now);
        assignedOfficerId = officer.id();
        assignedOfficerName = officer.name();
    }

    void requestInformation(Instant now) {
        move(Action.REQUEST_INFO, now);
    }

    void provideInformation(Instant now) {
        move(Action.PROVIDE_INFO, now);
    }

    void assess(BigDecimal reserve, Instant now) {
        move(Action.ASSESS, now);
        reserveAmount = reserve;
    }

    void settle(BigDecimal amount, Instant now) {
        move(Action.SETTLE, now);
        settledAmount = amount;
    }

    void reject(String reason, Instant now) {
        move(Action.REJECT, now);
        rejectionReason = reason;
    }

    private void move(Action action, Instant now) {
        status = ClaimStateMachine.next(status, action);
        updatedAt = now;
        if (status.isClosed()) {
            closedAt = now;
        }
    }

    ClaimSnapshot toSnapshot() {
        return new ClaimSnapshot(id, claimNumber, version, claimantId, claimantName, policyId, policyNumber, type,
                market, currency, incidentDate, incidentLocation, description, claimedAmount, reserveAmount,
                settledAmount, status.name(), assignedOfficerId, assignedOfficerName, rejectionReason, createdAt,
                updatedAt, closedAt);
    }

    public UUID getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public UUID getClaimantId() { return claimantId; }
    public String getClaimantName() { return claimantName; }
    public UUID getPolicyId() { return policyId; }
    public String getPolicyNumber() { return policyNumber; }
    public String getType() { return type; }
    public String getMarket() { return market; }
    public String getCurrency() { return currency; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getIncidentLocation() { return incidentLocation; }
    public String getDescription() { return description; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public ClaimStatus getStatus() { return status; }
    public UUID getAssignedOfficerId() { return assignedOfficerId; }
    public String getAssignedOfficerName() { return assignedOfficerName; }
    public BigDecimal getReserveAmount() { return reserveAmount; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Long getVersion() { return version; }
}
