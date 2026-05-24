package rs.raf.trading.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.trading.audit.model.AuditActionType;
import rs.raf.trading.audit.model.AuditLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * B7 — Audit log repozitorijum u trading-service domenu (port iz main PR #86).
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorId(Long actorId, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:actionType IS NULL OR a.actionType = :actionType)
          AND (:actorId IS NULL OR a.actorId = :actorId)
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt <= :to)
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findFiltered(
            @Param("actionType") AuditActionType actionType,
            @Param("actorId") Long actorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    List<AuditLog> findByTargetTypeAndTargetId(String targetType, Long targetId);
}
