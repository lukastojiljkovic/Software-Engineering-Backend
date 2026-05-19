package rs.raf.trading.dividend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendPayoutDto {

    private Long id;
    private Long ownerId;
    private String ownerType;
    private Long stockListingId;
    private String stockTicker;
    private Integer quantity;
    private BigDecimal priceOnDate;
    private BigDecimal dividendYieldRate;
    private BigDecimal grossAmount;
    private BigDecimal tax;
    private BigDecimal netAmount;
    private Long creditedAccountId;
    private String currencyCode;
    private LocalDate paymentDate;
    private Boolean taxExempt;
    private LocalDateTime createdAt;
}
