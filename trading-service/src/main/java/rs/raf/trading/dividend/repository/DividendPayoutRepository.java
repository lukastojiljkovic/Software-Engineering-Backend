package rs.raf.trading.dividend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.trading.dividend.model.DividendPayout;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DividendPayoutRepository extends JpaRepository<DividendPayout, Long> {

    /**
     * Istorija dividendi za datog vlasnika, sortirano novije-prvo.
     */
    List<DividendPayout> findByOwnerIdAndOwnerTypeOrderByPaymentDateDesc(Long ownerId, String ownerType);

    /**
     * Idempotentnost: vraca sve DividendPayout zapise za datu hartiju i datum.
     * Servis treba da proveri per-owner pomoccu stream().anyMatch() — ne Optional.
     * Vraca listu jer vise vlasnika moze drzati istu hartiju.
     */
    List<DividendPayout> findByStockListingIdAndPaymentDate(Long stockListingId, LocalDate paymentDate);

    /**
     * Istorija dividendi za konkretnu Portfolio poziciju (vlasnik + hartija).
     */
    List<DividendPayout> findByOwnerIdAndOwnerTypeAndStockListingId(Long ownerId, String ownerType,
                                                                    Long stockListingId);

    /**
     * Admin pregled svih isplata, sortirano novije-prvo, paginiran.
     */
    Page<DividendPayout> findAllByOrderByPaymentDateDesc(Pageable pageable);

    /**
     * Admin pregled isplata u datom vremenskom opsegu.
     */
    @Query("SELECT d FROM DividendPayout d WHERE d.paymentDate BETWEEN :from AND :to ORDER BY d.paymentDate DESC")
    Page<DividendPayout> findByPaymentDateBetween(@Param("from") LocalDate from,
                                                  @Param("to") LocalDate to,
                                                  Pageable pageable);
}
