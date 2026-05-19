package rs.raf.trading.order.service;

import org.springframework.stereotype.Service;
import rs.raf.trading.order.dto.CreateOrderDto;
import rs.raf.trading.order.model.OrderDirection;
import rs.raf.trading.order.model.OrderType;
import rs.raf.trading.stock.model.Listing;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ListingPriceService {

    public BigDecimal getPricePerUnit(CreateOrderDto dto, Listing listing, OrderType orderType, OrderDirection direction) {
        return switch (orderType) {
            case MARKET, STOP -> direction == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
            case LIMIT, STOP_LIMIT -> dto.getLimitValue();
        };
    }

    public BigDecimal calculateApproximatePrice(int contractSize, BigDecimal pricePerUnit, int quantity) {
        return BigDecimal.valueOf(contractSize)
                .multiply(pricePerUnit)
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
