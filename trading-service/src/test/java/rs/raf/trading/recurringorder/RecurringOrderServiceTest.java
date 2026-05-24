package rs.raf.trading.recurringorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.common.UserContext;
import rs.raf.trading.notification.service.NotificationService;
import rs.raf.trading.order.dto.CreateOrderDto;
import rs.raf.trading.order.service.OrderService;
import rs.raf.trading.recurringorder.dto.CreateRecurringOrderDto;
import rs.raf.trading.recurringorder.dto.RecurringOrderDto;
import rs.raf.trading.recurringorder.model.RecurringCadence;
import rs.raf.trading.recurringorder.model.RecurringMode;
import rs.raf.trading.recurringorder.model.RecurringOrder;
import rs.raf.trading.recurringorder.repository.RecurringOrderRepository;
import rs.raf.trading.recurringorder.service.RecurringOrderService;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit testovi za {@link RecurringOrderService} (mikroservisi varijanta).
 *
 * <p>Order i Listing su lokalni u trading-service-u; racun zivi u banka-core
 * i razresava se kroz {@link BankaCoreClient}. Svi pozivi su mock-ovani —
 * nema @SpringBootTest ni H2 konteksta.
 */
@ExtendWith(MockitoExtension.class)
public class RecurringOrderServiceTest {

    @Mock
    private RecurringOrderRepository recurringOrderRepo;

    @Mock
    private TradingUserResolver userResolver;

    @Mock
    private OrderService orderService;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private BankaCoreClient bankaCoreClient;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RecurringOrderService recurringOrderService;

    private UserContext clientContext;
    private UserContext employeeContext;

    @BeforeEach
    void setUp() {
        clientContext = new UserContext(1L, "CLIENT");
        employeeContext = new UserContext(2L, "EMPLOYEE");
    }

    private InternalAccountDto clientAccount(Long accountId, Long ownerClientId, BigDecimal available) {
        return new InternalAccountDto(
                accountId, "222000" + accountId, "Owner",
                available, available, BigDecimal.ZERO, "RSD",
                "ACTIVE",
                ownerClientId, null, "PERSONAL");
    }

    private InternalAccountDto employeeAccount(Long accountId, Long ownerEmployeeId, BigDecimal available) {
        return new InternalAccountDto(
                accountId, "222000" + accountId, "Owner",
                available, available, BigDecimal.ZERO, "RSD",
                "ACTIVE",
                null, ownerEmployeeId, "BANK_TRADING");
    }

    @Test
    void create_clientCanCreateRecurringOrder() {
        when(userResolver.resolveCurrent()).thenReturn(clientContext);
        when(bankaCoreClient.getAccount(1L))
                .thenReturn(clientAccount(1L, 1L, new BigDecimal("1000")));

        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("150"));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        RecurringOrder savedOrder = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC).plusDays(1))
                .active(true)
                .build();
        when(recurringOrderRepo.save(any())).thenReturn(savedOrder);

        CreateRecurringOrderDto dto = new CreateRecurringOrderDto();
        dto.setListingId(1L);
        dto.setDirection("BUY");
        dto.setMode(RecurringMode.BY_QUANTITY);
        dto.setValue(new BigDecimal("5"));
        dto.setAccountId(1L);
        dto.setCadence(RecurringCadence.DAILY);

        RecurringOrderDto result = recurringOrderService.create(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getOwnerId()).isEqualTo(1L);
        assertThat(result.getOwnerType()).isEqualTo("CLIENT");
        verify(recurringOrderRepo).save(any(RecurringOrder.class));
    }

    @Test
    void create_clientBlockedWhenAccountBelongsToSomeoneElse() {
        when(userResolver.resolveCurrent()).thenReturn(clientContext);
        // Account belongs to client 999, not the current client (id 1)
        when(bankaCoreClient.getAccount(1L))
                .thenReturn(clientAccount(1L, 999L, new BigDecimal("1000")));

        CreateRecurringOrderDto dto = new CreateRecurringOrderDto();
        dto.setListingId(1L);
        dto.setDirection("BUY");
        dto.setMode(RecurringMode.BY_QUANTITY);
        dto.setValue(new BigDecimal("5"));
        dto.setAccountId(1L);
        dto.setCadence(RecurringCadence.DAILY);

        assertThatThrownBy(() -> recurringOrderService.create(dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_employeeBlockedWhenUsingClientAccount() {
        when(userResolver.resolveCurrent()).thenReturn(employeeContext);
        // Account belongs to a client → employee cannot use it
        when(bankaCoreClient.getAccount(1L))
                .thenReturn(clientAccount(1L, 5L, new BigDecimal("1000")));

        CreateRecurringOrderDto dto = new CreateRecurringOrderDto();
        dto.setListingId(1L);
        dto.setDirection("BUY");
        dto.setMode(RecurringMode.BY_QUANTITY);
        dto.setValue(new BigDecimal("5"));
        dto.setAccountId(1L);
        dto.setCadence(RecurringCadence.DAILY);

        assertThatThrownBy(() -> recurringOrderService.create(dto))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_invalidListingThrows() {
        when(userResolver.resolveCurrent()).thenReturn(clientContext);
        when(bankaCoreClient.getAccount(1L))
                .thenReturn(clientAccount(1L, 1L, new BigDecimal("1000")));
        when(listingRepository.findById(999L)).thenReturn(Optional.empty());

        CreateRecurringOrderDto dto = new CreateRecurringOrderDto();
        dto.setListingId(999L);
        dto.setDirection("BUY");
        dto.setMode(RecurringMode.BY_QUANTITY);
        dto.setValue(new BigDecimal("5"));
        dto.setAccountId(1L);
        dto.setCadence(RecurringCadence.DAILY);

        assertThatThrownBy(() -> recurringOrderService.create(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hartija od vrednosti ne postoji");
    }

    @Test
    void listMy_returnsOnlyOwnersOrders() {
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        RecurringOrder order1 = RecurringOrder.builder()
                .id(1L).ownerId(1L).ownerType("CLIENT").listingId(1L)
                .direction("BUY").mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5")).accountId(1L)
                .cadence(RecurringCadence.DAILY).active(true).build();
        RecurringOrder order2 = RecurringOrder.builder()
                .id(2L).ownerId(1L).ownerType("CLIENT").listingId(2L)
                .direction("BUY").mode(RecurringMode.BY_AMOUNT)
                .value(new BigDecimal("100")).accountId(1L)
                .cadence(RecurringCadence.WEEKLY).active(false).build();

        when(recurringOrderRepo.findByOwnerIdAndOwnerTypeOrderByCreatedAtDesc(1L, "CLIENT"))
                .thenReturn(List.of(order1, order2));

        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        when(listingRepository.findById(anyLong())).thenReturn(Optional.of(listing));

        List<RecurringOrderDto> result = recurringOrderService.listMy();

        assertThat(result).hasSize(2);
    }

    @Test
    void pause_setsActiveToFalse() {
        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .active(true)
                .build();

        when(recurringOrderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        order.setActive(false);
        when(recurringOrderRepo.save(any())).thenReturn(order);

        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        RecurringOrderDto result = recurringOrderService.pause(1L);

        assertThat(result.isActive()).isFalse();
        ArgumentCaptor<RecurringOrder> captor = ArgumentCaptor.forClass(RecurringOrder.class);
        verify(recurringOrderRepo).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void pause_throwsWhenNotOwner() {
        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(999L)
                .ownerType("CLIENT")
                .build();

        when(recurringOrderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        assertThatThrownBy(() -> recurringOrderService.pause(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resume_setsActiveToTrueAndAdvancesNextRun() {
        LocalDateTime past = LocalDateTime.now(ZoneOffset.UTC).minusDays(2);
        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(past)
                .active(false)
                .build();

        when(recurringOrderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(userResolver.resolveCurrent()).thenReturn(clientContext);
        when(recurringOrderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        RecurringOrderDto result = recurringOrderService.resume(1L);

        assertThat(result.isActive()).isTrue();
        assertThat(result.getNextRun()).isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    }

    @Test
    void cancel_deletesOrder() {
        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .build();

        when(recurringOrderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(userResolver.resolveCurrent()).thenReturn(clientContext);

        recurringOrderService.cancel(1L);

        verify(recurringOrderRepo).deleteById(1L);
    }

    @Test
    void executeOne_byQuantity_createsMarketOrder() {
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("150"));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        when(bankaCoreClient.getAccount(1L))
                .thenReturn(clientAccount(1L, 1L, new BigDecimal("1000")));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        recurringOrderService.executeOne(order);

        ArgumentCaptor<CreateOrderDto> captor = ArgumentCaptor.forClass(CreateOrderDto.class);
        verify(orderService).createOrder(captor.capture());
        CreateOrderDto created = captor.getValue();
        assertThat(created.getQuantity()).isEqualTo(5);
        assertThat(created.getOrderType()).isEqualTo("MARKET");
        assertThat(created.getDirection()).isEqualTo("BUY");
    }

    @Test
    void executeOne_byAmount_calculatesQuantityFromPrice() {
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("200"));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        when(bankaCoreClient.getAccount(1L))
                .thenReturn(clientAccount(1L, 1L, new BigDecimal("2000")));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_AMOUNT)
                .value(new BigDecimal("1000"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        recurringOrderService.executeOne(order);

        ArgumentCaptor<CreateOrderDto> captor = ArgumentCaptor.forClass(CreateOrderDto.class);
        verify(orderService).createOrder(captor.capture());
        CreateOrderDto created = captor.getValue();
        assertThat(created.getQuantity()).isEqualTo(5); // floor(1000/200) = 5
    }

    @Test
    void executeOne_insufficientFunds_skipsAndAdvancesNextRun() {
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("150"));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        when(bankaCoreClient.getAccount(1L))
                .thenReturn(clientAccount(1L, 1L, BigDecimal.ZERO));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_QUANTITY)
                .value(new BigDecimal("5"))
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        recurringOrderService.executeOne(order);

        verify(orderService, never()).createOrder(any());
        verify(recurringOrderRepo).save(any());
    }

    @Test
    void executeOne_quantityLessThanOne_skips() {
        Listing listing = new Listing();
        listing.setId(1L);
        listing.setTicker("AAPL");
        listing.setPrice(new BigDecimal("200"));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(listing));

        RecurringOrder order = RecurringOrder.builder()
                .id(1L)
                .ownerId(1L)
                .ownerType("CLIENT")
                .listingId(1L)
                .direction("BUY")
                .mode(RecurringMode.BY_AMOUNT)
                .value(new BigDecimal("0.5")) // floor(0.5/200) = 0
                .accountId(1L)
                .cadence(RecurringCadence.DAILY)
                .nextRun(LocalDateTime.now(ZoneOffset.UTC))
                .active(true)
                .build();

        when(recurringOrderRepo.save(any())).thenReturn(order);

        recurringOrderService.executeOne(order);

        verify(orderService, never()).createOrder(any());
        verify(recurringOrderRepo).save(any());
    }
}
