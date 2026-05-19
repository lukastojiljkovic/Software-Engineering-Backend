package rs.raf.trading.portfolio.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.trading.common.UserContext;
import rs.raf.trading.portfolio.dto.PortfolioItemDto;
import rs.raf.trading.portfolio.dto.PortfolioSummaryDto;
import rs.raf.trading.portfolio.model.Portfolio;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.model.ListingType;
import rs.raf.trading.stock.repository.ListingRepository;
import rs.raf.trading.tax.repository.TaxRecordRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for PortfolioService covering:
 * - getMyPortfolio (profit calculation, empty portfolio)
 * - getSummary (total value, profit, tax)
 * - setPublicQuantity (validation, authorization)
 *
 * NAPOMENA (faza 2c): monolitni test je razresavao identitet preko
 * {@code ClientRepository}/{@code EmployeeRepository} + {@code SecurityContextHolder}.
 * trading-service razresava identitet preko {@link TradingUserResolver} (banka-core
 * interni API) — ovde je {@code @Mock}. Portfolio ne dira novac, pa nema
 * {@code BankaCoreClient} money seam-a.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private TaxRecordRepository taxRecordRepository;
    @Mock private TradingUserResolver userResolver;

    @InjectMocks
    private PortfolioService portfolioService;

    private void authenticateAs(Long userId) {
        lenient().when(userResolver.resolveCurrent())
                .thenReturn(new UserContext(userId, "CLIENT"));
    }

    private Portfolio buildPortfolio(Long id, Long userId, Long listingId, String ticker,
                                      int qty, BigDecimal avgPrice) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setUserId(userId);
        p.setUserRole("CLIENT");
        p.setListingId(listingId);
        p.setListingTicker(ticker);
        p.setListingName(ticker + " Inc");
        p.setListingType("STOCK");
        p.setQuantity(qty);
        p.setAverageBuyPrice(avgPrice);
        p.setPublicQuantity(0);
        p.setLastModified(LocalDateTime.now());
        return p;
    }

    private Listing buildListing(Long id, BigDecimal price) {
        Listing l = new Listing();
        l.setId(id);
        l.setPrice(price);
        l.setListingType(ListingType.STOCK);
        return l;
    }

    // ─── getMyPortfolio ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyPortfolio")
    class GetMyPortfolio {

        @Test
        @DisplayName("returns empty list when user has no portfolio")
        void emptyPortfolio() {
            authenticateAs(1L);
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(Collections.emptyList());

            List<PortfolioItemDto> result = portfolioService.getMyPortfolio();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("calculates profit correctly when price increased")
        void profitWhenPriceIncreased() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 10, new BigDecimal("100.00"));
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("120.00"))));

            List<PortfolioItemDto> result = portfolioService.getMyPortfolio();

            assertThat(result).hasSize(1);
            PortfolioItemDto item = result.get(0);
            // profit = (120 - 100) * 10 = 200
            assertThat(item.getProfit()).isEqualByComparingTo(new BigDecimal("200"));
            // profitPercent = ((120 - 100) / 100) * 100 = 20.00
            assertThat(item.getProfitPercent()).isEqualByComparingTo(new BigDecimal("20.00"));
            assertThat(item.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("120.00"));
        }

        @Test
        @DisplayName("calculates negative profit when price decreased")
        void negativeProfitWhenPriceDecreased() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 5, new BigDecimal("150.00"));
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("130.00"))));

            List<PortfolioItemDto> result = portfolioService.getMyPortfolio();

            PortfolioItemDto item = result.get(0);
            // profit = (130 - 150) * 5 = -100
            assertThat(item.getProfit()).isEqualByComparingTo(new BigDecimal("-100"));
            assertThat(item.getProfitPercent().doubleValue()).isLessThan(0);
        }

        @Test
        @DisplayName("returns zero price when listing not found")
        void listingNotFound_zeroPriceUsed() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 999L, "GONE", 10, new BigDecimal("50.00"));
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p));
            when(listingRepository.findById(999L)).thenReturn(Optional.empty());

            List<PortfolioItemDto> result = portfolioService.getMyPortfolio();

            assertThat(result.get(0).getCurrentPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns zero price when listing price is null")
        void listingPriceNull_zeroUsed() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "NULL", 5, new BigDecimal("100.00"));
            Listing listing = buildListing(10L, null);
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

            List<PortfolioItemDto> result = portfolioService.getMyPortfolio();

            assertThat(result.get(0).getCurrentPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("handles zero averageBuyPrice without division error")
        void zeroAverageBuyPrice_noDivisionError() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "FREE", 10, BigDecimal.ZERO);
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("50.00"))));

            List<PortfolioItemDto> result = portfolioService.getMyPortfolio();

            assertThat(result.get(0).getProfitPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("multiple portfolio items are all returned")
        void multipleItems() {
            authenticateAs(1L);
            Portfolio p1 = buildPortfolio(1L, 1L, 10L, "AAPL", 10, new BigDecimal("100.00"));
            Portfolio p2 = buildPortfolio(2L, 1L, 20L, "MSFT", 5, new BigDecimal("200.00"));
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p1, p2));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("110.00"))));
            when(listingRepository.findById(20L)).thenReturn(Optional.of(buildListing(20L, new BigDecimal("220.00"))));

            List<PortfolioItemDto> result = portfolioService.getMyPortfolio();

            assertThat(result).hasSize(2);
        }
    }

    // ─── getSummary ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary")
    class GetSummary {

        @Test
        @DisplayName("calculates total value and positive tax")
        void positiveProfitWithTax() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 10, new BigDecimal("100.00"));
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("120.00"))));

            PortfolioSummaryDto summary = portfolioService.getSummary();

            // totalValue = 120 * 10 = 1200
            assertThat(summary.getTotalValue()).isEqualByComparingTo(new BigDecimal("1200.00"));
            // totalProfit = (120-100)*10 = 200
            assertThat(summary.getTotalProfit()).isEqualByComparingTo(new BigDecimal("200.00"));
            // unpaidTax = 200 * 0.15 = 30
            assertThat(summary.getUnpaidTaxThisMonth()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(summary.getPaidTaxThisYear()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("no tax when loss")
        void noTaxOnLoss() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 10, new BigDecimal("150.00"));
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(List.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("130.00"))));

            PortfolioSummaryDto summary = portfolioService.getSummary();

            // totalProfit = (130-150)*10 = -200
            assertThat(summary.getTotalProfit()).isNegative();
            assertThat(summary.getUnpaidTaxThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("empty portfolio returns zeros")
        void emptyPortfolioSummary() {
            authenticateAs(1L);
            when(portfolioRepository.findByUserIdAndUserRole(1L, "CLIENT")).thenReturn(Collections.emptyList());

            PortfolioSummaryDto summary = portfolioService.getSummary();

            assertThat(summary.getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getTotalProfit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(summary.getUnpaidTaxThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ─── setPublicQuantity ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("setPublicQuantity")
    class SetPublicQuantity {

        @Test
        @DisplayName("sets publicQuantity and returns updated item")
        void setsPublicQuantity() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 100, new BigDecimal("100.00"));
            when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("110.00"))));

            PortfolioItemDto result = portfolioService.setPublicQuantity(1L, 50);

            assertThat(result.getPublicQuantity()).isEqualTo(50);
            verify(portfolioRepository).save(p);
        }

        @Test
        @DisplayName("throws when quantity exceeds total")
        void exceedsTotalQuantity() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 100, new BigDecimal("100.00"));
            when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> portfolioService.setPublicQuantity(1L, 150))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("throws when quantity is negative")
        void negativeQuantity() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 100, new BigDecimal("100.00"));
            when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> portfolioService.setPublicQuantity(1L, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when portfolio item not found")
        void portfolioNotFound() {
            authenticateAs(1L);
            when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> portfolioService.setPublicQuantity(99L, 10))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("throws when user does not own the portfolio item")
        void wrongUser() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 999L, 10L, "AAPL", 100, new BigDecimal("100.00")); // Different userId
            when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));

            assertThatThrownBy(() -> portfolioService.setPublicQuantity(1L, 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("pristup");
        }

        @Test
        @DisplayName("setting publicQuantity to 0 is allowed")
        void zeroQuantityAllowed() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 100, new BigDecimal("100.00"));
            p.setPublicQuantity(50);
            when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("100.00"))));

            PortfolioItemDto result = portfolioService.setPublicQuantity(1L, 0);

            assertThat(result.getPublicQuantity()).isEqualTo(0);
        }

        @Test
        @DisplayName("setting publicQuantity to total quantity is allowed")
        void maxQuantityAllowed() {
            authenticateAs(1L);
            Portfolio p = buildPortfolio(1L, 1L, 10L, "AAPL", 100, new BigDecimal("100.00"));
            when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));
            when(listingRepository.findById(10L)).thenReturn(Optional.of(buildListing(10L, new BigDecimal("100.00"))));

            PortfolioItemDto result = portfolioService.setPublicQuantity(1L, 100);

            assertThat(result.getPublicQuantity()).isEqualTo(100);
        }
    }

    // ─── Identitet ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Identitet")
    class Identity {

        /**
         * NAPOMENA (faza 2c): monolit je bacao {@code RuntimeException("Korisnik nije
         * pronadjen")} kad ni {@code ClientRepository} ni {@code EmployeeRepository}
         * ne razrese email. trading-service identitet razresava {@link TradingUserResolver}
         * koji na neuspeh banka-core poziva baca {@link IllegalStateException}
         * ("Autentifikovani korisnik nije pronadjen") — ova grana je pokrivena
         * {@code TradingUserResolverTest}-om. Ovde verifikujemo da
         * {@code PortfolioService} propagira tu gresku iz resolver-a.
         */
        @Test
        @DisplayName("propagira gresku iz resolver-a kad korisnik nije pronadjen")
        void userNotFound() {
            when(userResolver.resolveCurrent())
                    .thenThrow(new IllegalStateException("Autentifikovani korisnik nije pronadjen: nonexistent@test.com"));

            assertThatThrownBy(() -> portfolioService.getMyPortfolio())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nije pronadjen");
        }
    }
}
