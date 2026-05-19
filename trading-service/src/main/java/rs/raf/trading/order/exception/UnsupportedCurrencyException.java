package rs.raf.trading.order.exception;

/**
 * Baca se kada BankTradingAccountResolver ne moze da nadje bankin racun
 * u trazenoj valuti, ili kada CurrencyConversionService nema kurs za par.
 */
public class UnsupportedCurrencyException extends RuntimeException {
    public UnsupportedCurrencyException(String message) {
        super(message);
    }
}
