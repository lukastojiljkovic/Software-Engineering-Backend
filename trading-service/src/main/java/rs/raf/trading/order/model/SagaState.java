package rs.raf.trading.order.model;

/**
 * Stanje settlement SAGA-e za jedan {@link Order}.
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2c): ovo je NOVO polje uvedeno
 * u trading-service-u (ne postoji u monolitnoj kopiji Order-a). Kada
 * {@code trading-service} izvrsava nalog, novac vise nije u istoj bazi —
 * rezervacija/naplata/oslobadjanje sredstava ide kroz banka-core interni
 * {@code /internal/funds/**} seam. Ovaj enum prati u kojoj je fazi
 * distribuirana SAGA, da bi se posle pada mogla nastaviti ili kompenzovati.</p>
 *
 * <ul>
 *   <li>{@code STARTED} — nalog kreiran, jos nista nije rezervisano</li>
 *   <li>{@code FUNDS_RESERVED} — banka-core potvrdio rezervaciju sredstava
 *       (id rezervacije u {@code Order.bankaCoreReservationId})</li>
 *   <li>{@code SETTLED} — naplata izvrsena, hartija preneta, nalog DONE</li>
 *   <li>{@code COMPENSATED} — SAGA ponistena (rezervacija oslobodjena)</li>
 * </ul>
 */
public enum SagaState {
    STARTED,
    FUNDS_RESERVED,
    SETTLED,
    COMPENSATED
}
