package rs.raf.banka2_bek.internalapi.model;

public enum FundReservationStatus {
    RESERVED,    // sredstva su zakljucana, nije jos naplaceno
    COMMITTED,   // ceo iznos naplacen
    RELEASED     // ostatak oslobodjen
}
