package rs.raf.trading.audit.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * B7 — Audit log DTO u trading-service domenu
 * (port iz main PR #86; resolve actorName ide preko {@code BankaCoreClient.getUserById}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private Long id;
    private Long actorId;
    private String actorType;
    private String actorName;
    private String actionType;
    private String description;
    private String targetType;
    private Long targetId;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;
}
