package rs.raf.trading.option.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.trading.option.model.Option;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * JPA repozitorijum za Option entitet.
 *
 * NAPOMENA: Upiti koji vracaju opcije za odredjenu akciju (stockListingId) ce se
 * najcesce koristiti na frontendu za prikaz "option chain" tabele.
 */
@Repository
public interface OptionRepository extends JpaRepository<Option, Long> {

    /**
     * Pronalazi sve opcije (CALL i PUT) za odredjenu akciju.
     *
     * @param listingId ID Listing entiteta (akcije)
     * @return lista svih opcija vezanih za tu akciju
     */
    List<Option> findByStockListingId(Long listingId);

    /**
     * Pesimisticki write-lock dohvat opcije po ID-u — koristi se u
     * {@code exerciseOption} flow-u da spreci lost-update trku: dva paralelna
     * exercise-a bi inace procitala isti {@code openInterest}, dvaput ga
     * dekrementirala sa stale vrednosti i (preko idempotency replay-a) zaduzila
     * settlement samo jednom. Lock drzi red opcije do kraja exercise transakcije.
     *
     * <p>Mirror {@code OrderRepository.findByIdForUpdate} /
     * {@code PortfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate}.
     *
     * @param id ID Option entiteta
     * @return zakljucani Option red ili prazan Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Option o WHERE o.id = :id")
    Optional<Option> findByIdForUpdate(@Param("id") Long id);

    /**
     * Pronalazi opcije za odredjenu akciju i konkretan settlement datum.
     *
     * @param listingId ID Listing entiteta (akcije)
     * @param date      settlement datum za filtriranje
     * @return lista opcija za dati listing i datum
     */
    List<Option> findByStockListingIdAndSettlementDate(Long listingId, LocalDate date);

    /**
     * Pronalazi sve istekle opcije (settlement datum pre zadatog datuma).
     *
     * @param date referentni datum (obicno LocalDate.now())
     * @return lista isteklih opcija
     */
    List<Option> findBySettlementDateBefore(LocalDate date);

    /**
     * Brise sve istekle opcije iz baze.
     *
     * @param date referentni datum -- sve opcije sa settlementDate < date ce biti obrisane
     */
    @Modifying
    @Query("DELETE FROM Option o WHERE o.settlementDate < :date")
    void deleteBySettlementDateBefore(@Param("date") LocalDate date);

    /**
     * Proverava da li vec postoje opcije za datu akciju i settlement datum.
     *
     * @param listingId ID Listing entiteta
     * @param date      settlement datum
     * @return true ako vec postoje opcije za taj par (listing, datum)
     */
    boolean existsByStockListingIdAndSettlementDate(Long listingId, LocalDate date);
}
