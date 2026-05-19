package rs.raf.trading.otc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.trading.otc.model.OtcOffer;
import rs.raf.trading.otc.model.OtcOfferStatus;

import java.util.List;

@Repository
public interface OtcOfferRepository extends JpaRepository<OtcOffer, Long> {

    @Query("SELECT o FROM OtcOffer o WHERE o.status = :status " +
           "AND ((o.buyerId = :userId AND o.buyerRole = :userRole) " +
           "  OR (o.sellerId = :userId AND o.sellerRole = :userRole)) " +
           "ORDER BY o.lastModifiedAt DESC")
    List<OtcOffer> findActiveForUser(@Param("userId") Long userId,
                                     @Param("userRole") String userRole,
                                     @Param("status") OtcOfferStatus status);

    default List<OtcOffer> findActiveForUser(Long userId, String userRole) {
        return findActiveForUser(userId, userRole, OtcOfferStatus.ACTIVE);
    }
}
