package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/**
 * Citanje racuna za prikaz u trading-service (broj racuna, vlasnik, stanje).
 *
 * <p>{@code ownerClientId}/{@code ownerEmployeeId}/{@code accountCategory} nose
 * vlasnistvo racuna — trading-service ih koristi da reprodukuje monolitovu
 * proveru da li racun pripada akteru (npr. {@code InvestmentFundService}
 * provera da uplatni/isplatni racun pripada ulogovanom klijentu, odnosno da je
 * supervizorski izbor bankin {@code BANK_TRADING} racun).
 */
public record InternalAccountDto(Long id, String accountNumber, String ownerName,
                                 BigDecimal balance, BigDecimal availableBalance,
                                 BigDecimal reservedAmount, String currencyCode,
                                 String status,
                                 Long ownerClientId, Long ownerEmployeeId,
                                 String accountCategory) {
}
