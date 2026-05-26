package rs.raf.banka2_bek.savings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.savings.dto.SavingsRateDto;
import rs.raf.banka2_bek.savings.dto.UpsertSavingsRateDto;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsInterestRateRepository;

import java.time.LocalDate;

/**
 * BE-PAY-04: Declarative retry-on-optimistic-lock i unique-constraint za savings
 * interest rate upsert.
 *
 * <p>Razdvojen u zaseban bean od {@link SavingsInterestRateService} jer
 * {@link Retryable} koristi Spring AOP proxy — self-invocation
 * ({@code this.upsertOnce(...)} iz iste klase) bi zaobisao proxy i retry nikad
 * ne bi proradio. Zato {@code SavingsInterestRateService} drzi referencu na ovaj
 * bean i poziva ga externally (kroz proxy) → retry advice se aktivira.
 *
 * <p>Race scenario: dva concurrent admin POST-a -> oba nadju isti active record
 * -> oba ga deaktiviraju -> oba pokusaju insert novog -> unique constraint
 * violation (500 za jednog). Posle @Version polja na {@link SavingsInterestRate}
 * drugi update na deaktivaciji baca {@link ObjectOptimisticLockingFailureException},
 * Spring Retry hvata i ponovo pokusava (max 3 puta, exponential backoff 30 -> 60).
 * U sledecem prolazu drugi pokusaj vidi vec deaktiviran record pa ga ne dirne i
 * samo insert-uje svoj.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsInterestRateUpserter {

    private final SavingsInterestRateRepository rateRepo;
    private final CurrencyRepository currencyRepo;
    private final SavingsMapper mapper;

    @Retryable(
            retryFor = { ObjectOptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 30, multiplier = 2.0)
    )
    @Transactional
    public SavingsRateDto upsertOnce(UpsertSavingsRateDto dto) {
        Currency currency = currencyRepo.findByCode(dto.getCurrencyCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Valuta ne postoji: " + dto.getCurrencyCode()));

        rateRepo.findActive(currency.getId(), dto.getTermMonths()).ifPresent(existing -> {
            existing.setActive(false);
            rateRepo.save(existing);
        });

        SavingsInterestRate newRate = SavingsInterestRate.builder()
                .currency(currency)
                .termMonths(dto.getTermMonths())
                .annualRate(dto.getAnnualRate())
                .active(true)
                .effectiveFrom(LocalDate.now())
                .build();
        return mapper.toRateDto(rateRepo.save(newRate));
    }
}
