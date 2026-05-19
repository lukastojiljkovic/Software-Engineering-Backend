package rs.raf.trading.option.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2.contracts.internal.DebitFundsRequest;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.actuary.repository.ActuaryInfoRepository;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.option.model.Option;
import rs.raf.trading.option.model.OptionType;
import rs.raf.trading.option.repository.OptionRepository;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.model.ListingType;
import rs.raf.trading.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage testovi za {@link OptionService} — copy-first ekstrakcija +
 * money-seam rewiring (faza 2d-C).
 *
 * <p>NAPOMENA: monolitna verzija je reflection-om testirala privatne metode
 * {@code ensureUserCanExerciseOptions} (grana {@code permissions == null}) i
 * {@code getBankAccount} (grana {@code orElseThrow}). U trading-service-u te
 * grane vise ne postoje u istom obliku:
 * <ul>
 *   <li>{@code getUserPermissions} uvek vraca {@code List} (nikad null —
 *       {@code BankaCoreClient} vraca {@code List.of()} na prazan odgovor), pa
 *       ekvivalent je prazna lista permisija + bez aktuarskog reda → 403;</li>
 *   <li>bankin racun se razresava preko {@code getBankTradingAccount("USD")} —
 *       banka-core gresku (npr. 404) propagiramo kao {@link BankaCoreClientException}.</li>
 * </ul>
 * Grane se ovde pokrivaju kroz javni {@code exerciseOption} put (bez reflexije).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OptionServiceBranchCoverageTest {

    @Mock OptionRepository optionRepository;
    @Mock ListingRepository listingRepository;
    @Mock ActuaryInfoRepository actuaryInfoRepository;
    @Mock PortfolioRepository portfolioRepository;
    @Mock BankaCoreClient bankaCoreClient;

    OptionService service;

    @BeforeEach
    void setUp() {
        service = new OptionService(
                optionRepository, listingRepository, actuaryInfoRepository,
                portfolioRepository, bankaCoreClient);
    }

    private InternalUserDto employee(Long id, String email, boolean active) {
        return new InternalUserDto(id, "EMPLOYEE", email, "Test", "Employee", active, "Agent");
    }

    private Option buildCall(Long id, BigDecimal stockPrice, BigDecimal strike, int openInterest) {
        Listing listing = new Listing();
        listing.setId(55L);
        listing.setTicker("AAPL");
        listing.setName("Apple Inc.");
        listing.setListingType(ListingType.STOCK);
        listing.setPrice(stockPrice);

        Option o = new Option();
        o.setId(id);
        o.setTicker("AAPL260402C00185000");
        o.setStockListing(listing);
        o.setOptionType(OptionType.CALL);
        o.setStrikePrice(strike);
        o.setSettlementDate(LocalDate.now().plusDays(5));
        o.setOpenInterest(openInterest);
        o.setContractSize(100);
        return o;
    }

    @Test
    void exerciseOption_emptyPermissions_noActuaryRow_throws() {
        // Ekvivalent monolitove "permissions == null && nije aktuar" grane:
        // prazna lista permisija + nema ActuaryInfo reda → 403.
        when(bankaCoreClient.getUserByEmail("e@test.com"))
                .thenReturn(employee(1L, "e@test.com", true));
        when(bankaCoreClient.getUserPermissions("e@test.com")).thenReturn(Collections.emptyList());
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exerciseOption(1L, "e@test.com"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("aktuar");
    }

    @Test
    void exerciseOption_bankAccountLookupFails_propagatesBankaCoreException() {
        // Ekvivalent monolitove getBankAccount() orElseThrow grane —
        // banka-core vrati gresku pri razresavanju bankinog racuna.
        when(bankaCoreClient.getUserByEmail("agent@test.com"))
                .thenReturn(employee(2L, "agent@test.com", true));
        when(bankaCoreClient.getUserPermissions("agent@test.com")).thenReturn(List.of("ADMIN"));

        Option option = buildCall(1L, new BigDecimal("210.00"), new BigDecimal("180.00"), 3);
        when(optionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(option));
        when(bankaCoreClient.getBankTradingAccount("USD"))
                .thenThrow(new BankaCoreClientException(404, "Bank USD account not found"));

        assertThatThrownBy(() -> service.exerciseOption(1L, "agent@test.com"))
                .isInstanceOf(BankaCoreClientException.class)
                .hasMessageContaining("Bank USD account not found");
    }

    @Test
    void exerciseOption_callDebitNon409BankaCoreError_isRethrownAsIs() {
        // CALL debit: banka-core gresku koja NIJE 409 (npr. 500) ne pretvaramo u
        // IllegalStateException — propagira se kao BankaCoreClientException.
        when(bankaCoreClient.getUserByEmail("agent@test.com"))
                .thenReturn(employee(2L, "agent@test.com", true));
        when(bankaCoreClient.getUserPermissions("agent@test.com")).thenReturn(List.of("ADMIN"));

        Option option = buildCall(1L, new BigDecimal("210.00"), new BigDecimal("180.00"), 3);
        when(optionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(option));
        when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(
                new InternalAccountDto(99L, "333000000000000001", "Banka", BigDecimal.TEN,
                        BigDecimal.TEN, BigDecimal.ZERO, "USD", "ACTIVE", null, null, "BANK_TRADING"));
        when(bankaCoreClient.debitFunds(any(), any(DebitFundsRequest.class)))
                .thenThrow(new BankaCoreClientException(500, "internal error"));

        assertThatThrownBy(() -> service.exerciseOption(1L, "agent@test.com"))
                .isInstanceOf(BankaCoreClientException.class)
                .hasMessageContaining("internal error");
    }
}
