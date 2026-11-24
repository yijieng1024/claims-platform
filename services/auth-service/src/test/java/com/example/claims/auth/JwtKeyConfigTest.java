package com.example.claims.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeyConfigTest {

    @Test
    void generatedKeyIsPersistedAndReloadedUnchanged(@TempDir Path dir) throws Exception {
        Path keyFile = dir.resolve("nested/jwt-key.pem");
        var config = new JwtKeyConfig();

        var first = config.rsaKey(keyFile);
        assertThat(Files.readString(keyFile)).startsWith("-----BEGIN PRIVATE KEY-----");

        var reloaded = config.rsaKey(keyFile);
        assertThat(reloaded.getKeyID()).isEqualTo(first.getKeyID());
        assertThat(reloaded.toRSAPrivateKey()).isEqualTo(first.toRSAPrivateKey());
    }
}
