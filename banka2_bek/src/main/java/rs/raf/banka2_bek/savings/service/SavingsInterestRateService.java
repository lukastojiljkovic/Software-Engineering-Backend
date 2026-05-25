package rs.raf.banka2_bek.savings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.savings.dto.SavingsRateDto;
import rs.raf.banka2_bek.savings.dto.UpsertSavingsRateDto;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsInterestRateRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsInterestRateService {

    private static final int UPSERT_MAX_RETRIES = 3;
    private static final long UPSERT_BACKOFF_BASE_MS = 30L;

    private final SavingsInterestRateRepository rateRepo;
    private final CurrencyRepository currencyRepo;
    private final SavingsMapper mapper;

    @Transactional(readOnly = true)
    public Optional<SavingsInterestRate> findActive(Long currencyId, Integer termMonths) {
        return rateRepo.findActive(currencyId, termMonths);
    }

    @Transactional(readOnly = true)
    public List<SavingsRateDto> listActive(String currencyCode) {
        List<SavingsInterestRate> rates = currencyCode != null && !currencyCode.isBlank()
                ? rateRepo.findActiveByCurrencyCode(currencyCode.toUpperCase())
                : rateRepo.findAllActive();
        return rates.stream().map(mapper::toRateDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SavingsRateDto> listAll() {
        return rateRepo.findAll().stream().map(mapper::toRateDto).toList();
    }

    /**
     * BE-PAY-04: Upsert sa retry-on-optimistic-lock.
     *
     * <p>Race scenario pre fix-a: dva concurrent admin POST-a -> oba nadju
     * isti active record -> oba ga deaktiviraju -> oba pokusaju insert novog
     * -> unique constraint violation (500 errror za jednog).</p>
     *
     * <p>Posle: {@link SavingsInterestRate#getVersion @Version} polje pravi
     * da drugi update na deaktivaciji baci
     * {@link ObjectOptimisticLockingFailureException}. Mi hvatamo i ponovo
     * pokusavamo (max {@value #UPSERT_MAX_RETRIES} puta sa exponential
     * backoff). U sledecem prolazu drugi pokusaj vidi vec deaktiviran record
     * pa ga ne dirne i samo insert-uje svoj.</p>
     */
    public SavingsRateDto upsert(UpsertSavingsRateDto dto) {
        long backoffMs = UPSERT_BACKOFF_BASE_MS;
        ObjectOptimisticLockingFailureException lastOptimistic = null;
        DataIntegrityViolationException lastUnique = null;
        for (int attempt = 1; attempt <= UPSERT_MAX_RETRIES; attempt++) {
            try {
                return upsertOnce(dto);
            } catch (ObjectOptimisticLockingFailureException ex) {
                lastOptimistic = ex;
                log.warn("BE-PAY-04: optimistic lock retry {}/{} for upsert(currency={}, term={})",
                        attempt, UPSERT_MAX_RETRIES, dto.getCurrencyCode(), dto.getTermMonths());
            } catch (DataIntegrityViolationException ex) {
                lastUnique = ex;
                log.warn("BE-PAY-04: unique constraint retry {}/{} for upsert(currency={}, term={})",
                        attempt, UPSERT_MAX_RETRIES, dto.getCurrencyCode(), dto.getTermMonths());
            }
            if (attempt < UPSERT_MAX_RETRIES) {
                sleepUninterruptibly(backoffMs);
                backoffMs *= 2;
            }
        }
        if (lastOptimistic != null) {
            throw lastOptimistic;
        }
        throw lastUnique != null ? lastUnique :
                new IllegalStateException("Upsert nije uspeo posle " + UPSERT_MAX_RETRIES + " pokusaja");
    }

    @Transactional
    protected SavingsRateDto upsertOnce(UpsertSavingsRateDto dto) {
        Currency currency = currencyRepo.findByCode(dto.getCurrencyCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Valuta ne postoji: " + dto.getCurrencyCode()));

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

    private static void sleepUninterruptibly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
