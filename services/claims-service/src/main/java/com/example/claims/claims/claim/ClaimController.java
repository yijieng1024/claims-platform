package com.example.claims.claims.claim;

import com.example.claims.claims.claim.ClaimDtos.AssessmentRequest;
import com.example.claims.claims.claim.ClaimDtos.ClaimDetail;
import com.example.claims.claims.claim.ClaimDtos.ClaimSummary;
import com.example.claims.claims.claim.ClaimDtos.HistoryEntry;
import com.example.claims.claims.claim.ClaimDtos.InformationRequestBody;
import com.example.claims.claims.claim.ClaimDtos.InformationRequestView;
import com.example.claims.claims.claim.ClaimDtos.InformationResponseBody;
import com.example.claims.claims.claim.ClaimDtos.OpenClaimRequest;
import com.example.claims.claims.claim.ClaimDtos.PageResponse;
import com.example.claims.claims.claim.ClaimDtos.RejectionRequest;
import com.example.claims.claims.claim.ClaimDtos.SettlementRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which role may call which endpoint is in SecurityConfig; which claim they may touch is in ClaimService.
 * Every write returns the claim as it is afterwards.
 */
@RestController
@RequestMapping("/api/claims")
class ClaimController {

    private final ClaimService service;

    ClaimController(ClaimService service) {
        this.service = service;
    }

    @Operation(summary = "Claimant: open a claim on one of their own policies")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClaimDetail open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenClaimRequest body) {
        try {
            return service.open(Caller.of(jwt), body);
        } catch (DataIntegrityViolationException e) {
            // Q30: lost a race on the unique index. That transaction is rolled back and the winner is
            // committed (Postgres waits for it before reporting the violation), so look it up for the 409.
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            if (cause.contains("claims_policy_incident_uidx")) {
                service.throwIfDuplicate(body.policyId(), body.incidentDate());
            }
            throw e;
        }
    }

    @Operation(summary = "Claimant: my claims, newest first")
    @GetMapping("/my")
    List<ClaimSummary> my(@AuthenticationPrincipal Jwt jwt) {
        return service.myClaims(Caller.of(jwt));
    }

    @Operation(summary = "Officer: unassigned SUBMITTED claims in my market, oldest first")
    @GetMapping("/queue")
    List<ClaimSummary> queue(@AuthenticationPrincipal Jwt jwt) {
        return service.queue(Caller.of(jwt));
    }

    @Operation(summary = "Officer: my open claims")
    @GetMapping("/assigned")
    List<ClaimSummary> assigned(@AuthenticationPrincipal Jwt jwt) {
        return service.assignedToMe(Caller.of(jwt));
    }

    @Operation(summary = "Manager: all claims, newest first, optionally filtered by status and market")
    @GetMapping
    PageResponse<ClaimSummary> all(@RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String market,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(status, market, page, size);
    }

    @Operation(summary = "Claim detail (owner, officers of its market, manager)")
    @GetMapping("/{id}")
    ClaimDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(id, Caller.of(jwt));
    }

    @Operation(summary = "Status history, oldest first")
    @GetMapping("/{id}/history")
    List<HistoryEntry> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.history(id, Caller.of(jwt));
    }

    @Operation(summary = "Information requests, oldest first")
    @GetMapping("/{id}/information-requests")
    List<InformationRequestView> informationRequests(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.informationRequests(id, Caller.of(jwt));
    }

    @Operation(summary = "Officer: pick up a SUBMITTED claim (assigns it to me)")
    @PostMapping("/{id}/assign")
    ClaimDetail assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.assign(id, Caller.of(jwt));
    }

    @Operation(summary = "Assigned officer: ask the claimant for more information")
    @PostMapping("/{id}/information-requests")
    ClaimDetail requestInformation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody InformationRequestBody body) {
        return service.requestInformation(id, Caller.of(jwt), body.question());
    }

    @Operation(summary = "Claim owner: answer the open information request (text only for now)")
    @PostMapping("/{id}/information-requests/{requestId}/response")
    ClaimDetail respond(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID requestId,
            @Valid @RequestBody InformationResponseBody body) {
        return service.respond(id, requestId, Caller.of(jwt), body.responseText());
    }

    @Operation(summary = "Assigned officer: set or update the reserve (max: remaining cover on the policy)")
    @PostMapping("/{id}/assessment")
    ClaimDetail assess(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody AssessmentRequest body) {
        return service.assess(id, Caller.of(jwt), body.reserveAmount(), body.note());
    }

    @Operation(summary = "Assigned officer: settle an ASSESSED claim (max: remaining cover on the policy)")
    @PostMapping("/{id}/settlement")
    ClaimDetail settle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody SettlementRequest body) {
        return service.settle(id, Caller.of(jwt), body.settledAmount(), body.note());
    }

    @Operation(summary = "Assigned officer: reject the claim")
    @PostMapping("/{id}/rejection")
    ClaimDetail reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody RejectionRequest body) {
        return service.reject(id, Caller.of(jwt), body.reason());
    }
}
