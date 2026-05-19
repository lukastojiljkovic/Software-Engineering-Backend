package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/**
 * Direktan prenos novca izmedju dva racuna (OTC premija, dividenda, porez, fond).
 *
 * <p>Cross-currency-sposoban primitiv: banka-core debituje {@code fromAccountId}
 * za {@code debitAmount} u SOPSTVENOJ valuti tog racuna, kreditira
 * {@code toAccountId} za {@code creditAmount} u NJEGOVOJ sopstvenoj valuti, i —
 * ako je {@code commission > 0} — kreditira bankin {@code BANK_TRADING} racun u
 * {@code commissionCurrency} za {@code commission}. Pozivalac (trading-service)
 * je vec uradio FX matematiku (njegov lokalni {@code CurrencyConversionService}
 * koristi kurseve koji poticu iz banka-core, pa su konzistentni) i dostavlja
 * tacne {@code debitAmount}/{@code creditAmount}/{@code commission}. Za prenos
 * iste valute je {@code debitAmount == creditAmount}.
 *
 * <p>{@code commission}/{@code commissionCurrency} su opcioni — {@code null}/0
 * znaci bez provizije.
 */
public record TransferFundsRequest(Long fromAccountId, BigDecimal debitAmount,
                                   Long toAccountId, BigDecimal creditAmount,
                                   BigDecimal commission, String commissionCurrency,
                                   String description) {
}
