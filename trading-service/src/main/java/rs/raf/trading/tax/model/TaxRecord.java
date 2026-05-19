package rs.raf.trading.tax.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * NAPOMENA (copy-first ekstrakcija, faza 2c): kopija monolitnog
 * {@code rs.raf.banka2_bek.tax.model.TaxRecord}. Vlasnik je referenciran
 * soft id-em ({@code userId} + {@code userType}) — nema JPA veze ka
 * banka-core entitetima, pa je kopija doslovna.
 */
@Entity
@Table(name = "tax_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType; // CLIENT or EMPLOYEE

    @Column(name = "total_profit", nullable = false, precision = 18, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal totalProfit = BigDecimal.ZERO;

    @Column(name = "tax_owed", nullable = false, precision = 18, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal taxOwed = BigDecimal.ZERO;

    @Column(name = "tax_paid", nullable = false, precision = 18, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal taxPaid = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    @org.hibernate.annotations.ColumnDefault("'RSD'")
    @Builder.Default
    private String currency = "RSD";

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
