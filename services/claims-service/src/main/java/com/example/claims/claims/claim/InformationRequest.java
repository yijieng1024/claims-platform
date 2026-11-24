package com.example.claims.claims.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Text-only for now; uploaded files get linked to a request later (claim_documents, design 6.2). */
@Entity
@Table(name = "information_requests")
public class InformationRequest {

    public enum Status { OPEN, RESPONDED }

    @Id
    private UUID id;
    private UUID claimId;
    private UUID requestedById;
    private String question;
    @Enumerated(EnumType.STRING)
    private Status status;
    private String responseText;
    private Instant createdAt;
    private Instant respondedAt;

    protected InformationRequest() {
    }

    static InformationRequest open(UUID claimId, UUID officerId, String question, Instant now) {
        var r = new InformationRequest();
        r.id = UUID.randomUUID();
        r.claimId = claimId;
        r.requestedById = officerId;
        r.question = question;
        r.status = Status.OPEN;
        r.createdAt = now;
        return r;
    }

    void respond(String text, Instant now) {
        status = Status.RESPONDED;
        responseText = text;
        respondedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getRequestedById() { return requestedById; }
    public String getQuestion() { return question; }
    public Status getStatus() { return status; }
    public String getResponseText() { return responseText; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
