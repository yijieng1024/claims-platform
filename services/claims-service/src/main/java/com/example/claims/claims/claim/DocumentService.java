package com.example.claims.claims.claim;

import com.example.claims.claims.claim.ClaimDtos.DocumentView;
import com.example.claims.claims.storage.DocumentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Claim documents (design section 9). Uploading changes neither the claim's status nor the outbox:
 * reporting doesn't need documents.
 * <p>
 * Who: the claim owner uploads (the route is claimant-only); owner, the market's officers and managers
 * read. Anyone who can't see the claim gets 404 before any other check (Q17).
 * <p>
 * Disk and DB: the file is written first, then the row inserted, in one transaction that holds the
 * claim's row lock. If the insert or the commit fails, the file is deleted; if the write fails, there is
 * no row. A file deleted by hand later gives 410 on download.
 * <p>
 * The 10 MB limit is Spring's multipart limit (application.yml), rendered as 413 by the shared advice.
 */
@Service
class DocumentService {

    static final int MAX_DOCUMENTS_PER_CLAIM = 20;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** Allowed types: extension, declared content type and first bytes must all agree. */
    enum FileType {
        JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpg", "jpeg"),
        PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}, "png"),
        PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F'}, "pdf");

        final String contentType;
        final byte[] magic;
        final List<String> extensions;

        FileType(String contentType, byte[] magic, String... extensions) {
            this.contentType = contentType;
            this.magic = magic;
            this.extensions = List.of(extensions);
        }
    }

    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final InformationRequestRepository infoRequests;
    private final DocumentStorage storage;
    private final TransactionTemplate tx;

    DocumentService(ClaimRepository claims, ClaimDocumentRepository documents,
            InformationRequestRepository infoRequests, DocumentStorage storage, TransactionTemplate tx) {
        this.claims = claims;
        this.documents = documents;
        this.infoRequests = infoRequests;
        this.storage = storage;
        this.tx = tx;
    }

    DocumentView upload(UUID claimId, Caller owner, MultipartFile file, UUID informationRequestId) {
        String[] stored = {null};
        try {
            return tx.execute(status -> {
                Claim claim = claims.findByIdForUpdate(claimId).filter(c -> ClaimService.canSee(c, owner))
                        .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Claim not found"));
                if (claim.getStatus().isClosed()) {
                    throw error(HttpStatus.CONFLICT, "Documents can't be added to a %s claim".formatted(claim.getStatus()));
                }
                if (documents.countByClaimId(claimId) >= MAX_DOCUMENTS_PER_CLAIM) {
                    throw error(HttpStatus.CONFLICT, "A claim can have at most %d documents".formatted(MAX_DOCUMENTS_PER_CLAIM));
                }
                if (informationRequestId != null) {
                    var request = infoRequests.findByIdAndClaimId(informationRequestId, claimId)
                            .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "No such information request on this claim"));
                    if (request.getStatus() != InformationRequest.Status.OPEN) {
                        throw error(HttpStatus.CONFLICT, "This information request was already answered");
                    }
                }
                if (file.isEmpty()) {
                    throw error(HttpStatus.BAD_REQUEST, "The file is empty");
                }
                String name = displayName(file.getOriginalFilename());
                FileType type = check(name, file);
                stored[0] = io(() -> storage.store(claimId, file.getInputStream()));
                var doc = documents.saveAndFlush(ClaimDocument.of(claimId, informationRequestId, owner.id(), name,
                        stored[0], type.contentType, file.getSize(), Instant.now().truncatedTo(ChronoUnit.MICROS)));
                return DocumentView.of(doc);
            });
        } catch (RuntimeException e) {
            if (stored[0] != null) {
                storage.delete(claimId, stored[0]); // the row isn't there, so neither should the file be
            }
            throw e;
        }
    }

    List<DocumentView> list(UUID claimId, Caller caller) {
        visible(claimId, caller);
        return documents.findByClaimIdOrderByUploadedAtDesc(claimId).stream().map(DocumentView::of).toList();
    }

    record Content(ClaimDocument document, InputStream stream) {
    }

    /** The stream is opened here, so a missing file is a 410 before any response byte is written. */
    Content open(UUID claimId, UUID documentId, Caller caller) {
        visible(claimId, caller);
        ClaimDocument doc = documents.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Document not found"));
        try {
            return new Content(doc, storage.open(claimId, doc.getStoredFilename()));
        } catch (NoSuchFileException e) {
            log.warn("Document {} of claim {} has no file in storage", documentId, claimId);
            throw error(HttpStatus.GONE, "The file of this document is no longer available");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void visible(UUID claimId, Caller caller) {
        claims.findById(claimId).filter(c -> ClaimService.canSee(c, caller))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Claim not found"));
    }

    /** 415 unless extension, declared content type and the file's first bytes all say the same allowed type. */
    private static FileType check(String name, MultipartFile file) {
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        FileType type = Arrays.stream(FileType.values()).filter(t -> t.extensions.contains(extension)).findFirst()
                .orElseThrow(() -> error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG and PDF files are allowed"));
        if (!type.contentType.equals(declaredType(file))) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The file's content type doesn't match its .%s extension".formatted(extension));
        }
        byte[] head = io(() -> {
            try (InputStream in = file.getInputStream()) {
                return in.readNBytes(type.magic.length);
            }
        });
        if (!Arrays.equals(head, type.magic)) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The file's content is not a valid %s".formatted(type));
        }
        return type;
    }

    /** "application/pdf; name=x" becomes "application/pdf". */
    private static String declaredType(MultipartFile file) {
        String type = String.valueOf(file.getContentType());
        int semicolon = type.indexOf(';');
        return (semicolon < 0 ? type : type.substring(0, semicolon)).strip().toLowerCase(Locale.ROOT);
    }

    /** Display/download name only: no path parts, at most 255 characters, extension kept. */
    static String displayName(String original) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).strip();
        if (name.length() > MAX_FILENAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot >= 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, MAX_FILENAME_LENGTH - extension.length()) + extension;
        }
        return name;
    }

    private interface IoCall<T> {
        T call() throws IOException;
    }

    private static <T> T io(IoCall<T> call) {
        try {
            return call.call();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ResponseStatusException error(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }
}
