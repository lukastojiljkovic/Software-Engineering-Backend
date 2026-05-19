package rs.raf.trading.portfolio.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import rs.raf.trading.common.UserContext;
import rs.raf.trading.portfolio.dto.PortfolioItemDto;
import rs.raf.trading.portfolio.dto.PortfolioSummaryDto;
import rs.raf.trading.portfolio.model.Portfolio;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.model.ListingType;
import rs.raf.trading.stock.repository.ListingRepository;
import rs.raf.trading.tax.model.TaxRecord;
import rs.raf.trading.tax.repository.TaxRecordRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Dodatni testovi za {@link PortfolioService} koji pokrivaju grane
 * koje nisu dotaknute u {@link PortfolioServiceTest}:
 * - employee identitet (EMPLOYEE userType u {@code getSummary})
 * - {@code taxRecord.isPresent()} grana (placeno, dugovanje vece, dugovanje manje)
 * - {@code setPublicQuantity} kada je {@code avgPrice == 0}
 *
 * NAPOMENA (faza 2c): monolitni test je gadjao monolitne privatne metode
 * {@code getCurrentUserId()}/{@code isEmployee()} koji su citali
 * {@code SecurityContextHolder} + {@code ClientRepository}/{@code EmployeeRepository}.
 * U trading-service-u identitet (+ EMPLOYEE/CLIENT rola) razresava
 * {@link TradingUserResolver}; ova klasa je preradjena da mokuje resolver i
 * tako pokrije iste TaxRecord-branch ishode (employee path → EMPLOYEE userType
 * u {@code TaxRecord} lookup-u).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioServiceCoverageTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private TaxRecordRepository taxRecordRepository;
    @Mock private TradingUserResolver userResolver;

    @InjectMocks
    private PortfolioService portfolioService;

    private void authAsEmployee(Long employeeId) {
        lenient().when(userResolver.resolveCurrent())
                .thenReturn(new UserContext(employeeId, "EMPLOYEE"));
    }

    private void authAsClient(Long clientId) {
        lenient().when(userResolver.resolveCurrent())
                .thenReturn(new UserContext(clientId, "CLIENT"));
    }

    private Portfolio portfolio(Long id, Long userId, Long listingId, int qty, BigDecimal avg) {
        Portfolio p = new Portfolio();
        p.setId(id);
        p.setUserId(userId);
        p.setUserRole(null);
        p.setListingId(listingId);
        p.setListingTicker("T");
        p.setListingName("Name");
        p.setListingType("STOCK");
        p.setQuantity(qty);
        p.setAverageBuyPrice(avg);
        p.setPublicQuantity(0);
        p.setLastModified(LocalDateTime.now());
        return p;
    }

    private Listing listing(Long id, BigDecimal price) {
        Listing l = new Listing();
        l.setId(id);
        l.setPrice(price);
        l.setListingType(ListingType.STOCK);
        return l;
    }

    // ─── getMyPortfolio — employee identitet ────────────────────────────────

    @Test
    @DisplayName("getMyPortfolio radi za employee-a (resolver vraca EMPLOYEE)")
    void employeeUserResolved() {
        authAsEmployee(7L);
        when(portfolioRepository.findByUserIdAndUserRole(7L, "EMPLOYEE"))
                .thenReturn(Collections.emptyList());

        List<PortfolioItemDto> items = portfolioService.getMyPortfolio();

        assertThat(items).isEmpty();
    }

    // ─── getSummary — employee userType + taxRecord branches ─────────────────

    @Test
    @DisplayName("getSummary — employee sa TaxRecord-om (taxOwed > paid, remaining > 0)")
    void employeeSummaryWithTaxRecordRemainingPositive() {
        authAsEmployee(7L);
        Portfolio p = portfolio(1L, 7L, 10L, 10, new BigDecimal("100.00"));
        p.setUserRole("EMPLOYEE");
        when(portfolioRepository.findByUserIdAndUserRole(7L, "EMPLOYEE")).thenReturn(List.of(p));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, new BigDecimal("120.00"))));

        TaxRecord rec = TaxRecord.builder()
                .userId(7L)
                .userName("E")
                .userType("EMPLOYEE")
                .taxOwed(new BigDecimal("30.00"))
                .taxPaid(new BigDecimal("10.00"))
                .build();
        when(taxRecordRepository.findByUserIdAndUserType(7L, "EMPLOYEE"))
                .thenReturn(Optional.of(rec));

        PortfolioSummaryDto s = portfolioService.getSummary();

        assertThat(s.getPaidTaxThisYear()).isEqualByComparingTo("10.00");
        assertThat(s.getUnpaidTaxThisMonth()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("getSummary — TaxRecord remaining <= 0 daje unpaid=0")
    void clientSummaryWithTaxRecordFullyPaid() {
        authAsClient(3L);
        Portfolio p = portfolio(1L, 3L, 10L, 10, new BigDecimal("100.00"));
        p.setUserRole("CLIENT");
        when(portfolioRepository.findByUserIdAndUserRole(3L, "CLIENT")).thenReturn(List.of(p));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, new BigDecimal("120.00"))));

        TaxRecord rec = TaxRecord.builder()
                .userId(3L)
                .userName("C")
                .userType("CLIENT")
                .taxOwed(new BigDecimal("10.00"))
                .taxPaid(new BigDecimal("30.00")) // paid > owed → remaining negative
                .build();
        when(taxRecordRepository.findByUserIdAndUserType(3L, "CLIENT"))
                .thenReturn(Optional.of(rec));

        PortfolioSummaryDto s = portfolioService.getSummary();

        assertThat(s.getPaidTaxThisYear()).isEqualByComparingTo("30.00");
        assertThat(s.getUnpaidTaxThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getSummary — TaxRecord sa null taxPaid i null taxOwed")
    void clientSummaryWithNullTaxFields() {
        authAsClient(3L);
        when(portfolioRepository.findByUserIdAndUserRole(3L, "CLIENT")).thenReturn(Collections.emptyList());

        TaxRecord rec = new TaxRecord();
        rec.setUserId(3L);
        rec.setUserType("CLIENT");
        rec.setTaxPaid(null);
        rec.setTaxOwed(null);
        when(taxRecordRepository.findByUserIdAndUserType(3L, "CLIENT"))
                .thenReturn(Optional.of(rec));

        PortfolioSummaryDto s = portfolioService.getSummary();

        assertThat(s.getPaidTaxThisYear()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getUnpaidTaxThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── setPublicQuantity — avgPrice == 0 branch ────────────────────────────

    @Test
    @DisplayName("setPublicQuantity — avgPrice=0 ne racuna profitPercent")
    void setPublicQuantityWithZeroAvgPrice() {
        authAsClient(1L);
        Portfolio p = portfolio(1L, 1L, 10L, 100, BigDecimal.ZERO);
        p.setUserRole("CLIENT");
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(p));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing(10L, new BigDecimal("50.00"))));

        PortfolioItemDto dto = portfolioService.setPublicQuantity(1L, 10);

        assertThat(dto.getProfitPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.getPublicQuantity()).isEqualTo(10);
    }
}
