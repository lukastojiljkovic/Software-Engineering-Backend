package rs.raf.banka2_bek.investmentfund.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
================================================================================
 TODO — DNEVNI SNIMAK VREDNOSTI SVIH FONDOVA (23:45)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 316 "belezite istorijske podatke"
--------------------------------------------------------------------------------
 FLOW:
  1. Svaki dan u 23:45 (cron "0 45 23 * * *"):
     a. Dohvati sve aktivne fondove.
     b. Za svaki fund, FundValueCalculator.computeFundValue + computeProfit.
     c. Upise FundValueSnapshot za snapshotDate=today.
     d. Ako vec postoji snapshot za taj dan (manual trigger ili retry),
        UPDATE umesto INSERT.

 OPCIJE:
  - Dodati manual trigger endpoint (admin only) za testiranje.
  - Alternativno: cesci snapshot-i (1x po satu) ako treba preciznije.
================================================================================
*/
@Component
public class FundValueSnapshotScheduler {

    // TODO: injectovati InvestmentFundRepository, FundValueCalculator,
    //   FundValueSnapshotRepository

    @Scheduled(cron = "0 45 23 * * *")
    public void snapshotAllFunds() {
        // TODO: implementirati
    }
}
