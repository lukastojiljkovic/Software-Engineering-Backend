package rs.raf.trading.margin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO za kreiranje margin racuna.
 *
 * Klijent salje accountId (povezani obicni racun) i initialDeposit (pocetni depozit).
 * Sistem izracunava initialMargin, loanValue i maintenanceMargin na osnovu bankParticipation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMarginAccountDto {

    /** ID obicnog racuna na koji se vezuje margin racun */
    @NotNull(message = "ID racuna je obavezan")
    private Long accountId;

    /** Pocetni depozit korisnika */
    @NotNull(message = "Pocetni depozit je obavezan")
    @DecimalMin(value = "0.01", message = "Pocetni depozit mora biti veci od 0")
    private BigDecimal initialDeposit;
}
