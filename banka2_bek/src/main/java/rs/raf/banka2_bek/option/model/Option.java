package rs.raf.banka2_bek.option.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rs.raf.banka2_bek.stock.model.Listing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entitet za opcije (finansijske derivate).
 *
 * Opcija predstavlja ugovor koji daje kupcu pravo (ali ne i obavezu) da kupi (CALL)
 * ili proda (PUT) odredjenu akciju (stockListing) po unapred definisanoj ceni (strikePrice)
 * do odredjenog datuma (settlementDate).
 *
 * Specifikacija: Celina 3 - Opcije i Black-Scholes
 *
 * Ticker format:
 *   {STOCK_TICKER}{YYMMDD}{C/P}{STRIKE*1000 sa 8 cifara, zero-padded}
 *   Primer: MSFT220404C00180000
 *
 * Cena opcije (price) se racuna Black-Scholes modelom u BlackScholesService.
 */
@Entity
@Table(name = "options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Option {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ManyToOne relacija ka Listing entitetu (osnovna akcija). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_listing_id", nullable = false)
    private Listing stockListing;

    /**
     * Tip opcije: CALL ili PUT.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptionType optionType;

    /** Strike price - cena po kojoj kupac opcije moze da kupi/proda akciju. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal strikePrice;

    /** Implied Volatility (sigma) - procenjena volatilnost akcije. */
    @Column(nullable = false)
    private double impliedVolatility;

    /** Open Interest - ukupan broj otvorenih (neizmirenih) ugovora. */
    @Column(name = "open_interest")
    private int openInterest = 0;

    /** Settlement Date - datum isteka opcije. */
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    /** Contract Size - broj akcija po jednom ugovoru opcije (standardno 100). */
    @Column(name = "contract_size")
    private int contractSize = 100;

    /** Price - premija opcije izracunata Black-Scholes modelom. */
    @Column(precision = 18, scale = 4)
    private BigDecimal price;

    /** Ask - najniza cena po kojoj prodavac nudi opciju. */
    @Column(precision = 18, scale = 4)
    private BigDecimal ask;

    /** Bid - najvisa cena po kojoj kupac nudi opciju. */
    @Column(precision = 18, scale = 4)
    private BigDecimal bid;

    /** Volume - broj ugovora kojima se trgovalo tokom dana. */
    private long volume;

    /** Ticker - jedinstveni identifikator opcije. */
    @Column(nullable = false, unique = true)
    private String ticker;

    /**
     * Datum i vreme kreiranja opcije.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Automatski postavlja createdAt pre prvog upisa u bazu. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
