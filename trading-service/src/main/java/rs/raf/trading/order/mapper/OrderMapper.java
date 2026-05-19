package rs.raf.trading.order.mapper;

import rs.raf.trading.order.dto.CreateOrderDto;
import rs.raf.trading.order.dto.OrderDto;
import rs.raf.trading.order.model.*;
import rs.raf.trading.stock.model.Listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Centralizovano mapiranje Order <-> OrderDto.
 */
public final class OrderMapper {

    private OrderMapper() {}

    public static OrderDto toDto(Order order) {
        return toDto(order, null);
    }

    public static OrderDto toDto(Order order, String userName) {
        if (order == null) return null;

        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setListingId(order.getListing() != null ? order.getListing().getId() : null);
        dto.setUserName(userName);
        dto.setUserRole(order.getUserRole());
        dto.setListingTicker(order.getListing() != null ? order.getListing().getTicker() : null);
        dto.setListingName(order.getListing() != null ? order.getListing().getName() : null);
        dto.setListingType(order.getListing() != null && order.getListing().getListingType() != null
                ? order.getListing().getListingType().name() : null);
        dto.setOrderType(order.getOrderType() != null ? order.getOrderType().name() : null);
        dto.setQuantity(order.getQuantity());
        dto.setContractSize(order.getContractSize());
        dto.setPricePerUnit(order.getPricePerUnit());
        dto.setLimitValue(order.getLimitValue());
        dto.setStopValue(order.getStopValue());
        dto.setDirection(order.getDirection() != null ? order.getDirection().name() : null);
        dto.setStatus(order.getStatus() != null ? order.getStatus().name() : null);
        dto.setApprovedBy(order.getApprovedBy());
        dto.setDone(order.isDone());
        dto.setLastModification(order.getLastModification());
        dto.setRemainingPortions(order.getRemainingPortions());
        dto.setAfterHours(order.isAfterHours());
        dto.setAllOrNone(order.isAllOrNone());
        dto.setMargin(order.isMargin());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setAccountId(order.getAccountId());
        dto.setApproximatePrice(calculateApproximatePrice(order));
        dto.setListingSettlementDate(order.getListing() != null ? order.getListing().getSettlementDate() : null);
        dto.setFxCommission(order.getFxCommission());
        dto.setExchangeRate(order.getExchangeRate());
        dto.setFundId(order.getFundId());

        return dto;
    }

    public static Order fromCreateDto(CreateOrderDto dto, Listing listing) {
        Order order = new Order();
        order.setListing(listing);
        order.setOrderType(OrderType.valueOf(dto.getOrderType()));
        order.setQuantity(dto.getQuantity());
        order.setContractSize(dto.getContractSize());
        order.setLimitValue(dto.getLimitValue());
        order.setStopValue(dto.getStopValue());
        order.setDirection(OrderDirection.valueOf(dto.getDirection()));
        order.setAllOrNone(dto.isAllOrNone());
        order.setMargin(dto.isMargin());
        order.setAccountId(dto.getAccountId());
        order.setRemainingPortions(dto.getQuantity());
        order.setDone(false);
        order.setCreatedAt(LocalDateTime.now());
        order.setLastModification(LocalDateTime.now());
        return order;
    }

    private static BigDecimal calculateApproximatePrice(Order order) {
        if (order.getPricePerUnit() == null || order.getQuantity() == null) return null;
        int cs = order.getContractSize() != null ? order.getContractSize() : 1;
        return BigDecimal.valueOf(cs)
                .multiply(order.getPricePerUnit())
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
