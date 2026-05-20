package rs.raf.banka2_bek.interbank.wrapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;

import java.util.Map;

/**
 * Mapira inter-bank OTC wrapper greske u HTTP statuse za FE-facing rute.
 *
 * <p>Razlog za zaseban advice (umesto deljenja sa
 * {@code OtcNegotiationExceptionHandler}): inbound (§3.x) endpoint-i razmenjuju
 * JSON sa partner bankama po protokol-spec semantici (404 za nepostojeci user,
 * 409 za turn violation), dok wrapper rute razgovaraju sa FE-om i koriste
 * lokalne semantike (409 za exercise konflikt — ugovor nije ACTIVE, settlement
 * je prosao, pozivac nije buyer). Bez ovog handler-a, sve {@link
 * InterbankExceptions.InterbankException}-e bi {@code GlobalExceptionHandler}
 * mapirao u 400 preko {@code handleRuntimeException}.
 */
@RestControllerAdvice(assignableTypes = InterbankOtcWrapperController.class)
public class InterbankOtcWrapperExceptionHandler {

    /** §2.7.2 — exercise konflikt sa stanjem resursa (status/settlement/owner). */
    @ExceptionHandler(InterbankExceptions.InterbankExerciseConflictException.class)
    public ResponseEntity<Map<String, String>> handleExerciseConflict(
            InterbankExceptions.InterbankExerciseConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InterbankExceptions.InterbankNegotiationConflictException.class)
    public ResponseEntity<Map<String, String>> handleNegotiationConflict(
            InterbankExceptions.InterbankNegotiationConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    /** Partner banka nije dostupna ili je vratila grešku — 502. */
    @ExceptionHandler(InterbankExceptions.InterbankCommunicationException.class)
    public ResponseEntity<Map<String, String>> handleCommunication(
            InterbankExceptions.InterbankCommunicationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InterbankExceptions.InterbankProtocolException.class)
    public ResponseEntity<Map<String, String>> handleProtocol(
            InterbankExceptions.InterbankProtocolException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }
}
