package rs.raf.trading.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rs.raf.trading.stock.model.Listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Nalog za kupovinu ili prodaju hartije od vrednosti.
 *
 * Specifikacija: Celina 3 - Order
 *
 * NAPOMENA (copy-first ekstrakcija, faza 2c): kopija monolitnog
 * {@code rs.raf.banka2_bek.order.model.Order}. {@code listing} ostaje JPA
 * {@code @ManyToOne} veza ka {@link Listing} jer je {@code Listing} u
 * trading-service domenu (faza 2b). Sve ostale reference na banka-core
 * entitete su vec soft id-evi ({@code userId}, {@code accountId},
 * {@code reservedAccountId}, {@code fundId}). Dodata su dva NOVA polja
 * ({@code bankaCoreReservationId}, {@code sagaState}) — settlement SAGA
 * seam; monolitna kopija ih NEMA i nije dirana.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false)
    private String userRole; // "EMPLOYEE" ili "CLIENT"

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "contract_size", nullable = false)
    private Integer contractSize;

    @Column(name = "price_per_unit", precision = 18, scale = 4)
    private BigDecimal pricePerUnit;

    @Column(name = "limit_value", precision = 18, scale = 4)
    private BigDecimal limitValue;

    @Column(name = "stop_value", precision = 18, scale = 4)
    private BigDecimal stopValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "is_done", nullable = false)
    private boolean isDone;

    @Column(name = "last_modification")
    private LocalDateTime lastModification;

    @Column(name = "remaining_portions")
    private Integer remainingPortions;

    @Column(name = "after_hours", nullable = false)
    private boolean afterHours;

    @Column(name = "all_or_none", nullable = false)
    private boolean allOrNone;

    @Column(name = "is_margin", nullable = false)
    private boolean margin;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "approximate_price", precision = 18, scale = 4)
    private BigDecimal approximatePrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── Rezervacija sredstava ─────────────────────────────────────────────
    @Column(name = "reserved_account_id")
    private Long reservedAccountId;

    @Column(name = "reserved_amount", precision = 19, scale = 4)
    private BigDecimal reservedAmount;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(name = "reservation_released", nullable = false)
    private boolean reservationReleased = false;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Kurs u trenutku rezervacije (listing currency -> account currency). */
    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate;

    /**
     * FX komisija naplacena od klijenta kada trguje hartijom u valuti
     * razlicitoj od valute izabranog racuna. Izrazena u valuti racuna.
     * Za zaposlene (bankini racuni) i za iste valute je uvek 0.
     */
    @Column(name = "fx_commission", precision = 19, scale = 4)
    private BigDecimal fxCommission;

    /**
     * Kada je najranije dozvoljen sledeci fill pokusaj. Postavlja se posle
     * svakog uspesnog fill-a po spec-formuli:
     *   Random(0, 24 * 60 / (volume / remaining)) sekundi
     * plus 30 minuta ako je nalog kreiran u after-hours periodu.
     * Null znaci da jos nije bilo fill-a — prvi fill prolazi kroz
     * standardni initialDelay guard baziran na approvedAt/createdAt.
     */
    @Column(name = "next_fill_at")
    private LocalDateTime nextFillAt;

    /**
     * P3 — Spec Celina 4 (Nova) §3883-3964: kada supervizor kupuje hartiju
     * u ime investicionog fonda, ovde se belezi fondId. Pri commit-u, hartija
     * ulazi u portfolio fonda (ne supervizora), a sredstva se skidaju sa
     * RSD racuna fonda umesto sa bankinog racuna.
     *
     * Null znaci da je trgovina za banku (employees) ili za klijenta.
     */
    @Column(name = "fund_id")
    private Long fundId;

    // ── Settlement SAGA seam (NOVO u trading-service-u, faza 2c) ──────────
    /**
     * Id rezervacije sredstava koju je banka-core vratio na
     * {@code POST /internal/funds/reserve}. Cuva se da bi se rezervacija
     * mogla commit-ovati ({@code /commit}) ili oslobaditi ({@code /release})
     * u settlement SAGA-i. Null dok rezervacija nije izvrsena.
     */
    @Column(name = "banka_core_reservation_id")
    private String bankaCoreReservationId;

    /**
     * Trenutna faza settlement SAGA-e za ovaj nalog. Null za naloge
     * koji jos nisu usli u distribuiranu naplatu.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "saga_state")
    private SagaState sagaState;
}
