package com.example.claims.claims.claim;

import static com.example.claims.claims.claim.ClaimApiTest.CLAIMANT1;
import static com.example.claims.claims.claim.ClaimApiTest.CLAIMANT2;
import static com.example.claims.claims.claim.ClaimApiTest.MANAGER;
import static com.example.claims.claims.claim.ClaimApiTest.MY_MOTOR;
import static com.example.claims.claims.claim.ClaimApiTest.OFFICER_MY1;
import static com.example.claims.claims.claim.ClaimApiTest.OFFICER_MY2;
import static com.example.claims.claims.claim.ClaimApiTest.OFFICER_SG1;
import static com.example.claims.claims.claim.ClaimApiTest.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Document upload/list/download against real PostgreSQL, with files in a temp folder. Most requests go
 * through MockMvc; the size limit needs a real server (MockMvc skips the container's multipart parsing),
 * so that one test uses HTTP with a stubbed JwtDecoder.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DocumentApiTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("claims.documents.root", () -> storageRoot.toString());
    }

    static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;
    @LocalServerPort
    int port;
    @MockitoBean
    JwtDecoder jwtDecoder; // only used by the real-HTTP test; MockMvc's jwt() bypasses it

    @Test
    void ownerUploadsListsAndDownloadsIdenticalBytes() throws Exception {
        String claim = openClaim();
        String id = JsonPath.read(upload(CLAIMANT1, claim, file("Schadensbericht-ü 报告.pdf", "application/pdf", PDF), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("Schadensbericht-ü 报告.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$.informationRequestId").isEmpty())
                .andReturn().getResponse().getContentAsString(), "$.id");
        upload(CLAIMANT1, claim, file("photo.JPG", "image/jpeg", JPEG), null).andExpect(status().isCreated());

        mvc.perform(get("/api/claims/{c}/documents", claim).with(CLAIMANT1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].originalFilename").value(contains("photo.JPG", "Schadensbericht-ü 报告.pdf")));
        var response = mvc.perform(get("/api/claims/{c}/documents/{d}/content", claim, id).with(CLAIMANT1))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''Schadensbericht-%C3%BC%20%E6%8A%A5%E5%91%8A.pdf")))
                .andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(response.getHeaders("X-Content-Type-Options")).hasSize(1);
    }

    @Test
    void uploadForAnInformationRequestMustBeAnOpenRequestOfThisClaim() throws Exception {
        String claim = openClaim();
        action(OFFICER_MY1, claim, "assign", null).andExpect(status().isOk());
        String request = JsonPath.read(action(OFFICER_MY1, claim, "information-requests", "{\"question\":\"Photos?\"}")
                .andReturn().getResponse().getContentAsString(), "$.openInformationRequest.id");

        upload(CLAIMANT1, claim, file("damage.png", "image/png", PNG), request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.informationRequestId").value(request));
        mvc.perform(get("/api/claims/{c}/documents", claim).with(OFFICER_MY2))
                .andExpect(jsonPath("$[0].informationRequestId").value(request));
        // uploading doesn't move the claim
        mvc.perform(get("/api/claims/{c}", claim).with(CLAIMANT1)).andExpect(jsonPath("$.status").value("INFO_REQUESTED"));
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?::uuid").param(claim)
                .query(Integer.class).single()).isEqualTo(3); // submitted, assigned, info requested

        String otherClaim = openClaim();
        upload(CLAIMANT1, otherClaim, file("a.pdf", "application/pdf", PDF), request).andExpect(status().isBadRequest());

        action(CLAIMANT1, claim, "information-requests/" + request + "/response", "{\"responseText\":\"See photos\"}")
                .andExpect(status().isOk());
        upload(CLAIMANT1, claim, file("late.pdf", "application/pdf", PDF), request).andExpect(status().isConflict());
    }

    @Test
    void onlyThoseWhoCanSeeTheClaimGetItsDocuments() throws Exception {
        String claim = openClaim();
        String doc = uploadedPdf(claim);
        String list = "/api/claims/" + claim + "/documents";
        String download = list + "/" + doc + "/content";

        for (var outsider : new JwtRequestPostProcessor[] {CLAIMANT2, OFFICER_SG1}) {
            mvc.perform(get(list).with(outsider)).andExpect(status().isNotFound());
            mvc.perform(get(download).with(outsider)).andExpect(status().isNotFound());
        }
        upload(CLAIMANT2, claim, file("x.pdf", "application/pdf", PDF), null)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        // 404 comes before validation: an outsider learns nothing, even with a bad file
        upload(CLAIMANT2, claim, file("x.exe", "application/octet-stream", EXE), null).andExpect(status().isNotFound());
        upload(OFFICER_MY1, claim, file("x.pdf", "application/pdf", PDF), null).andExpect(status().isForbidden());

        for (var reader : new JwtRequestPostProcessor[] {MANAGER, OFFICER_MY2}) {
            mvc.perform(get(list).with(reader)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(doc));
            mvc.perform(get(download).with(reader)).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        }
        mvc.perform(get(list + "/" + UUID.randomUUID() + "/content").with(CLAIMANT1)).andExpect(status().isNotFound());
    }

    @Test
    void fileTypeIsCheckedByExtensionContentTypeAndFirstBytes() throws Exception {
        String claim = openClaim();
        upload(CLAIMANT1, claim, file("setup.pdf", "application/pdf", EXE), null)   // .exe renamed
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The file's content is not a valid PDF"));
        upload(CLAIMANT1, claim, file("image.pdf", "application/pdf", PNG), null).andExpect(status().isUnsupportedMediaType());
        upload(CLAIMANT1, claim, file("doc.pdf", "image/png", PDF), null).andExpect(status().isUnsupportedMediaType());
        upload(CLAIMANT1, claim, file("notes.txt", "text/plain", PDF), null).andExpect(status().isUnsupportedMediaType());
        upload(CLAIMANT1, claim, file("noextension", "application/pdf", PDF), null).andExpect(status().isUnsupportedMediaType());
        upload(CLAIMANT1, claim, file("empty.pdf", "application/pdf", new byte[0]), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is empty"));
        upload(CLAIMANT1, claim, file("ok.pdf", "application/pdf; charset=binary", PDF), null).andExpect(status().isCreated());
        assertThat(filesOf(claim)).hasSize(1); // rejected uploads left nothing on disk
    }

    @Test
    void closedClaimsAndTheTwentyFirstDocumentAreRefused() throws Exception {
        String settled = openClaim();
        action(OFFICER_MY1, settled, "assign", null).andExpect(status().isOk());
        action(OFFICER_MY1, settled, "assessment", "{\"reserveAmount\":100}").andExpect(status().isOk());
        action(OFFICER_MY1, settled, "settlement", "{\"settledAmount\":100}").andExpect(status().isOk());
        upload(CLAIMANT1, settled, file("a.pdf", "application/pdf", PDF), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Documents can't be added to a SETTLED claim"));

        String full = openClaim();
        for (int i = 1; i <= DocumentService.MAX_DOCUMENTS_PER_CLAIM; i++) {
            upload(CLAIMANT1, full, file("doc" + i + ".pdf", "application/pdf", PDF), null).andExpect(status().isCreated());
        }
        upload(CLAIMANT1, full, file("doc21.pdf", "application/pdf", PDF), null).andExpect(status().isConflict());
        assertThat(filesOf(full)).hasSize(DocumentService.MAX_DOCUMENTS_PER_CLAIM);
    }

    @Test
    void theOriginalFilenameNeverBecomesAPath() throws Exception {
        String claim = openClaim();
        String doc = JsonPath.read(upload(CLAIMANT1, claim, file("../../etc/passwd.pdf", "application/pdf", PDF), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("passwd.pdf"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String stored = jdbc.sql("SELECT stored_filename FROM claim_documents WHERE id = ?::uuid").param(doc)
                .query(String.class).single();
        assertThat(UUID.fromString(stored).toString()).isEqualTo(stored);
        assertThat(filesOf(claim)).containsExactly(storageRoot.resolve(claim).resolve(stored));
        mvc.perform(get("/api/claims/{c}/documents/{d}/content", claim, doc).with(CLAIMANT1))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("passwd.pdf")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("/"))));

        upload(CLAIMANT1, claim, file("C:\\Users\\me\\scan.pdf", "application/pdf", PDF), null)
                .andExpect(jsonPath("$.originalFilename").value("scan.pdf"));
        String longName = "x".repeat(300) + ".pdf";
        upload(CLAIMANT1, claim, file(longName, "application/pdf", PDF), null)
                .andExpect(jsonPath("$.originalFilename").value("x".repeat(251) + ".pdf"));
    }

    @Test
    void diskAndDatabaseStayConsistent() throws Exception {
        String claim = openClaim();
        // the row insert fails inside the database: the file just written must be removed again
        jdbc.sql("ALTER TABLE claim_documents ADD CONSTRAINT test_no_docs CHECK (size_bytes < 0) NOT VALID").update();
        try {
            upload(CLAIMANT1, claim, file("a.pdf", "application/pdf", PDF), null).andExpect(status().isInternalServerError());
        } finally {
            jdbc.sql("ALTER TABLE claim_documents DROP CONSTRAINT test_no_docs").update();
        }
        assertThat(filesOf(claim)).isEmpty();

        // a file deleted by hand: 410 ProblemDetail, not 500
        String doc = uploadedPdf(claim);
        for (Path f : filesOf(claim)) {
            Files.delete(f);
        }
        mvc.perform(get("/api/claims/{c}/documents/{d}/content", claim, doc).with(CLAIMANT1))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void moreThanTenMegabytesIs413OverRealHttp() throws Exception {
        String claim = openClaim();
        when(jwtDecoder.decode("claimant1")).thenReturn(Jwt.withTokenValue("claimant1").header("alg", "RS256")
                .subject("00000000-0000-0000-0002-000000000001").claim("role", "CLAIMANT")
                .claim("name", "Demo Claimant 1").claim("email", "claimant1@example.com")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build());
        byte[] big = Arrays.copyOf(PDF, 11 * 1024 * 1024);
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new ByteArrayResource(big) {
            @Override
            public String getFilename() {
                return "big.pdf";
            }
        });

        String[] result = RestClient.create().post().uri("http://localhost:" + port + "/api/claims/" + claim + "/documents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer claimant1")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(parts)
                .exchange((req, res) -> new String[] {String.valueOf(res.getStatusCode().value()),
                        String.valueOf(res.getHeaders().getContentType()), new String(res.getBody().readAllBytes())});

        assertThat(result[0]).isEqualTo("413");
        assertThat(result[1]).startsWith("application/problem+json");
        assertThat(filesOf(claim)).isEmpty();
    }

    // ---- helpers ----

    private String openClaim() throws Exception {
        String body = mvc.perform(post("/api/claims").with(CLAIMANT1).contentType(MediaType.APPLICATION_JSON).content("""
                        {"policyId":"%s","incidentDate":"%s","incidentLocation":"KL","description":"Dent",
                         "claimedAmount":500}""".formatted(MY_MOTOR, TODAY.minusDays(1))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String uploadedPdf(String claim) throws Exception {
        return JsonPath.read(upload(CLAIMANT1, claim, file("report.pdf", "application/pdf", PDF), null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions upload(JwtRequestPostProcessor user, String claim, MockMultipartFile file, String requestId)
            throws Exception {
        var request = multipart("/api/claims/{c}/documents", claim).file(file).with(user);
        if (requestId != null) {
            request.param("informationRequestId", requestId);
        }
        return mvc.perform(request);
    }

    private ResultActions action(JwtRequestPostProcessor user, String claim, String path, String json) throws Exception {
        return mvc.perform(post("/api/claims/" + claim + "/" + path).with(user)
                .contentType(MediaType.APPLICATION_JSON).content(json == null ? "" : json));
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    private List<Path> filesOf(String claim) throws Exception {
        Path dir = storageRoot.resolve(claim);
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var files = Files.list(dir)) {
            return files.toList();
        }
    }
}
