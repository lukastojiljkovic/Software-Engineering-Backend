package rs.raf.banka2_bek.interbank.exception;

/*
================================================================================
 TODO — EXCEPTION HIJERARHIJA ZA INTER-BANK SLOJ
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 InterbankException (base RuntimeException)
   ├─ InterbankCommunicationException  — HTTP/mrezne greske (timeout, 5xx, unknown bank)
   ├─ InterbankProtocolException       — validation (nevalidan payload, unknown type)
   └─ InterbankTransactionStuckException — retry scheduler odustao

 HANDLER:
 Registrovati @RestControllerAdvice u istoj paketi (vidi
 InterbankExceptionHandler.java) koji mapira exceptione u HTTP kodove:
  - Communication  -> 502 Bad Gateway (nepratilo)
  - Protocol       -> 400
  - Stuck          -> 500
================================================================================
*/
public final class InterbankExceptions {

    private InterbankExceptions() {}

    public static class InterbankException extends RuntimeException {
        public InterbankException(String message) { super(message); }
        public InterbankException(String message, Throwable cause) { super(message, cause); }
    }

    public static class InterbankCommunicationException extends InterbankException {
        public InterbankCommunicationException(String message) { super(message); }
        public InterbankCommunicationException(String message, Throwable cause) { super(message, cause); }
    }

    public static class InterbankProtocolException extends InterbankException {
        public InterbankProtocolException(String message) { super(message); }
    }

    public static class InterbankTransactionStuckException extends InterbankException {
        public InterbankTransactionStuckException(String message) { super(message); }
    }
}
