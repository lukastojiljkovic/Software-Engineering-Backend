package rs.raf.trading.margin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO za prikaz transakcije na margin racunu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarginTransactionDto {

    private Long id;
    private Long marginAccountId;
    private String type;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
}
