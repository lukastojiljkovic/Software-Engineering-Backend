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
 *  STUCK              — dosegnut MAX_RETRY; potrebna manuelna intervencija (supervisor).
 *  FAILED_PERMANENT   — primljen permanentan 4xx odgovor koji nije 408/425/429
 *                       (npr. 400 Bad Request, 422 Unprocessable Entity). Retry
 *                       ne bi pomogao jer je problem sa sadrzajem poruke, ne
 *                       sa transijentnim stanjem partnera. Terminalan — ne ulazi
 *                       u retry ciklus.
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
    INBOUND
}
