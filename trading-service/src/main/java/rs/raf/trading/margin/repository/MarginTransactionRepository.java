package rs.raf.trading.margin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.trading.margin.model.MarginTransaction;

import java.util.List;

@Repository
public interface MarginTransactionRepository extends JpaRepository<MarginTransaction, Long> {

    /** Pronalazi sve transakcije za dati margin racun, sortirane po datumu (najnovije prvo) */
    List<MarginTransaction> findByMarginAccountIdOrderByCreatedAtDesc(Long marginAccountId);
}
