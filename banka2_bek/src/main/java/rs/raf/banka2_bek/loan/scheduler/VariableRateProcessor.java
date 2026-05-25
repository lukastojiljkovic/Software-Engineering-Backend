package rs.raf.banka2_bek.loan.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.loan.model.Loan;
import rs.raf.banka2_bek.loan.model.LoanInstallment;
import rs.raf.banka2_bek.loan.model.LoanType;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * BE-PAY-03: Per-loan transaction processor za simulaciju promenljive
 * kamatne stope.
 *
 * <p>Razlog: {@link VariableRateScheduler#adjustVariableRates()} ranije je
 * imao outer {@code @Transactional} pa je jedna loona obrada (npr.
 * unexpected SQL error mid-loop) povlacila rollback svih ostalih loans-a u
 * batch-u. Sa per-loan {@code REQUIRES_NEW} izolacijom, scheduler iterira i
 * delegira; svaka greska se lokalizuje.</p>
 */
@Slf4j
@Component
public class VariableRateProcessor {

    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;

    public VariableRateProcessor(LoanRepository loanRepository,
                                 LoanInstallmentRepository installmentRepository) {
        this.loanRepository = loanRepository;
        this.installmentRepository = installmentRepository;
    }

    /**
     * BE-PAY-03: Adjust-uje jedan loan u nezavisnoj transakciji.
     * Recalculate-uje effective rate + monthly payment + sve neplacene rate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void adjustOne(Loan loan, BigDecimal offset) {
        BigDecimal oldEffectiveRate = loan.getEffectiveRate();
        BigDecimal oldMonthlyPayment = loan.getMonthlyPayment();

        // effectiveRate = nominalRate + offset + margin(loanType)
        BigDecimal margin = getMargin(loan.getLoanType());
        BigDecimal newEffectiveRate = loan.getNominalRate().add(offset).add(margin);

        // Ensure effective rate doesn't go below 0.50% (safety floor)
        if (newEffectiveRate.compareTo(new BigDecimal("0.50")) < 0) {
            newEffectiveRate = new BigDecimal("0.50");
        }

        // Count remaining (unpaid) installments
        List<LoanInstallment> unpaidInstallments = installmentRepository
                .findByLoanIdOrderByExpectedDueDateAsc(loan.getId())
                .stream()
                .filter(i -> !Boolean.TRUE.equals(i.getPaid()))
                .toList();

        int remainingMonths = unpaidInstallments.size();
        if (remainingMonths == 0) {
            log.info("Loan {} has no unpaid installments, skipping", loan.getLoanNumber());
            return;
        }

        // Recalculate monthly payment: A = P * r * (1+r)^n / ((1+r)^n - 1)
        // where P = remainingDebt, r = monthly rate, n = remaining months.
        // Safety floor (>= 0.50%) above guarantees monthlyRate > 0, so no zero-rate branch.
        BigDecimal monthlyRate = newEffectiveRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRn = onePlusR.pow(remainingMonths, MathContext.DECIMAL128);
        BigDecimal newMonthlyPayment = loan.getRemainingDebt()
                .multiply(monthlyRate)
                .multiply(onePlusRn)
                .divide(onePlusRn.subtract(BigDecimal.ONE), 4, RoundingMode.HALF_UP);

        // Update loan entity
        loan.setEffectiveRate(newEffectiveRate);
        loan.setMonthlyPayment(newMonthlyPayment);
        loanRepository.save(loan);

        // Recalculate and update unpaid installments with new principal/interest breakdown
        BigDecimal remainingPrincipal = loan.getRemainingDebt();
        for (int i = 0; i < unpaidInstallments.size(); i++) {
            LoanInstallment installment = unpaidInstallments.get(i);
            BigDecimal interestPortion = remainingPrincipal.multiply(monthlyRate).setScale(4, RoundingMode.HALF_UP);
            BigDecimal principalPortion = newMonthlyPayment.subtract(interestPortion);

            // Last installment covers the remaining principal exactly
            if (i == unpaidInstallments.size() - 1) {
                principalPortion = remainingPrincipal;
                interestPortion = newMonthlyPayment.subtract(principalPortion).max(BigDecimal.ZERO);
            }

            remainingPrincipal = remainingPrincipal.subtract(principalPortion);

            installment.setAmount(newMonthlyPayment);
            installment.setInterestRate(newEffectiveRate);
            installment.setInterestAmount(interestPortion);
            installment.setPrincipalAmount(principalPortion);
            installmentRepository.save(installment);
        }

        log.info("Loan {} adjusted: effectiveRate {}% -> {}%, monthlyPayment {} -> {}, remaining installments: {}",
                loan.getLoanNumber(),
                oldEffectiveRate, newEffectiveRate,
                oldMonthlyPayment, newMonthlyPayment,
                remainingMonths);
    }

    /**
     * Bank margin per loan type - mirrors LoanServiceImpl.getMargin().
     */
    private BigDecimal getMargin(LoanType type) {
        return switch (type) {
            case CASH -> new BigDecimal("1.75");
            case MORTGAGE -> new BigDecimal("1.50");
            case AUTO -> new BigDecimal("1.25");
            case REFINANCING -> new BigDecimal("1.00");
            case STUDENT -> new BigDecimal("0.75");
        };
    }
}
