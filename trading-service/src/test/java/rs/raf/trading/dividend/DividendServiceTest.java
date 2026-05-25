package rs.raf.trading.dividend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2.contracts.internal.CreditFundsRequest;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.common.UserContext;
import rs.raf.trading.dividend.dto.DividendPayoutDto;
import rs.raf.trading.dividend.model.DividendPayout;
import rs.raf.trading.dividend.repository.DividendPayoutRepository;
import rs.raf.trading.dividend.service.DividendService;
import rs.raf.trading.order.service.CurrencyConversionService;
import rs.raf.trading.portfolio.model.Portfolio;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.repository.ListingRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * Unit testovi za {@link DividendService} — B9 (dividende na akcije).
 *
 * <p>Svi pozivi ka banka-core su mock-ovani preko {@link BankaCoreClient}.
 * Portfolio i Listing su lokalni (trading_db) — mock-ovani direktno.
 */
@ExtendWith(MockitoExtension.class)
class DividendServiceTest {

    @InjectMocks
    private DividendService dividendService;

    @Mock
    private DividendPayoutRepository dividendPayoutRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private BankaCoreClient bankaCoreClient;

    @Mock
    private TradingUserResolver userResolver;

    @Mock
    private CurrencyConversionService currencyConversionService;

    /**
     * BE-FND-03 fix: {@code DividendService} ima self-injected proxy field
     * (@code self}) preko kog ide poziv {@code payDividendForOwner} iz
     * {@code processQuarterlyDividends} (bez toga intra-class self-invoke
     * zaobilazi {@code @Transactional} AOP). Mockito {@code @InjectMocks} ne
     * popunjava ovaj field — pa ga rucno setujemo na sam SUT instance u
     * {@link #setUpSelfProxy()}. U test okruzenju nemamo Spring kontekst,
     * pa nema pravog proxy-ja — ali za potrebe verify-ja poziva ovo je
     * dovoljno (test pokrivenost transakcionog ponasanja je odvojena).
     */
    @BeforeEach
    void setUpSelfProxy() {
        ReflectionTestUtils.setField(dividendService, "self", dividendService);
    }

    // ── Pomocni byggeri ───────────────────────────────────────────────────────

    private Portfolio buildPortfolio(Long userId, String userRole, Long listingId, int qty) {
        Portfolio p = new Portfolio();
        p.setUserId(userId);
        p.setUserRole(userRole);
        p.setListingId(listingId);
        p.setQuantity(qty);
        p.setListingType("STOCK");
        p.setListingTicker("AAPL");
        return p;
    }

    private Listing buildListing(BigDecimal price, BigDecimal annualYield, String baseCurrency) {
        Listing l = new Listing();
        l.setId(1L);
        l.setTicker("AAPL");
        l.setPrice(price);
        l.setDividendYield(annualYield);
        l.setBaseCurrency(baseCurrency);
        return l;
    }

    private InternalAccountDto stubAccount(Long id, String currency) {
        return new InternalAccountDto(id, "222000100000000001", "Test Owner",
                BigDecimal.valueOf(10000), BigDecimal.valueOf(10000), BigDecimal.ZERO,
                currency, "ACTIVE", null, null, "PERSONAL");
    }

    // ── Test 1: Preskace poziciju ako dividenda za taj datum vec postoji ──────

    @Test
    void processQuarterlyDividends_skipsAlreadyPaid() {
        LocalDate paymentDate = LocalDate.of(2025, 12, 31);
        Portfolio position = buildPortfolio(1L, "CLIENT", 10L, 5);

        when(portfolioRepository.findAllStockPositionsWithQuantity())
                .thenReturn(List.of(position));
        DividendPayout alreadyPaid = new DividendPayout();
        alreadyPaid.setOwnerId(1L);
        alreadyPaid.setOwnerType("CLIENT");
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(List.of(alreadyPaid));

        dividendService.processQuarterlyDividends(paymentDate);

        verify(dividendPayoutRepository, never()).save(any());
        verify(bankaCoreClient, never()).creditFunds(anyString(), any());
    }

    // ── Test 2: Zaposleni (EMPLOYEE) je porezno oslobodjen ───────────────────

    @Test
    void processQuarterlyDividends_taxExemptForEmployee() {
        LocalDate paymentDate = LocalDate.of(2025, 12, 31);
        Portfolio position = buildPortfolio(5L, "EMPLOYEE", 10L, 10);
        Listing listing = buildListing(new BigDecimal("100.00"), new BigDecimal("0.08"), "USD");

        when(portfolioRepository.findAllStockPositionsWithQuantity())
                .thenReturn(List.of(position));
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(List.of());
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(bankaCoreClient.getBankTradingAccount("USD"))
                .thenReturn(stubAccount(99L, "USD"));
        when(dividendPayoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dividendService.processQuarterlyDividends(paymentDate);

        ArgumentCaptor<DividendPayout> captor = ArgumentCaptor.forClass(DividendPayout.class);
        verify(dividendPayoutRepository).save(captor.capture());

        DividendPayout saved = captor.getValue();
        assertThat(saved.getTaxExempt()).isTrue();
        assertThat(saved.getTax()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Test 3: Klijent placa 15% poreza ─────────────────────────────────────

    @Test
    void processQuarterlyDividends_appliesTax15PercentForClient() {
        LocalDate paymentDate = LocalDate.of(2025, 12, 31);
        Portfolio position = buildPortfolio(2L, "CLIENT", 10L, 10);
        Listing listing = buildListing(new BigDecimal("100.00"), new BigDecimal("0.08"), "USD");

        when(portfolioRepository.findAllStockPositionsWithQuantity())
                .thenReturn(List.of(position));
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(List.of());
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(bankaCoreClient.getPreferredAccount("CLIENT", 2L, "USD"))
                .thenReturn(stubAccount(20L, "USD"));
        when(dividendPayoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dividendService.processQuarterlyDividends(paymentDate);

        ArgumentCaptor<DividendPayout> captor = ArgumentCaptor.forClass(DividendPayout.class);
        verify(dividendPayoutRepository).save(captor.capture());

        DividendPayout saved = captor.getValue();
        assertThat(saved.getTaxExempt()).isFalse();
        // gross = 10 * 100.00 * (0.08/4) = 20.00; tax = 20.00 * 0.15 = 3.00
        assertThat(saved.getTax()).isEqualByComparingTo(new BigDecimal("3.0000"));
        assertThat(saved.getNetAmount()).isEqualByComparingTo(new BigDecimal("17.0000"));
    }

    // ── Test 4: Provjera ispravnosti izracuna bruto iznosa ───────────────────

    @Test
    void processQuarterlyDividends_calculatesGrossCorrectly() {
        // qty=10, price=100.00, annualYield=0.08 → quarterly=0.02, gross=20.0000
        LocalDate paymentDate = LocalDate.of(2025, 12, 31);
        Portfolio position = buildPortfolio(3L, "CLIENT", 10L, 10);
        Listing listing = buildListing(new BigDecimal("100.00"), new BigDecimal("0.08"), "USD");

        when(portfolioRepository.findAllStockPositionsWithQuantity())
                .thenReturn(List.of(position));
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(List.of());
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(bankaCoreClient.getPreferredAccount("CLIENT", 3L, "USD"))
                .thenReturn(stubAccount(30L, "USD"));
        when(dividendPayoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dividendService.processQuarterlyDividends(paymentDate);

        ArgumentCaptor<DividendPayout> captor = ArgumentCaptor.forClass(DividendPayout.class);
        verify(dividendPayoutRepository).save(captor.capture());

        DividendPayout saved = captor.getValue();
        assertThat(saved.getGrossAmount()).isEqualByComparingTo(new BigDecimal("20.0000"));
        // quarterly yield = 0.08 / 4 = 0.020000
        assertThat(saved.getDividendYieldRate()).isEqualByComparingTo(new BigDecimal("0.020000"));
    }

    // ── Test 5: creditFunds se poziva sa netAmount ────────────────────────────

    @Test
    void processQuarterlyDividends_creditsAccountWithNetAmount() {
        LocalDate paymentDate = LocalDate.of(2025, 12, 31);
        Portfolio position = buildPortfolio(4L, "CLIENT", 10L, 10);
        Listing listing = buildListing(new BigDecimal("100.00"), new BigDecimal("0.08"), "USD");

        when(portfolioRepository.findAllStockPositionsWithQuantity())
                .thenReturn(List.of(position));
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(List.of());
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(bankaCoreClient.getPreferredAccount("CLIENT", 4L, "USD"))
                .thenReturn(stubAccount(40L, "USD"));
        when(dividendPayoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dividendService.processQuarterlyDividends(paymentDate);

        ArgumentCaptor<CreditFundsRequest> reqCaptor =
                ArgumentCaptor.forClass(CreditFundsRequest.class);
        verify(bankaCoreClient).creditFunds(anyString(), reqCaptor.capture());

        CreditFundsRequest req = reqCaptor.getValue();
        // gross = 20.0000, tax = 3.0000, net = 17.0000
        assertThat(req.amount()).isEqualByComparingTo(new BigDecimal("17.0000"));
        assertThat(req.accountId()).isEqualTo(40L);
        assertThat(req.currencyCode()).isEqualTo("USD");
        assertThat(req.commission()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── Test 6: RSD fallback kad preferiran racun u valuti listinga ne postoji

    @Test
    void processQuarterlyDividends_fallsBackToRsdAccountWhenCurrencyMismatch() {
        LocalDate paymentDate = LocalDate.of(2025, 12, 31);
        Portfolio position = buildPortfolio(6L, "CLIENT", 10L, 5);
        Listing listing = buildListing(new BigDecimal("200.00"), new BigDecimal("0.04"), "USD");

        when(portfolioRepository.findAllStockPositionsWithQuantity())
                .thenReturn(List.of(position));
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(List.of());
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

        // Klijent nema USD racun → BankaCoreClientException → RSD fallback
        when(bankaCoreClient.getPreferredAccount("CLIENT", 6L, "USD"))
                .thenThrow(new BankaCoreClientException(404, "No USD account"));

        BigDecimal convertedRsd = new BigDecimal("850.0000");
        when(currencyConversionService.convert(any(), eq("USD"), eq("RSD")))
                .thenReturn(convertedRsd);
        when(bankaCoreClient.getPreferredAccount("CLIENT", 6L, "RSD"))
                .thenReturn(stubAccount(60L, "RSD"));
        when(dividendPayoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dividendService.processQuarterlyDividends(paymentDate);

        // Mora biti pozvano: convert(netAmount, USD, RSD) + getPreferredAccount(..., RSD)
        verify(currencyConversionService).convert(any(), eq("USD"), eq("RSD"));
        verify(bankaCoreClient).getPreferredAccount("CLIENT", 6L, "RSD");

        ArgumentCaptor<CreditFundsRequest> reqCaptor =
                ArgumentCaptor.forClass(CreditFundsRequest.class);
        verify(bankaCoreClient).creditFunds(anyString(), reqCaptor.capture());

        CreditFundsRequest req = reqCaptor.getValue();
        assertThat(req.accountId()).isEqualTo(60L);
        assertThat(req.currencyCode()).isEqualTo("RSD");
        assertThat(req.amount()).isEqualByComparingTo(convertedRsd);
    }

    // ── Test 7: getMyDividendHistory vraca isplate trenutnog korisnika ────────

    @Test
    void getMyDividendHistory_returnsOnlyCurrentUserPayouts() {
        UserContext ctx = new UserContext(99L, "CLIENT");
        when(userResolver.resolveCurrent()).thenReturn(ctx);

        DividendPayout payout = new DividendPayout();
        payout.setOwnerId(99L);
        payout.setOwnerType("CLIENT");
        payout.setStockListingId(10L);
        payout.setStockTicker("AAPL");
        payout.setQuantity(3);
        payout.setPriceOnDate(new BigDecimal("150.00"));
        payout.setDividendYieldRate(new BigDecimal("0.020000"));
        payout.setGrossAmount(new BigDecimal("9.0000"));
        payout.setTax(new BigDecimal("1.3500"));
        payout.setNetAmount(new BigDecimal("7.6500"));
        payout.setCreditedAccountId(50L);
        payout.setCurrencyCode("USD");
        payout.setPaymentDate(LocalDate.of(2025, 9, 30));
        payout.setTaxExempt(false);

        when(dividendPayoutRepository.findByOwnerIdAndOwnerTypeOrderByPaymentDateDesc(99L, "CLIENT"))
                .thenReturn(List.of(payout));

        List<DividendPayoutDto> result = dividendService.getMyDividendHistory();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOwnerId()).isEqualTo(99L);
        assertThat(result.get(0).getOwnerType()).isEqualTo("CLIENT");
        verify(dividendPayoutRepository)
                .findByOwnerIdAndOwnerTypeOrderByPaymentDateDesc(99L, "CLIENT");
    }

    // ── Test 8: getDividendHistoryByPosition baca AccessDenied ako nije vlasnik

    @Test
    void getDividendHistoryByPosition_throwsAccessDeniedIfNotOwner() {
        // Korisnik je userId=1, ali pozicija pripada userId=2
        UserContext ctx = new UserContext(1L, "CLIENT");
        when(userResolver.resolveCurrent()).thenReturn(ctx);

        Portfolio portfolio = buildPortfolio(2L, "CLIENT", 10L, 5);
        portfolio.setId(77L);
        when(portfolioRepository.findById(77L)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> dividendService.getDividendHistoryByPosition(77L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Test 9: getAdminDividendHistory — samo 'from' defaultuje 'to' na danas ─

    @Test
    void getAdminDividendHistory_fromOnlyDefaultsTodayAsTo() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, 20);

        when(dividendPayoutRepository.findByPaymentDateBetween(eq(from), eq(today), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        dividendService.getAdminDividendHistory(from, null, pageable);

        verify(dividendPayoutRepository, times(1))
                .findByPaymentDateBetween(eq(from), eq(today), eq(pageable));
        verify(dividendPayoutRepository, never()).findAllByOrderByPaymentDateDesc(any());
    }

    // ── Test 10: payDividendForOwner cuva prosledjeni paymentDate nepromenjeno ─
    // Weekend shifting je odgovornost DividendScheduler-a, ne servisa.

    @Test
    void payDividendForOwner_storesProvidedPaymentDateAsIs() {
        // Prosledi subotu direktno servisu — on je samo cuva.
        LocalDate saturday = LocalDate.of(2025, 12, 27); // subota
        Portfolio position = buildPortfolio(7L, "CLIENT", 10L, 2);
        Listing listing = buildListing(new BigDecimal("50.00"), new BigDecimal("0.04"), "EUR");

        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(bankaCoreClient.getPreferredAccount("CLIENT", 7L, "USD"))
                .thenReturn(stubAccount(70L, "USD"));
        when(dividendPayoutRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DividendPayout result = dividendService.payDividendForOwner(position, saturday);

        assertThat(result.getPaymentDate()).isEqualTo(saturday);
        verify(dividendPayoutRepository).save(any());
    }
}
