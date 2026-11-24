package com.example.claims.claims.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest PENDING rows, locked until the transaction ends. SKIP LOCKED: a second publisher instance
     * takes other rows instead of waiting (design section 7).
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM outbox_events WHERE status = 'PENDING'
            ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED""")
    List<OutboxEvent> lockOldestPending(int limit);

    long countByStatus(String status);
}
