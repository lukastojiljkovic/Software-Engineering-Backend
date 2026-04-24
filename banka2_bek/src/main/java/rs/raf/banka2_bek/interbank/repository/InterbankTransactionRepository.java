package rs.raf.banka2_bek.interbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;
import rs.raf.banka2_bek.interbank.model.InterbankTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/*
================================================================================
 TODO — REPOSITORY ZA INTERBANK TRANSAKCIJE
 Zaduzen: BE tim
 Spec referenca: Celina 4
--------------------------------------------------------------------------------
 IMPLEMENTIRATI:

 1. Optional<InterbankTransaction> findByTransactionId(String transactionId);
    Za lookup po UUID-u koji je u svim porukama.

 2. List<InterbankTransaction> findByStatusIn(Collection<Status> statuses);
    Za InterbankRetryScheduler — sve koje su "in progress" (PREPARING,
    COMMITTING, ABORTING) i treba da se retry-uju ili da se proveri status.

 3. @Query("... where status in (:stuckStatuses) and lastRetryAt < :cutoff ...")
    List<InterbankTransaction> findStaleInProgress(List<Status> s, LocalDateTime cutoff);
    Za auto-abort posle timeout-a.

 NAPOMENA:
  - Spring Data ce automatski implementirati prve dve metode kroz ime.
  - Trecu treba definisati kroz @Query ili named query.
================================================================================
*/
public interface InterbankTransactionRepository extends JpaRepository<InterbankTransaction, Long> {

    Optional<InterbankTransaction> findByTransactionId(String transactionId);

    List<InterbankTransaction> findByStatusIn(List<InterbankTransactionStatus> statuses);

    @Query("select t from InterbankTransaction t " +
           "where t.status in :statuses " +
           "and (t.lastRetryAt is null or t.lastRetryAt < :cutoff)")
    List<InterbankTransaction> findStaleInProgress(List<InterbankTransactionStatus> statuses,
                                                    LocalDateTime cutoff);
}
