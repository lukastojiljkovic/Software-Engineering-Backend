package rs.raf.banka2_bek.investmentfund.model;

/*
================================================================================
 TODO — STATUS ULOGA/POVLACENJA IZ FONDA
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 245 (enum: pending, completed, failed)
--------------------------------------------------------------------------------
 PENDING   — uplata/povlacenje u obradi. Za povlacenja kad je likvidnost fonda
             nedovoljna — sistem prodaje hartije automatski; dok se ne izvrsi,
             status je PENDING.
 COMPLETED — sredstva prebacena, ClientFundPosition.totalInvested azuriran.
 FAILED    — neuspeh (npr. racun ne postoji, nedovoljno sredstava); razlog
             u failureReason.
================================================================================
*/
public enum ClientFundTransactionStatus {
    PENDING,
    COMPLETED,
    FAILED
}
