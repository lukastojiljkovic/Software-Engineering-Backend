package rs.raf.banka2_bek.transfer.model;

import jakarta.persistence.*;
import lombok.*;

import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false)
    private Account toAccount;

    // Iznos koji se skida sa izvornog računa
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fromAmount;

    // Iznos koji se dodaje na odredišni račun (može biti različit kod konverzije)
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal toAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_currency_id", nullable = false)
    private Currency fromCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_currency_id", nullable = false)
    private Currency toCurrency;

    // Kurs konverzije (null ako su valute iste)
    @Column(precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal commission = BigDecimal.ZERO;    // Provizija banke

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferType transferType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PROCESSING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Client createdBy;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
