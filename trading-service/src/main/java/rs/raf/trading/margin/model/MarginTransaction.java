package rs.raf.trading.margin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entitet koji predstavlja transakciju na margin racunu.
 *
 * Tipovi transakcija:
 *   DEPOSIT    - uplata sredstava na margin racun
 *   WITHDRAWAL - isplata sredstava sa margin racuna
 *   BUY        - kupovina hartija putem margin racuna
 *   SELL       - prodaja hartija putem margin racuna
 *
 * Specifikacija: Celina 3 - Margin racuni
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-D): {@code marginAccount} je
 * intra-paket veza ka {@code MarginAccount} (oba u {@code margin}-owned tabelama),
 * pa ostaje kao prava JPA {@code @ManyToOne} relacija.
 */
@Entity
@Table(name = "margin_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarginTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Margin racun na kom je izvrsena transakcija */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "margin_account_id", nullable = false)
    private MarginAccount marginAccount;

    /** Tip transakcije */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MarginTransactionType type;

    /** Iznos transakcije (uvek pozitivan) */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Opis transakcije (opciono) */
    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    @org.hibernate.annotations.ColumnDefault("CURRENT_TIMESTAMP")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
