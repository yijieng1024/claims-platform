package com.example.claims.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Asks claims-service whether a policy number belongs to an email (design section 4). Authenticates
 * with a short-lived service token signed by our own key; that token never leaves this class.
 */
@Component
class PolicyCheckClient {

    record VerifyRequest(String policyNumber, String email) {
    }

    record VerifyResponse(boolean match, String holderName) {
    }

    static final Duration SERVICE_TOKEN_TTL = Duration.ofMinutes(1);

    private static final Logger log = LoggerFactory.getLogger(PolicyCheckClient.class);

    private final RestClient restClient;
    private final JwtEncoder jwtEncoder;
    private final String issuer;

    PolicyCheckClient(JwtEncoder jwtEncoder,
                      @Value("${auth.jwt.issuer}") String issuer,
                      @Value("${auth.claims-service.url}") String claimsUrl,
                      @Value("${auth.claims-service.connect-timeout}") Duration connectTimeout,
                      @Value("${auth.claims-service.read-timeout}") Duration readTimeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(claimsUrl).requestFactory(requestFactory).build();
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
    }

    /** Holder name if the policy exists, isn't cancelled and is held by this email. 503 if claims-service can't say. */
    Optional<String> holderName(String policyNumber, String email) {
        try {
            VerifyResponse response = restClient.post()
                    .uri("/internal/policies/verify")
                    .headers(h -> h.setBearerAuth(serviceToken()))
                    .body(new VerifyRequest(policyNumber, email))
                    .retrieve() // 4xx/5xx throw RestClientException
                    .body(VerifyResponse.class);
            return response != null && response.match() ? Optional.ofNullable(response.holderName()) : Optional.empty();
        } catch (RestClientException e) {
            // down, timed out or answered with an error: not the user's fault, and no account is created
            log.warn("Policy check against claims-service failed: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Registration is temporarily unavailable, try again shortly");
        }
    }

    private String serviceToken() {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("auth-service")
                .issuedAt(now)
                .expiresAt(now.plus(SERVICE_TOKEN_TTL))
                .claim("role", "SERVICE")
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
