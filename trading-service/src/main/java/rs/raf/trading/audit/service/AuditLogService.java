package rs.raf.trading.audit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.audit.dto.AuditLogDto;
import rs.raf.trading.audit.model.AuditActionType;
import rs.raf.trading.audit.model.AuditLog;
import rs.raf.trading.audit.repository.AuditLogRepository;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.common.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * B7 — Audit log servis u trading-service domenu
 * (port iz main PR #86; identitet aktera (ime/prezime) resolve-uje preko
 * {@link BankaCoreClient#getUserById} jer Employee/Client tabele zive u
 * banka-core domenu — trading-service ih nema lokalno).
 *
 * {@code record()} koristi {@link Propagation#REQUIRES_NEW} da audit upis
 * ostane cak i ako pozivajuca transakcija bude rollback-ovana.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final BankaCoreClient bankaCoreClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String actorType, AuditActionType action,
                       String description, String targetType, Long targetId,
                       String oldValue, String newValue) {
        AuditLog log = AuditLog.builder()
                .actorId(actorId)
                .actorType(actorType)
                .actionType(action)
                .description(description)
                .targetType(targetType)
                .targetId(targetId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String actorType, AuditActionType action,
                       String description, String targetType, Long targetId) {
        record(actorId, actorType, action, description, targetType, targetId, null, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> query(AuditActionType actionType, Long actorId,
                                   LocalDateTime from, LocalDateTime to,
                                   Pageable pageable) {
        return auditLogRepository
                .findFiltered(actionType, actorId, from, to, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<AuditLogDto> findById(Long id) {
        return auditLogRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> findByResource(String targetType, Long targetId) {
        return auditLogRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AuditLogDto toDto(AuditLog log) {
        String actorName = resolveActorName(log.getActorId(), log.getActorType());
        return AuditLogDto.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .actorType(log.getActorType())
                .actorName(actorName)
                .actionType(log.getActionType().name())
                .description(log.getDescription())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String resolveActorName(Long actorId, String actorType) {
        if (actorId == null || actorId == 0L) {
            return "ID:" + actorId;
        }
        try {
            String role = "EMPLOYEE".equals(actorType) ? UserRole.EMPLOYEE : UserRole.CLIENT;
            InternalUserDto user = bankaCoreClient.getUserById(role, actorId);
            if (user != null && user.firstName() != null && user.lastName() != null) {
                return user.firstName() + " " + user.lastName();
            }
        } catch (RuntimeException ignored) {
            // Banka-core lookup failed — fallback below.
        }
        return "ID:" + actorId;
    }
}
