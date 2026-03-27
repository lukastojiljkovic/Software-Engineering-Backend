package rs.raf.banka2_bek.account.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountRequestDto {
    @NotBlank(message = "Tip racuna je obavezan")
    private String accountType;
    private String accountSubtype;
    private String currency;
    private BigDecimal initialDeposit;
    private Boolean createCard;
}
