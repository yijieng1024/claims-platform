package com.example.claims.claims.claim;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.claims.claims.claim.ClaimDtos.DocumentView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Claim documents; rules in {@link DocumentService}. */
@RestController
@RequestMapping("/api/claims/{id}/documents")
class DocumentController {

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @Operation(summary = "Claim owner: upload a JPEG, PNG or PDF (max 10 MB, 20 per claim), optionally for an open information request")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DocumentView upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID informationRequestId) {
        return service.upload(id, Caller.of(jwt), file, informationRequestId);
    }

    @Operation(summary = "Document metadata, newest first (owner, officers of the claim's market, manager)")
    @GetMapping
    List<DocumentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.list(id, Caller.of(jwt));
    }

    @Operation(summary = "Download a document (same access as the list)")
    // documented as binary so the generated client downloads a Blob (the real type is the stored JPEG/PNG/PDF type)
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{documentId}/content")
    ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @PathVariable UUID documentId) {
        var content = service.open(id, documentId, Caller.of(jwt));
        ClaimDocument doc = content.document();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .contentLength(doc.getSizeBytes())
                // always a download, never rendered inline; filename* carries non-ASCII names (RFC 6266)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(doc.getOriginalFilename(), UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(content.stream()));
    }
}
