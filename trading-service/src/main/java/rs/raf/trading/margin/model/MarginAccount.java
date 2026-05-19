package rs.raf.trading.margin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entitet koji predstavlja margin racun.
 *
 * Margin racun je vezan za obican racun (Account) i omogucava korisniku
 * da trguje hartijama od vrednosti koristeci pozajmljena sredstva od banke.
 *
 * Specifikacija: Celina 3 - Margin racuni
 *
 * Kljucni koncepti:
 *   - initialMargin: ukupna vrednost racuna (depozit + kredit banke)
 *   - loanValue: koliko je banka pozajmila korisniku
 *   - maintenanceMargin: minimalna vrednost ispod koje se racun blokira (margin call)
 *   - bankParticipation: procenat koji banka pokriva (npr. 0.50 = 50%)
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-D): u monolitu je {@code account}
 * bila JPA {@code @ManyToOne} veza ka {@code Account} entitetu. U trading-service-u
 * je {@code Account} entitet u banka-core domenu, pa je veza prevedena u soft id —
 * kolona {@code account_id} je zadrzana imenom (1:1 migracija podataka kasnije),
 * {@code nullable = false} ocuvan. Broj baznog racuna se denormalizuje u kolonu
 * {@code account_number} (postavlja se pri kreiranju iz {@code InternalAccountDto})
 * jer DTO mapper vise ne moze da dereferira uklonjenu {@code Account} vezu.
 * Bazni racun se citajno razresava preko {@code BankaCoreClient}.
 */
@Entity
@Table(name = "margin_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarginAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID povezanog obicnog racuna (banka-core) sa kog se skidaju/uplacuju sredstva. */
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * Denormalizovan broj baznog racuna — popunjava se pri kreiranju margin racuna
     * iz {@code InternalAccountDto}. Monolit ga je citao preko {@code account.getAccountNumber()};
     * posle FK→soft-id prevoda mora da zivi na ovom entitetu.
     */
    @Column(name = "account_number")
    private String accountNumber;

    /** ID korisnika (klijenta) oji je vlasnik margin racuna */
    @Column(name = "user_id", nullable = false)
    private Long userId;


    /**
     * Pocetna margina — ukupna vrednost racuna.
     * initialMargin = korisnikov depozit + loanValue
     * Formula: initialMargin = deposit / (1 - bankParticipation)
     */
    @Column(name = "initial_margin", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialMargin;

    /**
     * Vrednost kredita — koliko je banka pozajmila korisniku.
     * loanValue = initialMargin - deposit
     */
    @Column(name = "loan_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal loanValue;

    /**
     * Margina odrzavanja — minimalna vrednost racuna.
     * Ako initialMargin padne ispod ove vrednosti, racun se blokira (margin call).
     * Za akcije: maintenanceMargin = initialMargin * 0.5
     */
    @Column(name = "maintenance_margin", nullable = false, precision = 19, scale = 4)
    private BigDecimal maintenanceMargin;

    /**
     * Procenat ucestva banke u investiciji.
     * Npr. 0.50 znaci da banka pokriva 50% ukupne vrednosti.
     */
    @Column(name = "bank_participation", nullable = false, precision = 5, scale = 4)
    private BigDecimal bankParticipation;

    /** Status margin racuna (ACTIVE ili BLOCKED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @org.hibernate.annotations.ColumnDefault("'ACTIVE'")
    @Builder.Default
    private MarginAccountStatus status = MarginAccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @org.hibernate.annotations.ColumnDefault("CURRENT_TIMESTAMP")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
