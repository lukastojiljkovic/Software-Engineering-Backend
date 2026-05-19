package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/**
 * Jednostrani debit racuna bez credit kontra-strane (option exercise CALL,
 * margin createForUser pocetna uplata). Novac napusta banka-core ka trzistu/
 * margin ledger-u — banka-core zaduzuje {@code accountId} sa {@code amount} (u
 * sopstvenoj valuti racuna); opciona {@code commission} ide bankinom
 * BANK_TRADING racunu u {@code currencyCode}.
 */
public record DebitFundsRequest(Long accountId, BigDecimal amount, BigDecimal commission,
                                String currencyCode, String description) {
}
