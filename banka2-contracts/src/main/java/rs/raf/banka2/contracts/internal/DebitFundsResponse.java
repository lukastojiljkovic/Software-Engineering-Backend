package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/** Odgovor na jednostrani debit racuna (option exercise CALL, margin uplata). */
public record DebitFundsResponse(Long accountId, BigDecimal debitedAmount,
                                 BigDecimal balanceAfter) {
}
