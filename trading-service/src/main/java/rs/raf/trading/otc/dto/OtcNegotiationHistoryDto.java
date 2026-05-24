package rs.raf.trading.otc.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * B10 — Istorija OTC pregovora DTO (port iz main PR #89, Aja Timotic).
 *
 * Vraca se klijentu pri pregledu historije jednog pregovora ili
 * paginiranom prikazu istorijskih zapisa.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtcNegotiationHistoryDto {
    private Long id;
    private Long negotiationId;
    private Integer quantity;
    private BigDecimal pricePerShare;
    private BigDecimal premium;
    private LocalDate settlementDate;
    private String status;
    private Long modifiedById;
    private String modifiedByName;
    private LocalDateTime createdAt;
}
