package rs.raf.banka2_bek.savings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.savings.dto.SavingsRateDto;
import rs.raf.banka2_bek.savings.dto.UpsertSavingsRateDto;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsInterestRateRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsInterestRateService {

    private final SavingsInterestRateRepository rateRepo;
    private final SavingsMapper mapper;
    /**
     * Externally-injected bean za upsert retry — vidi {@link SavingsInterestRateUpserter}.
     * Razdvojen jer @Retryable AOP proxy ne presreca self-invocation.
     */
    private final SavingsInterestRateUpserter upserter;

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
     * BE-PAY-04: Upsert sa retry-on-optimistic-lock (Spring Retry @Retryable).
     *
     * <p>Delegira na {@link SavingsInterestRateUpserter#upsertOnce(UpsertSavingsRateDto)}
     * koji ima declarative {@link org.springframework.retry.annotation.Retryable} —
     * Spring AOP proxy retry-uje na {@link ObjectOptimisticLockingFailureException}
     * i {@link org.springframework.dao.DataIntegrityViolationException} sa exponential
     * backoff (max 3 pokusaja, 30 -> 60 ms).
     *
     * <p>Pre: manual {@code for}-loop u istoj klasi sa {@code Thread.sleep}.
     * Refactor 26.05.2026 — vidi commit poruke i {@code OPEN_TASKS.md} P2.
     */
    public SavingsRateDto upsert(UpsertSavingsRateDto dto) {
        return upserter.upsertOnce(dto);
    }
}
