package rs.raf.banka2_bek.interbank.model;

/*
================================================================================
 TODO — STATUSI 2PC / SAGA TRANSAKCIJA
 Zaduzen: BE tim
 Spec referenca: Celina 4, linije 376-419 (2PC happy path) i 421-424 (neuspeh)
--------------------------------------------------------------------------------
 INITIATED  — Zahtev kreiran u bazi, nista jos nije poslato. Prelazi u
              PREPARING nakon poslatog Prepare.
 PREPARING  — Prepare poslat drugoj banci; sredstva rezervisana kod posiljaoca.
              Prelazi u PREPARED kad stigne Ready odgovor.
 PREPARED   — Obe strane rezervisale. Moze da ide u COMMITTING ili ABORTING.
 COMMITTING — Commit poslat; cekamo potvrdu.
 COMMITTED  — Obe strane su potvrdile; sredstva prenesena / hartije prenesene.
 ABORTING   — Bilo koji korak je podbacio; rollback u toku (oslobodi rezervacije).
 ABORTED    — Rollback kompletiran, transakcija zatvorena bez efekta.
 STUCK      — Retry scheduler je odustao (vidi InterbankRetryScheduler.maxRetries).
              Treba manuelnu intervenciju supervizora.
================================================================================
*/
public enum InterbankTransactionStatus {
    INITIATED,
    PREPARING,
    PREPARED,
    COMMITTING,
    COMMITTED,
    ABORTING,
    ABORTED,
    STUCK
}
