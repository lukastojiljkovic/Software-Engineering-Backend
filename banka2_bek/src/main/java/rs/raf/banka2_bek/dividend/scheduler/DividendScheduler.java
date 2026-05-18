package rs.raf.banka2_bek.dividend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// ============================================================
// TODO [B9 - Isplata dividendi na akcije | Nosilac: Djordje Zlatanovic]
//
// Kvartalani scheduler koji pokree isplatu dividendi na poslednji radni dan
// svakog kvartala (mart, juni, septembar, decembar).
//
// ZAVISNOSTI ZA INJEKTOVANJE:
//   - DividendService dividendService
//
// IMPLEMENTIRATI:
//
//   @Scheduled(cron = "0 0 6 L MAR,JUN,SEP,DEC ?")
//   public void runQuarterlyDividendPayout()
//       — Pokrece dividendService.processQuarterlyDividends(LocalDate.now(ZoneOffset.UTC)).
//         Napomena: Spring @Scheduled cron ne podrzava direktno "poslednji radni dan" —
//         koristiti "L" specifikator za poslednji dan meseca (`0 0 6 L MAR,JUN,SEP,DEC ?`),
//         a u DividendService.processQuarterlyDividends dodati logiku da ako je poslednji dan
//         meseca subota/nedelja, preskociti na prethodni petak:
//           while (paymentDate.getDayOfWeek() == SATURDAY || paymentDate.getDayOfWeek() == SUNDAY)
//               paymentDate = paymentDate.minusDays(1);
//         Scheduler loguje pocetak i kraj + koliko isplata je procesirano.
//         Svaka greska na nivou jedne isplate NE sme da zaustavi ceo ciklus
//         (uhvati Exception per-payout i logovati, nastaviti sa ostalima —
//         pratiti SavingsScheduler sablon).
//
//   Razlog za @Component (ne @Service): ovo je infrastrukturna klasa bez poslovne logike —
//   konzistentno sa SavingsScheduler koji takodje koristi @Service, ali ovde je @Component
//   prihvatljivo i jasnije oznacava ulogu. Mozete koristiti @Service ako zelite konzistentnost
//   sa SavingsScheduler-om.
//
//   VAZNO: NE stavljati @Scheduled unutar DividendService direktno.
//   @Transactional pozivi moraju prolaziti kroz Spring AOP proxy, sto znaci da scheduler
//   mora biti zaseban bean koji poziva service metode — identican razlog kao
//   SavingsScheduler -> SavingsDepositProcessor.
//
// Konvencija: pratiti paket `savings` (SavingsScheduler) kao sablon.
// Spec: Zadaci_Backend.pdf, zadatak B9.
// ============================================================

@Component
@RequiredArgsConstructor
@Slf4j
public class DividendScheduler {
}
