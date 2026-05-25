package rs.raf.trading.order.repository;

import org.springframework.data.jpa.domain.Specification;
import rs.raf.trading.order.model.Order;
import rs.raf.trading.order.model.OrderStatus;
import rs.raf.trading.stock.model.ListingType;

import java.time.LocalDate;

public class OrderSpecification {

    private OrderSpecification() {}

    public static Specification<Order> hasUserId(Long userId) {
        return (root, query, cb) -> userId == null ? cb.conjunction()
                : cb.equal(root.get("userId"), userId);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdAfter(LocalDate dateFrom) {
        return (root, query, cb) -> dateFrom == null ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay());
    }

    public static Specification<Order> createdBefore(LocalDate dateTo) {
        return (root, query, cb) -> dateTo == null ? cb.conjunction()
                : cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay());
    }

    public static Specification<Order> hasListingType(ListingType listingType) {
        return (root, query, cb) -> {
            if (listingType == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("listing").get("listingType"), listingType);
        };
    }

    /**
     * BE-ORD-03: filtrira FUND ordere iz supervizorskog approval view-a.
     * Kada je {@code excludeFund=true} (default), vraca samo ordere bez
     * povezanog fonda (klijenti + zaposleni). FUND ordere ostaju dostupni
     * preko fund admin view-a (po fund.id) — supervizori NE treba da odobravaju
     * FUND ordere u opstem approval queue-u jer su to fund-management akcije
     * pokrenute od strane manager-a fonda.
     */
    public static Specification<Order> excludeFundOrders(boolean excludeFund) {
        return (root, query, cb) -> excludeFund
                ? cb.isNull(root.get("fundId"))
                : cb.conjunction();
    }
}
