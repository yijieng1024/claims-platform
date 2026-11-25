package com.example.claims.claims.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Policies are read-only seeded reference data, so plain SQL is enough (no JPA entity). */
@Repository
public class PolicyRepository {

    public record Policy(UUID id, String policyNumber, String holderName, String productType, String market,
            String currency, String insuredItem, BigDecimal sumInsured, LocalDate startDate, LocalDate endDate,
            String status, boolean inForceToday) {
    }

    // "today" is the database's current_date (UTC in the compose setup)
    private static final String SELECT = """
            SELECT id, policy_number, holder_name, product_type, market, currency, insured_item, sum_insured,
                   start_date, end_date, status,
                   (status = 'ACTIVE' AND current_date BETWEEN start_date AND end_date) AS in_force_today
            FROM policies
            """;

    private final JdbcClient jdbc;

    PolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Holder emails are stored lower-case; the lookup is case-insensitive. */
    public List<Policy> findByHolderEmail(String email) {
        return jdbc.sql(SELECT + "WHERE holder_email = lower(trim(:email)) ORDER BY start_date DESC, policy_number")
                .param("email", email)
                .query(Policy.class)
                .list();
    }

    public Optional<Policy> findById(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = :id").param("id", id).query(Policy.class).optional();
    }

    /** SELECT ... FOR UPDATE: assess and settle on one policy check its remaining cover one at a time (Q31). */
    public Optional<Policy> findByIdForUpdate(UUID id) {
        return jdbc.sql(SELECT + "WHERE id = :id FOR UPDATE").param("id", id).query(Policy.class).optional();
    }

    /** The policy only if its holder email is {@code email}: a claimant may only claim on their own policies. */
    public Optional<Policy> findByIdAndHolderEmail(UUID id, String email) {
        return jdbc.sql(SELECT + "WHERE id = :id AND holder_email = lower(trim(:email))")
                .param("id", id)
                .param("email", email)
                .query(Policy.class)
                .optional();
    }

    /** Registration check (design section 4): exists, not cancelled, holder email matches. Expired still counts. */
    public Optional<String> findHolderNameForRegistration(String policyNumber, String email) {
        return jdbc.sql("""
                        SELECT holder_name FROM policies
                        WHERE policy_number = upper(trim(:number))
                          AND holder_email = lower(trim(:email))
                          AND status <> 'CANCELLED'
                        """)
                .param("number", policyNumber)
                .param("email", email)
                .query(String.class)
                .optional();
    }
}
