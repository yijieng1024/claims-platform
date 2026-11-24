package com.example.claims.reporting;

import com.example.claims.events.ClaimEvent;
import com.example.claims.events.ClaimEventType;
import com.example.claims.events.ClaimSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** A claim moving through its lifecycle; each step bumps the version and returns the event claims-service would send. */
public final class TestClaim {

    public record User(UUID id, String name, String role, String market) {
    }

    public static final User CLAIMANT1 = new User(UUID.fromString("00000000-0000-0000-0002-000000000001"), "Demo Claimant 1", "CLAIMANT", null);
    public static final User CLAIMANT2 = new User(UUID.fromString("00000000-0000-0000-0002-000000000002"), "Demo Claimant 2", "CLAIMANT", null);
    public static final User OFFICER_MY1 = new User(UUID.fromString("00000000-0000-0000-0001-000000000011"), "Demo Officer MY-1", "OFFICER", "MY");
    public static final User OFFICER_MY2 = new User(UUID.fromString("00000000-0000-0000-0001-000000000012"), "Demo Officer MY-2", "OFFICER", "MY");
    public static final User OFFICER_SG1 = new User(UUID.fromString("00000000-0000-0000-0001-000000000021"), "Demo Officer SG-1", "OFFICER", "SG");
    public static final User MANAGER = new User(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Demo Manager", "MANAGER", null);

    private static final AtomicInteger NUMBERS = new AtomicInteger();

    /** A token like auth-service issues: market only for officers. */
    public static JwtRequestPostProcessor token(User u) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> {
            j.subject(u.id().toString()).claim("role", u.role()).claim("name", u.name());
            if (u.market() != null) {
                j.claim("market", u.market());
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + u.role()));
    }

    public final UUID id = UUID.randomUUID();
    public final String number = "CLM-2026-%06d".formatted(900_000 + NUMBERS.incrementAndGet());
    private final String market;
    private final String currency;
    private final User claimant;
    private final BigDecimal claimed;
    private final Instant createdAt = Instant.parse("2026-11-01T09:00:00Z");
    private long version = -1;
    private String status;
    private User officer;
    private BigDecimal reserve;
    private BigDecimal settled;
    private Instant assignedAt;
    private Instant closedAt;

    public TestClaim(String market, String currency, User claimant, String claimed) {
        this.market = market;
        this.currency = currency;
        this.claimant = claimant;
        this.claimed = new BigDecimal(claimed);
    }

    public ClaimEvent submitted() {
        return step(ClaimEventType.ClaimSubmitted, "SUBMITTED", claimant);
    }

    public ClaimEvent assigned(User officer, Instant at) {
        this.officer = officer;
        this.assignedAt = at;
        return step(ClaimEventType.ClaimAssigned, "UNDER_REVIEW", officer);
    }

    public ClaimEvent infoRequested() {
        return step(ClaimEventType.InformationRequested, "INFO_REQUESTED", officer);
    }

    public ClaimEvent infoProvided() {
        return step(ClaimEventType.InformationProvided, "UNDER_REVIEW", claimant);
    }

    public ClaimEvent assessed(String reserve) {
        this.reserve = new BigDecimal(reserve);
        return step(ClaimEventType.ClaimAssessed, "ASSESSED", officer);
    }

    public ClaimEvent settled(String amount, Instant at) {
        this.settled = new BigDecimal(amount);
        this.closedAt = at;
        return step(ClaimEventType.ClaimSettled, "SETTLED", officer);
    }

    public ClaimEvent rejected(Instant at) {
        this.closedAt = at;
        return step(ClaimEventType.ClaimRejected, "REJECTED", officer);
    }

    private ClaimEvent step(ClaimEventType type, String newStatus, User actor) {
        version++;
        status = newStatus;
        var snapshot = new ClaimSnapshot(id, number, version, "POL-" + market + "-MTR-0001", "MOTOR", market, currency,
                status, claimant.id(), officer == null ? null : officer.id(), officer == null ? null : officer.name(),
                claimed, reserve, settled, createdAt, assignedAt, closedAt);
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now().truncatedTo(ChronoUnit.MICROS), // DB precision
                new ClaimEvent.Actor(actor.id(), actor.name(), actor.role()), snapshot);
    }
}
