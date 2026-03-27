package rs.raf.banka2_bek.account.dto;


import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class AccountLimitsUpdateDto {

    @DecimalMin(value = "0.0", message = "Daily limit must be zero or positive.")
    private BigDecimal dailyLimit;

    @DecimalMin(value = "0.0", message = "Monthly limit must be zero or positive.")
    private BigDecimal monthlyLimit;
}
