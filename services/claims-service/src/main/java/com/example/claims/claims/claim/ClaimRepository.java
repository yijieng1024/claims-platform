package com.example.claims.claims.claim;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
