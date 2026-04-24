package rs.raf.banka2_bek.interbank.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
================================================================================
 TODO — SAGA / 2PC TRACKING ZA INTER-BANK TRANSAKCIJE
 Zaduzen: BE tim (backend lead)
 Spec referenca: Celina 4, linije 368-437 (Placanja 2PC) i 473-519 (OTC SAGA)
--------------------------------------------------------------------------------
 SVRHA:
 Entitet koji prati stanje jedne distribuirane transakcije (placanje ili OTC
 iskoriscavanje opcije) izmedju nase banke i jedne druge banke. Svaka interbank
 transakcija prolazi kroz fazu: INITIATED -> PREPARED -> COMMITTED (happy path)
 ili INITIATED -> ABORTED (failure).

 OBAVEZNA POLJA (vec definisana ispod, samo provera):
  - id                         PK
  - transactionId              String, UUID, generisan na strani incijatora;
                               koristi se za korelaciju poruka izmedju banaka
  - type                       InterbankTransactionType enum (PAYMENT, OTC)
  - status                     InterbankTransactionStatus enum
  - senderBankCode             oznaka banke koja je inicirala (nase ili drugo)
  - receiverBankCode           oznaka druge banke
  - amount                     iznos u originalnoj valuti
  - currency                   valuta (3-slovni kod)
  - convertedAmount            iznos nakon konverzije u valuti primaoca (za
                               placanja — nakon Prepare odgovora)
  - convertedCurrency          valuta primaoca
  - exchangeRate               primenjen kurs (null za iste valute)
  - commissionAmount           provizija (iz Ready odgovora)
  - senderAccountNumber        racun posiljaoca (nas)
  - receiverAccountNumber      racun primaoca (njihov ili nas ako smo primalac)
  - reservedAmount             kolicina koja je rezervisana na senderAccount-u
                               (ili null ako smo mi primalac)
  - listingId                  (za OTC) id akcije
  - quantity                   (za OTC) broj akcija
  - strikePrice                (za OTC) strike cena
  - createdAt                  kada je inicirano
  - preparedAt                 kada je Prepare faza zavrsena
  - committedAt                kada je Commit faza zavrsena
  - abortedAt                  kada je Abort poslat/primljen
  - lastRetryAt                za retry scheduler
  - retryCount                 broj pokusaja ponovnog slanja
  - failureReason              string greska ako je ABORTED

 RELACIJE:
  - Nema (namerno) jake relacije prema Account/Listing — entitet prati
    "nesto spoljno", kopira potrebne identifikatore. Account resolution
    u servisu po accountNumber-u.

 STATUSI (vidi InterbankTransactionStatus enum):
  - INITIATED       — zahtev zapisan, jos nisu poslate poruke
  - PREPARING       — Prepare poslat, cekamo Ready
  - PREPARED        — Ready primljen; sredstva rezervisana
  - COMMITTING      — Commit poslat, cekamo potvrdu
  - COMMITTED       — Commit potvrdjen, transakcija uspesna
  - ABORTING        — Abort poslat ili lokalni fail, rollback u toku
  - ABORTED         — Transakcija neuspesna, sve kompenzovano
  - STUCK           — Retry scheduler ne moze dalje (posle max pokusaja)

 INDEX:
  - transaction_id (unique) — za lookup po UUID-u
  - status + last_retry_at   — za retry scheduler

 LEKCIJE IZ INTRA-BANK OTC:
  - Ne koristi @ManyToOne, zadrzi identifikatore kao stringove/Long-ove.
  - @ColumnDefault je obavezan na bool/integer poljima (PostgreSQL).
================================================================================
*/
@Entity
@Table(name = "interbank_transactions", indexes = {
        @Index(name = "idx_ibt_transaction_id", columnList = "transaction_id", unique = true),
        @Index(name = "idx_ibt_status_retry",   columnList = "status, last_retry_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterbankTransaction {

    // TODO: proveri da li bi trebalo koristiti UUID kao PK. Preporuka: zadrzi
    // auto-increment Long i drzi UUID u `transactionId` (unique string kolona).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterbankTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterbankTransactionStatus status;

    @Column(name = "sender_bank_code", nullable = false, length = 16)
    private String senderBankCode;

    @Column(name = "receiver_bank_code", nullable = false, length = 16)
    private String receiverBankCode;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "converted_amount", precision = 19, scale = 4)
    private BigDecimal convertedAmount;

    @Column(name = "converted_currency", length = 3)
    private String convertedCurrency;

    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "commission_amount", precision = 19, scale = 4)
    private BigDecimal commissionAmount;

    @Column(name = "sender_account_number", length = 32)
    private String senderAccountNumber;

    @Column(name = "receiver_account_number", length = 32)
    private String receiverAccountNumber;

    @Column(name = "reserved_amount", precision = 19, scale = 4)
    private BigDecimal reservedAmount;

    // OTC-specific fields
    @Column(name = "listing_ticker", length = 32)
    private String listingTicker;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "strike_price", precision = 19, scale = 4)
    private BigDecimal strikePrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "aborted_at")
    private LocalDateTime abortedAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;
}
