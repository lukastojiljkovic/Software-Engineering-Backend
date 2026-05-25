package rs.raf.banka2_bek.loan.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import rs.raf.banka2_bek.loan.model.*;
import rs.raf.banka2_bek.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BE-PAY-03: Scheduler-level tests. Logika obrade jedne loan-a je preseljena
 * u {@link VariableRateProcessor} pa scheduler radi samo iteraciju +
 * delegaciju + per-loan exception isolation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VariableRateSchedulerTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private VariableRateProcessor processor;

    @InjectMocks
    private VariableRateScheduler scheduler;

    private Loan buildVariableLoan(Long id, String loanNumber, LoanType loanType,
                                   BigDecimal nominalRate, BigDecimal effectiveRate,
                                   BigDecimal monthlyPayment, BigDecimal remainingDebt) {
        Loan loan = new Loan();
        loan.setId(id);
        loan.setLoanNumber(loanNumber);
        loan.setLoanType(loanType);
        loan.setInterestType(InterestType.VARIABLE);
        loan.setNominalRate(nominalRate);
        loan.setEffectiveRate(effectiveRate);
        loan.setMonthlyPayment(monthlyPayment);
        loan.setRemainingDebt(remainingDebt);
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setAmount(BigDecimal.valueOf(100000));
        loan.setRepaymentPeriod(60);
        loan.setStartDate(LocalDate.now().minusMonths(6));
        loan.setEndDate(LocalDate.now().plusMonths(54));
        return loan;
    }

    @Nested
    @DisplayName("adjustVariableRates")
    class AdjustVariableRates {

        @Test
        @DisplayName("does nothing when no variable-rate loans exist")
        void doesNothingWhenNoVariableLoans() {
            when(loanRepository.findByInterestTypeAndStatusIn(any(), any())).thenReturn(Collections.emptyList());

            scheduler.adjustVariableRates();

            verifyNoInteractions(processor);
        }

        @Test
        @DisplayName("delegates each variable loan to processor with same offset")
        void delegatesEachLoan() {
            Loan loan1 = buildVariableLoan(1L, "VAR-001", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(80000));
            Loan loan2 = buildVariableLoan(2L, "VAR-002", LoanType.AUTO,
                    new BigDecimal("3.00"), new BigDecimal("4.25"),
                    new BigDecimal("1500.0000"), BigDecimal.valueOf(60000));

            when(loanRepository.findByInterestTypeAndStatusIn(any(), any())).thenReturn(List.of(loan1, loan2));

            scheduler.adjustVariableRates();

            ArgumentCaptor<BigDecimal> offsetCaptor = ArgumentCaptor.forClass(BigDecimal.class);
            verify(processor, times(2)).adjustOne(any(Loan.class), offsetCaptor.capture());

            List<BigDecimal> offsets = offsetCaptor.getAllValues();
            assertThat(offsets).hasSize(2);
            assertThat(offsets.get(0)).isEqualByComparingTo(offsets.get(1));
            assertThat(offsets.get(0)).isBetween(new BigDecimal("-1.50"), new BigDecimal("1.50"));
        }

        @Test
        @DisplayName("BE-PAY-03: one failing loan does NOT block subsequent loans")
        void oneFailingLoanDoesNotBlockOthers() {
            Loan bad = buildVariableLoan(1L, "VAR-BAD", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(80000));
            Loan good = buildVariableLoan(2L, "VAR-GOOD", LoanType.AUTO,
                    new BigDecimal("3.00"), new BigDecimal("4.25"),
                    new BigDecimal("1500.0000"), BigDecimal.valueOf(60000));

            when(loanRepository.findByInterestTypeAndStatusIn(any(), any())).thenReturn(List.of(bad, good));
            doThrow(new RuntimeException("DB error")).when(processor).adjustOne(eq(bad), any());

            scheduler.adjustVariableRates();

            verify(processor).adjustOne(eq(bad), any());
            verify(processor).adjustOne(eq(good), any());
        }

        @Test
        @DisplayName("processes single active variable-rate loan")
        void processesSingleActiveLoan() {
            Loan loan = buildVariableLoan(1L, "VAR-001", LoanType.CASH,
                    new BigDecimal("4.00"), new BigDecimal("5.75"),
                    new BigDecimal("2000.0000"), BigDecimal.valueOf(80000));

            when(loanRepository.findByInterestTypeAndStatusIn(any(), any())).thenReturn(List.of(loan));

            scheduler.adjustVariableRates();

            verify(processor).adjustOne(eq(loan), any(BigDecimal.class));
        }
    }
}
