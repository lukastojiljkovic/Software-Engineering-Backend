package rs.raf.trading.otc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * B10 — Istorija OTC pregovora (port iz main PR #89, Aja Timotic).
 *
 * Entitet koji cuva jedan snimak stanja pregovora u trenutku
 * svake izmene (counter-offer, accept, decline). Logicki FK ka
 * {@link OtcOffer#getId()} bez {@code @ManyToOne} da bi historija
 * ostala cak i kad se ponuda obrise.
 */
@Entity
@Table(name = "otc_negotiation_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtcNegotiationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "price_per_share", nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerShare;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal premium;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "modified_by_id", nullable = false)
    private Long modifiedById;

    @Column(name = "modified_by_name", nullable = false, length = 255)
    private String modifiedByName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
