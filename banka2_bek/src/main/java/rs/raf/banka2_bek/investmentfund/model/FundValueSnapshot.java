package rs.raf.banka2_bek.investmentfund.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/*
================================================================================
 TODO — DNEVNI SNAPSHOT VREDNOSTI FONDA (ZA PERFORMANCE GRAFIK)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 316 "Performanse fonda: tabela ili grafikon
                                       Ovo znaci da treba da pratite performanse
                                       fonda -> belezite istorijske podatke."
--------------------------------------------------------------------------------
 SVRHA:
 Svakog dana (FundValueSnapshotScheduler, u 23:45) racunamo fundValue i
 upisujemo red. FE na Detaljnom prikazu fonda zove
 GET /funds/{id}/performance?period=MONTH i dobija tacke za chart.

 POLJA:
  - id
  - fundId
  - snapshotDate      unique pair sa fundId
  - fundValue         vrednost u RSD
  - liquidAmount      keš u fondu
  - investedTotal     sum(ClientFundPosition.totalInvested)
  - profit            fundValue - investedTotal

 PERFORMANCE:
  Profit % = (fundValue - previousFundValue) / previousFundValue * 100
  (Racuna se na FE na osnovu kliznih vrednosti.)
================================================================================
*/
@Entity
@Table(name = "fund_value_snapshots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fvs_fund_date",
                columnNames = {"fund_id", "snapshot_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FundValueSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fund_id", nullable = false)
    private Long fundId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "fund_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal fundValue;

    @Column(name = "liquid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal liquidAmount;

    @Column(name = "invested_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal investedTotal;

    @Column(precision = 19, scale = 4)
    private BigDecimal profit;
}
