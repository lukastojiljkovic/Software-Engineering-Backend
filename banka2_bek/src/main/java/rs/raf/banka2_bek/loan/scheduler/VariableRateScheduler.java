package rs.raf.banka2_bek.loan.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.loan.model.InterestType;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanStatus;
import rs.raf.banka2_bek.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * BE-PAY-03: Monthly scheduler that simulates variable interest rate changes.
 *
 * <p>Per specification (Celina 4 - Simulacija promenljive kamatne stope):
 * <ul>
 *   <li>Generates a random offset in [-1.50%, +1.50%]</li>
 *   <li>Recalculates effectiveRate = nominalRate + offset + margin (by loan type)</li>
 *   <li>Recalculates monthly payment using the annuity formula for remaining debt/period</li>
 *   <li>Updates all future (unpaid) installments accordingly</li>
 * </ul></p>
 *
 * <p>BE-PAY-03 fix: no outer {@code @Transactional}; per-loan isolation via
 * {@link VariableRateProcessor} (REQUIRES_NEW). Jedna lose obradjena loon
 * vise ne rollback-uje sve ostale u batch-u.</p>
 */
@Slf4j
@Component
public class VariableRateScheduler {

    private final LoanRepository loanRepository;
    private final VariableRateProcessor processor;

    public VariableRateScheduler(LoanRepository loanRepository,
                                 VariableRateProcessor processor) {
        this.loanRepository = loanRepository;
        this.processor = processor;
    }

    /**
     * Runs at 01:00 AM on the 1st of each month.
     * Cron: second minute hour day-of-month month day-of-week
     */
    @Scheduled(cron = "0 0 1 1 * *")
    public void adjustVariableRates() {
        log.info("=== Variable rate adjustment started ===");

        List<Loan> variableLoans = loanRepository.findByInterestTypeAndStatusIn(
                InterestType.VARIABLE, List.of(LoanStatus.ACTIVE, LoanStatus.LATE));

        if (variableLoans.isEmpty()) {
            log.info("No active variable-rate loans found. Skipping.");
            return;
        }

        // Generate a single random offset for this month (shared across all loans)
        double rawOffset = ThreadLocalRandom.current().nextDouble(-1.50, 1.50);
        BigDecimal offset = BigDecimal.valueOf(rawOffset).setScale(2, RoundingMode.HALF_UP);
        log.info("Generated monthly interest rate offset: {}%", offset);

        int updatedCount = 0;
        int failedCount = 0;

        for (Loan loan : variableLoans) {
            try {
                processor.adjustOne(loan, offset);
                updatedCount++;
            } catch (RuntimeException e) {
                failedCount++;
                log.error("Failed to adjust rate for loan {}: {}", loan.getLoanNumber(), e.getMessage(), e);
            }
        }

        log.info("=== Variable rate adjustment completed: {}/{} loans updated ({} failed) ===",
                updatedCount, variableLoans.size(), failedCount);
    }
}
