package rs.raf.banka2_bek.investmentfund.service;

import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;

import java.math.BigDecimal;

/*
================================================================================
 TODO — KALKULATOR IZVEDENIH POLJA FONDA (vrednost, profit, procenat)
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 195-202 i 292-295
--------------------------------------------------------------------------------
 METODE:
  BigDecimal computeFundValue(InvestmentFund fund);
    = fund.account.balance
    + sum(Portfolio.quantity * Listing.price) za sve Portfolio sa
      userRole=FUND, userId=fund.id, konvertovano u RSD

  BigDecimal computeProfit(InvestmentFund fund);
    = computeFundValue(fund) - sum(ClientFundPosition.totalInvested for fund)

  BigDecimal computePositionValue(ClientFundPosition position);
    = fundValue * (position.totalInvested / totalInvested_across_all_positions)

  BigDecimal computePositionPercent(ClientFundPosition position);
    = position.totalInvested / sum(all positions) * 100
    * Napomena: ako je totalInvested_sum = 0, vrati 0 (edge case posle
                 brisanja svih pozicija).

 PERFORMANSE:
 - Ove metode se zovu cesto (svaki refresh Discovery stranice).
 - Optimalno: cache koji se invalidira pri svakoj ClientFundTransaction-i
   ili novoj order fill-u fund portfolija.
 - Za pocetak: racunaj uvek freshly, optimizuj kasnije ako bude sporo.

 KONVERZIJA VALUTA:
 - Fond je u RSD, ali hartije mogu biti u USD/EUR/JPY. Koristi
   CurrencyConversionService.convert(amount, fromCurrency, "RSD") bez komisije.
================================================================================
*/
@Service
public class FundValueCalculator {

    // TODO: injectovati PortfolioRepository, ListingRepository, AccountRepository,
    //   ClientFundPositionRepository, CurrencyConversionService

    public BigDecimal computeFundValue(InvestmentFund fund) {
        throw new UnsupportedOperationException("TODO");
    }

    public BigDecimal computeProfit(InvestmentFund fund) {
        throw new UnsupportedOperationException("TODO");
    }

    public BigDecimal computePositionValue(Long fundId, Long userId, String userRole) {
        throw new UnsupportedOperationException("TODO");
    }

    public BigDecimal computePositionPercent(Long fundId, Long userId, String userRole) {
        throw new UnsupportedOperationException("TODO");
    }
}
