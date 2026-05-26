package rs.raf.banka2_bek.savings.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.savings.dto.SavingsRateDto;
import rs.raf.banka2_bek.savings.dto.UpsertSavingsRateDto;
import rs.raf.banka2_bek.savings.entity.SavingsInterestRate;
import rs.raf.banka2_bek.savings.mapper.SavingsMapper;
import rs.raf.banka2_bek.savings.repository.SavingsInterestRateRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavingsInterestRateServiceTest {

    @Mock SavingsInterestRateRepository rateRepo;
    @Mock SavingsMapper mapper;
    @Mock SavingsInterestRateUpserter upserter;

    private SavingsInterestRateService service;

    @BeforeEach
    void setUp() {
        service = new SavingsInterestRateService(rateRepo, mapper, upserter);
    }

    private Currency rsd() {
        Currency c = new Currency();
        c.setId(1L);
        c.setCode("RSD");
        return c;
    }

    @Test
    void findActive_returnsRate() {
        SavingsInterestRate rate = SavingsInterestRate.builder()
                .id(1L).currency(rsd()).termMonths(12).annualRate(new BigDecimal("4.00"))
                .active(true).effectiveFrom(LocalDate.now()).build();
        when(rateRepo.findActive(1L, 12)).thenReturn(Optional.of(rate));

        Optional<SavingsInterestRate> result = service.findActive(1L, 12);
        assertThat(result).isPresent();
        assertThat(result.get().getAnnualRate()).isEqualByComparingTo("4.00");
    }

    @Test
    void findActive_empty() {
        when(rateRepo.findActive(99L, 7)).thenReturn(Optional.empty());
        assertThat(service.findActive(99L, 7)).isEmpty();
    }

    @Test
    void listActive_byCurrencyCode_filters() {
        SavingsInterestRate r = SavingsInterestRate.builder()
                .id(1L).currency(rsd()).termMonths(12).annualRate(new BigDecimal("4.00"))
                .active(true).effectiveFrom(LocalDate.now()).build();
        SavingsRateDto dto = SavingsRateDto.builder().id(1L).currencyCode("RSD").termMonths(12).build();
        when(rateRepo.findActiveByCurrencyCode("RSD")).thenReturn(List.of(r));
        when(mapper.toRateDto(r)).thenReturn(dto);

        List<SavingsRateDto> result = service.listActive("RSD");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrencyCode()).isEqualTo("RSD");
    }

    @Test
    void listActive_noCurrency_returnsAll() {
        when(rateRepo.findAllActive()).thenReturn(List.of());
        assertThat(service.listActive(null)).isEmpty();
    }

    @Test
    void listAll_delegatesToRepo() {
        when(rateRepo.findAll()).thenReturn(List.of());
        assertThat(service.listAll()).isEmpty();
        verify(rateRepo).findAll();
    }

    /**
     * BE-PAY-04 (refactor 26.05.2026): upsert sada delegira na
     * {@link SavingsInterestRateUpserter#upsertOnce(UpsertSavingsRateDto)} koji
     * ima @Retryable. Service samo prosljedjuje arg i return value — sva
     * retry-on-optimistic-lock logika je u upserter beanu (testovi u
     * {@link SavingsInterestRateUpserterTest}).
     */
    @Test
    void upsert_delegatesToUpserter() {
        UpsertSavingsRateDto dto = new UpsertSavingsRateDto();
        dto.setCurrencyCode("RSD");
        dto.setTermMonths(12);
        dto.setAnnualRate(new BigDecimal("4.25"));
        SavingsRateDto expected = SavingsRateDto.builder()
                .id(1L).currencyCode("RSD").termMonths(12).annualRate(new BigDecimal("4.25")).build();
        when(upserter.upsertOnce(dto)).thenReturn(expected);

        SavingsRateDto result = service.upsert(dto);

        assertThat(result).isEqualTo(expected);
        verify(upserter).upsertOnce(dto);
    }

    @Test
    void upsert_propagatesUpserterException() {
        UpsertSavingsRateDto dto = new UpsertSavingsRateDto();
        dto.setCurrencyCode("XYZ");
        dto.setTermMonths(12);
        dto.setAnnualRate(new BigDecimal("4.00"));
        when(upserter.upsertOnce(dto)).thenThrow(new IllegalArgumentException("Valuta ne postoji: XYZ"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.upsert(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valuta ne postoji");
    }

    @Test
    void upsert_invokesUpserterOnlyOnce() {
        UpsertSavingsRateDto dto = new UpsertSavingsRateDto();
        dto.setCurrencyCode("RSD");
        dto.setTermMonths(6);
        dto.setAnnualRate(new BigDecimal("3.00"));
        SavingsRateDto resp = SavingsRateDto.builder().annualRate(new BigDecimal("3.00")).build();
        when(upserter.upsertOnce(any(UpsertSavingsRateDto.class))).thenReturn(resp);

        service.upsert(dto);

        // Service sloj ne pravi vlastiti retry loop — vidi upserter za @Retryable.
        verify(upserter, org.mockito.Mockito.times(1)).upsertOnce(any(UpsertSavingsRateDto.class));
    }
}
