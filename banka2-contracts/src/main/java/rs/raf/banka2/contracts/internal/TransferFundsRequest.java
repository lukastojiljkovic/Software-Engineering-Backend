package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/** Direktan prenos novca izmedju dva racuna (OTC premija, dividenda, porez, fond). */
public record TransferFundsRequest(Long fromAccountId, Long toAccountId,
                                   BigDecimal amount, String currencyCode, String description) {
}
