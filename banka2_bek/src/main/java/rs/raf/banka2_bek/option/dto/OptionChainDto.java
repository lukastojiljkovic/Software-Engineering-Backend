package rs.raf.banka2_bek.option.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO koji grupisje opcije po settlement datumu u "option chain" format.
 *
 * Option chain je standardni prikaz opcija na berzama gde se za svaki settlement datum
 * prikazuju CALL opcije levo i PUT opcije desno, grupisane po strike ceni.
 *
 * Frontend koristi ovaj DTO za prikaz tabele sa tabovima po datumima.
 */
@Data
public class OptionChainDto {

    @Schema(description = "Datum isteka za ovu grupu opcija")
    private LocalDate settlementDate;

    @Schema(description = "Lista CALL opcija za ovaj datum, sortirane po strike ceni")
    private List<OptionDto> calls;

    @Schema(description = "Lista PUT opcija za ovaj datum, sortirane po strike ceni")
    private List<OptionDto> puts;

    @Schema(description = "Trenutna cena osnovne akcije")
    private BigDecimal currentStockPrice;
}
