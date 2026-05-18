package rs.raf.banka2_bek.internalapi.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.internalapi.model.FundReservation;

import java.util.Optional;

public interface FundReservationRepository extends JpaRepository<FundReservation, Long> {

    Optional<FundReservation> findByReservationId(String reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FundReservation r where r.reservationId = :rid")
    Optional<FundReservation> findByReservationIdForUpdate(@Param("rid") String rid);
}
