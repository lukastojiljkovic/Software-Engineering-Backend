package rs.raf.trading.tax.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.trading.tax.model.TaxRecord;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRecordRepository extends JpaRepository<TaxRecord, Long> {

    Optional<TaxRecord> findByUserIdAndUserType(Long userId, String userType);

    // PG cast za null-safe parametre (vidi CLAUDE.md Runda 24.04).
    @Query("SELECT t FROM TaxRecord t WHERE " +
           "(cast(:name as string) IS NULL OR LOWER(t.userName) LIKE LOWER(CONCAT('%', cast(:name as string), '%'))) AND " +
           "(cast(:userType as string) IS NULL OR t.userType = cast(:userType as string))")
    List<TaxRecord> findByFilters(@Param("name") String name, @Param("userType") String userType);
}
