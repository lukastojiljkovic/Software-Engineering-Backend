package rs.raf.banka2_bek.loan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.model.LoanStatus;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.notification.NotificationPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BE-PAY-02: Per-installment transaction processor.
 *
 * <p>Razlog izdvajanja: {@link LoanInstallmentScheduler} ranije je imao outer
 * {@code @Transactional} na {@code processInstallments()} — jedna lose
 * installment (npr. DB constraint violation, neocekivani SQL error) je
 * povlacila rollback svih prethodno uspesno obradjenih installments u istom
 * batch-u. To je suprotno SavingsScheduler obrascu i intent-u "process all
 * due installments, isolate failures".</p>
 *
 * <p>Resenje: ovaj bean ima {@link Propagation#REQUIRES_NEW} na
 * {@link #processOne(LoanInstallment, LocalDate)} — svaki installment dobija
 * svoju nezavisnu transakciju. Scheduler iterira + delegira, hvata
 * exception per-installment i nastavlja.</p>
 */
@Slf4j
@Component
public class InstallmentProcessor {

    private final LoanInstallmentRepository installmentRepository;
    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final NotificationPublisher notificationPublisher;
    private final String bankRegistrationNumber;

    public InstallmentProcessor(LoanInstallmentRepository installmentRepository,
                                LoanRepository loanRepository,
                                AccountRepository accountRepository,
                                NotificationPublisher notificationPublisher,
                                @Value("${bank.registration-number}") String bankRegistrationNumber) {
        this.installmentRepository = installmentRepository;
        this.loanRepository = loanRepository;
        this.accountRepository = accountRepository;
        this.notificationPublisher = notificationPublisher;
        this.bankRegistrationNumber = bankRegistrationNumber;
    }

    /**
     * BE-PAY-02: Procesira jednu installment u nezavisnoj transakciji.
     * Greska/rollback ne utice na druge installments u batch-u.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(LoanInstallment installment, LocalDate today) {
        Loan loan = installment.getLoan();
        Account account = accountRepository.findForUpdateById(loan.getAccount().getId())
                .orElse(null);

        if (account == null) {
            log.error("Account not found for loan {}", loan.getLoanNumber());
            return;
        }

        String currencyCode = loan.getCurrency().getCode();

        if (account.getAvailableBalance().compareTo(installment.getAmount()) >= 0) {
            // Deduct from client account
            account.setBalance(account.getBalance().subtract(installment.getAmount()));
            account.setAvailableBalance(account.getAvailableBalance().subtract(installment.getAmount()));
            accountRepository.save(account);

            // Credit to bank account (full installment = principal + interest as profit)
            Account bankAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, currencyCode)
                    .orElse(null);
            if (bankAccount != null) {
                bankAccount.setBalance(bankAccount.getBalance().add(installment.getAmount()));
                bankAccount.setAvailableBalance(bankAccount.getAvailableBalance().add(installment.getAmount()));
                accountRepository.save(bankAccount);
            }

            installment.setPaid(true);
            installment.setActualDueDate(today);
            installmentRepository.save(installment);

            // Update remaining debt (only principal portion reduces debt)
            BigDecimal principalPaid = installment.getPrincipalAmount() != null
                    ? installment.getPrincipalAmount() : installment.getAmount();
            loan.setRemainingDebt(loan.getRemainingDebt().subtract(principalPaid));
            if (loan.getRemainingDebt().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setRemainingDebt(BigDecimal.ZERO);
                loan.setStatus(LoanStatus.PAID);
            }
            loanRepository.save(loan);

            log.info("Installment {} paid for loan {} (interest profit: {})",
                    installment.getId(), loan.getLoanNumber(),
                    installment.getInterestAmount() != null ? installment.getInterestAmount() : "N/A");

            try {
                notificationPublisher.sendInstallmentPaidMail(
                        loan.getClient().getEmail(),
                        loan.getLoanNumber(),
                        installment.getAmount(),
                        currencyCode,
                        loan.getRemainingDebt());
            } catch (Exception e) {
                log.warn("Failed to send installment paid notification email", e);
            }
        } else {
            // Insufficient funds - reschedule for 72h later
            LocalDate nextRetryDate = today.plusDays(3);
            installment.setExpectedDueDate(nextRetryDate);
            installmentRepository.save(installment);

            if (loan.getStatus() == LoanStatus.ACTIVE) {
                loan.setStatus(LoanStatus.LATE);
                loanRepository.save(loan);
            }

            log.warn("Insufficient funds for installment {} on loan {}. Rescheduled to {}",
                    installment.getId(), loan.getLoanNumber(), nextRetryDate);

            try {
                notificationPublisher.sendInstallmentFailedMail(
                        loan.getClient().getEmail(),
                        loan.getLoanNumber(),
                        installment.getAmount(),
                        currencyCode,
                        nextRetryDate);
            } catch (Exception e) {
                log.warn("Failed to send installment failed notification email", e);
            }
        }
    }
}
