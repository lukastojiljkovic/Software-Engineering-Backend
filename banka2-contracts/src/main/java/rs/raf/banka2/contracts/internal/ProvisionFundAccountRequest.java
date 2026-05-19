package rs.raf.banka2.contracts.internal;

/**
 * Zahtev trading-service-a da banka-core provizionira gotovinski (RSD) racun
 * za novi investicioni fond. Odgovor je {@link InternalAccountDto}.
 */
public record ProvisionFundAccountRequest(String fundName, Long managerEmployeeId) {
}
