package rs.raf.banka2_bek.card.model;

import jakarta.persistence.*;
import lombok.*;
import rs.raf.banka2_bek.account.model.Account;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(name = "card_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CardRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    private BigDecimal cardLimit;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CardType cardType;

    /** Kategorija placanja (Debit / Credit / Internet prepaid) — odvojeno od brenda. */
    @Enumerated(EnumType.STRING)
    @Column(name = "card_category", length = 25)
    private CardCategory cardCategory;

    /** Kreditni limit, samo za CREDIT kategoriju. */
    @Column(name = "credit_limit", precision = 19, scale = 4)
    @org.hibernate.annotations.ColumnDefault("0")
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(nullable = false)
    private String clientEmail;

    @Column(nullable = false)
    private String clientName;

    @Column(nullable = false)
    private String status; // PENDING, APPROVED, REJECTED

    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;
    private String processedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
