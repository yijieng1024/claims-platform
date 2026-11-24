package com.example.claims.claims.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InformationRequestRepository extends JpaRepository<InformationRequest, UUID> {

    List<InformationRequest> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    Optional<InformationRequest> findByClaimIdAndStatus(UUID claimId, InformationRequest.Status status);

    Optional<InformationRequest> findByIdAndClaimId(UUID id, UUID claimId);
}
