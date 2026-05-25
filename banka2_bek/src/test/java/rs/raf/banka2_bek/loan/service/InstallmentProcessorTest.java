package rs.raf.banka2_bek.loan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.model.LoanStatus;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.notification.NotificationPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BE-PAY-02: Unit testovi za per-installment processor.
 *
 * <p>Pokriva svu pravu logiku koja je ranije bila u
 * {@link LoanInstallmentScheduler#processInstallments()}: payment success,
 * insufficient funds reschedule, null principal fallback, status promene,
 * account/bank account lookup, email failure isolation.</p>
 */
@ExtendWith(MockitoExtension.class)
class InstallmentProcessorTest {

    private static final String BANK_REG_NUMBER = "1234567890";

    @Mock private LoanInstallmentRepository installmentRepository;
    @Mock private LoanRepository loanRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private NotificationPublisher notificationPublisher;

    private InstallmentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InstallmentProcessor(
                installmentRepository, loanRepository, accountRepository,
                notificationPublisher, BANK_REG_NUMBER);
    }

    private Currency buildCurrency() {
        Currency currency = new Currency();
        currency.setId(1L);
        currency.setCode("RSD");
        return currency;
    }

    private Client buildClient() {
        Client client = mock(Client.class);
        lenient().when(client.getEmail()).thenReturn("client@banka.rs");
        return client;
    }

    private Account buildAccount(Long id, BigDecimal balance) {
        Account account = new Account();
        account.setId(id);
        account.setBalance(balance);
        account.setAvailableBalance(balance);
        return account;
    }

    private Loan buildLoan(Long id, Account account, Client client, Currency currency,
                           BigDecimal remainingDebt) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setLoanNumber("LOAN-" + id);
        loan.setAccount(account);
        loan.setClient(client);
        loan.setCurrency(currency);
        loan.setRemainingDebt(remainingDebt);
        loan.setStatus(LoanStatus.ACTIVE);
        return loan;
    }

    private LoanInstallment buildInstallment(Long id, Loan loan, BigDecimal amount, LocalDate dueDate) {
        LoanInstallment inst = new LoanInstallment();
        inst.setId(id);
        inst.setLoan(loan);
        inst.setAmount(amount);
        inst.setPrincipalAmount(amount.multiply(new BigDecimal("0.80")));
        inst.setInterestAmount(amount.multiply(new BigDecimal("0.20")));
        inst.setExpectedDueDate(dueDate);
        inst.setPaid(false);
        return inst;
    }

    @Nested
    @DisplayName("processOne — sufficient funds path")
    class SufficientFunds {

        @Test
        @DisplayName("pays installment when account has sufficient funds")
        void paysInstallment() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(50000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());

            processor.processOne(installment, LocalDate.now());

            assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(45000));
            assertThat(account.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(45000));
            assertThat(installment.getPaid()).isTrue();
            assertThat(installment.getActualDueDate()).isEqualTo(LocalDate.now());
            verify(installmentRepository).save(installment);
            verify(loanRepository).save(loan);
        }

        @Test
        @DisplayName("credits bank account when found")
        void creditsBankAccount() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account clientAccount = buildAccount(1L, BigDecimal.valueOf(50000));
            Account bankAccount = buildAccount(2L, BigDecimal.valueOf(1000000));
            Loan loan = buildLoan(1L, clientAccount, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(clientAccount));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.of(bankAccount));

            processor.processOne(installment, LocalDate.now());

            assertThat(bankAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1005000));
            assertThat(bankAccount.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(1005000));
            verify(accountRepository).save(bankAccount);
        }

        @Test
        @DisplayName("sets loan status to PAID when remaining debt reaches zero")
        void setsStatusToPaidWhenDebtCleared() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(50000));
            BigDecimal installmentAmount = BigDecimal.valueOf(5000);
            BigDecimal principalAmount = installmentAmount.multiply(new BigDecimal("0.80"));
            Loan loan = buildLoan(1L, account, client, currency, principalAmount);
            LoanInstallment installment = buildInstallment(1L, loan, installmentAmount, LocalDate.now());

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());

            processor.processOne(installment, LocalDate.now());

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID);
            assertThat(loan.getRemainingDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("email failure does not rollback installment processing on successful payment")
        void emailFailureDoesNotRollbackPayment() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(50000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());
            doThrow(new RuntimeException("SMTP error"))
                    .when(notificationPublisher).sendInstallmentPaidMail(
                            anyString(), anyString(), any(), anyString(), any());

            processor.processOne(installment, LocalDate.now());

            assertThat(installment.getPaid()).isTrue();
            verify(installmentRepository).save(installment);
        }

        @Test
        @DisplayName("falls back to amount when principalAmount is null")
        void nullPrincipalFallsBackToAmount() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(50000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(20000));
            LoanInstallment installment = new LoanInstallment();
            installment.setId(1L);
            installment.setLoan(loan);
            installment.setAmount(BigDecimal.valueOf(5000));
            installment.setPrincipalAmount(null); // triggers fallback
            installment.setInterestAmount(null);  // triggers "N/A" log branch
            installment.setExpectedDueDate(LocalDate.now());
            installment.setPaid(false);

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            when(accountRepository.findBankAccountForUpdateByCurrency(any(), eq("RSD")))
                    .thenReturn(Optional.empty());

            processor.processOne(installment, LocalDate.now());

            assertThat(installment.getPaid()).isTrue();
            // remainingDebt reduced by amount (fallback) = 20000 - 5000 = 15000
            assertThat(loan.getRemainingDebt()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        }
    }

    @Nested
    @DisplayName("processOne — insufficient funds path")
    class InsufficientFunds {

        @Test
        @DisplayName("reschedules installment for 72h later when insufficient funds")
        void reschedulesWhenInsufficientFunds() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(1000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

            processor.processOne(installment, LocalDate.now());

            assertThat(installment.getPaid()).isFalse();
            assertThat(installment.getExpectedDueDate()).isEqualTo(LocalDate.now().plusDays(3));
            assertThat(loan.getStatus()).isEqualTo(LoanStatus.LATE);
            verify(installmentRepository).save(installment);
            verify(loanRepository).save(loan);
        }

        @Test
        @DisplayName("does not change loan status to LATE if already LATE")
        void doesNotChangeLateStatusAgain() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(100));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(50000));
            loan.setStatus(LoanStatus.LATE);
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

            processor.processOne(installment, LocalDate.now());

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.LATE);
            verify(loanRepository, never()).save(loan);
        }

        @Test
        @DisplayName("email failure does not rollback installment rescheduling")
        void emailFailureDoesNotRollbackRescheduling() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(1L, BigDecimal.valueOf(100));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));
            doThrow(new RuntimeException("SMTP error"))
                    .when(notificationPublisher).sendInstallmentFailedMail(
                            anyString(), anyString(), any(), anyString(), any());

            processor.processOne(installment, LocalDate.now());

            assertThat(installment.getExpectedDueDate()).isEqualTo(LocalDate.now().plusDays(3));
            verify(installmentRepository).save(installment);
        }
    }

    @Nested
    @DisplayName("processOne — error paths")
    class ErrorPaths {

        @Test
        @DisplayName("skips installment when account not found")
        void skipsWhenAccountNotFound() {
            Currency currency = buildCurrency();
            Client client = buildClient();
            Account account = buildAccount(99L, BigDecimal.valueOf(50000));
            Loan loan = buildLoan(1L, account, client, currency, BigDecimal.valueOf(100000));
            LoanInstallment installment = buildInstallment(1L, loan, BigDecimal.valueOf(5000),
                    LocalDate.now());

            when(accountRepository.findForUpdateById(99L)).thenReturn(Optional.empty());

            processor.processOne(installment, LocalDate.now());

            verify(installmentRepository, never()).save(any());
            verify(loanRepository, never()).save(any());
        }
    }
}
