package rs.raf.banka2_bek.interbank.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;

import java.util.List;
import java.util.Optional;

/*
================================================================================
 TODO — REPOSITORY ZA INTERBANK PORUKE (AUDIT)
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 IMPLEMENTIRATI:

 1. Optional<InterbankMessage> findByMessageId(String messageId);
    Za idempotentnost — ako druga banka posalje isti messageId dva puta.

 2. List<InterbankMessage> findByTransactionIdOrderByCreatedAtAsc(String txId);
    Za debug prikaz istorije jedne transakcije.

 Sve ostalo dobijamo standardno iz JpaRepository.
================================================================================
*/
public interface InterbankMessageRepository extends JpaRepository<InterbankMessage, Long> {

    Optional<InterbankMessage> findByMessageId(String messageId);

    List<InterbankMessage> findByTransactionIdOrderByCreatedAtAsc(String transactionId);
}
