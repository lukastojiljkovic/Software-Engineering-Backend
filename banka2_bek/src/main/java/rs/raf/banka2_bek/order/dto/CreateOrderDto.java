package rs.raf.banka2_bek.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderDto {

    @NotNull(message = "Listing ID je obavezan")
    private Long listingId;

    @NotNull(message = "Tip ordera je obavezan (MARKET, LIMIT, STOP, STOP_LIMIT)")
    private String orderType;

    @NotNull(message = "Kolicina je obavezna")
    @Min(value = 1, message = "Quantity and contractSize must be > 0")
    private Integer quantity;

    @Min(value = 1, message = "Contract size must be > 0")
    private Integer contractSize = 1;

    @NotNull(message = "Smer je obavezan (BUY, SELL)")
    private String direction;

    private BigDecimal limitValue;  // za LIMIT i STOP_LIMIT
    private BigDecimal stopValue;   // za STOP i STOP_LIMIT
    private boolean allOrNone;      // AON flag
    private boolean margin;         // Margin flag

    @NotNull(message = "ID racuna je obavezan")
    private Long accountId;
}
