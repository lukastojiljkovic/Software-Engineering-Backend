package rs.raf.banka2_bek.option.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO za pojedinacnu opciju.
 *
 * Koristi se kao odgovor na GET /options/{id} i kao element unutar OptionChainDto lista.
 * Mapira se iz Option entiteta putem OptionMapper-a.
 *
 * Mapira se iz Option entiteta putem OptionMapper#toDto(Option).
 */
@Data
public class OptionDto {

    @Schema(description = "Interni ID opcije")
    private Long id;

    @Schema(description = "Ticker opcije", example = "AAPL260402C00185000")
    private String ticker;

    @Schema(description = "Tip opcije: CALL ili PUT", example = "CALL")
    private String optionType;

    @Schema(description = "Ticker osnovne akcije", example = "AAPL")
    private String stockTicker;

    @Schema(description = "Naziv osnovne akcije", example = "Apple Inc.")
    private String stockName;

    @Schema(description = "ID osnovne akcije (Listing ID)")
    private Long stockListingId;

    @Schema(description = "Strike cena opcije")
    private BigDecimal strikePrice;

    @Schema(description = "Premija opcije izracunata Black-Scholes modelom")
    private BigDecimal price;

    @Schema(description = "Ask cena opcije")
    private BigDecimal ask;

    @Schema(description = "Bid cena opcije")
    private BigDecimal bid;

    @Schema(description = "Implied volatility (sigma parametar za Black-Scholes)", example = "0.35")
    private double impliedVolatility;

    @Schema(description = "Broj otvorenih ugovora")
    private int openInterest;

    @Schema(description = "Obim trgovanja (broj ugovora)")
    private long volume;

    @Schema(description = "Datum isteka opcije")
    private LocalDate settlementDate;

    @Schema(description = "Velicina ugovora (broj akcija po ugovoru, standardno 100)", example = "100")
    private int contractSize;

    @Schema(description = "Da li je opcija 'in the money' (CALL: stockPrice > strike, PUT: stockPrice < strike)")
    private boolean inTheMoney;

    @Schema(description = "Trenutna cena osnovne akcije")
    private BigDecimal currentStockPrice;

    @Schema(description = "Datum kreiranja opcije")
    private LocalDateTime createdAt;
}
