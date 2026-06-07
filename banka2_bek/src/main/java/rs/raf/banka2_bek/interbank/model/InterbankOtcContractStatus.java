package rs.raf.banka2_bek.interbank.model;

/**
 * T12 — Stanje inter-bank OTC opcionog ugovora.
 *
 * Spec ref: Protokol §2.7.2 Options; Celina 5 (Nova) — Izvrsavanje kupoprodaje
 * (SAGA pattern).
 *
 * Ugovor se kreira kada druga strana prihvati (§3.6) — premium se odmah
 * placa kupcu->prodavcu, hartije se rezervisu kod prodavca. Ugovor je
 * EXERCISED kad kupac iskoristi opciju pre `settlementDate`-a, EXPIRED
 * inace (rezervacija hartija se vraca prodavcu).
 */
public enum InterbankOtcContractStatus {
    /** Ugovor vazeci, kupac jos nije iskoristio opciju, settlementDate nije prosao. */
    ACTIVE,
    /**
     * P1-interbank-otc-2 (1336/1535) — prelazno stanje: kupac je pokrenuo exercise,
     * sredstva su rezervisana i 2PC je u toku, ali jos nije commit-ovan. Sluzi kao
     * pessimistic-lock claim marker da dva konkurentna exercise-a ne udju oba u 2PC
     * (drugi vidi != ACTIVE pod lock-om → 409). Na uspesan COMMIT_TX → EXERCISED;
     * na pad/odustajanje → vraca se u ACTIVE (rezervacija oslobodjena).
     */
    EXERCISING,
    /** Kupac iskoristio opciju i transakcija je commitovana. Vidi protokol §2.7.2. */
    EXERCISED,
    /** Settlement datum prosao bez iskoriscenja — rezervacija se oslobadja. */
    EXPIRED,
    /**
     * T9 / S10b — kupac je rucno odbio ("Odbi") sklopljeni ugovor pre settlement-a.
     * Buyer-ova accept-time strike rezervacija se oslobadja ("pare za kupovinu se
     * vracaju"), premija OSTAJE prodavcu, hartije se NE prenose. Lokalno zatvaranje:
     * sellerova rezervacija hartija se oslobadja na NJIHOVOM expiry-ju (cross-bank
     * poruka nije obavezna).
     */
    DECLINED
}
