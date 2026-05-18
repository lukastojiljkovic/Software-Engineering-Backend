package rs.raf.banka2.contracts.internal;

/** Strukturisan odgovor na gresku internog API-ja. */
public record InternalErrorDto(String code, String message) {
}
