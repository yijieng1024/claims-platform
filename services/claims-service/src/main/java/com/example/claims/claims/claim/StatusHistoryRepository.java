package com.example.claims.claims.claim;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {

    List<StatusHistory> findByClaimIdOrderByChangedAtAsc(UUID claimId);
}
