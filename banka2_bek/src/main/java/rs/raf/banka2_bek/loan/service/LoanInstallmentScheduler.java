package rs.raf.banka2_bek.loan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * BE-PAY-02: Scheduler orchestrator (no @Transactional).
 *
 * <p>Iterira sve neplacene installments koje su due i delegira na
 * {@link InstallmentProcessor#processOne(LoanInstallment, LocalDate)} koji
 * radi sa {@code REQUIRES_NEW} propagation — svaka installment dobija svoju
 * Tx, greska per-installment ne rollback-uje ostatak batch-a.</p>
 *
 * <p>Per spec (Celina 2 - Automatsko skidanje rata):
 * <ol>
 *   <li>Checks all loans with installments due today.</li>
 *   <li>Attempts to deduct from client's account.</li>
 *   <li>If insufficient funds, marks as late and retries after 72h.</li>
 *   <li>On success, marks installment as paid and updates remaining debt.</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class LoanInstallmentScheduler {

    private final LoanInstallmentRepository installmentRepository;
    private final InstallmentProcessor processor;

    public LoanInstallmentScheduler(LoanInstallmentRepository installmentRepository,
                                    InstallmentProcessor processor) {
        this.installmentRepository = installmentRepository;
        this.processor = processor;
    }

    /**
     * Runs daily at 2:00 AM - processes all unpaid installments due today.
     * BE-PAY-02: no outer @Transactional; per-installment isolation via
     * {@link InstallmentProcessor}.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void processInstallments() {
        LocalDate today = LocalDate.now();
        log.info("Processing loan installments for date: {}", today);

        List<LoanInstallment> dueInstallments = installmentRepository
                .findByExpectedDueDateLessThanEqualAndPaidFalse(today);

        int processed = 0;
        int failed = 0;
        for (LoanInstallment installment : dueInstallments) {
            try {
                processor.processOne(installment, today);
                processed++;
            } catch (RuntimeException ex) {
                failed++;
                log.error("Failed to process installment id={} on loan {}: {}",
                        installment.getId(),
                        installment.getLoan() != null ? installment.getLoan().getLoanNumber() : "?",
                        ex.getMessage(), ex);
            }
        }

        log.info("Processed {} installments ({} succeeded, {} failed)",
                dueInstallments.size(), processed, failed);
    }
}
