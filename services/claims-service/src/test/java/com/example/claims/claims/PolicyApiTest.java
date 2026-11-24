package com.example.claims.claims;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs against the seeded policies (V3__seed_policies.sql) in a real PostgreSQL. Tokens come from
 * spring-security-test's jwt(); the role-claim mapping itself is covered by JwtVerificationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PolicyApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void myPoliciesReturnsOnlyTheCallersPolicies() throws Exception {
        mvc.perform(get("/api/policies/my").with(claimant("Claimant1@Example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].policyNumber",
                        containsInAnyOrder("POL-MY-MTR-0001", "POL-SG-PRP-0002", "POL-TH-MTR-0003")))
                .andExpect(jsonPath("$[?(@.policyNumber == 'POL-MY-MTR-0001')].inForceToday").value(true))
                .andExpect(jsonPath("$[?(@.policyNumber == 'POL-TH-MTR-0003')].inForceToday").value(false));
    }

    @Test
    void verifyMatchesCorrectNumberAndEmail() throws Exception {
        verify("POL-MY-PRP-0007", "HOLDER3@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").value(true))
                .andExpect(jsonPath("$.holderName").value("Demo Holder Three"));
    }

    @Test
    void verifyDoesNotMatchWrongEmail() throws Exception {
        verify("POL-MY-PRP-0007", "holder4@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").value(false))
                .andExpect(jsonPath("$.holderName").doesNotExist());
    }

    @Test
    void verifyDoesNotMatchCancelledPolicy() throws Exception {
        verify("POL-VN-MTR-0010", "holder6@example.com")
                .andExpect(jsonPath("$.match").value(false));
    }

    @Test
    void verifyMatchesExpiredPolicy() throws Exception {
        verify("POL-TH-MTR-0003", "claimant1@example.com")
                .andExpect(jsonPath("$.match").value(true));
    }

    @Test
    void claimantCannotCallInternalEndpoints() throws Exception {
        mvc.perform(post("/internal/policies/verify").with(claimant("claimant1@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyNumber\":\"POL-MY-MTR-0001\",\"email\":\"claimant1@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void serviceTokenCannotCallPublicApi() throws Exception {
        mvc.perform(get("/api/policies/my").with(service()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/claims/my").with(service()))
                .andExpect(status().isForbidden());
    }

    private ResultActions verify(String policyNumber, String email) throws Exception {
        return mvc.perform(post("/internal/policies/verify").with(service())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"policyNumber\":\"%s\",\"email\":\"%s\"}".formatted(policyNumber, email)));
    }

    private static JwtRequestPostProcessor claimant(String email) {
        return jwt().jwt(j -> j.claim("role", "CLAIMANT").claim("email", email))
                .authorities(new SimpleGrantedAuthority("ROLE_CLAIMANT"));
    }

    private static JwtRequestPostProcessor service() {
        return jwt().jwt(j -> j.subject("auth-service").claim("role", "SERVICE"))
                .authorities(new SimpleGrantedAuthority("ROLE_SERVICE"));
    }
}
