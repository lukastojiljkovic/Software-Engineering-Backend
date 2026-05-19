package rs.raf.banka2_bek.pricealert.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// ============================================================
// TODO [B5 - Cenovni alarmi (Price Alert) | Nosilac: Aleksa Vucinic]
//
// Scheduled komponenta koja periodicno pokusava da obare alarme
// cija cena je vec presla prag, ali koji nisu okidani u realnom
// vremenu (npr. zbog pada servera, privremene nedostupnosti
// Alpha Vantage-a, ili alarma postavljenih retroaktivno).
//
// IMPLEMENTIRATI:
//
// Zavisnosti (polja za injekciju):
//   - PriceAlertRepository alertRepository
//       Za dohvatanje aktivnih alarma koji jos nisu okidani.
//   - PriceAlertService priceAlertService
//       Za poziv checkAlerts(listingId, currentPrice) po listingu.
//   - trenutne cene hartija -> HTTP klijent ka trading-service-u
//       (trgovinski domen je iseljen u trading-service — pod-faza 2f cutover;
//        videti TradingServiceClient obrazac u paketu `assistant`)
//       Za dohvatanje trenutnih cena svih hartija na kojima postoje
//       aktivni alarmi.
//
// Metoda koja SE MORA implementirati:
//
//   @Scheduled(fixedRate = 60_000)   // svakih 60 sekundi
//   public void scanActiveAlerts()
//       Tok:
//         1. alertRepository.findDistinctListingIdsByActiveTrue()
//            (ovu metodu takodje dodati u PriceAlertRepository —
//            @Query("SELECT DISTINCT a.listingId FROM PriceAlert a
//                    WHERE a.active = true"))
//            -> List<Long> listingIds
//         2. Za svaki listingId:
//            a. Dohvatiti trenutnu cenu hartije iz listings tabele
//               (listing.getCurrentPrice() ili ekvivalentno polje
//               u projektu — proveriti sa koordinatorom koji paket
//               cuva trzisnu cenu).
//            b. Pozvati priceAlertService.checkAlerts(listingId, price).
//            c. Uhvatiti svaki Exception po listingu (try/catch),
//               logovati na WARN i nastaviti sa sledecim — greska
//               jednog listinga ne sme da zaustavi ceo scan.
//         3. log.info na kraju: "PriceAlertScheduler: scan zavrsen,
//            {} listinga provereno" sa brojacem.
//
// Napomena: @Scheduled metoda namerno NIJE deklarisana u skeleton-u
// (bila bi pokrenuta prazna i ne bi radila nista korisno). Koordinator
// ce je dodati nakon implementacije service-a. Metoda ne sme biti
// @Transactional direktno — delegira transakcije na PriceAlertService
// (isti razlog kao kod SavingsScheduler -> SavingsDepositProcessor).
//
// Konvencija: pratiti paket `savings` kao sablon (SavingsScheduler.java).
// Spec: Zadaci_Backend.pdf, zadatak B5.
// ============================================================
@Component
@RequiredArgsConstructor
@Slf4j
public class PriceAlertScheduler {
}
