package com.example.claims.claims.claim;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ClaimRepository extends JpaRepository<Claim, UUID> {

    List<Claim> findByClaimantIdOrderByCreatedAtDesc(UUID claimantId);

    List<Claim> findByMarketAndStatusAndAssignedOfficerIdIsNullOrderByCreatedAtAsc(String market, ClaimStatus status);

    List<Claim> findByAssignedOfficerIdAndStatusNotInOrderByCreatedAtAsc(UUID officerId, Collection<ClaimStatus> closed);

    /** A null filter means "any". */
    @Query("""
            select c from Claim c
            where (:status is null or c.status = :status) and (:market is null or c.market = :market)
            """)
    Page<Claim> search(ClaimStatus status, String market, Pageable pageable);

    /** Q30: the claim that blocks another one for the same incident (a REJECTED one doesn't). */
    Optional<Claim> findFirstByPolicyIdAndIncidentDateAndStatusNot(UUID policyId, LocalDate incidentDate,
            ClaimStatus status);

    /** Q31: what the policy's other SETTLED claims have paid out. */
    @Query(value = """
            SELECT coalesce(sum(settled_amount), 0) FROM claims
            WHERE policy_id = :policyId AND status = 'SETTLED' AND id <> :claimId""", nativeQuery = true)
    BigDecimal settledOnPolicyExcept(UUID policyId, UUID claimId);

    /** SELECT ... FOR UPDATE: serialises document uploads per claim (the 20-document limit). Doesn't bump version. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> findByIdForUpdate(UUID id);
}
