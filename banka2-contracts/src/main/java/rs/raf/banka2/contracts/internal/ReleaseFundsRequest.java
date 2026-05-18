package rs.raf.banka2.contracts.internal;

/** Oslobadjanje preostalog rezervisanog iznosa. reason je informativan. */
public record ReleaseFundsRequest(String reason) {
}
