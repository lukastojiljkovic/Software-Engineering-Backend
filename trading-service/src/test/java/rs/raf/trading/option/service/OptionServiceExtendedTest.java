package rs.raf.trading.option.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2.contracts.internal.CreditFundsRequest;
import rs.raf.banka2.contracts.internal.DebitFundsRequest;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.actuary.model.ActuaryInfo;
import rs.raf.trading.actuary.repository.ActuaryInfoRepository;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.option.dto.OptionChainDto;
import rs.raf.trading.option.dto.OptionDto;
import rs.raf.trading.option.model.Option;
import rs.raf.trading.option.model.OptionType;
import rs.raf.trading.option.repository.OptionRepository;
import rs.raf.trading.portfolio.model.Portfolio;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.model.ListingType;
import rs.raf.trading.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Prosireni testovi za {@link OptionService} — copy-first ekstrakcija + money-seam
 * rewiring (faza 2d-C):
 * - getOptionsForStock (grupisanje, sortiranje, listing not found)
 * - getOptionById
 * - exerciseOption CALL sa portfolio operacijama (banka-core debitFunds)
 * - exerciseOption PUT sa uklanjanjem akcija (banka-core creditFunds)
 * - PUT in-the-money validacija
 * - ADMIN korisnik moze izvrsiti opciju
 * - nedovoljno sredstava banke za CALL (banka-core 409)
 * - nedovoljno akcija za PUT
 *
 * <p>NAPOMENA: monolitni test je proveravao {@code Account.balance} bankinog
 * racuna direktno; ovde se umesto toga verifikuje {@code BankaCoreClient}
 * pozvan korektno (debitFunds/creditFunds) — bankin {@code Account} sad zivi u
 * banka-core domenu.
 */
@ExtendWith(MockitoExtension.class)
class OptionServiceExtendedTest {

    @Mock private OptionRepository optionRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private BankaCoreClient bankaCoreClient;

    private OptionService optionService;

    @BeforeEach
    void setUp() {
        optionService = new OptionService(
                optionRepository, listingRepository, actuaryInfoRepository,
                portfolioRepository, bankaCoreClient);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Listing buildStockListing(Long id, BigDecimal price) {
        Listing l = new Listing();
        l.setId(id);
        l.setTicker("AAPL");
        l.setName("Apple Inc.");
        l.setListingType(ListingType.STOCK);
        l.setPrice(price);
        return l;
    }

    private Option buildOption(Long id, OptionType type, BigDecimal stockPrice,
                                BigDecimal strike, LocalDate settlement, int openInterest) {
        Listing listing = buildStockListing(55L, stockPrice);
        Option o = new Option();
        o.setId(id);
        o.setTicker("AAPL260402" + (type == OptionType.CALL ? "C" : "P") + "00185000");
        o.setStockListing(listing);
        o.setOptionType(type);
        o.setStrikePrice(strike);
        o.setSettlementDate(settlement);
        o.setOpenInterest(openInterest);
        o.setContractSize(100);
        o.setPrice(new BigDecimal("5.00"));
        o.setAsk(new BigDecimal("5.50"));
        o.setBid(new BigDecimal("4.50"));
        o.setImpliedVolatility(0.25);
        o.setVolume(1000);
        return o;
    }

    private InternalUserDto employee(Long id, String email, boolean active) {
        return new InternalUserDto(id, "EMPLOYEE", email, "Test", "Employee", active, "Agent");
    }

    private void mockAuthorizedActuary(String email, Long empId) {
        when(bankaCoreClient.getUserByEmail(email)).thenReturn(employee(empId, email, true));
        when(bankaCoreClient.getUserPermissions(email)).thenReturn(List.of("AGENT"));
        when(actuaryInfoRepository.findByEmployeeId(empId)).thenReturn(Optional.of(new ActuaryInfo()));
    }

    private InternalAccountDto bankAccount() {
        return new InternalAccountDto(99L, "333000000000000001", "Banka 2025",
                new BigDecimal("1000000.00"), new BigDecimal("1000000.00"), BigDecimal.ZERO,
                "USD", "ACTIVE", null, null, "BANK_TRADING");
    }

    // ─── getOptionsForStock ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOptionsForStock")
    class GetOptionsForStock {

        @Test
        @DisplayName("throws EntityNotFoundException when listing not found")
        void listingNotFound() {
            when(listingRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionService.getOptionsForStock(999L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("returns empty list when no options exist for stock")
        void noOptions() {
            Listing listing = buildStockListing(1L, new BigDecimal("150.00"));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
            when(optionRepository.findByStockListingId(1L)).thenReturn(Collections.emptyList());

            List<OptionChainDto> result = optionService.getOptionsForStock(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("groups options by settlement date and separates calls and puts")
        void groupsByDateAndType() {
            Listing listing = buildStockListing(1L, new BigDecimal("150.00"));
            LocalDate date1 = LocalDate.of(2026, 4, 10);
            LocalDate date2 = LocalDate.of(2026, 5, 10);

            Option call1 = buildOption(1L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("155"), date1, 10);
            call1.setStockListing(listing);
            Option put1 = buildOption(2L, OptionType.PUT, new BigDecimal("150"), new BigDecimal("145"), date1, 5);
            put1.setStockListing(listing);
            Option call2 = buildOption(3L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("160"), date2, 8);
            call2.setStockListing(listing);

            when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
            when(optionRepository.findByStockListingId(1L)).thenReturn(List.of(call1, put1, call2));

            List<OptionChainDto> result = optionService.getOptionsForStock(1L);

            assertThat(result).hasSize(2);
            // Sorted by date
            assertThat(result.get(0).getSettlementDate()).isEqualTo(date1);
            assertThat(result.get(1).getSettlementDate()).isEqualTo(date2);
            // First chain has 1 call, 1 put
            assertThat(result.get(0).getCalls()).hasSize(1);
            assertThat(result.get(0).getPuts()).hasSize(1);
            // Second chain has 1 call, 0 puts
            assertThat(result.get(1).getCalls()).hasSize(1);
            assertThat(result.get(1).getPuts()).isEmpty();
        }

        @Test
        @DisplayName("calls are sorted by strike price ascending")
        void callsSortedByStrike() {
            Listing listing = buildStockListing(1L, new BigDecimal("150.00"));
            LocalDate date = LocalDate.of(2026, 4, 10);

            Option call1 = buildOption(1L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("170"), date, 10);
            call1.setStockListing(listing);
            Option call2 = buildOption(2L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("155"), date, 10);
            call2.setStockListing(listing);
            Option call3 = buildOption(3L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("160"), date, 10);
            call3.setStockListing(listing);

            when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));
            when(optionRepository.findByStockListingId(1L)).thenReturn(List.of(call1, call2, call3));

            List<OptionChainDto> result = optionService.getOptionsForStock(1L);

            List<OptionDto> calls = result.get(0).getCalls();
            assertThat(calls).hasSize(3);
            assertThat(calls.get(0).getStrikePrice()).isEqualByComparingTo(new BigDecimal("155"));
            assertThat(calls.get(1).getStrikePrice()).isEqualByComparingTo(new BigDecimal("160"));
            assertThat(calls.get(2).getStrikePrice()).isEqualByComparingTo(new BigDecimal("170"));
        }
    }

    // ─── getOptionById ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOptionById")
    class GetOptionById {

        @Test
        @DisplayName("returns OptionDto for existing option")
        void existingOption() {
            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("150"), new BigDecimal("145"), LocalDate.now().plusDays(5), 10);
            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

            OptionDto dto = optionService.getOptionById(1L);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getOptionType()).isEqualTo("CALL");
            assertThat(dto.isInTheMoney()).isTrue(); // 150 > 145 for CALL
        }

        @Test
        @DisplayName("throws EntityNotFoundException for missing option")
        void missingOption() {
            when(optionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> optionService.getOptionById(99L))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    // ─── exerciseOption - CALL ──────────────────────────────────────────────────

    @Nested
    @DisplayName("exerciseOption - CALL")
    class ExerciseCall {

        @Test
        @DisplayName("CALL exercise debits bank via banka-core, adds to portfolio, decrements openInterest")
        void callExerciseSuccess() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());
            when(portfolioRepository.findByUserIdAndUserRole(12L, "EMPLOYEE"))
                    .thenReturn(Collections.emptyList());

            optionService.exerciseOption(1L, "agent@test.com");

            // Banka-core debit strike × contractSize = 180 × 100 = 18000 USD.
            verify(bankaCoreClient).debitFunds(any(), any(DebitFundsRequest.class));
            // Open interest decremented
            assertThat(option.getOpenInterest()).isEqualTo(3);
            verify(optionRepository).save(option);
            verify(portfolioRepository).save(any(Portfolio.class));
        }

        @Test
        @DisplayName("CALL exercise fails when banka-core returns 409 (insufficient bank funds)")
        void callExerciseInsufficientFunds() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());
            when(bankaCoreClient.debitFunds(any(), any(DebitFundsRequest.class)))
                    .thenThrow(new BankaCoreClientException(409, "insufficient funds"));

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nedovoljno sredstava");
        }

        @Test
        @DisplayName("CALL exercise updates existing portfolio with weighted average")
        void callExerciseUpdatesExistingPortfolio() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            Portfolio existingPortfolio = new Portfolio();
            existingPortfolio.setUserId(12L);
            existingPortfolio.setUserRole("EMPLOYEE");
            existingPortfolio.setListingId(55L);
            existingPortfolio.setQuantity(50);
            existingPortfolio.setAverageBuyPrice(new BigDecimal("200.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());
            when(portfolioRepository.findByUserIdAndUserRole(12L, "EMPLOYEE"))
                    .thenReturn(List.of(existingPortfolio));

            optionService.exerciseOption(1L, "agent@test.com");

            // quantity should be 50 + 100 = 150
            assertThat(existingPortfolio.getQuantity()).isEqualTo(150);
            verify(portfolioRepository).save(existingPortfolio);
        }
    }

    // ─── exerciseOption - PUT ───────────────────────────────────────────────────

    @Nested
    @DisplayName("exerciseOption - PUT")
    class ExercisePut {

        @Test
        @DisplayName("PUT exercise removes shares and credits bank via banka-core")
        void putExerciseSuccess() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(12L);
            portfolio.setUserRole("EMPLOYEE");
            portfolio.setListingId(55L);
            portfolio.setQuantity(200);
            portfolio.setAverageBuyPrice(new BigDecimal("170.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(portfolioRepository.findByUserIdAndUserRole(12L, "EMPLOYEE"))
                    .thenReturn(List.of(portfolio));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());

            optionService.exerciseOption(1L, "agent@test.com");

            // Portfolio reduced by contractSize (100)
            assertThat(portfolio.getQuantity()).isEqualTo(100);
            // Banka-core credit strike × contractSize = 180 × 100 = 18000 USD.
            verify(bankaCoreClient).creditFunds(any(), any(CreditFundsRequest.class));
            assertThat(option.getOpenInterest()).isEqualTo(2);
        }

        @Test
        @DisplayName("PUT exercise deletes portfolio when shares reach zero")
        void putExerciseDeletesPortfolio() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(12L);
            portfolio.setUserRole("EMPLOYEE");
            portfolio.setListingId(55L);
            portfolio.setQuantity(100); // Exactly contractSize
            portfolio.setAverageBuyPrice(new BigDecimal("170.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(portfolioRepository.findByUserIdAndUserRole(12L, "EMPLOYEE"))
                    .thenReturn(List.of(portfolio));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());

            optionService.exerciseOption(1L, "agent@test.com");

            verify(portfolioRepository).delete(portfolio);
        }

        @Test
        @DisplayName("PUT exercise fails when user has no shares")
        void putExerciseNoShares() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());
            when(portfolioRepository.findByUserIdAndUserRole(12L, "EMPLOYEE"))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nema dovoljno akcija");
        }

        @Test
        @DisplayName("PUT exercise fails when user has insufficient shares")
        void putExerciseInsufficientShares() {
            mockAuthorizedActuary("agent@test.com", 12L);

            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("150.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            Portfolio portfolio = new Portfolio();
            portfolio.setUserId(12L);
            portfolio.setUserRole("EMPLOYEE");
            portfolio.setListingId(55L);
            portfolio.setQuantity(50); // Less than contractSize (100)
            portfolio.setAverageBuyPrice(new BigDecimal("170.00"));

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());
            when(portfolioRepository.findByUserIdAndUserRole(12L, "EMPLOYEE"))
                    .thenReturn(List.of(portfolio));

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nedovoljno akcija");
        }

        @Test
        @DisplayName("PUT option not in-the-money throws exception")
        void putNotInTheMoney() {
            mockAuthorizedActuary("agent@test.com", 12L);

            // stock=200, strike=180 -> for PUT, need stock < strike
            Option option = buildOption(1L, OptionType.PUT, new BigDecimal("200.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 3);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "agent@test.com"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("in-the-money");
        }
    }

    // ─── Authorization ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Authorization checks")
    class Authorization {

        @Test
        @DisplayName("ADMIN employee can exercise options")
        void adminCanExercise() {
            // ADMIN preko permisije — bez ActuaryInfo reda.
            when(bankaCoreClient.getUserByEmail("admin@test.com"))
                    .thenReturn(employee(15L, "admin@test.com", true));
            when(bankaCoreClient.getUserPermissions("admin@test.com")).thenReturn(List.of("ADMIN"));

            Option option = buildOption(1L, OptionType.CALL, new BigDecimal("210.00"),
                    new BigDecimal("180.00"), LocalDate.now().plusDays(5), 4);

            when(optionRepository.findById(1L)).thenReturn(Optional.of(option));
            when(bankaCoreClient.getBankTradingAccount("USD")).thenReturn(bankAccount());
            when(portfolioRepository.findByUserIdAndUserRole(15L, "EMPLOYEE"))
                    .thenReturn(Collections.emptyList());

            optionService.exerciseOption(1L, "admin@test.com");

            // Exercise prolazi bez AccessDeniedException — ADMIN permisija je dovoljna,
            // cak i bez ActuaryInfo reda (findByEmployeeId vraca prazan Optional).
            verify(optionRepository).save(option);
        }

        @Test
        @DisplayName("employee not found in banka-core throws AccessDeniedException")
        void employeeNotFound() {
            when(bankaCoreClient.getUserByEmail("unknown@test.com"))
                    .thenThrow(new BankaCoreClientException(404, "not found"));

            assertThatThrownBy(() -> optionService.exerciseOption(1L, "unknown@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
