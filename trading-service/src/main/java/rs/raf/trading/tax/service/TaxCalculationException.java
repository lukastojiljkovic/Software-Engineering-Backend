package rs.raf.trading.tax.service;

/**
 * BE-ORD-08: signalizira da obracun poreza za tekuceg korisnika ne moze biti
 * korektno izvrsen — najcesce zbog nedostupnog FX kursa (banka-core
 * /internal/fx/rates 5xx/timeout) koji bi inace tisko fallback-ovao na
 * raw amount i tretirao npr. USD 1000 kao 1000 RSD (severe under-taxation).
 *
 * <p>Bacanje exception-a u {@link TaxService#calculateTaxForAllUsers()}
 * prekida obracun za TOG korisnika, ali {@link rs.raf.trading.tax.scheduler.TaxScheduler}
 * je-uhvati i nastavi sa sledecim korisnikom. Supervizor dobija notifikaciju
 * da je obracun za korisnika preskocen, pa moze ili da pokrene retry kad FX
 * radi, ili da rucno reseti taxPaid.
 */
public class TaxCalculationException extends RuntimeException {

    private final Long userId;
    private final String userType;

    public TaxCalculationException(Long userId, String userType, String message, Throwable cause) {
        super(message, cause);
        this.userId = userId;
        this.userType = userType;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserType() {
        return userType;
    }
}
