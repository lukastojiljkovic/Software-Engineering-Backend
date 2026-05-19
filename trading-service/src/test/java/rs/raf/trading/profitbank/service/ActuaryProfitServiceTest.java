package rs.raf.trading.profitbank.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.common.UserRole;
import rs.raf.trading.order.model.Order;
import rs.raf.trading.order.model.OrderDirection;
import rs.raf.trading.order.repository.OrderRepository;
import rs.raf.trading.order.service.CurrencyConversionService;
import rs.raf.trading.profitbank.dto.ProfitBankDtos.ActuaryProfitDto;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.model.ListingType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Jedinicni test {@link ActuaryProfitService} — pokriva profit agregaciju
 * (pure-compute jezgro) protiv trading-service {@code Order}/{@code Listing}.
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-E): {@code profitbank} paket u
 * monolitu nije imao testove. Ovo je novi test pisan po obrascu 2d
 * {@code option}/{@code margin} servisnih testova — banka-core identitet
 * ({@code getUserById}/{@code getUserPermissions}) je mockovan.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActuaryProfitServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private BankaCoreClient bankaCoreClient;
    @Mock private CurrencyConversionService currencyConversionService;

    @InjectMocks
    private ActuaryProfitService service;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Listing listing(Long id, String exchange) {
        Listing l = new Listing();
        l.setId(id);
        l.setTicker("T" + id);
        l.setName("Listing " + id);
        l.setExchangeAcronym(exchange);
        l.setListingType(ListingType.STOCK);
        l.setPrice(new BigDecimal("100.00"));
        return l;
    }

    private Order order(Long actuarId, String userRole, Listing listing,
                        OrderDirection direction, int qty, String pricePerUnit) {
        Order o = new Order();
        o.setUserId(actuarId);
        o.setUserRole(userRole);
        o.setListing(listing);
        o.setDirection(direction);
        o.setQuantity(qty);
        o.setContractSize(1);
        o.setPricePerUnit(pricePerUnit == null ? null : new BigDecimal(pricePerUnit));
        o.setDone(true);
        return o;
    }

    private void mockEmployee(Long id, String firstName, String lastName,
                              String email, List<String> permissions) {
        when(bankaCoreClient.getUserById(UserRole.EMPLOYEE, id)).thenReturn(
                new InternalUserDto(id, "EMPLOYEE", email, firstName, lastName, true, "Agent"));
        lenient().when(bankaCoreClient.getUserPermissions(email)).thenReturn(permissions);
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void emptyOrders_returnsEmptyList() {
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of());

        assertThat(service.listAllActuariesProfit()).isEmpty();
        verify(bankaCoreClient, never()).getUserById(anyString(), any());
    }

    @Test
    void singleActuary_sellMinusBuy_sameCurrency_noFx() {
        Listing belex = listing(1L, "BELEX"); // RSD listing
        // BUY 10 @ 100 = 1000 cost; SELL 10 @ 150 = 1500 value; profit = 500 RSD
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(7L, UserRole.EMPLOYEE, belex, OrderDirection.BUY, 10, "100.00"),
                order(7L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 10, "150.00")));
        mockEmployee(7L, "Ana", "Anic", "ana@test.com", List.of("AGENT"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        assertThat(result).hasSize(1);
        ActuaryProfitDto dto = result.get(0);
        assertThat(dto.getEmployeeId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("Ana Anic");
        assertThat(dto.getPosition()).isEqualTo("AGENT");
        assertThat(dto.getTotalProfitRsd()).isEqualByComparingTo("500.00");
        assertThat(dto.getOrdersDone()).isEqualTo(2);
        // RSD listing -> nikad ne zove FX konverziju
        verify(currencyConversionService, never()).convert(any(), anyString(), anyString());
    }

    @Test
    void foreignCurrencyListing_convertsProfitToRsd() {
        Listing nasdaq = listing(2L, "NASDAQ"); // USD listing
        // SELL 5 @ 200 = 1000 USD; BUY 5 @ 120 = 600 USD; profit = 400 USD
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(8L, UserRole.EMPLOYEE, nasdaq, OrderDirection.BUY, 5, "120.00"),
                order(8L, UserRole.EMPLOYEE, nasdaq, OrderDirection.SELL, 5, "200.00")));
        mockEmployee(8L, "Marko", "Markic", "marko@test.com", List.of("SUPERVISOR"));
        // 400 USD -> 47200 RSD (kurs 118)
        when(currencyConversionService.convert(new BigDecimal("400.00"), "USD", "RSD"))
                .thenReturn(new BigDecimal("47200.00"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalProfitRsd()).isEqualByComparingTo("47200.00");
        assertThat(result.get(0).getPosition()).isEqualTo("SUPERVISOR");
    }

    @Test
    void clientAndFundOrders_areIgnored() {
        Listing belex = listing(3L, "BELEX");
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(99L, UserRole.CLIENT, belex, OrderDirection.SELL, 10, "150.00"),
                order(50L, "FUND", belex, OrderDirection.SELL, 10, "150.00")));

        assertThat(service.listAllActuariesProfit()).isEmpty();
        verify(bankaCoreClient, never()).getUserById(anyString(), any());
    }

    @Test
    void orderWithNullListing_isSkipped() {
        Listing belex = listing(4L, "BELEX");
        Order nullListingOrder = order(11L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 1, "10.00");
        nullListingOrder.setListing(null);
        Order validOrder = order(11L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 2, "100.00");
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(nullListingOrder, validOrder));
        mockEmployee(11L, "Ivo", "Ivic", "ivo@test.com", List.of("AGENT"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        // samo validOrder uracunat: SELL 2 @ 100 = 200 RSD profit (nema BUY)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalProfitRsd()).isEqualByComparingTo("200.00");
        assertThat(result.get(0).getOrdersDone()).isEqualTo(1);
    }

    @Test
    void multipleActuaries_sortedByProfitDescending() {
        Listing belex = listing(5L, "BELEX");
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                // aktuar 1: profit 100
                order(1L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 1, "100.00"),
                // aktuar 2: profit 900
                order(2L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 9, "100.00")));
        mockEmployee(1L, "Low", "Profit", "low@test.com", List.of("AGENT"));
        mockEmployee(2L, "High", "Profit", "high@test.com", List.of("SUPERVISOR"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(2L); // 900 RSD prvi
        assertThat(result.get(1).getEmployeeId()).isEqualTo(1L); // 100 RSD drugi
    }

    @Test
    void adminPermission_resolvesToSupervisorPosition() {
        Listing belex = listing(6L, "BELEX");
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(3L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 1, "100.00")));
        mockEmployee(3L, "Adm", "Inistrator", "admin@test.com", List.of("ADMIN"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        assertThat(result.get(0).getPosition()).isEqualTo("SUPERVISOR");
    }

    @Test
    void actuarNoLongerInBankaCore_isExcludedFromList() {
        Listing belex = listing(7L, "BELEX");
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(404L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 1, "100.00"),
                order(5L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 2, "100.00")));
        // 404 -> banka-core baca exception (zaposleni vise ne postoji)
        when(bankaCoreClient.getUserById(UserRole.EMPLOYEE, 404L))
                .thenThrow(new BankaCoreClientException(404, "not found"));
        mockEmployee(5L, "Still", "Here", "still@test.com", List.of("AGENT"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        // samo aktuar #5 ostaje; #404 izostavljen
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(5L);
    }

    @Test
    void permissionsLookupFailure_fallsBackToAgentPosition() {
        Listing belex = listing(8L, "BELEX");
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(6L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 1, "100.00")));
        when(bankaCoreClient.getUserById(UserRole.EMPLOYEE, 6L)).thenReturn(
                new InternalUserDto(6L, "EMPLOYEE", "x@test.com", "Perm", "Fail", true, "Agent"));
        // permisije lookup pada -> graceful AGENT default
        when(bankaCoreClient.getUserPermissions("x@test.com"))
                .thenThrow(new BankaCoreClientException(500, "boom"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPosition()).isEqualTo("AGENT");
    }

    @Test
    void nullPricePerUnit_treatedAsZero() {
        Listing belex = listing(9L, "BELEX");
        // BUY sa null cenom -> 0 cost; SELL 1 @ 100 -> 100 value; profit 100
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(12L, UserRole.EMPLOYEE, belex, OrderDirection.BUY, 5, null),
                order(12L, UserRole.EMPLOYEE, belex, OrderDirection.SELL, 1, "100.00")));
        mockEmployee(12L, "Null", "Price", "null@test.com", List.of("AGENT"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        assertThat(result.get(0).getTotalProfitRsd()).isEqualByComparingTo("100.00");
    }

    @Test
    void fxConversionFailure_fallsBackToRawAmount() {
        Listing nasdaq = listing(10L, "NASDAQ"); // USD
        when(orderRepository.findByIsDoneTrue()).thenReturn(List.of(
                order(13L, UserRole.EMPLOYEE, nasdaq, OrderDirection.SELL, 3, "100.00")));
        mockEmployee(13L, "Fx", "Fail", "fx@test.com", List.of("AGENT"));
        // 300 USD profit; konverzija puca -> koristi se raw 300
        when(currencyConversionService.convert(eq(new BigDecimal("300.00")), eq("USD"), eq("RSD")))
                .thenThrow(new RuntimeException("exchange unreachable"));

        List<ActuaryProfitDto> result = service.listAllActuariesProfit();

        assertThat(result.get(0).getTotalProfitRsd()).isEqualByComparingTo("300.00");
    }
}
