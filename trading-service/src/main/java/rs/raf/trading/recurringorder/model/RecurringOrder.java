package rs.raf.trading.recurringorder.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// JPA entitet koji predstavlja jedan trajni nalog (DCA nalog).
// Tabla u bazi: `recurring_orders`.
//
// Mikroservisi varijanta: ovaj entitet zivi u trading-service-u (trading_db).
// Bankarski podaci (accountId) su soft-reference (Long) i razresavaju se preko
// BankaCoreClient-a (rs.raf.trading.client) — racun NIJE FK ka lokalnoj tabeli.
//
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@Entity
@Table(name = "recurring_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 16)
    private String ownerType;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false, length = 4)
    private String direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecurringMode mode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal value;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private RecurringCadence cadence;

    @Column(nullable = false)
    private LocalDateTime nextRun;

    @ColumnDefault("1")
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
