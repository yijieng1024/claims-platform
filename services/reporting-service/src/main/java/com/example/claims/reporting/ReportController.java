package com.example.claims.reporting;

import io.swagger.v3.oas.annotations.Operation;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboards: plain SQL over {@code claim_summary}, no stored counters (design 6.3). "Open" = not
 * SETTLED and not REJECTED. Officers always get their own market; managers all, or one via {@code market}.
 * {@code asOf} (Q21) is the newest event time ({@code occurredAt}) reflected in the rows the report covers,
 * i.e. its market scope, closed claims included since closing one changes the report (null if none).
 * <p>
 * Workload and performance list only officers who appear in {@code claim_summary}: reporting learns
 * about officers from events, so one who never picked up a claim isn't listed.
 */
@RestController
@RequestMapping("/api/reports")
class ReportController {

    private static final String OPEN = "status NOT IN ('SETTLED', 'REJECTED')";
    private static final String IN_MARKET = "(CAST(:market AS varchar) IS NULL OR market = :market)";

    private final JdbcClient jdbc;

    ReportController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record StatusCount(String status, long count) {
    }

    record MarketCount(String market, long count) {
    }

    record Outstanding(OffsetDateTime asOf, List<StatusCount> byStatus, List<MarketCount> byMarket) {
    }

    @Operation(summary = "Open claim counts by status and by market (officer: own market)")
    @GetMapping("/outstanding")
    Outstanding outstanding(@AuthenticationPrincipal Jwt jwt) {
        String market = marketFor(jwt, null);
        return new Outstanding(asOf(market),
                jdbc.sql("SELECT status, count(*) AS count FROM claim_summary WHERE " + OPEN + " AND " + IN_MARKET
                        + " GROUP BY status ORDER BY status").param("market", market).query(StatusCount.class).list(),
                jdbc.sql("SELECT market, count(*) AS count FROM claim_summary WHERE " + OPEN + " AND " + IN_MARKET
                        + " GROUP BY market ORDER BY market").param("market", market).query(MarketCount.class).list());
    }

    /**
     * Q2/Q3: per currency, never converted; claimed amounts are kept apart from reserved exposure. Q24:
     * always all six currencies, in market order (MY, SG, TH, ID, PH, VN), with zeros when nothing is open.
     */
    record CurrencyExposure(String currency, BigDecimal reservedExposure, long reservedCount, long unreservedCount,
            BigDecimal unreservedClaimedAmount) {
    }

    record Exposure(OffsetDateTime asOf, List<CurrencyExposure> currencies) {
    }

    @Operation(summary = "Manager: per currency, reserves of open claims, and open claims without a reserve")
    @GetMapping("/exposure")
    Exposure exposure() {
        return new Exposure(asOf(null), jdbc.sql("""
                        SELECT cur.currency,
                               coalesce(sum(s.reserve_amount), 0)                                                 AS reserved_exposure,
                               count(s.reserve_amount)                                                            AS reserved_count,
                               count(s.claim_id) FILTER (WHERE s.reserve_amount IS NULL)                          AS unreserved_count,
                               coalesce(sum(s.claimed_amount) FILTER (WHERE s.reserve_amount IS NULL), 0)         AS unreserved_claimed_amount
                        FROM (VALUES (1, 'MYR'), (2, 'SGD'), (3, 'THB'), (4, 'IDR'), (5, 'PHP'), (6, 'VND')) AS cur (ord, currency)
                        LEFT JOIN claim_summary s ON s.currency = cur.currency AND s.%s
                        GROUP BY cur.ord, cur.currency ORDER BY cur.ord""".formatted(OPEN))
                .query(CurrencyExposure.class).list());
    }

    record OfficerWorkload(UUID officerId, String officerName, String market, long openClaims, long underReview,
            long infoRequested, long assessed) {
    }

    record Workload(OffsetDateTime asOf, List<OfficerWorkload> officers) {
    }

    @Operation(summary = "Open claims per officer, by status (officer: own market; manager: all or ?market=)")
    @GetMapping("/workload")
    Workload workload(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String market) {
        String m = marketFor(jwt, market);
        return new Workload(asOf(m), jdbc.sql("""
                        SELECT assigned_officer_id AS officer_id, max(assigned_officer_name) AS officer_name, market,
                               count(*) FILTER (WHERE %s)                        AS open_claims,
                               count(*) FILTER (WHERE status = 'UNDER_REVIEW')   AS under_review,
                               count(*) FILTER (WHERE status = 'INFO_REQUESTED') AS info_requested,
                               count(*) FILTER (WHERE status = 'ASSESSED')       AS assessed
                        FROM claim_summary
                        WHERE assigned_officer_id IS NOT NULL AND %s
                        GROUP BY assigned_officer_id, market ORDER BY market, officer_name""".formatted(OPEN, IN_MARKET))
                .param("market", m).query(OfficerWorkload.class).list());
    }

    record Amount(String currency, BigDecimal amount) {
    }

    /** Q20: average days from pick-up to close, i.e. the officer's handling time. */
    record OfficerPerformance(UUID officerId, String officerName, String market, long closed, long settled,
            long rejected, BigDecimal averageDaysToClose, List<Amount> settledAmounts) {
    }

    record Performance(OffsetDateTime asOf, List<OfficerPerformance> officers) {
    }

    @Operation(summary = "Closed claims per officer and average days assigned to closed (officer: own market; manager: all or ?market=)")
    @GetMapping("/performance")
    Performance performance(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String market) {
        String m = marketFor(jwt, market);
        Map<UUID, List<Amount>> settledByOfficer = jdbc.sql("""
                        SELECT assigned_officer_id, currency, sum(settled_amount) AS amount FROM claim_summary
                        WHERE status = 'SETTLED' AND %s
                        GROUP BY assigned_officer_id, currency ORDER BY currency""".formatted(IN_MARKET))
                .param("market", m)
                .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), new Amount(rs.getString(2), rs.getBigDecimal(3))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return new Performance(asOf(m), jdbc.sql("""
                        SELECT assigned_officer_id, max(assigned_officer_name), market,
                               count(*) FILTER (WHERE closed_at IS NOT NULL),
                               count(*) FILTER (WHERE status = 'SETTLED'),
                               count(*) FILTER (WHERE status = 'REJECTED'),
                               round(avg(extract(epoch FROM closed_at - assigned_at)) / 86400, 2)
                        FROM claim_summary
                        WHERE assigned_officer_id IS NOT NULL AND %s
                        GROUP BY assigned_officer_id, market ORDER BY market, 2""".formatted(IN_MARKET))
                .param("market", m)
                .query((rs, n) -> {
                    UUID officer = rs.getObject(1, UUID.class);
                    return new OfficerPerformance(officer, rs.getString(2), rs.getString(3), rs.getLong(4),
                            rs.getLong(5), rs.getLong(6), rs.getBigDecimal(7), settledByOfficer.getOrDefault(officer, List.of()));
                }).list());
    }

    private OffsetDateTime asOf(String market) {
        return jdbc.sql("SELECT max(last_event_at) FROM claim_summary WHERE " + IN_MARKET).param("market", market)
                .query(OffsetDateTime.class).list().getFirst();
    }

    /** Officers are always limited to their own market (Q15); managers get what they asked for (null = all). */
    private static String marketFor(Jwt jwt, String requested) {
        return "OFFICER".equals(jwt.getClaimAsString("role")) ? jwt.getClaimAsString("market") : requested;
    }
}
