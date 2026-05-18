package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

public record TransferFundsResponse(Long fromAccountId, Long toAccountId,
                                    BigDecimal amount, BigDecimal fromBalanceAfter,
                                    BigDecimal toBalanceAfter) {
}
