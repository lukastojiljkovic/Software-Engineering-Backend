package rs.raf.banka2_bek.order.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.mapper.OrderMapper;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.BankTradingAccountResolver;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.order.service.FundReservationService;
import rs.raf.banka2_bek.order.service.FundsVerificationService;
import rs.raf.banka2_bek.order.service.ListingPriceService;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.order.service.OrderStatusService;
import rs.raf.banka2_bek.order.service.OrderValidationService;
import rs.raf.banka2_bek.berza.service.ExchangeManagementService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final ActuaryInfoRepository actuaryInfoRepository;
    private final ClientRepository clientRepository;
    private final EmployeeRepository employeeRepository;
    private final OrderValidationService orderValidationService;
    private final ListingPriceService listingPriceService;
    private final FundsVerificationService fundsVerificationService;
    private final OrderStatusService orderStatusService;
    private final ExchangeManagementService exchangeManagementService;
    private final AccountRepository accountRepository;
    private final FundReservationService fundReservationService;
    private final BankTradingAccountResolver bankTradingAccountResolver;
    private final CurrencyConversionService currencyConversionService;

    @Override
    @Transactional
    public OrderDto createOrder(CreateOrderDto dto) {
        // Step 1: Validate input
        orderValidationService.validate(dto);

        OrderType orderType = orderValidationService.parseOrderType(dto.getOrderType());
        OrderDirection direction = orderValidationService.parseDirection(dto.getDirection());

        // Step 2: Fetch listing
        Listing listing = listingRepository.findById(dto.getListingId())
                .orElseThrow(() -> new EntityNotFoundException("Listing not found"));

        // Step 3: Determine price
        BigDecimal pricePerUnit = listingPriceService.getPricePerUnit(dto, listing, orderType, direction);
        BigDecimal approximatePrice = listingPriceService.calculateApproximatePrice(
                dto.getContractSize(), pricePerUnit, dto.getQuantity());

        // Step 4: Resolve current user
        UserContext userContext = resolveCurrentUser();
        boolean isEmployee = "EMPLOYEE".equals(userContext.userRole);

        // Step 5: Resolve account (bankin za zaposlene, licni za klijente)
        //         i izracunaj rezervacioni iznos u valuti tog racuna
        String listingCurrencyCode = resolveListingCurrency(listing);
        Account account;
        if (direction == OrderDirection.BUY) {
            if (isEmployee) {
                account = bankTradingAccountResolver.resolve(listingCurrencyCode);
            } else {
                account = accountRepository.findForUpdateById(dto.getAccountId())
                        .orElseThrow(() -> new EntityNotFoundException("Racun ne postoji: " + dto.getAccountId()));
            }
        } else {
            // SELL — TODO Phase 4: reservation za portfolio
            account = null;
        }

        BigDecimal exchangeRate = null;
        BigDecimal totalReservation = null;
        if (direction == OrderDirection.BUY && account != null) {
            String accountCurrencyCode = account.getCurrency().getCode();
            exchangeRate = currencyConversionService.getRate(listingCurrencyCode, accountCurrencyCode);
            BigDecimal approxInAccountCurrency = currencyConversionService.convert(
                    approximatePrice, listingCurrencyCode, accountCurrencyCode);
            // Provizija se obracunava u listing (USD-denominovanoj) valuti, zatim se konvertuje
            // u valutu racuna — tako cap od $7/$12 ostaje ispravan za sve kombinacije valuta.
            BigDecimal commissionInAccountCurrency;
            if (isEmployee) {
                commissionInAccountCurrency = BigDecimal.ZERO;
            } else {
                BigDecimal commissionInListingCurrency = calculateCommissionInListingCurrency(approximatePrice, orderType);
                commissionInAccountCurrency = currencyConversionService.convert(
                        commissionInListingCurrency, listingCurrencyCode, accountCurrencyCode);
            }
            totalReservation = approxInAccountCurrency.add(commissionInAccountCurrency)
                    .setScale(4, RoundingMode.HALF_UP);
        }

        // Step 6: Verify funds / securities
        //   BUY (klijent + agent): koristimo availableBalance proveru na resolvovanom racunu
        //   SELL: postojeca logika (portfolio check) — TODO Phase 4 zameniti sa reservedQuantity
        if (direction == OrderDirection.BUY && account != null && totalReservation != null) {
            if (account.getAvailableBalance().compareTo(totalReservation) < 0) {
                throw new InsufficientFundsException(
                        "Nedovoljno raspolozivih sredstava na racunu " + account.getAccountNumber());
            }
        } else if (direction == OrderDirection.SELL) {
            fundsVerificationService.verify(dto, userContext.userId, approximatePrice, listing, orderType, direction);
        }

        // Step 7: Determine status
        OrderStatus status = orderStatusService.determineStatus(userContext.userRole, userContext.userId, approximatePrice);
        String approvedBy = (status == OrderStatus.APPROVED) ? "No need for approval" : null;

        // Step 8: Compute afterHours
        boolean afterHours = computeAfterHours(listing);

        // Step 9: Build and save order
        Order order = OrderMapper.fromCreateDto(dto, listing);
        order.setUserId(userContext.userId);
        // S44 fix: eksplicitno setujemo userRole sa resolved userContext-a
        order.setUserRole(userContext.userRole);
        order.setPricePerUnit(pricePerUnit);
        order.setApproximatePrice(approximatePrice);
        order.setStatus(status);
        order.setApprovedBy(approvedBy);
        order.setAfterHours(afterHours);

        if (direction == OrderDirection.BUY && account != null) {
            order.setReservedAccountId(account.getId());
            order.setReservedAmount(totalReservation);
            order.setExchangeRate(exchangeRate);
            // Za agente pisemo bankin racun i na accountId da fill ima referencu
            if (isEmployee) {
                order.setAccountId(account.getId());
            }
        }

        if (status == OrderStatus.APPROVED) {
            order.setApprovedAt(LocalDateTime.now());
        }

        Order savedOrder = orderRepository.save(order);

        // Step 10: Rezervacija sredstava za BUY ordere koji su odmah APPROVED
        if (direction == OrderDirection.BUY && status == OrderStatus.APPROVED && account != null) {
            fundReservationService.reserveForBuy(savedOrder, account);
        }
        // TODO Phase 4: reserveForSell za SELL ordere

        // Step 11: Update agent usedLimit if APPROVED
        if (status == OrderStatus.APPROVED && isEmployee) {
            final BigDecimal limitDelta = totalReservation != null ? totalReservation : approximatePrice;
            Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(userContext.userId);
            actuaryOpt.ifPresent(actuary -> {
                if (actuary.getActuaryType() == ActuaryType.AGENT) {
                    BigDecimal current = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
                    actuary.setUsedLimit(current.add(limitDelta));
                    actuaryInfoRepository.save(actuary);
                }
            });
        }

        // Step 12: Execution handled by OrderScheduler cron job

        return toDtoWithUserName(savedOrder);
    }

    /**
     * Resolve-uje ISO kod valute za dati listing.
     * Za FOREX koristi baseCurrency, inace mapira iz exchange acronym-a.
     * Fallback je USD.
     */
    private String resolveListingCurrency(Listing listing) {
        if (listing.getListingType() == ListingType.FOREX && listing.getBaseCurrency() != null) {
            return listing.getBaseCurrency();
        }
        String exchange = listing.getExchangeAcronym();
        if (exchange == null) {
            return "USD";
        }
        return switch (exchange.toUpperCase()) {
            case "NYSE", "NASDAQ", "CME" -> "USD";
            case "LSE" -> "GBP";
            case "XETRA" -> "EUR";
            case "BELEX" -> "RSD";
            default -> "USD";
        };
    }

    /**
     * Racuna proviziju u valuti listinga (gde USD cap ima smisla).
     * Spec: Market min(14% * cena, $7), Limit min(24% * cena, $12).
     * Za non-USD listinge, cap od $7/$12 se tretira kao literal iznos
     * u listing valuti — pragmaticna aproksimacija jer se vecina listinga
     * denominuje u USD.
     */
    private BigDecimal calculateCommissionInListingCurrency(BigDecimal approxInListingCurrency, OrderType orderType) {
        return switch (orderType) {
            case MARKET, STOP -> approxInListingCurrency.multiply(new BigDecimal("0.14"))
                    .min(new BigDecimal("7"))
                    .setScale(4, RoundingMode.HALF_UP);
            case LIMIT, STOP_LIMIT -> approxInListingCurrency.multiply(new BigDecimal("0.24"))
                    .min(new BigDecimal("12"))
                    .setScale(4, RoundingMode.HALF_UP);
        };
    }

    @Override
    @Transactional
    public OrderDto approveOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"+ orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be approved");
        }

        String supervisorName = getSupervisorName();

        Listing listing = order.getListing();
        if (listing.getSettlementDate() != null &&
                listing.getSettlementDate().isBefore(java.time.LocalDate.now())) {
            order.setStatus(OrderStatus.DECLINED);
            order.setApprovedBy(supervisorName);
            order.setLastModification(LocalDateTime.now());
            Order saved = orderRepository.save(order);
            return toDtoWithUserName(saved);
        }
        order.setStatus(OrderStatus.APPROVED);
        order.setApprovedBy(supervisorName);
        order.setLastModification(LocalDateTime.now());

        Order saved = orderRepository.save(order);

        // Update agent usedLimit when supervisor approves
        if ("EMPLOYEE".equals(order.getUserRole())) {
            Optional<ActuaryInfo> actuaryOpt = orderStatusService.getAgentInfo(order.getUserId());
            actuaryOpt.ifPresent(actuary -> {
                if (actuary.getActuaryType() == ActuaryType.AGENT) {
                    BigDecimal current = actuary.getUsedLimit() != null ? actuary.getUsedLimit() : BigDecimal.ZERO;
                    BigDecimal orderPrice = order.getApproximatePrice() != null ? order.getApproximatePrice() : BigDecimal.ZERO;
                    actuary.setUsedLimit(current.add(orderPrice));
                    actuaryInfoRepository.save(actuary);
                }
            });
        }

        return toDtoWithUserName(saved);
    }

    @Override
    @Transactional
    public OrderDto declineOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found " + orderId));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.APPROVED) {
            throw new IllegalStateException("Only PENDING or APPROVED orders can be declined/cancelled");
        }

        String supervisorName = getSupervisorName();

        order.setStatus(OrderStatus.DECLINED);
        order.setApprovedBy(supervisorName);
        order.setLastModification(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        return toDtoWithUserName(saved);
    }

    @Override
    public Page<OrderDto> getAllOrders(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            return orderRepository.findAll(pageable).map(this::toDtoWithUserName);
        }

        OrderStatus orderStatus;
        try {
            orderStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status +
                    ". Valid status: ALL, PENDING, APPROVED, DECLINED, DONE");
        }

        return orderRepository.findByStatus(orderStatus, pageable).map(this::toDtoWithUserName);
    }

    @Override
    public Page<OrderDto> getMyOrders(int page, int size) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Optional<Client> clientOpt = clientRepository.findByEmail(email);
        if (clientOpt.isPresent()) {
            return orderRepository.findByUserId(clientOpt.get().getId(), pageable).map(this::toDtoWithUserName);
        }

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return orderRepository.findByUserId(employee.getId(), pageable).map(this::toDtoWithUserName);
    }
    @Override
    public OrderDto getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order with ID " + orderId + " not found"));

        boolean isSupervisor = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EMPLOYEE"));

        if (isSupervisor) {
            return toDtoWithUserName(order);
        }

        Long currentUserId = resolveCurrentUser().userId();
        if (!order.getUserId().equals(currentUserId)) {
            throw new IllegalStateException("You dont have access to this account");
        }

        return toDtoWithUserName(order);
    }

    private UserContext resolveCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<Client> clientOpt = clientRepository.findByEmail(email);
        if (clientOpt.isPresent()) {
            return new UserContext(clientOpt.get().getId(), "CLIENT");
        }

        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return new UserContext(employee.getId(), "EMPLOYEE");
    }

    private boolean computeAfterHours(Listing listing) {
        String exchange = listing.getExchangeAcronym();
        if (exchange == null) return false;

        try {
            return exchangeManagementService.isAfterHours(exchange);
        } catch (Exception e) {
            // Exchange not found or unknown — treat as not after-hours
            return false;
        }
    }
    private String getSupervisorName() {
        UserContext userContext = resolveCurrentUser();
        return employeeRepository.findById(userContext.userId())
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElseThrow(() -> new IllegalStateException("Supervisor not found"));
    }

    private String resolveUserName(Long userId, String userRole) {
        if ("CLIENT".equals(userRole)) {
            return clientRepository.findById(userId)
                    .map(c -> c.getFirstName() + " " + c.getLastName())
                    .orElse("Unknown");
        }
        return employeeRepository.findById(userId)
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse("Unknown");
    }

    private OrderDto toDtoWithUserName(Order order) {
        String userName = resolveUserName(order.getUserId(), order.getUserRole());
        return OrderMapper.toDto(order, userName);
    }

    private record UserContext(Long userId, String userRole) {}
}
