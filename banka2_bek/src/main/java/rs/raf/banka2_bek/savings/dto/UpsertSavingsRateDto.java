package rs.raf.banka2_bek.savings.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.Set;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpsertSavingsRateDto {

    /**
     * BE-PAY-05: skup dozvoljenih trajanja po spec-u Celina 2 (Stedna knjizica).
     * Termini van ovog skupa nikad nece match-ovati ni jedan klijentski deposit
     * pa ih ne dozvoljavamo na DTO sloju (ne samo u service-u kako je bilo).
     */
    public static final Set<Integer> VALID_TERMS = Set.of(3, 6, 12, 24, 36);

    @NotBlank
    private String currencyCode;

    @NotNull @Min(3) @Max(36)
    private Integer termMonths;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "50.0", message = "Maksimalna stopa je 50% p.a.")
    private BigDecimal annualRate;

    /**
     * BE-PAY-05: enforce-uje da termMonths bude u {@link #VALID_TERMS} setu
     * (3, 6, 12, 24, 36). Bez ovoga admin moze persist-ovati termMonths=7 koji
     * nikad ne match-uje deposit lookup.
     */
    @AssertTrue(message = "termMonths mora biti jedna od: 3, 6, 12, 24, 36")
    public boolean isValidTermMonths() {
        return termMonths != null && VALID_TERMS.contains(termMonths);
    }
}
