package com.example.claims.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full app against a real PostgreSQL in Docker. claims-service is replaced by a tiny JDK HTTP server,
 * so the real RestClient, timeouts and error handling are exercised over HTTP.
 */
@SpringBootTest(properties = "auth.jwt.key-path=target/test-jwt-key.pem")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    /** What the stand-in claims-service does with the next verify call. */
    enum ClaimsMode { MATCH, NO_MATCH, SERVER_ERROR, DROP_CONNECTION }

    static volatile ClaimsMode claimsMode;
    static volatile String lastAuthorization;
    static volatile String lastBody;
    static final AtomicInteger claimsCalls = new AtomicInteger();
    static final HttpServer claims = startClaimsStub();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("auth.claims-service.url", () -> "http://localhost:" + claims.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        claims.stop(0);
    }

    @BeforeEach
    void reset() {
        claimsMode = ClaimsMode.MATCH;
        claimsCalls.set(0);
        lastAuthorization = null;
        lastBody = null;
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    JwtDecoder jwtDecoder;

    @Test
    void registerWithValidPolicyThenLoginThenMe() throws Exception {
        String email = uniqueEmail();
        register(body(email, "POL-MY-PRP-0007"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLAIMANT"));

        // claims-service was asked with a 1-minute SERVICE token signed by our key
        assertThat(lastBody).contains("\"policyNumber\":\"POL-MY-PRP-0007\"").contains("\"email\":\"" + email + "\"");
        Jwt serviceToken = jwtDecoder.decode(lastAuthorization.substring("Bearer ".length()));
        assertThat(serviceToken.getSubject()).isEqualTo("auth-service");
        assertThat(serviceToken.getClaimAsString("role")).isEqualTo("SERVICE");
        assertThat(Duration.between(serviceToken.getIssuedAt(), serviceToken.getExpiresAt())).isEqualTo(Duration.ofMinutes(1));

        String loginBody = login(email, "secret-pass")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("CLAIMANT"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(loginBody, "$.accessToken");
        assertThat(jwtDecoder.decode(token).getClaimAsString("email")).isEqualTo(email);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Test Person"))
                .andExpect(jsonPath("$.market").isEmpty());
    }

    @Test
    void failedPolicyCheckIsGeneric400AndCreatesNoUser() throws Exception {
        claimsMode = ClaimsMode.NO_MATCH;
        String email = uniqueEmail();
        register(body(email, "POL-VN-MTR-0010"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(AuthService.POLICY_NOT_VERIFIED));
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @Test
    void claimsServiceUnreachableIs503AndCreatesNoUser() throws Exception {
        claimsMode = ClaimsMode.DROP_CONNECTION;
        String email = uniqueEmail();
        register(body(email, "POL-MY-PRP-0007"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @Test
    void claimsServiceErrorIs503AndCreatesNoUser() throws Exception {
        claimsMode = ClaimsMode.SERVER_ERROR;
        String email = uniqueEmail();
        register(body(email, "POL-MY-PRP-0007"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @Test
    void registerWithExistingEmailIsConflictWithoutCallingClaims() throws Exception {
        String email = uniqueEmail();
        register(body(email, "POL-MY-PRP-0007")).andExpect(status().isCreated());
        claimsCalls.set(0);

        register(body(email.toUpperCase(), "POL-MY-PRP-0007"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
        // seeded account: never went through register
        register(body("claimant1@example.com", "POL-MY-MTR-0001")).andExpect(status().isConflict());

        assertThat(claimsCalls.get()).isZero();
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
        register("{\"email\":\"%s\",\"password\":\"secret-pass\",\"name\":\"Sneaky\",\"policyNumber\":\"POL-MY-PRP-0007\",\"role\":\"OFFICER\",\"market\":\"MY\"}"
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
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.policyNumber").exists());
        assertThat(claimsCalls.get()).isZero();
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

    private static String body(String email, String policyNumber) {
        return "{\"email\":\"%s\",\"password\":\"secret-pass\",\"name\":\"Test Person\",\"policyNumber\":\"%s\"}"
                .formatted(email, policyNumber);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static HttpServer startClaimsStub() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/policies/verify", exchange -> {
                claimsCalls.incrementAndGet();
                lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
                lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                switch (claimsMode) {
                    case DROP_CONNECTION -> {
                        exchange.close(); // no response at all: same client-side failure as a refused connection
                        return;
                    }
                    case SERVER_ERROR -> exchange.sendResponseHeaders(500, -1);
                    default -> {
                        byte[] json = (claimsMode == ClaimsMode.MATCH
                                ? "{\"match\":true,\"holderName\":\"Demo Holder\"}"
                                : "{\"match\":false}").getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, json.length);
                        exchange.getResponseBody().write(json);
                    }
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
