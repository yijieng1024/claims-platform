package com.example.claims.claims.policy;

import com.example.claims.claims.policy.PolicyRepository.Policy;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Access rules (CLAIMANT for /api/policies, SERVICE for /internal) are in SecurityConfig. */
@RestController
public class PolicyController {

    record VerifyRequest(@NotBlank @Size(max = 30) String policyNumber, @NotBlank @Size(max = 255) String email) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VerifyResponse(boolean match, @Nullable String holderName) {
    }

    private final PolicyRepository policies;

    PolicyController(PolicyRepository policies) {
        this.policies = policies;
    }

    @Operation(summary = "Policies whose holder email matches the caller's email")
    @GetMapping("/api/policies/my")
    List<Policy> myPolicies(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        return email == null ? List.of() : policies.findByHolderEmail(email);
    }

    @Operation(summary = "Internal (service token only): does this policy belong to this email?")
    @PostMapping("/internal/policies/verify")
    VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        return policies.findHolderNameForRegistration(request.policyNumber(), request.email())
                .map(name -> new VerifyResponse(true, name))
                .orElse(new VerifyResponse(false, null));
    }
}
