package rs.raf.banka2_bek.interbank.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * T12 — Repository za inter-bank OTC opcione ugovore.
 *
 * Spec ref: Protokol §2.7.2 Options, §3.6 Accepting an offer; Celina 4
 * (Nova) — Sklopljeni ugovori; Celina 5 (Nova) — Postignut dogovor.
 *
 * KORISNICI:
 *   T2/T3        — kreira pri prihvatanju ponude (§3.6) i azurira pri exercise
 *   FE           — `findByLocalParty...` za "Sklopljeni ugovori" tab
 *   ExpiryScheduler (kasnije) — `findByStatusAndSettlementDateBefore` za auto-expire
 */
public interface InterbankOtcContractRepository extends JpaRepository<InterbankOtcContract, Long> {

    /** 1:1 mapiranje pregovor -> ugovor. Vraca .empty() ako pregovor nije rezultovao ugovorom. */
    Optional<InterbankOtcContract> findBySourceNegotiationId(Long sourceNegotiationId);

    /**
     * FINDING 2 — PESSIMISTIC_WRITE lookup po sourceNegotiationId za inbound
     * seller-settlement (§2.7.2 exercise). Per-{@code InterbankTransaction} red-lock u
     * {@code commitLocal} NE serijalizuje dva RAZLICITA-txId konkurentna exercise-a
     * ISTOG ugovora; {@code findBySourceNegotiationId} je non-locking, pa bi oba commit-a
     * procitala ACTIVE i oba settle-ovala → {@code commitStock} isporuci 2k hartija i
     * {@code commitRecipientCredit} (bez idempotency kljuca) kreditira 2×strike. Lock na
     * ovom finder-u serijalizuje konkurentne commit-ove: drugi ceka, pa pod lock-om vidi
     * EXERCISED i preskace settle. Mirror pattern {@code findByIdForUpdate} /
     * {@code InterbankTransactionRepository#findForUpdateByTransactionRoutingNumberAndTransactionIdString}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InterbankOtcContract c where c.sourceNegotiationId = :sourceNegotiationId")
    Optional<InterbankOtcContract> findBySourceNegotiationIdForUpdate(
            @Param("sourceNegotiationId") Long sourceNegotiationId);

    /**
     * P1-interbank-otc-2 (1336/1535) — PESSIMISTIC_WRITE lookup za exercise claim.
     * Intra-bank SAGA exercise koristi {@code findByIdForUpdate}; inter-bank
     * exercise pre ovog fix-a nije imao lock izmedju {@code status==ACTIVE} provere
     * i 2PC {@code execute(tx)} → dva konkurentna exercise-a su oba prosla i pokrenula
     * dvostruki 2PC/debit. Sad claim metoda lock-uje red, re-citanje statusa pod
     * lock-om, i flip-uje ACTIVE→EXERCISING — drugi pozivalac cekajuci na lock-u
     * vidi EXERCISING i odbija (409). Vidi {@code InterbankOtcWrapperService#claimForExercise}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InterbankOtcContract c where c.id = :id")
    Optional<InterbankOtcContract> findByIdForUpdate(@Param("id") Long id);

    /**
     * Sklopljeni ugovori za datog korisnika (BUYER ili SELLER), bilo kog
     * statusa. Koristi se za "Sklopljeni ugovori" tab — FE filtrira po
     * statusu (vazeci/istekli) na klijent strani.
     */
    List<InterbankOtcContract> findByLocalPartyIdAndLocalPartyRole(
            Long localPartyId, String localPartyRole);

    /**
     * Auto-expiry helper: ugovori cija je settlementDate prosao a status je
     * jos ACTIVE. Scheduler ce ih pokupiti i pozvati expire() (oslobadja
     * rezervaciju hartija po §2.7.2).
     */
    List<InterbankOtcContract> findByStatusAndSettlementDateBefore(
            InterbankOtcContractStatus status, OffsetDateTime settlementDateBefore);

    /**
     * P2-tax-interbank-otc-1 — svi EXERCISED inter-bank OTC ugovori. Tax engine
     * (trading-service) ih dohvata preko {@code GET /internal/interbank-otc/exercised}
     * da bi ukljucio realizovanu kapitalnu dobit lokalnih CLIENT-ova u 15% obracun
     * (banka-core nema tax modul). DB-filtriran upit (status) umesto findAll +
     * in-memory filter — EXERCISED ostaje zauvek pa lista raste; isti antipattern
     * koji je R5 1901 popravio na intra strani.
     */
    List<InterbankOtcContract> findByStatus(InterbankOtcContractStatus status);
}
