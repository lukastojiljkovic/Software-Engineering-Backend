package rs.raf.trading.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.order.dto.CreateOrderDto;
import rs.raf.trading.order.model.OrderDirection;
import rs.raf.trading.order.model.OrderType;
import rs.raf.trading.portfolio.model.Portfolio;
import rs.raf.trading.portfolio.repository.PortfolioRepository;
import rs.raf.trading.stock.model.Listing;
import rs.raf.trading.stock.model.ListingType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * NAPOMENA (copy-first ekstrakcija, faza 2c): provera sredstava (BUY) je
 * read-only — u monolitu je citala {@code Account} iz {@code AccountRepository}.
 * U trading-service-u racuni zive u banka-core domenu, pa se metadata racuna
 * cita preko banka-core internog seam-a ({@link BankaCoreClient#getAccount(Long)}).
 * Stvarna garancija dovoljnosti sredstava je banka-core {@code reserve} koji
 * vraca 409 — ova provera je samo brza pre-validacija. Provera hartija (SELL)
 * dira samo lokalni {@code Portfolio}.
 */
@Service
@RequiredArgsConstructor
public class FundsVerificationService {

    private final BankaCoreClient bankaCoreClient;
    private final PortfolioRepository portfolioRepository;

    /** @deprecated Koristiti {@link #verify(CreateOrderDto, Long, String, BigDecimal, Listing, OrderType, OrderDirection)}. */
    @Deprecated
    public void verify(CreateOrderDto dto, Long userId, BigDecimal approximatePrice, Listing listing, OrderType orderType, OrderDirection direction) {
        verify(dto, userId, rs.raf.trading.common.UserRole.CLIENT, approximatePrice, listing, orderType, direction);
    }

    public void verify(CreateOrderDto dto, Long userId, String userRole, BigDecimal approximatePrice, Listing listing, OrderType orderType, OrderDirection direction) {
        InternalAccountDto account = bankaCoreClient.getAccount(dto.getAccountId());

        if (dto.isMargin()) {
            verifyMargin(account, listing);
        }

        if (direction == OrderDirection.BUY) {
            verifyBuy(account, approximatePrice, orderType);
        } else {
            verifySell(userId, userRole, listing, dto.getQuantity());
        }
    }

    private void verifyMargin(InternalAccountDto account, Listing listing) {
        BigDecimal maintenanceMargin = computeMaintenanceMargin(listing);
        BigDecimal initialMarginCost = maintenanceMargin.multiply(new BigDecimal("1.1")).setScale(4, RoundingMode.HALF_UP);

        boolean balanceSufficient = account.balance().compareTo(initialMarginCost) > 0;
        boolean creditSufficient = account.availableBalance().compareTo(initialMarginCost) > 0;

        if (!balanceSufficient && !creditSufficient) {
            throw new IllegalArgumentException("Insufficient funds for margin order");
        }
    }

    private BigDecimal computeMaintenanceMargin(Listing listing) {
        if (listing.getListingType() == ListingType.STOCK) {
            return listing.getPrice().multiply(new BigDecimal("0.5"));
        } else {
            // FOREX and FUTURES: contractSize * price * 10%
            int contractSize = listing.getContractSize() != null ? listing.getContractSize() : 1;
            return BigDecimal.valueOf(contractSize)
                    .multiply(listing.getPrice())
                    .multiply(new BigDecimal("0.1"))
                    .setScale(4, RoundingMode.HALF_UP);
        }
    }

    private void verifyBuy(InternalAccountDto account, BigDecimal approximatePrice, OrderType orderType) {
        BigDecimal commission = computeCommission(approximatePrice, orderType);
        BigDecimal required = approximatePrice.add(commission);

        if (account.balance().compareTo(required) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
    }

    private BigDecimal computeCommission(BigDecimal approximatePrice, OrderType orderType) {
        // Spec: Market → min(14% * price, $7), Limit → min(24% * price, $12)
        // "u zavisnosti od toga koji iznos je manji"
        return switch (orderType) {
            case MARKET, STOP -> approximatePrice.multiply(new BigDecimal("0.14")).min(new BigDecimal("7"));
            case LIMIT, STOP_LIMIT -> approximatePrice.multiply(new BigDecimal("0.24")).min(new BigDecimal("12"));
        };
    }

    private void verifySell(Long userId, String userRole, Listing listing, int quantity) {
        // Check actual portfolio holdings (not just completed orders)
        int currentHolding = portfolioRepository.findByUserIdAndUserRole(userId, userRole).stream()
                .filter(p -> p.getListingId().equals(listing.getId()))
                .mapToInt(Portfolio::getQuantity)
                .sum();

        if (currentHolding < quantity) {
            throw new IllegalArgumentException("Insufficient securities in portfolio. You have " + currentHolding + " but tried to sell " + quantity);
        }
    }
}
