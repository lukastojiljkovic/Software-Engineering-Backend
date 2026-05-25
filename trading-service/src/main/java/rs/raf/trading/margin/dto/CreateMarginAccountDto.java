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
 * <p>BE-STK-07 (25.05.2026): polja {@code initialMargin}, {@code maintenanceMargin}
 * i {@code bankParticipation} se sad zadaju eksplicitno od strane zaposlenog
 * (umesto hardcoded MM=IM×0.5, BP=0.5 formule iz ranije verzije). Marzni_Racuni.txt
 * §29-55 eksplicitno trazi da DTO sadrzi sve tri vrednosti.
 *
 * <p>Backwards-compat: ako su {@code initialMargin/maintenanceMargin/bankParticipation}
 * svi null, koristi se legacy formula (MM=IM*0.5, BP=0.5) sa {@code initialDeposit}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMarginAccountDto {

    /**
     * Legacy 2-arg konstruktor — backward-compat za teste koji koriste
     * stari format {@code new CreateMarginAccountDto(accountId, initialDeposit)}.
     */
    public CreateMarginAccountDto(Long accountId, BigDecimal initialDeposit) {
        this.accountId = accountId;
        this.initialDeposit = initialDeposit;
    }

    /** ID obicnog racuna na koji se vezuje margin racun (Marzni_Racuni.txt §17 — Currency uvek RSD). */
    @NotNull(message = "ID racuna je obavezan")
    private Long accountId;

    /**
     * Pocetni depozit korisnika (legacy putanja kad nisu zadati IM/MM/BP eksplicitno).
     * Mora biti > 0 ako se koristi.
     */
    @DecimalMin(value = "0.00", message = "Pocetni depozit ne sme biti negativan")
    private BigDecimal initialDeposit;

    /**
     * BE-STK-07: Pocetna margina (stanje na racunu) — zadaje zaposleni.
     * Po Marzni_Racuni.txt §37: "Double InitialMargin". Mora biti > 0 ako je zadat.
     */
    @DecimalMin(value = "0.00", message = "InitialMargin ne sme biti negativan")
    private BigDecimal initialMargin;

    /**
     * BE-STK-07: Maintenance margina — zadaje zaposleni.
     * Po Marzni_Racuni.txt §39: "Double MaitenanceMargin". Validacija: MM <= IM
     * (sprovedi se u servisu, ne u DTO da se izbegne SuperRefine slozenost).
     */
    @DecimalMin(value = "0.00", message = "MaintenanceMargin ne sme biti negativan")
    private BigDecimal maintenanceMargin;

    /**
     * BE-STK-07: BankParticipation — zadaje zaposleni.
     * Po Marzni_Racuni.txt §41: "Double BankParticipation".
     * Validni opseg: 0 < BP < 1 (provera u servisu).
     */
    @DecimalMin(value = "0.0000", message = "BankParticipation ne sme biti negativan")
    private BigDecimal bankParticipation;
}
