package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.savings.dto.SavingsRateDto;
import rs.raf.banka2_bek.savings.dto.UpsertSavingsRateDto;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsInterestRateRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-PAY-04 (refactor 26.05.2026 → Spring Retry): verifikuje da
 * {@link SavingsInterestRateUpserter#upsertOnce(UpsertSavingsRateDto)} pravilno
 * retry-uje na {@link ObjectOptimisticLockingFailureException} i
 * {@link DataIntegrityViolationException} kroz Spring AOP @Retryable proxy.
 *
 * <p>Koristi minimalni Spring kontekst sa @EnableRetry da bi AOP advice bio aktivan
 * (cisti Mockito unit test bi zaobisao proxy i retry se ne bi dogodio).
 *
 * <p>Mockovi su registrovani kao @Bean u test konfiguraciji (Spring Boot 4 vise
 * ne podrzava @MockBean — modernije je rucno wired Mockito mocks).
 */
@SpringJUnitConfig
@ContextConfiguration(classes = SavingsInterestRateUpserterTest.RetryTestConfig.class)
class SavingsInterestRateUpserterTest {

    @EnableRetry
    @Configuration
    static class RetryTestConfig {

        @Bean
        SavingsInterestRateRepository rateRepo() {
            return Mockito.mock(SavingsInterestRateRepository.class);
        }

        @Bean
        CurrencyRepository currencyRepo() {
            return Mockito.mock(CurrencyRepository.class);
        }

        @Bean
        SavingsMapper savingsMapper() {
            return Mockito.mock(SavingsMapper.class);
        }

        @Bean
        SavingsInterestRateUpserter upserter(SavingsInterestRateRepository rateRepo,
                                             CurrencyRepository currencyRepo,
                                             SavingsMapper mapper) {
            return new SavingsInterestRateUpserter(rateRepo, currencyRepo, mapper);
        }
    }

    @Autowired SavingsInterestRateRepository rateRepo;
    @Autowired CurrencyRepository currencyRepo;
    @Autowired SavingsMapper mapper;
    @Autowired SavingsInterestRateUpserter upserter;

    @AfterEach
    void resetMocks() {
        Mockito.reset(rateRepo, currencyRepo, mapper);
    }

    private Currency rsd() {
        Currency c = new Currency();
        c.setId(1L);
        c.setCode("RSD");
        return c;
    }

    private UpsertSavingsRateDto dto(BigDecimal rate) {
        UpsertSavingsRateDto d = new UpsertSavingsRateDto();
        d.setCurrencyCode("RSD");
        d.setTermMonths(12);
        d.setAnnualRate(rate);
        return d;
    }

    @Test
    void upsertOnce_retriesOnOptimisticLock_succeedsOnThirdAttempt() {
        Currency c = rsd();
        UpsertSavingsRateDto request = dto(new BigDecimal("5.00"));

        when(currencyRepo.findByCode("RSD")).thenReturn(Optional.of(c));
        when(rateRepo.findActive(1L, 12)).thenReturn(Optional.empty());
        // Prva 2 save-a baca OptimisticLockingFailure, treci uspeva.
        when(rateRepo.save(any(SavingsInterestRate.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(SavingsInterestRate.class, 0L))
                .thenThrow(new ObjectOptimisticLockingFailureException(SavingsInterestRate.class, 0L))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toRateDto(any(SavingsInterestRate.class))).thenAnswer(inv -> {
            SavingsInterestRate r = inv.getArgument(0);
            return SavingsRateDto.builder().annualRate(r.getAnnualRate()).build();
        });

        SavingsRateDto result = upserter.upsertOnce(request);

        assertThat(result.getAnnualRate()).isEqualByComparingTo("5.00");
        // 3 save poziva (2 fail + 1 success) → @Retryable se aktivirao.
        verify(rateRepo, times(3)).save(any(SavingsInterestRate.class));
    }

    @Test
    void upsertOnce_retriesOnUniqueConstraint_succeedsOnSecondAttempt() {
        Currency c = rsd();
        UpsertSavingsRateDto request = dto(new BigDecimal("4.25"));

        when(currencyRepo.findByCode("RSD")).thenReturn(Optional.of(c));
        when(rateRepo.findActive(1L, 12)).thenReturn(Optional.empty());
        when(rateRepo.save(any(SavingsInterestRate.class)))
                .thenThrow(new DataIntegrityViolationException("uk_savings_rates_currency_term_active"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toRateDto(any(SavingsInterestRate.class))).thenAnswer(inv -> {
            SavingsInterestRate r = inv.getArgument(0);
            return SavingsRateDto.builder().annualRate(r.getAnnualRate()).build();
        });

        SavingsRateDto result = upserter.upsertOnce(request);

        assertThat(result.getAnnualRate()).isEqualByComparingTo("4.25");
        verify(rateRepo, times(2)).save(any(SavingsInterestRate.class));
    }

    @Test
    void upsertOnce_exhaustsRetries_propagatesException() {
        Currency c = rsd();
        UpsertSavingsRateDto request = dto(new BigDecimal("5.00"));

        when(currencyRepo.findByCode("RSD")).thenReturn(Optional.of(c));
        when(rateRepo.findActive(1L, 12)).thenReturn(Optional.empty());
        when(rateRepo.save(any(SavingsInterestRate.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(SavingsInterestRate.class, 0L));

        assertThatThrownBy(() -> upserter.upsertOnce(request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // maxAttempts = 3 → tacno 3 save poziva, pa exception propaguje.
        verify(rateRepo, times(3)).save(any(SavingsInterestRate.class));
    }

    @Test
    void upsertOnce_currencyNotFound_throwsImmediatelyWithoutRetry() {
        UpsertSavingsRateDto request = dto(new BigDecimal("4.00"));
        request.setCurrencyCode("XYZ");
        when(currencyRepo.findByCode("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> upserter.upsertOnce(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valuta ne postoji");

        // IllegalArgumentException NIJE u retryFor liste → nema retry-a, nema save-a.
        verify(rateRepo, times(0)).save(any(SavingsInterestRate.class));
    }
}
