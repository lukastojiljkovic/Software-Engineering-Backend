package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

public record CommitFundsResponse(String reservationId, BigDecimal committedTotal,
                                  BigDecimal balanceAfter, BigDecimal reservedRemaining) {
}
