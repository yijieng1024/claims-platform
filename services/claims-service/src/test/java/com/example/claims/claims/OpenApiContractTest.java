package com.example.claims.claims;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Q28: the spec marks every field required unless it is {@code @Nullable} (rule in web-common). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OpenApiContractTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void alwaysPresentFieldsAreRequiredAndNullableOnesAreNot() throws Exception {
        String detail = "$.components.schemas.ClaimDetail";
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(detail + ".required", hasItems("id", "claimNumber", "status", "sumInsured")))
                .andExpect(jsonPath(detail + ".required", not(hasItem("reserveAmount"))))
                .andExpect(jsonPath(detail + ".required", not(hasItem("assignedOfficerId"))))
                .andExpect(jsonPath(detail + ".properties.reserveAmount.type", hasItems("number", "null")))
                // optional request field: an assessment note may be left out
                .andExpect(jsonPath("$.components.schemas.AssessmentRequest.required", not(hasItem("note"))));
    }
}
