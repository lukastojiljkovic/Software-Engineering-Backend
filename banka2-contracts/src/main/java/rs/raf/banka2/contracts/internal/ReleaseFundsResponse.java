package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

public record ReleaseFundsResponse(String reservationId, BigDecimal releasedAmount,
                                   BigDecimal availableBalanceAfter) {
}
