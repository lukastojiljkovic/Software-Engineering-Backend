package rs.raf.trading.tax.util;

import java.math.BigDecimal;

/**
 * Konstante vezane za porez na kapitalnu dobit.
 *
 * Spec (Celina 3 - Porez u nasem sistemu): "Iznos poreza je 15%".
 * Pre je bio duplikovan u {@code TaxService.TAX_RATE} i inline u
 * {@code PortfolioService#getSummary}.
 */
public final class TaxConstants {

    private TaxConstants() {}

    /** Stopa poreza na kapitalnu dobit — 15%. */
    public static final BigDecimal TAX_RATE = new BigDecimal("0.15");
}
