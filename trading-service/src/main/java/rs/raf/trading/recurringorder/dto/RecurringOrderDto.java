package rs.raf.trading.recurringorder.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// DTO koji se vraca klijentu pri GET operacijama trajnog naloga.
//
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringOrderDto {

    private Long id;
    private Long ownerId;
    private String ownerType;
    private Long listingId;
    private String listingTicker;
    private String direction;
    private String mode;
    private BigDecimal value;
    private Long accountId;
    private String cadence;
    private LocalDateTime nextRun;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
