package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/** Odgovor sa handle-om rezervacije. */
public record ReserveFundsResponse(String reservationId, Long accountId,
                                   BigDecimal reservedAmount, BigDecimal availableBalanceAfter) {
}
