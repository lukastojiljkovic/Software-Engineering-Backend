package rs.raf.banka2_bek.otc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtcOfferDto {
    private Long id;
    private Long listingId;
    private String listingTicker;
    private String listingName;
    private String listingCurrency;

    private Long buyerId;
    private String buyerName;
    private Long sellerId;
    private String sellerName;

    private Integer quantity;
    private BigDecimal pricePerStock;
    private BigDecimal premium;
    /** Trenutna trzisna cena hartije — koristi je FE za bojenje odstupanja (±5 / ±20%). */
    private BigDecimal currentPrice;
    private LocalDate settlementDate;

    private Long lastModifiedById;
    private String lastModifiedByName;
    private Long waitingOnUserId;
    private boolean myTurn;

    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
}
