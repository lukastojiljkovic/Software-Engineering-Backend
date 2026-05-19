package rs.raf.trading.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioSummaryDto {
    private BigDecimal totalValue;
    private BigDecimal totalProfit;
    private BigDecimal paidTaxThisYear;
    private BigDecimal unpaidTaxThisMonth;
}
