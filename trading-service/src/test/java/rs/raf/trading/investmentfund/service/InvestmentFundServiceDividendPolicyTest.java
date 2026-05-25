package rs.raf.trading.investmentfund.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.trading.actuary.repository.ActuaryInfoRepository;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.common.UserRole;
import rs.raf.trading.investmentfund.dto.InvestmentFundDtos.InvestmentFundDetailDto;
import rs.raf.trading.investmentfund.model.InvestmentFund;
import rs.raf.trading.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.trading.investmentfund.repository.ClientFundTransactionRepository;
import rs.raf.trading.investmentfund.repository.FundValueSnapshotRepository;
import rs.raf.trading.investmentfund.repository.InvestmentFundRepository;
import rs.raf.trading.investmentfund.scheduler.FundValueSnapshotScheduler;
import rs.raf.trading.order.service.CurrencyConversionService;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TODO_final C4 #14 / Sc 70: testovi za politiku obrade dividendi
 * ({@link InvestmentFundService#updateDividendPolicy}).
 *
 * <p>Pokriva:
 * <ul>
 *   <li>admin moze za bilo koji fond (override fund manager check),</li>
 *   <li>supervizor moze samo za fond koji on menadzira,</li>
 *   <li>non-manager supervizor dobija {@link AccessDeniedException},</li>
 *   <li>nepostojeci fund → {@link EntityNotFoundException},</li>
 *   <li>null reinvest flag → {@link IllegalArgumentException}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InvestmentFundService — updateDividendPolicy (TODO_final C4 #14 / Sc 70)")
class InvestmentFundServiceDividendPolicyTest {

    @Mock private FundValueSnapshotRepository fundValueSnapshotRepository;
    @Mock private InvestmentFundRepository investmentFundRepository;
    @Mock private ClientFundPositionRepository clientFundPositionRepository;
    @Mock private ClientFundTransactionRepository clientFundTransactionRepository;
    @Mock private BankaCoreClient bankaCoreClient;
    @Mock private TradingUserResolver tradingUserResolver;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private FundValueCalculator fundValueCalculator;
    @Mock private FundLiquidationService fundLiquidationService;
    @Mock private CurrencyConversionService currencyConversionService;
    @Mock private FundValueSnapshotScheduler fundValueSnapshotScheduler;

    @InjectMocks
    private InvestmentFundService service;

    private InvestmentFund fund;

    @BeforeEach
    void setUp() {
        fund = new InvestmentFund();
        fund.setId(101L);
        fund.setName("Alpha Growth");
        fund.setManagerEmployeeId(10L);
        fund.setAccountId(5001L);
        fund.setReinvestDividends(false);
    }

    private InternalAccountDto fundAccount() {
        return new InternalAccountDto(5001L, "222000100000050010", "Banka 2 d.o.o.",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "RSD", "ACTIVE", null, null, "FUND");
    }

    private void stubDetailsCalls() {
        when(bankaCoreClient.getAccount(5001L)).thenReturn(fundAccount());
        when(portfolioRepository.findByUserIdAndUserRole(101L, UserRole.FUND))
                .thenReturn(Collections.emptyList());
        when(fundValueCalculator.computeFundValue(any())).thenReturn(BigDecimal.ZERO);
        when(fundValueCalculator.computeProfit(any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("admin moze prebaciti bilo koji fond na reinvest=true")
    void updateDividendPolicy_adminCanToggleAnyFund() {
        when(investmentFundRepository.findById(101L)).thenReturn(Optional.of(fund));
        stubDetailsCalls();

        InvestmentFundDetailDto result = service.updateDividendPolicy(101L, true, 999L, true);

        ArgumentCaptor<InvestmentFund> savedCaptor = ArgumentCaptor.forClass(InvestmentFund.class);
        verify(investmentFundRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getReinvestDividends()).isTrue();
        assertThat(result).isNotNull();
        assertThat(result.getReinvestDividends()).isTrue();
    }

    @Test
    @DisplayName("supervisor-manager moze prebaciti svoj fond")
    void updateDividendPolicy_supervisorOwnerCanToggle() {
        when(investmentFundRepository.findById(101L)).thenReturn(Optional.of(fund));
        stubDetailsCalls();

        InvestmentFundDetailDto result = service.updateDividendPolicy(101L, true, 10L, false);

        ArgumentCaptor<InvestmentFund> savedCaptor = ArgumentCaptor.forClass(InvestmentFund.class);
        verify(investmentFundRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getReinvestDividends()).isTrue();
        assertThat(result.getReinvestDividends()).isTrue();
    }

    @Test
    @DisplayName("non-manager supervizor dobija AccessDeniedException")
    void updateDividendPolicy_nonManagerSupervisor_throwsAccessDenied() {
        when(investmentFundRepository.findById(101L)).thenReturn(Optional.of(fund));

        assertThatThrownBy(() -> service.updateDividendPolicy(101L, true, 22L, false))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("politiku dividendi");

        verify(investmentFundRepository, never()).save(any());
    }

    @Test
    @DisplayName("nepostojeci fund -> EntityNotFoundException")
    void updateDividendPolicy_fundMissing_throws() {
        when(investmentFundRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDividendPolicy(999L, true, 10L, true))
                .isInstanceOf(EntityNotFoundException.class);

        verify(investmentFundRepository, never()).save(any());
    }

    @Test
    @DisplayName("null fund id -> IllegalArgumentException")
    void updateDividendPolicy_nullFundId_throws() {
        assertThatThrownBy(() -> service.updateDividendPolicy(null, true, 10L, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null reinvest flag -> IllegalArgumentException")
    void updateDividendPolicy_nullReinvest_throws() {
        assertThatThrownBy(() -> service.updateDividendPolicy(101L, null, 10L, true))
                .isInstanceOf(IllegalArgumentException.class);

        verify(investmentFundRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggle false -> true menja flag")
    void updateDividendPolicy_togglesFromFalseToTrue() {
        fund.setReinvestDividends(false);
        when(investmentFundRepository.findById(101L)).thenReturn(Optional.of(fund));
        stubDetailsCalls();

        InvestmentFundDetailDto result = service.updateDividendPolicy(101L, true, 10L, false);

        assertThat(fund.getReinvestDividends()).isTrue();
        assertThat(result.getReinvestDividends()).isTrue();
    }

    @Test
    @DisplayName("toggle true -> false menja flag nazad na distribute")
    void updateDividendPolicy_togglesFromTrueToFalse() {
        fund.setReinvestDividends(true);
        when(investmentFundRepository.findById(101L)).thenReturn(Optional.of(fund));
        stubDetailsCalls();

        InvestmentFundDetailDto result = service.updateDividendPolicy(101L, false, 10L, false);

        assertThat(fund.getReinvestDividends()).isFalse();
        assertThat(result.getReinvestDividends()).isFalse();
    }
}
