package rs.raf.banka2_bek.actuary.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rs.raf.banka2_bek.employee.model.Employee;

import java.math.BigDecimal;

/**
 * Entitet koji cuva aktuarske podatke za zaposlene (agente i supervizore).
 * Svaki admin je ujedno i supervizor. Agent ima limit, supervizor nema.
 *
 * Specifikacija: Celina 3 - Aktuari
 */
@Entity
@Table(name = "actuary_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActuaryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActuaryType actuaryType;

    @Column(name = "daily_limit")
    private BigDecimal dailyLimit;

    @Column(name = "used_limit")
    private BigDecimal usedLimit;

    @Column(name = "need_approval", nullable = false)
    private boolean needApproval;
}
