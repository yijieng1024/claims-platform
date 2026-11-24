package com.example.claims.claims;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.claims.claims.policy.PolicyController;
import com.example.claims.claims.policy.PolicyRepository;
import com.example.claims.web.WebCommonAutoConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs the real resource-server config against a stand-in auth-service JWKS endpoint (no DB needed).
 */
@WebMvcTest(PolicyController.class)
@Import(SecurityConfig.class)
@ImportAutoConfiguration(WebCommonAutoConfiguration.class)
class JwtVerificationTest {

    static final String ISSUER = "http://localhost:8081";
    static final RSAKey trustedKey = newKey("trusted");
    static final RSAKey otherKey = newKey("trusted"); // same kid, different key: signature must still fail
    static final HttpServer jwksServer = startJwksServer();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
    }

    @AfterAll
    static void stop() {
        jwksServer.stop(0);
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    PolicyRepository policies;

    @Test
    void tokenFromAuthServiceKeyIsAccepted() throws Exception {
        when(policies.findByHolderEmail("claimant1@example.com")).thenReturn(List.of());
        // /api/policies/** needs ROLE_CLAIMANT, so a 200 also proves the role claim was mapped
        mvc.perform(get("/api/policies/my").header("Authorization", "Bearer " + token(trustedKey, "CLAIMANT")))
                .andExpect(status().isOk());
        verify(policies).findByHolderEmail("claimant1@example.com");
    }

    @Test
    void roleClaimDecidesAccess() throws Exception {
        mvc.perform(get("/api/policies/my").header("Authorization", "Bearer " + token(trustedKey, "OFFICER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() throws Exception {
        mvc.perform(get("/api/policies/my").header("Authorization", "Bearer " + token(otherKey, "CLAIMANT")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/policies/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static String token(RSAKey key, String role) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("00000000-0000-0000-0002-000000000001")
                .claim("role", role)
                .claim("name", "Demo Claimant 1")
                .claim("email", "claimant1@example.com")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey newKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] body = new JWKSet(trustedKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
