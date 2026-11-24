package com.example.claims.claims.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

    long countByClaimId(UUID claimId);

    List<ClaimDocument> findByClaimIdOrderByUploadedAtDesc(UUID claimId);

    Optional<ClaimDocument> findByIdAndClaimId(UUID id, UUID claimId);
}
