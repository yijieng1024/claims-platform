package com.example.claims.claims.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.UUID;

/**
 * Where uploaded claim documents live (design section 9). Local disk today; object storage (S3) would be
 * a second implementation, nothing else changes. Callers never pass a user-supplied name: the stored name
 * is generated here.
 */
public interface DocumentStorage {

    /** Saves the content under a new random name and returns that name. */
    String store(UUID claimId, InputStream content) throws IOException;

    /** @throws NoSuchFileException if the file is gone (e.g. deleted by hand) */
    InputStream open(UUID claimId, String storedFilename) throws IOException;

    /** Best effort; a missing file is not an error. */
    void delete(UUID claimId, String storedFilename);
}
