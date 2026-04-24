package rs.raf.banka2_bek.interbank.model;

/*
================================================================================
 TODO — TIPOVI INTER-BANK TRANSAKCIJA
 Zaduzen: BE tim
 Spec referenca: Celina 4, Komunikacija banaka (placanja + OTC)
--------------------------------------------------------------------------------
 PAYMENT — Klijent A salje novac klijentu banke B (ili obratno). Protokol:
           2-phase commit (Celina 4 linije 382-419).
 OTC     — Kupac (banka A) iskoriscava opcioni ugovor sa prodavcem iz banke B.
           Protokol: SAGA sa 5 faza (Celina 4 linije 473-519).
================================================================================
*/
public enum InterbankTransactionType {
    PAYMENT,
    OTC
}
