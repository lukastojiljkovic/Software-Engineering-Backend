package rs.raf.banka2_bek.loan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BE-PAY-02: Scheduler-level tests. Logika obrade jedne installment je
 * preseljena u {@link InstallmentProcessor} pa scheduler radi samo iteraciju
 * + delegaciju + per-installment exception isolation.
 */
@ExtendWith(MockitoExtension.class)
class LoanInstallmentSchedulerTest {

    @Mock
    private LoanInstallmentRepository installmentRepository;

    @Mock
    private InstallmentProcessor processor;

    private LoanInstallmentScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LoanInstallmentScheduler(installmentRepository, processor);
    }

    private LoanInstallment buildInstallment(Long id) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setLoanNumber("LOAN-" + id);

        LoanInstallment inst = new LoanInstallment();
        inst.setId(id);
        inst.setLoan(loan);
        inst.setAmount(BigDecimal.valueOf(5000));
        inst.setExpectedDueDate(LocalDate.now());
        inst.setPaid(false);
        return inst;
    }

    @Nested
    @DisplayName("processInstallments")
    class ProcessInstallments {

        @Test
        @DisplayName("does nothing when no installments are due")
        void doesNothingWhenNoDueInstallments() {
            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(Collections.emptyList());

            scheduler.processInstallments();

            verifyNoInteractions(processor);
        }

        @Test
        @DisplayName("delegates each installment to processor")
        void delegatesEachInstallmentToProcessor() {
            LoanInstallment i1 = buildInstallment(1L);
            LoanInstallment i2 = buildInstallment(2L);
            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(i1, i2));

            scheduler.processInstallments();

            verify(processor).processOne(eq(i1), any(LocalDate.class));
            verify(processor).processOne(eq(i2), any(LocalDate.class));
            verifyNoMoreInteractions(processor);
        }

        @Test
        @DisplayName("BE-PAY-02: one failing installment does NOT block subsequent installments")
        void oneFailingInstallmentDoesNotBlockOthers() {
            LoanInstallment bad = buildInstallment(1L);
            LoanInstallment good1 = buildInstallment(2L);
            LoanInstallment good2 = buildInstallment(3L);

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(bad, good1, good2));
            doThrow(new RuntimeException("DB constraint violated"))
                    .when(processor).processOne(eq(bad), any(LocalDate.class));

            scheduler.processInstallments();

            verify(processor).processOne(eq(bad), any(LocalDate.class));
            verify(processor).processOne(eq(good1), any(LocalDate.class));
            verify(processor).processOne(eq(good2), any(LocalDate.class));
        }

        @Test
        @DisplayName("processes multiple installments in one run")
        void processesMultipleInstallments() {
            LoanInstallment inst1 = buildInstallment(1L);
            LoanInstallment inst2 = buildInstallment(2L);

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(inst1, inst2));

            scheduler.processInstallments();

            verify(processor, times(2)).processOne(any(LoanInstallment.class), any(LocalDate.class));
        }

        @Test
        @DisplayName("passes correct today() date to processor")
        void passesTodayDateToProcessor() {
            LoanInstallment inst = buildInstallment(1L);
            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(inst));

            scheduler.processInstallments();

            verify(processor).processOne(eq(inst), eq(LocalDate.now()));
        }

        @Test
        @DisplayName("uses today() when querying for due installments")
        void queriesByToday() {
            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(Collections.emptyList());

            scheduler.processInstallments();

            verify(installmentRepository).findByExpectedDueDateLessThanEqualAndPaidFalse(LocalDate.now());
        }

        @Test
        @DisplayName("all installments fail — scheduler completes without throwing")
        void allInstallmentsFailButSchedulerCompletes() {
            LoanInstallment i1 = buildInstallment(1L);
            LoanInstallment i2 = buildInstallment(2L);

            when(installmentRepository.findByExpectedDueDateLessThanEqualAndPaidFalse(any()))
                    .thenReturn(List.of(i1, i2));
            doThrow(new RuntimeException("DB error"))
                    .when(processor).processOne(any(LoanInstallment.class), any(LocalDate.class));

            scheduler.processInstallments();

            verify(processor).processOne(eq(i1), any(LocalDate.class));
            verify(processor).processOne(eq(i2), any(LocalDate.class));
        }
    }
}
