package rs.raf.trading.investmentfund.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// ============================================================
// TODO [B12 - Statistika investicionih fondova | Nosilac: Milica Zoranovic]
//
// DTO koji nosi izracunate statisticke metrike performansi jednog fonda
// kao odgovor na GET /funds/{id}/statistics.
//
// IMPLEMENTIRATI — dodati sledeca polja:
//   - Long fundId
//       ID fonda na koji se statistike odnose.
//
//   - String fundName
//       Ime fonda (radi citljivosti odgovora, ne zahteva poseban poziv).
//
//   - Integer snapshotCount
//       Ukupan broj dnevnih snimaka koji su korisceni za racunanje.
//       Klijent moze prikazati "na osnovu N dana istorije".
//
//   - BigDecimal annualizedReturnPercent
//       Annualizovani prinos u procentima, zaokruzen na 4 decimale.
//       Null ako nije dovoljno snimaka (< MIN_SNAPSHOTS_REQUIRED).
//
//   - BigDecimal volatilityPercent
//       Standardna devijacija mesecnih prinosa u procentima,
//       zaokruzena na 4 decimale.
//       Null ako nije dovoljno mesecnih tacaka (< 2).
//
//   - BigDecimal maxDrawdownPercent
//       Maksimalni drawdown u procentima (negativna vrednost ili nula).
//       Null ako nije dovoljno snimaka.
//
//   - BigDecimal rewardToVariabilityRatio
//       Sharpe-like racio (annualizedReturn / volatility), 4 decimale.
//       Null ako volatilnost nije dostupna ili je nula.
//
//   - Boolean sufficientHistory
//       Convenience flag: true ako je snapshotCount >= MIN_SNAPSHOTS_REQUIRED.
//       Klijent moze koristiti da prikaze upozorenje "premalo podataka".
//
// Konvencija: pratiti stil @Getter @Setter @NoArgsConstructor @AllArgsConstructor
// @Builder kao u SavingsDepositDto iz paketa `savings`.
// Spec: Zadaci_Backend.pdf, zadatak B12.
// ============================================================
// Napomena: skeleton je prazan pa nosi samo @NoArgsConstructor. Pri dodavanju
// polja vratiti @AllArgsConstructor, @Builder i import java.math.BigDecimal.
@Getter
@Setter
@NoArgsConstructor
public class FundStatisticsDto {
}
