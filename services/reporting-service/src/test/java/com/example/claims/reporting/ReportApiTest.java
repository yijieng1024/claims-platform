package com.example.claims.reporting;

import static com.example.claims.reporting.TestClaim.CLAIMANT1;
import static com.example.claims.reporting.TestClaim.CLAIMANT2;
import static com.example.claims.reporting.TestClaim.MANAGER;
import static com.example.claims.reporting.TestClaim.OFFICER_MY1;
import static com.example.claims.reporting.TestClaim.OFFICER_MY2;
import static com.example.claims.reporting.TestClaim.OFFICER_SG1;
import static com.example.claims.reporting.TestClaim.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.claims.events.ClaimEvent;
import com.example.claims.reporting.events.ClaimEventProjector;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Report and notification endpoints. Events go straight into the projector (the Kafka path has its own
 * test), and the tables are emptied before each test so the numbers are exact.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReportApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ClaimEventProjector projector;

    @BeforeEach
    void emptyReadModel() {
        jdbc.sql("TRUNCATE claim_summary, notifications, processed_events").update();
    }

    @Test
    void outstandingCountsOpenClaimsByStatusAndMarket() throws Exception {
        mvc.perform(get("/api/reports/outstanding").with(token(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").isEmpty())
                .andExpect(jsonPath("$.byStatus").isEmpty());

        apply(new TestClaim("MY", "MYR", CLAIMANT1, "100").submitted());
        var assessed = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        apply(assessed.submitted(), assessed.assigned(OFFICER_MY1, T0), assessed.assessed("50"));
        var sg = new TestClaim("SG", "SGD", CLAIMANT2, "100");
        ClaimEvent sgSubmitted = sg.submitted();
        apply(sgSubmitted);
        var settled = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        ClaimEvent settle;
        apply(settled.submitted(), settled.assigned(OFFICER_MY1, T0), settled.assessed("50"),
                settle = settled.settled("50", T0));

        String body = mvc.perform(get("/api/reports/outstanding").with(token(MANAGER)))
                .andExpect(jsonPath("$.byStatus[*].status").value(contains("ASSESSED", "SUBMITTED")))
                .andExpect(jsonPath("$.byStatus[*].count").value(contains(1, 2)))
                .andExpect(jsonPath("$.byMarket[*].market").value(contains("MY", "SG")))
                .andExpect(jsonPath("$.byMarket[*].count").value(contains(2, 1)))
                .andReturn().getResponse().getContentAsString();
        // Q21: asOf is the newest event time in scope; settling a claim changed this report too
        assertThat(asOf(body)).isEqualTo(settle.occurredAt());
        body = mvc.perform(get("/api/reports/outstanding").with(token(OFFICER_SG1)))
                .andExpect(jsonPath("$.byMarket[*].market").value(contains("SG")))
                .andExpect(jsonPath("$.byStatus[*].count").value(contains(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(asOf(body)).isEqualTo(sgSubmitted.occurredAt());
    }

    private static Instant asOf(String body) {
        return OffsetDateTime.parse(JsonPath.read(body, "$.asOf")).toInstant();
    }

    @Test
    void exposureIsPerCurrencyAndOnlyOpenClaims() throws Exception {
        var myReserved1 = new TestClaim("MY", "MYR", CLAIMANT1, "900");
        apply(myReserved1.submitted(), myReserved1.assigned(OFFICER_MY1, T0), myReserved1.assessed("500.00"));
        var myReserved2 = new TestClaim("MY", "MYR", CLAIMANT1, "400");
        apply(myReserved2.submitted(), myReserved2.assigned(OFFICER_MY1, T0), myReserved2.assessed("300.50"));
        var myNoReserve1 = new TestClaim("MY", "MYR", CLAIMANT1, "1000.00");
        apply(myNoReserve1.submitted());
        var myNoReserve2 = new TestClaim("MY", "MYR", CLAIMANT1, "2000.25");
        apply(myNoReserve2.submitted(), myNoReserve2.assigned(OFFICER_MY2, T0));
        var mySettled = new TestClaim("MY", "MYR", CLAIMANT1, "5000"); // closed: left out
        apply(mySettled.submitted(), mySettled.assigned(OFFICER_MY1, T0), mySettled.assessed("999"),
                mySettled.settled("999", T0));
        var sgReserved = new TestClaim("SG", "SGD", CLAIMANT2, "800");
        apply(sgReserved.submitted(), sgReserved.assigned(OFFICER_SG1, T0), sgReserved.assessed("700.00"));
        var sgRejected = new TestClaim("SG", "SGD", CLAIMANT2, "7000"); // closed without reserve: left out
        apply(sgRejected.submitted(), sgRejected.assigned(OFFICER_SG1, T0), sgRejected.rejected(T0));

        mvc.perform(get("/api/reports/exposure").with(token(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").isNotEmpty())
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].currency").value("MYR"))
                .andExpect(jsonPath("$.currencies[0].reservedExposure").value(800.50))
                .andExpect(jsonPath("$.currencies[0].reservedCount").value(2))
                .andExpect(jsonPath("$.currencies[0].unreservedCount").value(2))
                .andExpect(jsonPath("$.currencies[0].unreservedClaimedAmount").value(3000.25))
                .andExpect(jsonPath("$.currencies[1].currency").value("SGD"))
                .andExpect(jsonPath("$.currencies[1].reservedExposure").value(700.00))
                .andExpect(jsonPath("$.currencies[1].reservedCount").value(1))
                .andExpect(jsonPath("$.currencies[1].unreservedCount").value(0))
                .andExpect(jsonPath("$.currencies[1].unreservedClaimedAmount").value(0));
        mvc.perform(get("/api/reports/exposure").with(token(OFFICER_MY1))).andExpect(status().isForbidden());
    }

    @Test
    void performanceAveragesDaysFromAssignedToClosed() throws Exception {
        var settled = new TestClaim("MY", "MYR", CLAIMANT1, "4000");
        apply(settled.submitted(), settled.assigned(OFFICER_MY1, T0), settled.assessed("3800"),
                settled.settled("3800.00", T0.plus(Duration.ofDays(2))));
        var rejected = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        apply(rejected.submitted(), rejected.assigned(OFFICER_MY1, T0),
                rejected.rejected(T0.plus(Duration.ofDays(4)).plus(Duration.ofHours(12))));
        var open = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        apply(open.submitted(), open.assigned(OFFICER_MY1, T0));
        var other = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        apply(other.submitted(), other.assigned(OFFICER_MY2, T0));

        mvc.perform(get("/api/reports/performance").with(token(OFFICER_MY2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officers[*].officerName").value(contains("Demo Officer MY-1", "Demo Officer MY-2")))
                .andExpect(jsonPath("$.officers[0].closed").value(2))
                .andExpect(jsonPath("$.officers[0].settled").value(1))
                .andExpect(jsonPath("$.officers[0].rejected").value(1))
                .andExpect(jsonPath("$.officers[0].averageDaysToClose").value(3.25)) // (2 + 4.5) / 2
                .andExpect(jsonPath("$.officers[0].settledAmounts[0].currency").value("MYR"))
                .andExpect(jsonPath("$.officers[0].settledAmounts[0].amount").value(3800.00))
                .andExpect(jsonPath("$.officers[1].closed").value(0))
                .andExpect(jsonPath("$.officers[1].averageDaysToClose").isEmpty())
                .andExpect(jsonPath("$.officers[1].settledAmounts").isEmpty());
    }

    @Test
    void workloadIsLimitedToTheOfficersMarket() throws Exception {
        var my1 = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        apply(my1.submitted(), my1.assigned(OFFICER_MY1, T0));
        var my2 = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        apply(my2.submitted(), my2.assigned(OFFICER_MY1, T0), my2.infoRequested());
        var my3 = new TestClaim("MY", "MYR", CLAIMANT1, "100");
        apply(my3.submitted(), my3.assigned(OFFICER_MY1, T0), my3.rejected(T0));
        var sg = new TestClaim("SG", "SGD", CLAIMANT2, "100");
        apply(sg.submitted(), sg.assigned(OFFICER_SG1, T0), sg.assessed("10"));
        apply(new TestClaim("MY", "MYR", CLAIMANT1, "100").submitted()); // in the queue: nobody's workload

        mvc.perform(get("/api/reports/workload").with(token(OFFICER_MY1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officers[*].officerId").value(contains(OFFICER_MY1.id().toString())))
                .andExpect(jsonPath("$.officers[0].market").value("MY"))
                .andExpect(jsonPath("$.officers[0].openClaims").value(2))
                .andExpect(jsonPath("$.officers[0].underReview").value(1))
                .andExpect(jsonPath("$.officers[0].infoRequested").value(1))
                .andExpect(jsonPath("$.officers[0].assessed").value(0));
        // an officer can't widen the scope with ?market=
        mvc.perform(get("/api/reports/workload").param("market", "SG").with(token(OFFICER_MY1)))
                .andExpect(jsonPath("$.officers[*].market").value(contains("MY")));
        mvc.perform(get("/api/reports/workload").with(token(MANAGER)))
                .andExpect(jsonPath("$.officers[*].officerName").value(containsInAnyOrder("Demo Officer MY-1", "Demo Officer SG-1")));
        mvc.perform(get("/api/reports/workload").param("market", "SG").with(token(MANAGER)))
                .andExpect(jsonPath("$.officers[*].officerName").value(contains("Demo Officer SG-1")))
                .andExpect(jsonPath("$.officers[0].assessed").value(1));
    }

    @Test
    void claimantsCannotSeeReports() throws Exception {
        for (String report : new String[] {"outstanding", "exposure", "workload", "performance"}) {
            mvc.perform(get("/api/reports/" + report).with(token(CLAIMANT1))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/reports/whoami").with(token(MANAGER))).andExpect(status().isNotFound());
    }

    @Test
    void notificationsAreMineUnreadFirstAndCanBeMarkedRead() throws Exception {
        var claim = new TestClaim("MY", "MYR", CLAIMANT1, "4000");
        apply(claim.submitted(), claim.assigned(OFFICER_MY1, T0), claim.infoRequested(), claim.infoProvided(),
                claim.assessed("3800"), claim.settled("3800", T0));
        var other = new TestClaim("MY", "MYR", CLAIMANT2, "100");
        apply(other.submitted(), other.assigned(OFFICER_MY1, T0), other.rejected(T0));

        String body = mvc.perform(get("/api/notifications").with(token(CLAIMANT1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type").value(contains("CLAIM_SETTLED", "INFO_REQUESTED", "CLAIM_ASSIGNED")))
                .andExpect(jsonPath("$[0].message").value("Claim %s has been settled: MYR 3,800.00".formatted(claim.number)))
                .andExpect(jsonPath("$[1].message").value("More information is needed for claim %s.".formatted(claim.number)))
                .andExpect(jsonPath("$[0].readAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/notifications").with(token(OFFICER_MY1)))
                .andExpect(jsonPath("$[*].type").value(contains("INFO_PROVIDED")))
                .andExpect(jsonPath("$[0].message").value("The claimant has responded on claim %s.".formatted(claim.number)));
        mvc.perform(get("/api/notifications").with(token(CLAIMANT2)))
                .andExpect(jsonPath("$[*].type").value(contains("CLAIM_REJECTED", "CLAIM_ASSIGNED")));

        String newest = JsonPath.read(body, "$[0].id");
        mvc.perform(post("/api/notifications/" + newest + "/read").with(token(CLAIMANT2))).andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + newest + "/read").with(token(CLAIMANT1))).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").with(token(CLAIMANT1)))
                .andExpect(jsonPath("$[*].type").value(contains("INFO_REQUESTED", "CLAIM_ASSIGNED", "CLAIM_SETTLED")))
                .andExpect(jsonPath("$[2].readAt").isNotEmpty());
    }

    private void apply(ClaimEvent... events) {
        for (ClaimEvent e : events) {
            projector.apply(e);
        }
    }
}
