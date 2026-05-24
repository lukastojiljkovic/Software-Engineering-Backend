package rs.raf.trading.dividend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.dividend.model.DividendPayout;
import rs.raf.trading.dividend.repository.DividendPayoutRepository;
import rs.raf.trading.dividend.service.DividendService;
import rs.raf.trading.investmentfund.model.ClientFundTransaction;
import rs.raf.trading.investmentfund.service.FundDividendService;
import rs.raf.trading.order.service.CurrencyConversionService;
import rs.raf.trading.portfolio.model.Portfolio;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Integracioni test za B9/B11: za FUND-vlasniske STOCK pozicije
 * {@link DividendService#processQuarterlyDividends} ne knjizi sredstva preko
 * banka-core direktno, vec delegira na {@link FundDividendService} koji
 * sam orkestrira priliv na fond racun (mokirano u ovom testu).
 */
@SpringBootTest
@ActiveProfiles("test")
class DividendFundIntegrationTest {

    @Autowired
    private DividendService dividendService;

    @MockitoBean
    private DividendPayoutRepository dividendPayoutRepository;

    @MockitoBean
    private PortfolioRepository portfolioRepository;

    @MockitoBean
    private ListingRepository listingRepository;

    @MockitoBean
    private TradingUserResolver userResolver;

    @MockitoBean
    private CurrencyConversionService currencyConversionService;

    @MockitoBean
    private FundDividendService fundDividendService;

    @MockitoBean
    private BankaCoreClient bankaCoreClient;

    @Test
    @DisplayName("B9 integrates with B11: fund-owned stock dividend is credited through FundDividendService")
    void processQuarterlyDividends_fundOwnedStock_delegatesToFundDividendService() {
        LocalDate paymentDate = LocalDate.of(2026, 3, 31);

        Portfolio fundPortfolio = Portfolio.builder()
                .id(1L)
                .userId(7L)
                .userRole("FUND")
                .listingId(10L)
                .listingTicker("AAPL")
                .listingName("Apple Inc.")
                .listingType("STOCK")
                .quantity(10)
                .averageBuyPrice(new BigDecimal("90.0000"))
                .publicQuantity(0)
                .reservedQuantity(0)
                .build();

        Listing listing = new Listing();
        listing.setId(10L);
        listing.setTicker("AAPL");
        listing.setName("Apple Inc.");
        listing.setPrice(new BigDecimal("100.0000"));
        listing.setDividendYield(new BigDecimal("0.0800"));
        listing.setBaseCurrency("RSD");
        listing.setQuoteCurrency("RSD");

        ClientFundTransaction fundDividendTransaction = new ClientFundTransaction();
        fundDividendTransaction.setSourceAccountId(500L);

        when(portfolioRepository.findAllStockPositionsWithQuantity()).thenReturn(List.of(fundPortfolio));
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(Collections.emptyList());
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(fundDividendService.creditDividendToFund(
                eq(7L),
                eq(10L),
                eq(new BigDecimal("20.0000"))
        )).thenReturn(fundDividendTransaction);

        when(dividendPayoutRepository.save(any(DividendPayout.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        dividendService.processQuarterlyDividends(paymentDate);

        verify(fundDividendService).creditDividendToFund(
                eq(7L),
                eq(10L),
                eq(new BigDecimal("20.0000"))
        );

        // FUND grana ne knjizi novac preko banka-core jednostranim kreditom —
        // taj posao radi FundDividendService u svom @Transactional metodu.
        verify(bankaCoreClient, never()).creditFunds(anyString(), any());
        verify(bankaCoreClient, never()).getPreferredAccount(anyString(), anyLong(), anyString());

        org.mockito.ArgumentCaptor<DividendPayout> payoutCaptor =
                org.mockito.ArgumentCaptor.forClass(DividendPayout.class);

        verify(dividendPayoutRepository).save(payoutCaptor.capture());

        DividendPayout savedPayout = payoutCaptor.getValue();

        assertEquals(7L, savedPayout.getOwnerId());
        assertEquals("FUND", savedPayout.getOwnerType());
        assertEquals(10L, savedPayout.getStockListingId());
        assertEquals("AAPL", savedPayout.getStockTicker());
        assertEquals(10, savedPayout.getQuantity());
        assertEquals(500L, savedPayout.getCreditedAccountId());
        assertEquals("RSD", savedPayout.getCurrencyCode());
        assertTrue(savedPayout.getTaxExempt());

        assertEquals(0, savedPayout.getGrossAmount().compareTo(new BigDecimal("20.0000")));
        assertEquals(0, savedPayout.getNetAmount().compareTo(new BigDecimal("20.0000")));
        assertEquals(0, savedPayout.getTax().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("B9/B11 integration is idempotent: already paid fund dividend is skipped")
    void processQuarterlyDividends_fundDividendAlreadyPaid_skipsB11Delegation() {
        LocalDate paymentDate = LocalDate.of(2026, 3, 31);

        Portfolio fundPortfolio = Portfolio.builder()
                .id(1L)
                .userId(7L)
                .userRole("FUND")
                .listingId(10L)
                .listingTicker("AAPL")
                .listingName("Apple Inc.")
                .listingType("STOCK")
                .quantity(10)
                .averageBuyPrice(new BigDecimal("90.0000"))
                .publicQuantity(0)
                .reservedQuantity(0)
                .build();

        DividendPayout existingPayout = DividendPayout.builder()
                .ownerId(7L)
                .ownerType("FUND")
                .stockListingId(10L)
                .stockTicker("AAPL")
                .quantity(10)
                .priceOnDate(new BigDecimal("100.0000"))
                .dividendYieldRate(new BigDecimal("0.020000"))
                .grossAmount(new BigDecimal("20.0000"))
                .tax(BigDecimal.ZERO)
                .netAmount(new BigDecimal("20.0000"))
                .creditedAccountId(500L)
                .currencyCode("RSD")
                .paymentDate(paymentDate)
                .taxExempt(true)
                .build();

        when(portfolioRepository.findAllStockPositionsWithQuantity()).thenReturn(List.of(fundPortfolio));
        when(dividendPayoutRepository.findByStockListingIdAndPaymentDate(10L, paymentDate))
                .thenReturn(List.of(existingPayout));

        dividendService.processQuarterlyDividends(paymentDate);

        verify(fundDividendService, never()).creditDividendToFund(
                anyLong(),
                anyLong(),
                any(BigDecimal.class)
        );

        verify(dividendPayoutRepository, never()).save(any(DividendPayout.class));
        verifyNoInteractions(bankaCoreClient);
    }
}
