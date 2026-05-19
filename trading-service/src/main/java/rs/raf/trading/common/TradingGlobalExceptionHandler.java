package rs.raf.trading.common;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rs.raf.trading.common.dto.MessageResponseDto;

/**
 * Globalni (app-wide) exception handler za trading-service.
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2c): u monolitu su aktuarski (i ostali)
 * kontroleri za greske koje njihov scoped handler ne hvata padali na monolitni
 * {@code rs.raf.banka2_bek.auth.config.GlobalExceptionHandler}. trading-service je
 * do sada imao samo scoped handlere ({@code ActuaryExceptionHandler},
 * {@code ListingExceptionHandler}), pa su nehvtaceni izuzeci (npr. bare
 * {@code RuntimeException} iz {@code ActuaryServiceImpl.updateAgentLimit} kad cilj
 * nije agent) izlazili kao 5xx. Ovaj advice preslikava monolitne mapinge tako da
 * domenske greske vracaju verne 4xx statuse.</p>
 *
 * <p>Preslikani mapinzi monolitnog {@code GlobalExceptionHandler}-a:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} &rarr; 400 (prva field greska)</li>
 *   <li>{@link EntityNotFoundException} &rarr; 404</li>
 *   <li>{@link IllegalArgumentException} &rarr; 400</li>
 *   <li>{@link IllegalStateException} &rarr; 403</li>
 *   <li>{@link AccessDeniedException} &rarr; 403</li>
 *   <li>{@link TransactionSystemException} &rarr; unwrap cause &rarr; 404/403/400</li>
 *   <li>{@link RuntimeException} (catch-all) &rarr; 400</li>
 * </ul>
 * Telo odgovora je {@link MessageResponseDto} ({@code {"message": ...}}) — isti
 * oblik koji monolitni {@code GlobalExceptionHandler} koristi.</p>
 *
 * <p>Scoped {@code @RestControllerAdvice(assignableTypes = ...)} handleri zadrzavaju
 * prednost za svoje kontrolere — Spring bira advice po specificnosti, a uz
 * {@code @Order(HIGHEST_PRECEDENCE)} na scoped handlerima i {@code LOWEST_PRECEDENCE}
 * ovde redosled je determinisitkan.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class TradingGlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponseDto> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("Validation error");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponseDto(message));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<MessageResponseDto> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResponseDto> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<MessageResponseDto> handleForbidden(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MessageResponseDto> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    /**
     * Spring AOP wrap-uje nehvtacene RuntimeException-e koji padaju na commit fazi
     * {@code @Transactional} metode u {@link TransactionSystemException}. Pravu
     * poruku korisnika dobijemo unwrap-ovanjem cause lanca i mapiramo poznate
     * tipove u prikladan HTTP status (isto kao monolitni GlobalExceptionHandler).
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<MessageResponseDto> handleTransactionSystemException(TransactionSystemException ex) {
        Throwable cause = ex.getMostSpecificCause();
        while (cause != null
                && cause.getCause() != null
                && cause.getCause() != cause
                && (cause instanceof TransactionException
                    || cause instanceof DataAccessException)) {
            cause = cause.getCause();
        }
        String message = cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()
                ? cause.getMessage()
                : "Doslo je do greske prilikom obrade zahteva.";
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (cause instanceof EntityNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (cause instanceof AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
        }
        return ResponseEntity.status(status).body(new MessageResponseDto(message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<MessageResponseDto> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponseDto(ex.getMessage()));
    }
}
