package rs.raf.trading.order.service;

import org.springframework.stereotype.Service;
import rs.raf.trading.order.dto.CreateOrderDto;
import rs.raf.trading.order.model.OrderDirection;
import rs.raf.trading.order.model.OrderType;

import java.math.BigDecimal;

@Service
public class OrderValidationService {

    public void validate(CreateOrderDto dto) {
        OrderType orderType = parseOrderType(dto.getOrderType());
        parseDirection(dto.getDirection());

        if (dto.getQuantity() == null || dto.getQuantity() <= 0 ||
                dto.getContractSize() == null || dto.getContractSize() <= 0) {
            throw new IllegalArgumentException("Quantity and contractSize must be > 0");
        }

        if (orderType == OrderType.LIMIT || orderType == OrderType.STOP_LIMIT) {
            if (dto.getLimitValue() == null || dto.getLimitValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Limit value is required for LIMIT and STOP_LIMIT orders");
            }
        }

        if (orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT) {
            if (dto.getStopValue() == null || dto.getStopValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Stop value is required for STOP and STOP_LIMIT orders");
            }
        }

        // XOR enforcement: order mora imati ili accountId (klijent/zaposleni
        // direktan order) ili fundId (supervizor kupuje u ime fonda — P3 /
        // Celina 4 (Nova) §3883-3964), ali NE oba (kontradikcija).
        // Bez ovog XOR-a se FUND BUY supervizora gusi pre nego sto OrderServiceImpl
        // stigne da resolve-uje fund.accountId.
        if (dto.getAccountId() == null && dto.getFundId() == null) {
            throw new IllegalArgumentException("Either accountId or fundId is required");
        }
        if (dto.getAccountId() != null && dto.getFundId() != null) {
            throw new IllegalArgumentException("accountId and fundId are mutually exclusive");
        }
    }

    public OrderType parseOrderType(String orderType) {
        try {
            return OrderType.valueOf(orderType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid order type or direction");
        }
    }

    public OrderDirection parseDirection(String direction) {
        try {
            return OrderDirection.valueOf(direction);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid order type or direction");
        }
    }
}
