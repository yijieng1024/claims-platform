package com.example.claims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Full app against a real PostgreSQL in Docker. Skipped (not failed) when Docker isn't available. */
@SpringBootTest(properties = "auth.jwt.key-path=target/test-jwt-key.pem")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void registerThenLoginThenMe() throws Exception {
        String email = uniqueEmail();
        register("{\"email\":\"%s\",\"password\":\"secret-pass\",\"name\":\"Test Person\"}".formatted(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMANT"));

        String body = login(email, "secret-pass")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("CLAIMANT"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Test Person"))
                .andExpect(jsonPath("$.market").isEmpty());
    }

    @Test
    void registerWithExistingEmailIsConflict() throws Exception {
        String email = uniqueEmail();
        String json = "{\"email\":\"%s\",\"password\":\"secret-pass\",\"name\":\"Test Person\"}".formatted(email);
        register(json).andExpect(status().isCreated());

        register(json.replace(email, email.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        String wrongPassword = login("officer.my1@example.com", "not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@example.com", "not-the-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void registerIgnoresRoleInBody() throws Exception {
        String email = uniqueEmail();
        register("{\"email\":\"%s\",\"password\":\"secret-pass\",\"name\":\"Sneaky\",\"role\":\"OFFICER\",\"market\":\"MY\"}"
                .formatted(email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMANT"))
                .andExpect(jsonPath("$.market").isEmpty());

        login(email, "secret-pass").andExpect(jsonPath("$.user.role").value("CLAIMANT"));
    }

    @Test
    void invalidRegistrationIsProblemDetailWithFieldErrors() throws Exception {
        register("{\"email\":\"not-an-email\",\"password\":\"short\",\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void meWithoutTokenIsProblemDetail401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
