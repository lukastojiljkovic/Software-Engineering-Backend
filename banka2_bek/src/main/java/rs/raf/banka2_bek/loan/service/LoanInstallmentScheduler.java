package rs.raf.banka2_bek.loan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.model.LoanStatus;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanInstallmentScheduler {

    private final LoanInstallmentRepository installmentRepository;
    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;

    /**
     * Runs daily at 2:00 AM - processes all unpaid installments due today.
     * Per spec (Celina 2 - Automatsko skidanje rata):
     * 1. Checks all loans with installments due today
     * 2. Attempts to deduct from client's account
     * 3. If insufficient funds, marks as late and retries after 72h
     * 4. On success, marks installment as paid and updates remaining debt
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void processInstallments() {
        LocalDate today = LocalDate.now();
        log.info("Processing loan installments for date: {}", today);

        List<LoanInstallment> dueInstallments = installmentRepository
                .findByExpectedDueDateLessThanEqualAndPaidFalse(today);

        for (LoanInstallment installment : dueInstallments) {
            processInstallment(installment, today);
        }

        log.info("Processed {} installments", dueInstallments.size());
    }

    private void processInstallment(LoanInstallment installment, LocalDate today) {
        Loan loan = installment.getLoan();
        Account account = loan.getAccount();

        if (account.getAvailableBalance().compareTo(installment.getAmount()) >= 0) {
            // Sufficient funds - deduct and mark as paid
            account.setBalance(account.getBalance().subtract(installment.getAmount()));
            account.setAvailableBalance(account.getAvailableBalance().subtract(installment.getAmount()));
            accountRepository.save(account);

            installment.setPaid(true);
            installment.setActualDueDate(today);
            installmentRepository.save(installment);

            // Update remaining debt on loan
            loan.setRemainingDebt(loan.getRemainingDebt().subtract(installment.getAmount()));
            if (loan.getRemainingDebt().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setRemainingDebt(BigDecimal.ZERO);
                loan.setStatus(LoanStatus.PAID);
            }
            loanRepository.save(loan);

            log.info("Installment {} paid for loan {}", installment.getId(), loan.getLoanNumber());
        } else {
            // Insufficient funds - reschedule for 72h later
            installment.setExpectedDueDate(today.plusDays(3));
            installmentRepository.save(installment);

            // Mark loan as late if not already
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                loan.setStatus(LoanStatus.LATE);
                loanRepository.save(loan);
            }

            log.warn("Insufficient funds for installment {} on loan {}. Rescheduled to {}",
                    installment.getId(), loan.getLoanNumber(), today.plusDays(3));
        }
    }
}
