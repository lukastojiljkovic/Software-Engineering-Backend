package rs.raf.banka2_bek.interbank.model;

/**
 * Status InterbankMessage zapisa.
 *
 * Outbound:
 *  PENDING            — poslata, jos nije primljen 200/204; retry-uje se u sledecem
 *                       ciklusu InterbankRetryScheduler-a (vidi protokol §2.9).
 *  SENT               — primljen 200/204 — terminalan.
 *  SENT_WAITING_ASYNC — BE-INT-02 fix: primljen 202 Accepted, partner je prihvatio
 *                       i nastavlja obradu asinhrono. NIJE terminalno (cekamo da
 *                       partner posalje COMMIT_TX/ROLLBACK_TX), ali NE smemo retry-ovati
 *                       jer bi to bila duplikacija. Scheduler ovo preskace.
 *  STUCK              — dosegnut MAX_RETRY za fazu-1 (NEW_TX); potrebna manuelna
 *                       intervencija (supervisor). Terminalan — ne ulazi u retry ciklus.
 *  FAILED_PERMANENT   — primljen permanentan 4xx odgovor koji nije 408/425/429
 *                       (npr. 400 Bad Request, 422 Unprocessable Entity), ILI je
 *                       poruka definitivno abortovana / trajno-neisporuciva
 *                       (nerazresivo routing, nemoguce-deserijalizovati telo, NEW_TX
 *                       cija je transakcija lokalno abortovana — vidi
 *                       InterbankMessageService.markOutboundAborted/markOutboundNonRetryable).
 *                       Retry ne bi pomogao. Terminalan — ne ulazi u retry ciklus.
 *  DEAD_LETTER        — dead-letter backstop (facet c): poruka je dosegla
 *                       konfigurabilni {@code interbank.retry.max-attempts} (default 50)
 *                       transientnih neuspeha. VAZI ZA SVE tipove, ukljucujuci
 *                       COMMIT_TX/ROLLBACK_TX — phase-2 se po §2.9 retransmituje
 *                       neograniceno, ali maxAttempts ogranicava beskonacni log-spam
 *                       kad je partner TRAJNO mrtav. Terminalan — ne ulazi u retry
 *                       ciklus; zahteva operativnu intervenciju (WARN logovan jednom).
 *
 * Inbound:
 *  INBOUND — primljena, obradjena, odgovor cache-iran (responseBody +
 *            httpStatus); pri retry-u sa istim idempotenceKey-em vraca isto.
 */
public enum InterbankMessageStatus {
    PENDING,
    SENT,
    SENT_WAITING_ASYNC,
    STUCK,
    FAILED_PERMANENT,
    DEAD_LETTER,
    INBOUND
}
