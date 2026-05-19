package rs.raf.trading.otc.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CounterOtcOfferDto {
    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    @Positive
    private BigDecimal pricePerStock;

    @NotNull
    @Positive
    private BigDecimal premium;

    @NotNull
    @Future
    private LocalDate settlementDate;
}
