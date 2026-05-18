package rs.raf.banka2.contracts.internal;

/**
 * Srednji devizni kurs jedne valute, izrazen kao "koliko jedinica te valute
 * za 1 RSD" (RSD = 1.0). Izlaze ga banka-core preko GET /internal/fx/rates;
 * trading-service ga koristi za FOREX cross-rate racun u ListingServiceImpl.
 */
public record FxRateDto(String currency, double rate) {
}
