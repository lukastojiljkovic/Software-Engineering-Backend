package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/** Zahtev za rezervaciju sredstava na racunu (BUY order, OTC, fond). */
public record ReserveFundsRequest(Long accountId, BigDecimal amount, String currencyCode) {
}
