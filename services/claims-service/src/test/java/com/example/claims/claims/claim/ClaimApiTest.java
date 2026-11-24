package com.example.claims.claims.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The claims API against a real PostgreSQL with the seeded policies (V3/V4). Users are the seeded auth
 * accounts (same ids/emails), with tokens from spring-security-test's jwt().
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ClaimApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    // seeded policies
    static final String MY_MOTOR = "00000000-0000-0000-0003-000000000001";     // claimant1, sum insured 85,000
    static final String TH_EXPIRED = "00000000-0000-0000-0003-000000000003";   // claimant1, ended 18 months ago
    static final String ID_MOTOR = "00000000-0000-0000-0003-000000000004";     // claimant2
    static final String VN_CANCELLED = "00000000-0000-0000-0003-000000000010"; // holder6

    static final JwtRequestPostProcessor CLAIMANT1 = user("00000000-0000-0000-0002-000000000001", "Demo Claimant 1", "CLAIMANT", "claimant1@example.com", null);
    static final JwtRequestPostProcessor CLAIMANT2 = user("00000000-0000-0000-0002-000000000002", "Demo Claimant 2", "CLAIMANT", "claimant2@example.com", null);
    static final JwtRequestPostProcessor HOLDER6 = user("00000000-0000-0000-0002-000000000099", "Demo Holder Six", "CLAIMANT", "holder6@example.com", null);
    static final JwtRequestPostProcessor OFFICER_MY1 = user("00000000-0000-0000-0001-000000000011", "Demo Officer MY-1", "OFFICER", "officer.my1@example.com", "MY");
    static final JwtRequestPostProcessor OFFICER_MY2 = user("00000000-0000-0000-0001-000000000012", "Demo Officer MY-2", "OFFICER", "officer.my2@example.com", "MY");
    static final JwtRequestPostProcessor OFFICER_SG1 = user("00000000-0000-0000-0001-000000000021", "Demo Officer SG-1", "OFFICER", "officer.sg1@example.com", "SG");
    static final JwtRequestPostProcessor MANAGER = user("00000000-0000-0000-0000-000000000001", "Demo Manager", "MANAGER", "manager@example.com", null);

    static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;

    @Test
    void happyPathOpenAssignRequestRespondAssessSettle() throws Exception {
        String id = openClaim(CLAIMANT1, MY_MOTOR, TODAY.minusDays(3), "1200.00");
        expectOutbox(id, 1, "ClaimSubmitted", 0);
        mvc.perform(get("/api/claims/" + id).with(CLAIMANT1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimNumber").value(matchesPattern("CLM-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.type").value("MOTOR"))       // copied from the policy
                .andExpect(jsonPath("$.market").value("MY"))
                .andExpect(jsonPath("$.currency").value("MYR"))
                .andExpect(jsonPath("$.policyNumber").value("POL-MY-MTR-0001"))
                .andExpect(jsonPath("$.sumInsured").value(85000.00));

        mvc.perform(get("/api/claims/queue").with(OFFICER_MY1)).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/claims/queue").with(OFFICER_SG1)).andExpect(jsonPath("$[*].id", not(hasItem(id))));

        action(OFFICER_MY1, id, "assign", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedOfficerName").value("Demo Officer MY-1"))
                .andExpect(jsonPath("$.assignedAt").isNotEmpty());
        expectOutbox(id, 2, "ClaimAssigned", 1);
        // Q19: assigned_at is stored and carried in the snapshot
        assertThat(jdbc.sql("""
                        SELECT (payload->'claim'->>'assignedAt')::timestamptz = c.assigned_at
                        FROM outbox_events o JOIN claims c ON c.id = o.aggregate_id
                        WHERE o.aggregate_id = ?::uuid AND o.event_type = 'ClaimAssigned'""")
                .param(id).query(Boolean.class).single()).isTrue();
        mvc.perform(get("/api/claims/queue").with(OFFICER_MY1)).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc.perform(get("/api/claims/assigned").with(OFFICER_MY1)).andExpect(jsonPath("$[*].id", hasItem(id)));

        String requestId = JsonPath.read(action(OFFICER_MY1, id, "information-requests", "{\"question\":\"Photo of the damage?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INFO_REQUESTED"))
                .andExpect(jsonPath("$.openInformationRequest.question").value("Photo of the damage?"))
                .andReturn().getResponse().getContentAsString(), "$.openInformationRequest.id");
        expectOutbox(id, 3, "InformationRequested", 2);

        respond(CLAIMANT1, id, requestId, "Rear bumper dented, photo to follow")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.openInformationRequest").isEmpty());
        expectOutbox(id, 4, "InformationProvided", 3);
        respond(CLAIMANT1, id, requestId, "again").andExpect(status().isConflict());

        action(OFFICER_MY1, id, "assessment", "{\"reserveAmount\":1000.00,\"note\":\"Garage quote\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSESSED"))
                .andExpect(jsonPath("$.reserveAmount").value(1000.00));
        expectOutbox(id, 5, "ClaimAssessed", 4);

        action(OFFICER_MY1, id, "settlement", "{\"settledAmount\":950.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settledAmount").value(950.00))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        expectOutbox(id, 6, "ClaimSettled", 5);

        mvc.perform(get("/api/claims/" + id + "/history").with(CLAIMANT1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].toStatus").value(contains(
                        "SUBMITTED", "UNDER_REVIEW", "INFO_REQUESTED", "UNDER_REVIEW", "ASSESSED", "SETTLED")))
                .andExpect(jsonPath("$[0].fromStatus").isEmpty())
                .andExpect(jsonPath("$[1].changedByName").value("Demo Officer MY-1"))
                .andExpect(jsonPath("$[3].changedByRole").value("CLAIMANT"))
                .andExpect(jsonPath("$[4].note").value("Reserve set to MYR 1000.00. Garage quote"));
        mvc.perform(get("/api/claims/" + id + "/information-requests").with(OFFICER_MY2))
                .andExpect(jsonPath("$[0].status").value("RESPONDED"))
                .andExpect(jsonPath("$[0].responseText").value("Rear bumper dented, photo to follow"));
        mvc.perform(get("/api/claims/assigned").with(OFFICER_MY1)).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc.perform(get("/api/claims/my").with(CLAIMANT1)).andExpect(jsonPath("$[*].id", hasItem(id)));
    }

    @Test
    void reserveUpdateAndRejectionWriteHistoryAndEvents() throws Exception {
        String id = assignedClaim(OFFICER_MY1);
        action(OFFICER_MY1, id, "assessment", "{\"reserveAmount\":500}").andExpect(status().isOk());
        action(OFFICER_MY1, id, "assessment", "{\"reserveAmount\":800,\"note\":\"New quote\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSESSED"))
                .andExpect(jsonPath("$.reserveAmount").value(800));
        expectOutbox(id, 4, "ClaimAssessed", 3); // exposure changed, so still an event

        // more information after assessing (Q7)
        action(OFFICER_MY1, id, "information-requests", "{\"question\":\"Police report?\"}")
                .andExpect(jsonPath("$.status").value("INFO_REQUESTED"));
        // only one OPEN request per claim: a second request is an invalid transition
        action(OFFICER_MY1, id, "information-requests", "{\"question\":\"And another?\"}")
                .andExpect(status().isConflict());
        String requestId = JsonPath.read(mvc.perform(get("/api/claims/" + id).with(OFFICER_MY1))
                .andReturn().getResponse().getContentAsString(), "$.openInformationRequest.id");
        respond(CLAIMANT1, id, requestId, "Attached").andExpect(status().isOk());

        action(OFFICER_MY1, id, "rejection", "{\"reason\":\"Not covered\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Not covered"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        expectOutbox(id, 7, "ClaimRejected", 6);

        mvc.perform(get("/api/claims/" + id + "/history").with(MANAGER))
                .andExpect(jsonPath("$[3].fromStatus").value("ASSESSED"))
                .andExpect(jsonPath("$[3].toStatus").value("ASSESSED"))
                .andExpect(jsonPath("$[3].note").value("Reserve updated to MYR 800. New quote"))
                .andExpect(jsonPath("$[6].note").value("Not covered"));
        action(OFFICER_MY1, id, "rejection", "{\"reason\":\"again\"}").andExpect(status().isConflict());
    }

    @Test
    void openingAClaimChecksThePolicy() throws Exception {
        open(CLAIMANT2, MY_MOTOR, TODAY.minusDays(1), "100").andExpect(status().isNotFound()); // someone else's
        open(CLAIMANT1, "00000000-0000-0000-0000-00000000dead", TODAY.minusDays(1), "100").andExpect(status().isNotFound());
        open(HOLDER6, VN_CANCELLED, TODAY.minusDays(1), "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cancelled")));
        open(CLAIMANT1, TH_EXPIRED, TODAY.minusDays(1), "100") // incident after the cover ended
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cover period")));
        open(CLAIMANT1, MY_MOTOR, TODAY.minusMonths(7), "100").andExpect(status().isBadRequest()); // before start
        open(CLAIMANT1, MY_MOTOR, TODAY.plusDays(1), "100")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Incident date cannot be in the future"));
        open(CLAIMANT1, MY_MOTOR, TODAY, "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.claimedAmount").exists());
        // expired policy is fine if the incident was inside the cover period
        open(CLAIMANT1, TH_EXPIRED, TODAY.minusMonths(24), "100").andExpect(status().isCreated());
        open(CLAIMANT2, ID_MOTOR, TODAY, "100").andExpect(status().isCreated());
    }

    @Test
    void outboxFailureRollsBackTheClaimChange() throws Exception {
        String id = openClaim(CLAIMANT1, MY_MOTOR, TODAY.minusDays(1), "300");
        // make the next ClaimAssigned outbox insert fail inside the database; NOT VALID skips existing rows
        jdbc.sql("ALTER TABLE outbox_events ADD CONSTRAINT test_no_assigned CHECK (event_type <> 'ClaimAssigned') NOT VALID").update();
        try {
            action(OFFICER_MY1, id, "assign", null).andExpect(status().isInternalServerError());
        } finally {
            jdbc.sql("ALTER TABLE outbox_events DROP CONSTRAINT test_no_assigned").update();
        }
        Map<String, Object> claim = jdbc.sql("SELECT status, version, assigned_officer_id FROM claims WHERE id = ?::uuid")
                .param(id).query().singleRow();
        assertThat(claim).containsEntry("status", "SUBMITTED").containsEntry("version", 0L);
        assertThat(claim.get("assigned_officer_id")).isNull();
        assertThat(count("claim_status_history", id)).isEqualTo(1);
        assertThat(count("outbox_events", id)).isEqualTo(1);

        action(OFFICER_MY1, id, "assign", null).andExpect(status().isOk()); // works again without the constraint
    }

    @Test
    void concurrentAssignOneWinsOtherGets409() throws Exception {
        for (int round = 0; round < 3; round++) {
            String id = openClaim(CLAIMANT1, MY_MOTOR, TODAY.minusDays(1), "400");
            var barrier = new CyclicBarrier(2);
            List<Future<Integer>> results = new ArrayList<>();
            try (var pool = Executors.newFixedThreadPool(2)) {
                for (var officer : List.of(OFFICER_MY1, OFFICER_MY2)) {
                    results.add(pool.submit(() -> {
                        barrier.await();
                        return action(officer, id, "assign", null).andReturn().getResponse().getStatus();
                    }));
                }
            }
            List<Integer> statuses = new ArrayList<>();
            for (var r : results) {
                statuses.add(r.get());
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(count("claim_status_history", id)).isEqualTo(2);
            assertThat(count("outbox_events", id)).isEqualTo(2);
        }
    }

    @Test
    void whoMaySeeAndActOnAClaim() throws Exception {
        String id = openClaim(CLAIMANT1, MY_MOTOR, TODAY.minusDays(1), "500");

        // another market's officer and another claimant: 404, the claim "doesn't exist" for them
        mvc.perform(get("/api/claims/" + id).with(OFFICER_SG1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/claims/" + id + "/history").with(OFFICER_SG1)).andExpect(status().isNotFound());
        action(OFFICER_SG1, id, "assign", null).andExpect(status().isNotFound());
        mvc.perform(get("/api/claims/" + id).with(CLAIMANT2))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/claims/" + id).with(MANAGER)).andExpect(status().isOk());
        mvc.perform(get("/api/claims/" + id).with(OFFICER_MY2)).andExpect(status().isOk());

        // managers are read-only; claimants can't do officer actions
        action(MANAGER, id, "assign", null).andExpect(status().isForbidden());
        action(CLAIMANT1, id, "assign", null).andExpect(status().isForbidden());

        action(OFFICER_MY1, id, "assign", null).andExpect(status().isOk());
        // same market, but not the assigned officer
        action(OFFICER_MY2, id, "assessment", "{\"reserveAmount\":100}")
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        // permission before state (Q17): settling an UNDER_REVIEW claim is invalid, but a non-assigned
        // officer is told 403 (not 409), and another market's officer 404 — neither learns the status
        action(OFFICER_MY2, id, "settlement", "{\"settledAmount\":100}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.currentStatus").doesNotExist());
        action(OFFICER_SG1, id, "settlement", "{\"settledAmount\":100}").andExpect(status().isNotFound());

        String requestId = JsonPath.read(action(OFFICER_MY1, id, "information-requests", "{\"question\":\"Q?\"}")
                .andReturn().getResponse().getContentAsString(), "$.openInformationRequest.id");
        respond(CLAIMANT2, id, requestId, "not mine").andExpect(status().isNotFound());
        respond(CLAIMANT1, id, "00000000-0000-0000-0000-000000000000", "wrong id").andExpect(status().isNotFound());
    }

    @Test
    void amountAndStatusRules() throws Exception {
        String id = assignedClaim(OFFICER_MY1);
        action(OFFICER_MY1, id, "assessment", "{\"reserveAmount\":85000.01}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("sum insured")));
        action(OFFICER_MY1, id, "settlement", "{\"settledAmount\":100}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.currentStatus").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.action").value("SETTLE"));
        action(OFFICER_MY1, id, "rejection", "{\"reason\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());

        action(OFFICER_MY1, id, "assessment", "{\"reserveAmount\":85000.00}").andExpect(status().isOk());
        action(OFFICER_MY1, id, "settlement", "{\"settledAmount\":85000.01}").andExpect(status().isBadRequest());
        assertThat(count("outbox_events", id)).isEqualTo(3); // rejected attempts wrote nothing
    }

    @Test
    void managerListsAllMarketsWithFiltersAndPages() throws Exception {
        String my = openClaim(CLAIMANT1, MY_MOTOR, TODAY.minusDays(1), "10");
        String id = openClaim(CLAIMANT2, ID_MOTOR, TODAY.minusDays(1), "10");

        mvc.perform(get("/api/claims").with(MANAGER).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(my)))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)))
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
        mvc.perform(get("/api/claims").with(MANAGER).param("market", "ID").param("status", "SUBMITTED").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(my))))
                .andExpect(jsonPath("$.content[*].market", not(hasItem("MY"))));
        mvc.perform(get("/api/claims").with(MANAGER).param("size", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/claims").with(MANAGER).param("size", "1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/claims").with(MANAGER).param("status", "NOPE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/claims").with(OFFICER_MY1)).andExpect(status().isForbidden());
    }

    // ---- helpers ----

    /** The claim has exactly {@code rows} outbox rows; the newest is PENDING with this type and claim version. */
    private void expectOutbox(String claimId, int rows, String eventType, long version) {
        assertThat(count("outbox_events", claimId)).isEqualTo(rows);
        Map<String, Object> last = jdbc.sql("""
                        SELECT id::text AS id, event_type, status, payload->>'eventId' AS event_id,
                               payload->>'eventType' AS payload_type, (payload->'claim'->>'version')::bigint AS version
                        FROM outbox_events WHERE aggregate_id = ?::uuid ORDER BY created_at DESC LIMIT 1""")
                .param(claimId).query().singleRow();
        assertThat(last).containsEntry("event_type", eventType).containsEntry("payload_type", eventType)
                .containsEntry("status", "PENDING").containsEntry("version", version);
        assertThat(last.get("event_id")).isEqualTo(last.get("id"));
    }

    private int count(String table, String claimId) {
        String column = table.equals("outbox_events") ? "aggregate_id" : "claim_id";
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = ?::uuid")
                .param(claimId).query(Integer.class).single();
    }

    private String assignedClaim(JwtRequestPostProcessor officer) throws Exception {
        String id = openClaim(CLAIMANT1, MY_MOTOR, TODAY.minusDays(2), "900");
        action(officer, id, "assign", null).andExpect(status().isOk());
        return id;
    }

    private String openClaim(JwtRequestPostProcessor claimant, String policyId, LocalDate incident, String amount)
            throws Exception {
        String body = open(claimant, policyId, incident, amount).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions open(JwtRequestPostProcessor claimant, String policyId, LocalDate incident, String amount)
            throws Exception {
        return mvc.perform(post("/api/claims").with(claimant).contentType(MediaType.APPLICATION_JSON).content("""
                {"policyId":"%s","incidentDate":"%s","incidentLocation":"Jalan Contoh, KL",
                 "description":"Hit from behind at a junction","claimedAmount":%s}""".formatted(policyId, incident, amount)));
    }

    private ResultActions action(JwtRequestPostProcessor user, String claimId, String path, String json) throws Exception {
        return mvc.perform(post("/api/claims/" + claimId + "/" + path).with(user)
                .contentType(MediaType.APPLICATION_JSON).content(json == null ? "" : json));
    }

    private ResultActions respond(JwtRequestPostProcessor user, String claimId, String requestId, String text)
            throws Exception {
        return action(user, claimId, "information-requests/" + requestId + "/response",
                "{\"responseText\":\"%s\"}".formatted(text));
    }

    private static JwtRequestPostProcessor user(String id, String name, String role, String email, String market) {
        return jwt().jwt(j -> {
            j.subject(id).claim("role", role).claim("name", name).claim("email", email);
            if (market != null) {
                j.claim("market", market);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
