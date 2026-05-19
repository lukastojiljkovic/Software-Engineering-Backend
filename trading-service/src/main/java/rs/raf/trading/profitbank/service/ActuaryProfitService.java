package rs.raf.trading.profitbank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
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
import rs.raf.trading.stock.util.ListingCurrencyResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P6 — Spec Celina 4 (Nova) §4393-4505 (Stranica: Profit aktuara).
 *
 * Za svakog aktuara (supervizor + agent) racuna ukupan profit u RSD:
 *   - Per-listing: SELL value - BUY cost (samo done orderi)
 *   - Konvertuj u RSD po srednjem kursu (bez komisije)
 *   - Sumiraj kroz sve listinge
 *
 * Cache: Caffeine sa TTL 5 min (vidi {@link ProfitBankCacheConfig}).
 * Iteracija po svim DONE orderima + per-order FX konverzija je O(n) u
 * broju ordera; posle 1000+ ordera u bazi sirov racun traje ~1-2s. Cache
 * smanjuje na ~5ms na ponovljene pozive sa istim ulazom.
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-E): {@code Order}/{@code Listing}
 * su lokalni trading-service entiteti (faza 2b/2c). Identitet aktuara
 * (zaposlenog) je banka-core domen — monolitni {@code EmployeeRepository}
 * lookup je zamenjen {@link BankaCoreClient#getUserById(String, Long)}
 * (ime + prezime) i {@link BankaCoreClient#getUserPermissions(String)}
 * (SUPERVISOR vs AGENT pozicija). Servis nista ne mutira — nema novcanog
 * seam-a; samo cita i racuna.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActuaryProfitService {

    private final OrderRepository orderRepository;
    private final BankaCoreClient bankaCoreClient;
    private final CurrencyConversionService currencyConversionService;

    @Cacheable(value = ProfitBankCacheConfig.ACTUARY_PROFIT_CACHE, sync = true)
    public List<ActuaryProfitDto> listAllActuariesProfit() {
        // 1) Skupi sve DONE ordere koje su inicirali zaposleni (userRole=EMPLOYEE).
        List<Order> doneEmployeeOrders = orderRepository.findByIsDoneTrue().stream()
                .filter(o -> UserRole.isEmployee(o.getUserRole()))
                .toList();

        // 2) Per-aktuar: per-listing { sell, buy }
        Map<Long, Map<Long, BigDecimal>> sellByActuarPerListing = new HashMap<>();
        Map<Long, Map<Long, BigDecimal>> buyByActuarPerListing = new HashMap<>();
        Map<Long, Map<Long, String>> currencyByActuarPerListing = new HashMap<>();
        Map<Long, Integer> ordersDoneCount = new HashMap<>();

        for (Order order : doneEmployeeOrders) {
            if (order.getListing() == null) continue;
            Long actuarId = order.getUserId();
            Long listingId = order.getListing().getId();
            BigDecimal value = nullSafe(order.getPricePerUnit())
                    .multiply(BigDecimal.valueOf(order.getQuantity()))
                    .multiply(BigDecimal.valueOf(order.getContractSize()));

            currencyByActuarPerListing
                    .computeIfAbsent(actuarId, k -> new HashMap<>())
                    .putIfAbsent(listingId, resolveOrderCurrency(order.getListing()));

            if (order.getDirection() == OrderDirection.SELL) {
                sellByActuarPerListing
                        .computeIfAbsent(actuarId, k -> new HashMap<>())
                        .merge(listingId, value, BigDecimal::add);
            } else {
                buyByActuarPerListing
                        .computeIfAbsent(actuarId, k -> new HashMap<>())
                        .merge(listingId, value, BigDecimal::add);
            }
            ordersDoneCount.merge(actuarId, 1, Integer::sum);
        }

        // 3) Per-aktuar: sum profit u RSD
        Set<Long> allActuarIds = new HashSet<>();
        allActuarIds.addAll(sellByActuarPerListing.keySet());
        allActuarIds.addAll(buyByActuarPerListing.keySet());

        return allActuarIds.stream()
                .map(actuarId -> buildActuaryProfit(
                        actuarId,
                        sellByActuarPerListing.getOrDefault(actuarId, Map.of()),
                        buyByActuarPerListing.getOrDefault(actuarId, Map.of()),
                        currencyByActuarPerListing.getOrDefault(actuarId, Map.of()),
                        ordersDoneCount.getOrDefault(actuarId, 0)))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ActuaryProfitDto::getTotalProfitRsd).reversed())
                .toList();
    }

    private ActuaryProfitDto buildActuaryProfit(
            Long actuarId,
            Map<Long, BigDecimal> sellByListing,
            Map<Long, BigDecimal> buyByListing,
            Map<Long, String> currencyByListing,
            int ordersDone) {
        // Identitet aktuara razresava banka-core. Ako zaposleni vise ne postoji
        // (banka-core vrati 404 -> BankaCoreClientException), aktuar se izostavlja
        // iz liste — verno monolitnom `employeeRepository.findById(...).orElse(null)`.
        InternalUserDto emp;
        try {
            emp = bankaCoreClient.getUserById(UserRole.EMPLOYEE, actuarId);
        } catch (BankaCoreClientException e) {
            log.warn("Aktuar #{} nije razresiv preko banka-core ({}); izostavljam iz liste",
                    actuarId, e.getMessage());
            return null;
        }
        if (emp == null) {
            return null;
        }

        BigDecimal totalProfitRsd = BigDecimal.ZERO;
        Set<Long> allListings = new HashSet<>(sellByListing.keySet());
        allListings.addAll(buyByListing.keySet());
        for (Long listingId : allListings) {
            BigDecimal sell = sellByListing.getOrDefault(listingId, BigDecimal.ZERO);
            BigDecimal buy = buyByListing.getOrDefault(listingId, BigDecimal.ZERO);
            BigDecimal assetProfit = sell.subtract(buy);
            String ccy = currencyByListing.getOrDefault(listingId, "RSD");
            totalProfitRsd = totalProfitRsd.add(convertToRsd(assetProfit, ccy));
        }

        Set<String> perms = resolvePermissions(emp.email());
        // Admin je uvek supervisor (po Celini 3); ako nema ni jedno ni drugo, AGENT.
        String position = (perms.contains("SUPERVISOR") || perms.contains("ADMIN"))
                ? "SUPERVISOR" : "AGENT";

        String name = (nullSafeStr(emp.firstName()) + " " + nullSafeStr(emp.lastName())).trim();

        return new ActuaryProfitDto(
                actuarId,
                name.isEmpty() ? "Unknown" : name,
                position,
                totalProfitRsd.setScale(2, RoundingMode.HALF_UP),
                ordersDone);
    }

    /**
     * Razresava permisije zaposlenog preko banka-core. Permisije odredjuju samo
     * prikaznu poziciju (SUPERVISOR / AGENT) — ako banka-core lookup padne,
     * gracefully vracamo prazan skup (sto monolitno mapira na AGENT default).
     */
    private Set<String> resolvePermissions(String email) {
        if (email == null || email.isBlank()) {
            return Set.of();
        }
        try {
            return new HashSet<>(bankaCoreClient.getUserPermissions(email));
        } catch (BankaCoreClientException e) {
            log.warn("Permisije za {} nisu razresive preko banka-core ({}); "
                    + "podrazumevam AGENT poziciju", email, e.getMessage());
            return Set.of();
        }
    }

    private BigDecimal convertToRsd(BigDecimal amount, String fromCurrency) {
        if (amount == null || amount.signum() == 0) return BigDecimal.ZERO;
        if (fromCurrency == null || "RSD".equalsIgnoreCase(fromCurrency)) return amount;
        try {
            return currencyConversionService.convert(amount, fromCurrency, "RSD");
        } catch (RuntimeException e) {
            log.warn("Konverzija {} -> RSD nije uspela ({}); koristim raw amount", fromCurrency, e.getMessage());
            return amount;
        }
    }

    private String resolveOrderCurrency(Listing listing) {
        return ListingCurrencyResolver.resolveSafe(listing, "RSD");
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String nullSafeStr(String value) {
        return value != null ? value : "";
    }
}
