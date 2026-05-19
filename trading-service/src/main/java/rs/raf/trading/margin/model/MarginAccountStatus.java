package rs.raf.trading.margin.model;

/**
 * Status margin racuna.
 *
 * ACTIVE  - racun je aktivan, korisnik moze da trguje na margini
 * BLOCKED - racun je blokiran jer je initialMargin pao ispod maintenanceMargin
 *           (margin call). Korisnik mora da uplati sredstva da bi odblokirao.
 */
public enum MarginAccountStatus {
    ACTIVE,
    BLOCKED
}
