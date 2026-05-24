package rs.raf.trading.otc.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.trading.otc.model.OtcNegotiationHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * B10 — JPA repozitorijum za istoriju OTC pregovora (port iz main PR #89).
 */
public interface OtcNegotiationHistoryRepository
        extends JpaRepository<OtcNegotiationHistory, Long> {

    List<OtcNegotiationHistory> findByNegotiationIdOrderByCreatedAtAsc(Long negotiationId);

    List<OtcNegotiationHistory> findByModifiedByIdOrderByCreatedAtDesc(Long modifiedById);

    List<OtcNegotiationHistory> findByStatusOrderByCreatedAtDesc(String status);

    List<OtcNegotiationHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to);

    @Query("SELECT h FROM OtcNegotiationHistory h " +
            "WHERE (:status IS NULL OR h.status = :status) " +
            "  AND (:modifiedById IS NULL OR h.modifiedById = :modifiedById) " +
            "  AND (:from IS NULL OR h.createdAt >= :from) " +
            "  AND (:to IS NULL OR h.createdAt <= :to) " +
            "ORDER BY h.createdAt DESC")
    Page<OtcNegotiationHistory> findWithFilters(
            @Param("status") String status,
            @Param("modifiedById") Long modifiedById,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
