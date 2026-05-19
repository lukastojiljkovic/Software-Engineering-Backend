package rs.raf.trading.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OrderDto {
    private Long id;
    private Long listingId;
    private String userName;
    private String userRole;
    private String listingTicker;
    private String listingName;
    private String listingType;
    private String orderType;
    private Integer quantity;
    private Integer contractSize;
    private BigDecimal pricePerUnit;
    private BigDecimal limitValue;
    private BigDecimal stopValue;
    private String direction;
    private String status;
    private String approvedBy;
    private boolean isDone;
    private Integer remainingPortions;
    private boolean afterHours;
    private boolean allOrNone;
    private boolean margin;
    private BigDecimal approximatePrice;
    private Long accountId;
    private LocalDateTime createdAt;
    private LocalDateTime lastModification;
    private LocalDate listingSettlementDate;
    /** FX (menjacnica) komisija u valuti racuna; null ili 0 ako konverzija nije bila potrebna. */
    private BigDecimal fxCommission;
    /** Kurs listing -> account currency (mid) u trenutku rezervacije/odobravanja. */
    private BigDecimal exchangeRate;
    /**
     * P3 — Spec Celina 4 (Nova) §3883-3964: ako je nalog kreiran u ime
     * investicionog fonda (supervizor flow), ovde stoji fund.id; FE prikazuje
     * "Kupljeno za fond X" badge u OrdersListPage.
     */
    private Long fundId;
}
