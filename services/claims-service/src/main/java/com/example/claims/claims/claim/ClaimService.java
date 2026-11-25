package com.example.claims.claims.claim;

import com.example.claims.claims.claim.ClaimDtos.ClaimDetail;
import com.example.claims.claims.claim.ClaimDtos.ClaimSummary;
import com.example.claims.claims.claim.ClaimDtos.HistoryEntry;
import com.example.claims.claims.claim.ClaimDtos.InformationRequestView;
import com.example.claims.claims.claim.ClaimDtos.OpenClaimRequest;
import com.example.claims.claims.claim.ClaimDtos.PageResponse;
import com.example.claims.claims.claim.ClaimStateMachine.Action;
import com.example.claims.claims.outbox.OutboxEvent;
import com.example.claims.claims.outbox.OutboxEventRepository;
import com.example.claims.claims.policy.PolicyRepository;
import com.example.claims.claims.policy.PolicyRepository.Policy;
import com.example.claims.events.ClaimEvent;
import com.example.claims.events.ClaimEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * All claim rules (design section 5). Each write is one transaction: update the claim, add a history
 * row, add a PENDING outbox row carrying the full event. If any step fails, none of it is saved.
 * <p>
 * Visibility: claimants see their own claims, officers their market's, managers all. Anything else is a
 * 404, so nobody learns that a claim exists. Officers acting on a claim they can see but don't own: 403.
 * Permission is always checked before the claim's status (Q17).
 */
@Service
@Transactional
class ClaimService {

    private static final EnumSet<ClaimStatus> CLOSED = EnumSet.of(ClaimStatus.SETTLED, ClaimStatus.REJECTED);

    private final ClaimRepository claims;
    private final StatusHistoryRepository history;
    private final InformationRequestRepository infoRequests;
    private final OutboxEventRepository outbox;
    private final PolicyRepository policies;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    ClaimService(ClaimRepository claims, StatusHistoryRepository history, InformationRequestRepository infoRequests,
            OutboxEventRepository outbox, PolicyRepository policies, JdbcClient jdbc, JsonMapper json) {
        this.claims = claims;
        this.history = history;
        this.infoRequests = infoRequests;
        this.outbox = outbox;
        this.policies = policies;
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---- writes ----

    ClaimDetail open(Caller claimant, OpenClaimRequest req) {
        Policy policy = policies.findByIdAndHolderEmail(req.policyId(), claimant.email())
                .orElseThrow(() -> notFound("Policy not found"));
        if ("CANCELLED".equals(policy.status())) {
            throw badRequest("Policy %s is cancelled".formatted(policy.policyNumber()));
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC); // Q14: "today" is the UTC date, as in the database
        if (req.incidentDate().isAfter(today)) {
            throw badRequest("Incident date cannot be in the future");
        }
        if (req.incidentDate().isBefore(policy.startDate()) || req.incidentDate().isAfter(policy.endDate())) {
            throw badRequest("Incident date is outside the policy cover period (%s to %s)"
                    .formatted(policy.startDate(), policy.endDate()));
        }
        throwIfDuplicate(policy.id(), req.incidentDate());
        Instant now = now();
        var claim = Claim.open(nextClaimNumber(today.getYear()), claimant, policy, req.incidentDate(),
                req.incidentLocation().trim(), req.description().trim(), req.claimedAmount(), now);
        return save(claim, null, claimant, ClaimEventType.ClaimSubmitted, null, now);
    }

    /**
     * Q30: 409 if the policy already has a claim for this incident date that wasn't rejected. Also called
     * after a lost race on the unique index (ClaimController), when the winner has been committed.
     */
    void throwIfDuplicate(UUID policyId, LocalDate incidentDate) {
        claims.findFirstByPolicyIdAndIncidentDateAndStatusNot(policyId, incidentDate, ClaimStatus.REJECTED)
                .ifPresent(existing -> {
                    throw new DuplicateClaimException(existing);
                });
    }

    ClaimDetail assign(UUID claimId, Caller officer) {
        Claim claim = visible(claimId, officer);
        ClaimStatus from = claim.getStatus();
        Instant now = now();
        claim.assign(officer, now); // 409 unless SUBMITTED, i.e. not yet picked up
        return save(claim, from, officer, ClaimEventType.ClaimAssigned, null, now);
    }

    ClaimDetail requestInformation(UUID claimId, Caller officer, String question) {
        Claim claim = forAssignedOfficer(claimId, officer, Action.REQUEST_INFO);
        ClaimStatus from = claim.getStatus();
        Instant now = now();
        claim.requestInformation(now);
        infoRequests.save(InformationRequest.open(claim.getId(), officer.id(), question.trim(), now));
        return save(claim, from, officer, ClaimEventType.InformationRequested, question.trim(), now);
    }

    ClaimDetail respond(UUID claimId, UUID requestId, Caller claimant, String responseText) {
        Claim claim = visible(claimId, claimant); // only the owner gets past this (route is claimant-only)
        InformationRequest request = infoRequests.findByIdAndClaimId(requestId, claimId)
                .orElseThrow(() -> notFound("Information request not found"));
        if (request.getStatus() != InformationRequest.Status.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This information request was already answered");
        }
        ClaimStatus from = claim.getStatus();
        Instant now = now();
        claim.provideInformation(now);
        request.respond(responseText.trim(), now);
        return save(claim, from, claimant, ClaimEventType.InformationProvided, null, now);
    }

    /** From UNDER_REVIEW, or again from ASSESSED to change the reserve (history row, no status change). */
    ClaimDetail assess(UUID claimId, Caller officer, BigDecimal reserve, String note) {
        Claim claim = forAssignedOfficer(claimId, officer, Action.ASSESS);
        requireWithinRemainingCover(reserve, claim, "Reserve");
        ClaimStatus from = claim.getStatus();
        Instant now = now();
        claim.assess(reserve, now);
        String text = (from == ClaimStatus.ASSESSED ? "Reserve updated to " : "Reserve set to ")
                + claim.getCurrency() + " " + reserve.toPlainString();
        return save(claim, from, officer, ClaimEventType.ClaimAssessed, withNote(text, note), now);
    }

    ClaimDetail settle(UUID claimId, Caller officer, BigDecimal amount, String note) {
        Claim claim = forAssignedOfficer(claimId, officer, Action.SETTLE);
        requireWithinRemainingCover(amount, claim, "Settled amount");
        ClaimStatus from = claim.getStatus();
        Instant now = now();
        claim.settle(amount, now);
        String text = "Settled at " + claim.getCurrency() + " " + amount.toPlainString();
        return save(claim, from, officer, ClaimEventType.ClaimSettled, withNote(text, note), now);
    }

    ClaimDetail reject(UUID claimId, Caller officer, String reason) {
        Claim claim = forAssignedOfficer(claimId, officer, Action.REJECT);
        ClaimStatus from = claim.getStatus();
        Instant now = now();
        claim.reject(reason.trim(), now);
        return save(claim, from, officer, ClaimEventType.ClaimRejected, reason.trim(), now);
    }

    /**
     * Claim, history and outbox in the current transaction. The flush bumps {@code version} (and fails
     * with an optimistic-lock error if someone else changed the claim meanwhile) before the snapshot is
     * taken, so the event carries the version that was actually saved.
     */
    private ClaimDetail save(Claim claim, ClaimStatus from, Caller actor, ClaimEventType type, String note,
            Instant now) {
        claims.saveAndFlush(claim);
        history.save(StatusHistory.of(claim.getId(), from, claim.getStatus(), actor, note, now));
        var event = new ClaimEvent(UUID.randomUUID(), type, now, actor.actor(), claim.toSnapshot());
        outbox.saveAndFlush(OutboxEvent.pending(event.eventId(), claim.getId(), type.name(),
                json.writeValueAsString(event), now));
        return detail(claim);
    }

    // ---- reads ----

    @Transactional(readOnly = true)
    List<ClaimSummary> myClaims(Caller claimant) {
        return claims.findByClaimantIdOrderByCreatedAtDesc(claimant.id()).stream().map(ClaimSummary::of).toList();
    }

    @Transactional(readOnly = true)
    List<ClaimSummary> queue(Caller officer) {
        return claims.findByMarketAndStatusAndAssignedOfficerIdIsNullOrderByCreatedAtAsc(officer.market(),
                ClaimStatus.SUBMITTED).stream().map(ClaimSummary::of).toList();
    }

    @Transactional(readOnly = true)
    List<ClaimSummary> assignedToMe(Caller officer) {
        return claims.findByAssignedOfficerIdAndStatusNotInOrderByCreatedAtAsc(officer.id(), CLOSED).stream()
                .map(ClaimSummary::of).toList();
    }

    @Transactional(readOnly = true)
    PageResponse<ClaimSummary> search(ClaimStatus status, String market, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(claims.search(status, market, pageable).map(ClaimSummary::of));
    }

    @Transactional(readOnly = true)
    ClaimDetail get(UUID claimId, Caller caller) {
        return detail(visible(claimId, caller));
    }

    @Transactional(readOnly = true)
    List<HistoryEntry> history(UUID claimId, Caller caller) {
        visible(claimId, caller);
        return history.findByClaimIdOrderByChangedAtAsc(claimId).stream().map(HistoryEntry::of).toList();
    }

    @Transactional(readOnly = true)
    List<InformationRequestView> informationRequests(UUID claimId, Caller caller) {
        visible(claimId, caller);
        return infoRequests.findByClaimIdOrderByCreatedAtAsc(claimId).stream().map(InformationRequestView::of)
                .toList();
    }

    // ---- helpers ----

    /** The claim if the caller may see it, otherwise 404 (not 403: don't reveal that it exists). */
    private Claim visible(UUID claimId, Caller caller) {
        return claims.findById(claimId).filter(c -> canSee(c, caller)).orElseThrow(() -> notFound("Claim not found"));
    }

    /** Owner, officers of the claim's market, managers. Also used for documents. */
    static boolean canSee(Claim c, Caller caller) {
        return switch (caller.role()) {
            case Caller.CLAIMANT -> c.getClaimantId().equals(caller.id());
            case Caller.OFFICER -> c.getMarket().equals(caller.market());
            case Caller.MANAGER -> true;
            default -> false;
        };
    }

    /**
     * Permission before state (Q17): not your market 404, not your claim 403, and only then the status
     * check (409), so someone without rights learns nothing about the claim's state. The status is
     * checked here, before amount validation, so a wrong-status request is a 409 rather than a 400.
     */
    private Claim forAssignedOfficer(UUID claimId, Caller officer, Action action) {
        Claim claim = visible(claimId, officer);
        if (!officer.id().equals(claim.getAssignedOfficerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the officer assigned to this claim can do this");
        }
        ClaimStateMachine.next(claim.getStatus(), action);
        return claim;
    }

    private ClaimDetail detail(Claim claim) {
        InformationRequestView open = infoRequests.findByClaimIdAndStatus(claim.getId(), InformationRequest.Status.OPEN)
                .map(InformationRequestView::of).orElse(null);
        Policy policy = policies.findById(claim.getPolicyId()).orElseThrow(); // FK guarantees it exists
        return ClaimDetail.of(claim, policy.sumInsured(), remainingCover(claim, policy), open);
    }

    /** Q31: sum insured minus what the policy's other SETTLED claims paid. Other claims' reserves don't count. */
    private BigDecimal remainingCover(Claim claim, Policy policy) {
        return policy.sumInsured().subtract(claims.settledOnPolicyExcept(policy.id(), claim.getId()));
    }

    /**
     * Q31: the amount must fit in the policy's remaining cover. The policy row stays locked until commit,
     * so two officers settling two claims on one policy at once are checked one after the other and the
     * second one sees the first's settlement.
     */
    private void requireWithinRemainingCover(BigDecimal amount, Claim claim, String what) {
        Policy policy = policies.findByIdForUpdate(claim.getPolicyId()).orElseThrow();
        BigDecimal remaining = remainingCover(claim, policy);
        if (amount.compareTo(remaining) > 0) {
            throw badRequest("%s cannot exceed the remaining cover on this policy: %s %s (sum insured %s %s)"
                    .formatted(what, policy.currency(), remaining.toPlainString(), policy.currency(),
                            policy.sumInsured().toPlainString()));
        }
    }

    private String nextClaimNumber(int year) {
        long n = jdbc.sql("SELECT nextval('claim_number_seq')").query(Long.class).single();
        return "CLM-%d-%06d".formatted(year, n);
    }

    private static String withNote(String text, String note) {
        return note == null || note.isBlank() ? text : text + ". " + note.trim();
    }

    /** Postgres stores microseconds; truncating keeps the event snapshot equal to what is in the table. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static ResponseStatusException notFound(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    /** The claimant owns the policy, so naming their existing claim reveals nothing; the id lets the UI link to it. */
    static class DuplicateClaimException extends ErrorResponseException {

        DuplicateClaimException(Claim existing) {
            super(HttpStatus.CONFLICT, problem(existing), null);
        }

        private static ProblemDetail problem(Claim existing) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "A claim for this incident already exists: " + existing.getClaimNumber());
            problem.setProperty("existingClaimId", existing.getId());
            problem.setProperty("existingClaimNumber", existing.getClaimNumber());
            return problem;
        }
    }
}
