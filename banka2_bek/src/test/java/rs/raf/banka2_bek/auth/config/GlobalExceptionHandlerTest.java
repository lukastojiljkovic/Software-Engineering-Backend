package rs.raf.banka2_bek.auth.config;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import rs.raf.banka2_bek.auth.dto.MessageResponseDto;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ── MethodArgumentNotValidException → 400 ───────────────────────

    @Test
    void handleValidationException_returnsBadRequest() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "Email is required"));

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<MessageResponseDto> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Email is required");
    }

    @Test
    void handleValidationException_noFieldErrors_returnsDefaultMessage() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<MessageResponseDto> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation error");
    }

    // ── EntityNotFoundException → 404 ───────────────────────────────

    @Test
    void handleEntityNotFound_returnsNotFound() {
        EntityNotFoundException ex = new EntityNotFoundException("Employee not found");

        ResponseEntity<MessageResponseDto> response = handler.handleEntityNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Employee not found");
    }

    // ── IllegalArgumentException → 400 ──────────────────────────────

    @Test
    void handleBadRequest_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");

        ResponseEntity<MessageResponseDto> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid input");
    }

    // ── IllegalStateException → 403 ─────────────────────────────────

    @Test
    void handleForbidden_returnsForbidden() {
        IllegalStateException ex = new IllegalStateException("Account is deactivated");

        ResponseEntity<MessageResponseDto> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Account is deactivated");
    }

    // ── RuntimeException → 400 ──────────────────────────────────────

    @Test
    void handleRuntimeException_returnsBadRequest() {
        RuntimeException ex = new RuntimeException("Something went wrong");

        ResponseEntity<MessageResponseDto> response = handler.handleRuntimeException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong");
    }

    // ── AccessDeniedException → 403 ─────────────────────────────────

    @Test
    void handleAccessDenied_returnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<MessageResponseDto> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
    }

    // ── TransactionSystemException (14.05.2026 vece-4 fix) ───────────────
    // PM prijavio: "Could not commit JPA transaction" toast umesto pravog
    // BE validation message-a (npr. "Nedovoljno sredstava"). Spring AOP wrap-uje
    // sve nehvtacene RuntimeException-e u @Transactional metodama u
    // TransactionSystemException sa generic porukom. Treba unwrap cause lanca.

    @Test
    void handleTransactionSystemException_unwrapsRootCause() {
        IllegalArgumentException root = new IllegalArgumentException("Nedovoljno sredstava na racunu.");
        org.springframework.transaction.TransactionSystemException tx =
                new org.springframework.transaction.TransactionSystemException("Could not commit JPA transaction", root);

        ResponseEntity<MessageResponseDto> response = handler.handleTransactionSystemException(tx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Nedovoljno sredstava na racunu.");
    }

    @Test
    void handleTransactionSystemException_entityNotFoundCause_returns404() {
        EntityNotFoundException root = new EntityNotFoundException("Racun ne postoji: 999");
        org.springframework.transaction.TransactionSystemException tx =
                new org.springframework.transaction.TransactionSystemException("Could not commit JPA transaction", root);

        ResponseEntity<MessageResponseDto> response = handler.handleTransactionSystemException(tx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Racun ne postoji: 999");
    }

    @Test
    void handleTransactionSystemException_accessDeniedCause_returns403() {
        AccessDeniedException root = new AccessDeniedException("Racun ne pripada klijentu.");
        org.springframework.transaction.TransactionSystemException tx =
                new org.springframework.transaction.TransactionSystemException("Could not commit JPA transaction", root);

        ResponseEntity<MessageResponseDto> response = handler.handleTransactionSystemException(tx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Racun ne pripada klijentu.");
    }

    @Test
    void handleTransactionSystemException_nullMessageFallback() {
        org.springframework.transaction.TransactionSystemException tx =
                new org.springframework.transaction.TransactionSystemException("Could not commit");

        ResponseEntity<MessageResponseDto> response = handler.handleTransactionSystemException(tx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Fallback message kad nema cause-a (getMostSpecificCause() vraca samog ex-a — sa porukom "Could not commit")
        assertThat(response.getBody().getMessage()).isNotBlank();
    }
}
