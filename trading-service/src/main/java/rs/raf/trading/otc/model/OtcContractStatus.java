package rs.raf.trading.otc.model;

/**
 * Stanje OTC opcionog ugovora.
 *
 * Spec: Celina 4 - Sklopljeni ugovori.
 */
public enum OtcContractStatus {
    /** Ugovor je vazeci i jos nije istekao — kupac moze iskoristiti. */
    ACTIVE,
    /** Kupac je iskoristio pravo i izvrsio kupoprodaju. */
    EXERCISED,
    /** Settlement datum je prosao, ugovor nije iskoriscen. */
    EXPIRED
}
