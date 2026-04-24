package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
================================================================================
 TODO — JEDNA UPLATA/ISPLATA U FOND
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 221-261
--------------------------------------------------------------------------------
 Svaka uplata (is_inflow=true) ili povlacenje (is_inflow=false) klijenta
 ili banke u fond ide kroz ovaj entitet. Posle commit-a azuriramo
 ClientFundPosition.totalInvested += iznos (ili -= za odliv).

 POLJA:
  - id
  - fundId            FK na InvestmentFund.id
  - userId            klijent koji uplacuje / povlaci
  - userRole          "CLIENT" (banka se tretira kao klijent preko ownerClientId)
  - amountRsd         Iznos u RSD (u tabeli)
  - sourceAccountId   Za investment: sa kog racuna klijent uplacuje;
                      Za redemption: na koji racun povlaci
                      (banka: fund account za invest, bank account za redeem)
  - isInflow          true = uplata, false = povlacenje
  - status            PENDING/COMPLETED/FAILED (vidi enum)
  - createdAt
  - completedAt
  - failureReason     string

 STATUS PENDING:
 Razlog zasto nije odmah COMPLETED: redemption ako fond nema dovoljno
 likvidnih RSD — sistem salje FundLiquidationService da proda hartije;
 dok se prodaja ne izvrsi, transakcija je PENDING. Spec linija 261.

 NAPOMENA O KOMISIJI:
  - Klijent investicija/povlacenje: 1% FX komisija na konverziju
    (ako racun nije RSD).
  - Supervizor investicija/povlacenje (banka): 0% komisija
    (spec linija 351).
================================================================================
*/
@Entity
@Table(name = "client_fund_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientFundTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_id", nullable = false)
    private Long fundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, length = 16)
    private String userRole;

    @Column(name = "amount_rsd", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountRsd;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "is_inflow", nullable = false)
    @org.hibernate.annotations.ColumnDefault("1")
    private boolean inflow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClientFundTransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;
}
