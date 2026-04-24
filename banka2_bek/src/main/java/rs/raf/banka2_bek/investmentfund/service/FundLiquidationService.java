package rs.raf.banka2_bek.investmentfund.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/*
================================================================================
 TODO — AUTO-LIKVIDACIJA HARTIJA KAD FOND NEMA DOVOLJNO RSD
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 261 i 350
--------------------------------------------------------------------------------
 KONTEKST:
 Ako klijent trazi povlacenje iz fonda, a fund.account.balance < amount,
 ne mozemo odmah isplatiti. Spec kaze: "vrsi se automatska likvidacija
 dovoljnog broja hartija i klijent dobija obavestenje".

 STRATEGIJA PRODAJE:
 - Odaberemo najveci(-a) holding(e) po RSD-vrednosti (najmanje akcija =
   najmanje impact na portfolio).
 - Kreiramo SELL Market order za `userId = fund.id, userRole = FUND`.
 - Kada se order IZVRSI (Done), upisujemo uplatu na fund.accountId.
 - Onda resavamo pending ClientFundTransaction-e u FIFO redu.

 ALTERNATIVA:
 - Prodaj proporcionalno (1% svakog holdinga) — manje impact, ali vise
   ordera.

 METODE:
  void liquidateFor(Long fundId, BigDecimal amountRsd);
    - Izracunaj koliko akcija kojih listing-a moramo prodati
    - Kreiraj Order-e
    - Markiraj transakciju kao "likvidacija u toku"

  void onFillCompleted(Long orderId);
    - Hook koji OrderExecutionService zove kad se Order tipa FUND zavrsava
    - Ako je to bila likvidacija, pokusaj da resavis PENDING transakcije

 ALTERNATIVA (jednostavnija, za prvi iteration):
 - Sinhronno: odbaci withdraw ako nema dovoljno cash-a u fondu, i reci
   klijentu "Cekajte da fond proda akcije — pokusajte ponovo sutra". Tim
   ima izbor, nije blocker za KT.
================================================================================
*/
@Service
public class FundLiquidationService {

    // TODO: injectovati OrderService, PortfolioRepository, ListingRepository,
    //   ClientFundTransactionRepository, AccountRepository

    @Transactional
    public void liquidateFor(Long fundId, BigDecimal amountRsd) {
        throw new UnsupportedOperationException("TODO");
    }

    @Transactional
    public void onFillCompleted(Long orderId) {
        // TODO: proveri da li je order.userRole=FUND; ako da, probaj da
        //   zavrsis neku PENDING ClientFundTransaction-u
        throw new UnsupportedOperationException("TODO");
    }
}
