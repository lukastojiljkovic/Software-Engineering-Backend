package rs.raf.trading.otc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Javno dostupna akcija iz tudjeg portfolija — prikaz u Portal: OTC Trgovina.
 * Prikazuje koliko komada prodavac javno nudi i trenutnu cenu iz listinga.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtcListingDto {
    private Long portfolioId;
    private Long listingId;
    private String listingTicker;
    private String listingName;
    private String exchangeAcronym;
    private String listingCurrency;
    private BigDecimal currentPrice;
    private Integer publicQuantity;
    private Integer availablePublicQuantity;

    private Long sellerId;
    private String sellerRole;
    private String sellerName;
}
