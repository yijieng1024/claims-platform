package com.example.claims.claims.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Files at {@code {root}/{claimId}/{storedFilename}}; the stored name is a random UUID, so the original
 * filename never touches the file system (no path traversal).
 */
@Component
class LocalDiskDocumentStorage implements DocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskDocumentStorage.class);

    private final Path root;

    LocalDiskDocumentStorage(@Value("${claims.documents.root:./data/uploads}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(UUID claimId, InputStream content) throws IOException {
        String name = UUID.randomUUID().toString();
        Path file = path(claimId, name);
        Files.createDirectories(file.getParent());
        try {
            Files.copy(content, file); // fails rather than overwrite
        } catch (IOException e) {
            Files.deleteIfExists(file); // no half-written file left behind
            throw e;
        }
        return name;
    }

    @Override
    public InputStream open(UUID claimId, String storedFilename) throws IOException {
        return Files.newInputStream(path(claimId, storedFilename));
    }

    @Override
    public void delete(UUID claimId, String storedFilename) {
        try {
            Files.deleteIfExists(path(claimId, storedFilename));
        } catch (IOException e) {
            log.warn("Could not delete document file {}/{}: {}", claimId, storedFilename, e.toString());
        }
    }

    /** Both parts are UUIDs, so the result always stays under root. */
    private Path path(UUID claimId, String storedFilename) {
        return root.resolve(claimId.toString()).resolve(UUID.fromString(storedFilename).toString());
    }
}
