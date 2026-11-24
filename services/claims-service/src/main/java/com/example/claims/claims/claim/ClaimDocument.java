package com.example.claims.claims.claim;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Metadata of an uploaded file; the bytes are in DocumentStorage under {@code storedFilename}. */
@Entity
@Table(name = "claim_documents")
public class ClaimDocument {

    @Id
    private UUID id;
    private UUID claimId;
    private UUID informationRequestId;
    private UUID uploadedById;
    private String originalFilename;
    private String storedFilename;
    private String contentType;
    private long sizeBytes;
    private Instant uploadedAt;

    protected ClaimDocument() {
    }

    static ClaimDocument of(UUID claimId, UUID informationRequestId, UUID uploadedById, String originalFilename,
            String storedFilename, String contentType, long sizeBytes, Instant now) {
        var d = new ClaimDocument();
        d.id = UUID.randomUUID();
        d.claimId = claimId;
        d.informationRequestId = informationRequestId;
        d.uploadedById = uploadedById;
        d.originalFilename = originalFilename;
        d.storedFilename = storedFilename;
        d.contentType = contentType;
        d.sizeBytes = sizeBytes;
        d.uploadedAt = now;
        return d;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public UUID getInformationRequestId() { return informationRequestId; }
    public UUID getUploadedById() { return uploadedById; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getUploadedAt() { return uploadedAt; }
}
