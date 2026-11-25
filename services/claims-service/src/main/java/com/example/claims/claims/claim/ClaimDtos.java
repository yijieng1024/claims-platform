package com.example.claims.claims.claim;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;

/**
 * Request and response bodies of the claims API. Amounts fit numeric(15,2).
 * In the OpenAPI spec every field is required unless marked {@code @Nullable} (Q28, see web-common).
 */
final class ClaimDtos {

    private ClaimDtos() {
    }

    record OpenClaimRequest(
            @NotNull UUID policyId,
            @NotNull LocalDate incidentDate,
            @NotBlank @Size(max = 255) String incidentLocation,
            @NotBlank @Size(max = 4000) String description,
            @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal claimedAmount) {
    }

    record InformationRequestBody(@NotBlank @Size(max = 2000) String question) {
    }

    record InformationResponseBody(@NotBlank @Size(max = 4000) String responseText) {
    }

    record AssessmentRequest(
            @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal reserveAmount,
            @Nullable @Size(max = 2000) String note) {
    }

    record SettlementRequest(
            @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal settledAmount,
            @Nullable @Size(max = 2000) String note) {
    }

    record RejectionRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    /** List row: no description. */
    record ClaimSummary(UUID id, String claimNumber, String policyNumber, String type, String market,
            String currency, LocalDate incidentDate, BigDecimal claimedAmount, @Nullable BigDecimal reserveAmount,
            @Nullable BigDecimal settledAmount, ClaimStatus status, String claimantName,
            @Nullable String assignedOfficerName,
            Instant createdAt, Instant updatedAt) {

        static ClaimSummary of(Claim c) {
            return new ClaimSummary(c.getId(), c.getClaimNumber(), c.getPolicyNumber(), c.getType(), c.getMarket(),
                    c.getCurrency(), c.getIncidentDate(), c.getClaimedAmount(), c.getReserveAmount(),
                    c.getSettledAmount(), c.getStatus(), c.getClaimantName(), c.getAssignedOfficerName(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    record ClaimDetail(UUID id, String claimNumber, long version, ClaimStatus status, UUID policyId,
            String policyNumber, BigDecimal sumInsured, BigDecimal remainingCover, String type, String market,
            String currency,
            LocalDate incidentDate, String incidentLocation, String description, BigDecimal claimedAmount,
            @Nullable BigDecimal reserveAmount, @Nullable BigDecimal settledAmount, @Nullable String rejectionReason,
            UUID claimantId, String claimantName, @Nullable UUID assignedOfficerId, @Nullable String assignedOfficerName,
            @Nullable Instant assignedAt, Instant createdAt, Instant updatedAt, @Nullable Instant closedAt,
            @Nullable InformationRequestView openInformationRequest) {

        /** {@code remainingCover}: sum insured minus the policy's other settled claims (Q31). */
        static ClaimDetail of(Claim c, BigDecimal sumInsured, BigDecimal remainingCover,
                InformationRequestView openRequest) {
            return new ClaimDetail(c.getId(), c.getClaimNumber(), c.getVersion(), c.getStatus(), c.getPolicyId(),
                    c.getPolicyNumber(), sumInsured, remainingCover, c.getType(), c.getMarket(), c.getCurrency(), c.getIncidentDate(),
                    c.getIncidentLocation(), c.getDescription(), c.getClaimedAmount(), c.getReserveAmount(),
                    c.getSettledAmount(), c.getRejectionReason(), c.getClaimantId(), c.getClaimantName(),
                    c.getAssignedOfficerId(), c.getAssignedOfficerName(), c.getAssignedAt(), c.getCreatedAt(),
                    c.getUpdatedAt(),
                    c.getClosedAt(), openRequest);
        }
    }

    record HistoryEntry(@Nullable ClaimStatus fromStatus, ClaimStatus toStatus, UUID changedById, String changedByName,
            String changedByRole, @Nullable String note, Instant changedAt) {

        static HistoryEntry of(StatusHistory h) {
            return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getChangedById(), h.getChangedByName(),
                    h.getChangedByRole(), h.getNote(), h.getChangedAt());
        }
    }

    record InformationRequestView(UUID id, String question, InformationRequest.Status status, @Nullable String responseText,
            UUID requestedById, Instant createdAt, @Nullable Instant respondedAt) {

        static InformationRequestView of(InformationRequest r) {
            return new InformationRequestView(r.getId(), r.getQuestion(), r.getStatus(), r.getResponseText(),
                    r.getRequestedById(), r.getCreatedAt(), r.getRespondedAt());
        }
    }

    /** Document metadata; {@code informationRequestId} is set if it was uploaded for a request. */
    record DocumentView(UUID id, @Nullable UUID informationRequestId, String originalFilename, String contentType,
            long sizeBytes, UUID uploadedById, Instant uploadedAt) {

        static DocumentView of(ClaimDocument d) {
            return new DocumentView(d.getId(), d.getInformationRequestId(), d.getOriginalFilename(),
                    d.getContentType(), d.getSizeBytes(), d.getUploadedById(), d.getUploadedAt());
        }
    }

    /** Stable JSON for a page (Spring's PageImpl JSON is not a guaranteed format). */
    record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                    p.getTotalPages());
        }
    }
}
