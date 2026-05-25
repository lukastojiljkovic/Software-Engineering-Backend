package rs.raf.trading.recurringorder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.common.UserContext;
import rs.raf.trading.notification.model.NotificationType;
import rs.raf.trading.notification.service.NotificationService;
import rs.raf.trading.order.dto.CreateOrderDto;
import rs.raf.trading.order.service.OrderService;
import rs.raf.trading.recurringorder.dto.CreateRecurringOrderDto;
import rs.raf.trading.recurringorder.dto.RecurringOrderDto;
import rs.raf.trading.recurringorder.model.RecurringCadence;
import rs.raf.trading.recurringorder.model.RecurringMode;
import rs.raf.trading.recurringorder.model.RecurringOrder;
import rs.raf.trading.recurringorder.repository.RecurringOrderRepository;
import rs.raf.trading.security.TradingUserResolver;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

// ============================================================
// [B8 - Trajni nalozi (DCA / RecurringOrder) | Nosilac: Nikola Djurovic] - DONE
//
// Poslovni servis za upravljanje trajnim nalozima i njihovo izvrsavanje.
//
// Mikroservisi varijanta:
//  - Order/Listing su LOKALNI u trading-service (rs.raf.trading.order/stock)
//  - Racun je u banka-core — razresava se preko BankaCoreClient-a (HTTP RPC)
//  - In-app notifikacije ne perzistuju lokalno — publish-uju se preko RabbitMQ
//    kroz trading NotificationService (IN_APP_GENERIC). Banka-core je vlasnik
//    `notifications` tabele; trading-service salje samo event.
//
// Spec: Zadaci_Backend.pdf, zadatak B8.
// ============================================================
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringOrderService {

    private final RecurringOrderRepository recurringOrderRepo;
    private final TradingUserResolver userResolver;
    private final OrderService orderService;
    private final ListingRepository listingRepository;
    private final BankaCoreClient bankaCoreClient;
    private final NotificationService notificationService;

    @Transactional
    public RecurringOrderDto create(CreateRecurringOrderDto dto) {
        UserContext me = userResolver.resolveCurrent();

        // Verifikuj racun preko banka-core RPC-a (racun nije lokalan u trading-service-u)
        InternalAccountDto account;
        try {
            account = bankaCoreClient.getAccount(dto.getAccountId());
        } catch (BankaCoreClientException ex) {
            throw new IllegalArgumentException("Racun ne postoji");
        }
        if (account == null) {
            throw new IllegalArgumentException("Racun ne postoji");
        }

        if (me.isClient()) {
            if (account.ownerClientId() == null || !account.ownerClientId().equals(me.userId())) {
                throw new AccessDeniedException("Racun ne pripada klijentu.");
            }
        } else if (me.isEmployee()) {
            // Za zaposlene: trebalo bi proveriti da li mogu koristiti taj racun.
            // Ako je racun klijenta, zaposleni ne moze da ga koristi za trajne naloge.
            if (account.ownerClientId() != null) {
                throw new AccessDeniedException("Zaposleni ne moze koristiti klijentske racune za trajne naloge.");
            }
        }

        // Verifikuj hartiju (lokalno)
        Listing listing = listingRepository.findById(dto.getListingId())
                .orElseThrow(() -> new IllegalArgumentException("Hartija od vrednosti ne postoji"));

        // Odredi nextRun
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextRun;
        if (dto.getFirstRun() != null && dto.getFirstRun().isAfter(now)) {
            nextRun = dto.getFirstRun();
        } else {
            nextRun = advanceNextRun(now, dto.getCadence());
        }

        RecurringOrder order = RecurringOrder.builder()
                .ownerId(me.userId())
                .ownerType(me.userRole())
                .listingId(dto.getListingId())
                .direction(dto.getDirection())
                .mode(dto.getMode())
                .value(dto.getValue())
                .accountId(dto.getAccountId())
                .cadence(dto.getCadence())
                .nextRun(nextRun)
                .active(true)
                .build();

        order = recurringOrderRepo.save(order);

        log.info("Trajni nalog kreiran: id={}, owner={}, listing={} ({}), cadence={}",
                order.getId(), me.userId(), dto.getListingId(), listing.getTicker(), dto.getCadence());

        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<RecurringOrderDto> listMy() {
        UserContext me = userResolver.resolveCurrent();
        return recurringOrderRepo.findByOwnerIdAndOwnerTypeOrderByCreatedAtDesc(me.userId(), me.userRole())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecurringOrderDto getById(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        return toDto(order);
    }

    @Transactional
    public RecurringOrderDto pause(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        order.setActive(false);
        order = recurringOrderRepo.save(order);

        log.info("Trajni nalog pauziran: id={}", id);

        return toDto(order);
    }

    @Transactional
    public RecurringOrderDto resume(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        order.setActive(true);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        order.setNextRun(advanceNextRun(now, order.getCadence()));
        order = recurringOrderRepo.save(order);

        log.info("Trajni nalog reaktiviran: id={}, nextRun={}", id, order.getNextRun());

        return toDto(order);
    }

    @Transactional
    public void cancel(Long id) {
        RecurringOrder order = recurringOrderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trajni nalog ne postoji"));

        UserContext me = userResolver.resolveCurrent();
        if (!order.getOwnerId().equals(me.userId())) {
            throw new AccessDeniedException("Trajni nalog ne pripada korisniku.");
        }

        recurringOrderRepo.deleteById(id);

        log.info("Trajni nalog obrisan: id={}", id);
    }

    /**
     * Izvrsava jedan trajni nalog. Poziva se iz {@link rs.raf.trading.recurringorder.scheduler.RecurringOrderScheduler}.
     * REQUIRES_NEW da bi se greska jednog naloga izolovala od ostatka batch-a.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeOne(RecurringOrder recurringOrder) {
        try {
            // a. Dohvati trenutnu cenu (lokalno)
            Listing listing = listingRepository.findById(recurringOrder.getListingId())
                    .orElseThrow(() -> new IllegalArgumentException("Hartija od vrednosti ne postoji"));

            if (listing.getPrice() == null || listing.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Scheduler: Hartija {} nema validnu cenu, preskacem nalog id={}",
                        listing.getTicker(), recurringOrder.getId());
                advanceAndSave(recurringOrder);
                return;
            }

            // b. Izracunaj kolicinu
            long quantity;
            if (recurringOrder.getMode() == RecurringMode.BY_QUANTITY) {
                quantity = recurringOrder.getValue().longValue();
            } else {
                // BY_AMOUNT
                BigDecimal qtyDecimal = recurringOrder.getValue()
                        .divide(listing.getPrice(), RoundingMode.FLOOR);
                quantity = qtyDecimal.longValue();
            }

            // c. Provera kolicine >= 1
            if (quantity < 1) {
                log.warn("Scheduler: Izracunata kolicina < 1 za nalog id={}, preskacem",
                        recurringOrder.getId());
                advanceAndSave(recurringOrder);
                return;
            }

            // d. Verifikuj dostupna sredstva preko banka-core RPC-a
            InternalAccountDto account;
            try {
                account = bankaCoreClient.getAccount(recurringOrder.getAccountId());
            } catch (BankaCoreClientException ex) {
                log.warn("Scheduler: banka-core lookup pao za nalog id={}: {}",
                        recurringOrder.getId(), ex.getMessage());
                advanceAndSave(recurringOrder);
                return;
            }
            if (account == null) {
                log.warn("Scheduler: Racun {} ne postoji za nalog id={}, preskacem",
                        recurringOrder.getAccountId(), recurringOrder.getId());
                advanceAndSave(recurringOrder);
                return;
            }

            BigDecimal estimatedCost = listing.getPrice()
                    .multiply(BigDecimal.valueOf(quantity));

            BigDecimal availableBalance = account.availableBalance();
            if (availableBalance == null || availableBalance.compareTo(estimatedCost) < 0) {
                // Nema dovoljno sredstava — best-effort notifikacija + skip
                notifyInsufficientFunds(recurringOrder);
                log.warn("Scheduler: Nedovoljno sredstava za nalog id={}, dostupno: {}, potrebno: {}",
                        recurringOrder.getId(), availableBalance, estimatedCost);
                advanceAndSave(recurringOrder);
                return;
            }

            // e. Za aktuare (EMPLOYEE): orderService.createOrder ce odbiti ako se prekorace limiti.
            //    U trading-service-u, dnevni limit / usedLimit je odgovornost OrderService-a;
            //    ne dupliramo proveru ovde — exception ce nas dovesti u catch granu.

            // f. Kreiraj Market Order
            //    Scheduler je sistemska akcija — nema realnog korisnika koji
            //    moze da unese TOTP kod. Pozivamo overload sa internalActor=true
            //    sto OrderServiceImpl prepoznaje kao bypass za OTP guard koji
            //    bi inace stigao iz OrderController-a (public REST flow).
            CreateOrderDto orderDto = new CreateOrderDto();
            orderDto.setOrderType("MARKET");
            orderDto.setDirection(recurringOrder.getDirection());
            orderDto.setListingId(recurringOrder.getListingId());
            orderDto.setQuantity((int) quantity);
            orderDto.setAccountId(recurringOrder.getAccountId());
            orderDto.setAllOrNone(false);
            orderDto.setMargin(false);
            // NE postavljamo otpCode — internalActor=true znaci da OTP guard ne stoji.

            orderService.createOrder(orderDto, true);

            log.info("Scheduler: Market order kreiran iz trajnog naloga id={}, quantity={}, listing={}",
                    recurringOrder.getId(), quantity, listing.getTicker());

            // g. Azuriraj nextRun
            advanceAndSave(recurringOrder);

        } catch (Exception e) {
            log.error("Scheduler: Greska pri izvrsavanju trajnog naloga id={}: {}",
                    recurringOrder.getId(), e.getMessage(), e);
            advanceAndSave(recurringOrder);
        }
    }

    private void advanceAndSave(RecurringOrder order) {
        LocalDateTime newNextRun = advanceNextRun(order.getNextRun(), order.getCadence());
        order.setNextRun(newNextRun);
        recurringOrderRepo.save(order);
    }

    private LocalDateTime advanceNextRun(LocalDateTime from, RecurringCadence cadence) {
        return switch (cadence) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
        };
    }

    private void notifyInsufficientFunds(RecurringOrder order) {
        try {
            notificationService.notify(
                    order.getOwnerId(),
                    order.getOwnerType(),
                    NotificationType.RECURRING_ORDER_SKIPPED,
                    "Trajni nalog preskocen - nedovoljna sredstva",
                    "Trajni nalog id=" + order.getId() + " nije izvrsen jer nema dovoljno sredstava na racunu.",
                    "RECURRING_ORDER",
                    order.getId()
            );
        } catch (Exception ex) {
            log.warn("Scheduler: notifikacija o preskocenom trajnom nalogu id={} nije poslata: {}",
                    order.getId(), ex.getMessage());
        }
    }

    private RecurringOrderDto toDto(RecurringOrder order) {
        Listing listing = listingRepository.findById(order.getListingId()).orElse(null);
        String ticker = listing != null ? listing.getTicker() : "N/A";

        RecurringOrderDto dto = new RecurringOrderDto();
        dto.setId(order.getId());
        dto.setOwnerId(order.getOwnerId());
        dto.setOwnerType(order.getOwnerType());
        dto.setListingId(order.getListingId());
        dto.setListingTicker(ticker);
        dto.setDirection(order.getDirection());
        dto.setMode(order.getMode().toString());
        dto.setValue(order.getValue());
        dto.setAccountId(order.getAccountId());
        dto.setCadence(order.getCadence().toString());
        dto.setNextRun(order.getNextRun());
        dto.setActive(order.isActive());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());

        return dto;
    }
}
