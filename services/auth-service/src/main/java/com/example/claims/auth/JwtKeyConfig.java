package com.example.claims.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RSA signing key. Loaded from a PKCS#8 PEM file; if the file doesn't exist (local dev) a new key is
 * generated and written there, so tokens stay valid across restarts. The path must be outside git.
 */
@Configuration
class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey rsaKey(@Value("${auth.jwt.key-path}") Path path) throws Exception {
        RSAPrivateCrtKey privateKey = Files.exists(path) ? read(path) : generateAndSave(path);
        var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint()
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    /** auth-service validates its own tokens (for /api/auth/me) with the local public key. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey rsaKey, @Value("${auth.jwt.issuer}") String issuer) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private static RSAPrivateCrtKey read(Path path) throws Exception {
        String base64 = Files.readString(path).replaceAll("-----[A-Z ]+-----", "").replaceAll("\s", "");
        log.info("Loaded JWT signing key from {}", path.toAbsolutePath());
        return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private static RSAPrivateCrtKey generateAndSave(Path path) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var privateKey = (RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(privateKey.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, pem);
        try {
            Files.setPosixFilePermissions(absolute, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException windows) {
            // no POSIX permissions on Windows; the file lives in the user's profile
        }
        log.warn("No JWT signing key found - generated a new one at {}", absolute);
        return privateKey;
    }
}
