package rs.raf.banka2_bek.assistant.client;

/**
 * Greska pri pozivu {@code trading-service} PUBLIC API-ja iz Arbitro
 * write/read handler-a (faza 2f).
 *
 * <p>{@code httpStatus} = status koji je vratio trading-service (400 nevalidni
 * parametri, 403 nedozvoljena akcija, 404 resurs ne postoji, 409 konflikt
 * stanja, ...). {@code message} je citljiva poruka iz tela trading-service
 * odgovora — Arbitro je prikazuje korisniku kao {@code tool_result} gresku ili
 * {@code AgentAction.errorMessage}, tako da se ponasanje (validacija, OTP,
 * preview) ne menja u odnosu na raniji in-process poziv.
 */
public class TradingServiceClientException extends RuntimeException {

    private final int httpStatus;

    public TradingServiceClientException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
