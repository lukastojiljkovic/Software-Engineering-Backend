package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/**
 * Naplata (dela) rezervacije — npr. jedan fill BUY order-a.
 * amount = stvarni trosak fill-a; commission = provizija; beneficiaryAccountId
 * (opciono) = racun kome ide {@code amount} (npr. prodavac kod OTC-a). Ako je null,
 * novac napusta rezervisani racun (trziste/banka).
 */
public record CommitFundsRequest(BigDecimal amount, BigDecimal commission,
                                 Long beneficiaryAccountId, String description) {
}
