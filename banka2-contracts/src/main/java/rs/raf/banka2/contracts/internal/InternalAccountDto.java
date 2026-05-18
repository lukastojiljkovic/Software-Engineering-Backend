package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/** Citanje racuna za prikaz u trading-service (broj racuna, vlasnik, stanje). */
public record InternalAccountDto(Long id, String accountNumber, String ownerName,
                                 BigDecimal balance, BigDecimal availableBalance,
                                 BigDecimal reservedAmount, String currencyCode,
                                 String status) {
}
