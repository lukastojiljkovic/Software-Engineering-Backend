package rs.raf.banka2.contracts.internal;

import java.math.BigDecimal;

/**
 * Direktan prenos novca izmedju dva racuna (OTC premija, dividenda, porez, fond).
 *
 * <p>Cross-currency-sposoban primitiv: banka-core debituje {@code fromAccountId}
 * za {@code debitAmount} u SOPSTVENOJ valuti tog racuna, kreditira
 * {@code toAccountId} za {@code creditAmount} u NJEGOVOJ sopstvenoj valuti, i —
 * ako je {@code commission > 0} — kreditira bankin {@code BANK_TRADING} racun u
 * {@code commissionCurrency} za {@code commission}. Pozivalac (trading-service)
 * je vec uradio FX matematiku (njegov lokalni {@code CurrencyConversionService}
 * koristi kurseve koji poticu iz banka-core, pa su konzistentni) i dostavlja
 * tacne {@code debitAmount}/{@code creditAmount}/{@code commission}. Za prenos
 * iste valute je {@code debitAmount == creditAmount}.
 *
 * <p>{@code commission}/{@code commissionCurrency} su opcioni — {@code null}/0
 * znaci bez provizije.
 *
 * <p>BE-INT-07: {@code expectedRate} je OPCIONI FX kurs koji caller dostavlja
 * kao integrity check kad je prenos cross-currency. Ako je not-null, banka-core
 * validira da {@code |creditAmount - (debitAmount * expectedRate)|} ne prelazi
 * 1% od {@code |creditAmount|} — tolerancija pokriva rounding razlike, ali
 * uhvata FX racunske bug-ove na seam boundary-ju. {@code null} preskoci proveru
 * (back-compat sa starim caller-ima).
 */
public record TransferFundsRequest(Long fromAccountId, BigDecimal debitAmount,
                                   Long toAccountId, BigDecimal creditAmount,
                                   BigDecimal commission, String commissionCurrency,
                                   String description, BigDecimal expectedRate) {

    /**
     * Back-compat ctor: bez {@code expectedRate} (postavlja {@code null}, ne radi
     * FX integrity check). Postojeci pozivaoci ne moraju da se menjaju.
     */
    public TransferFundsRequest(Long fromAccountId, BigDecimal debitAmount,
                                Long toAccountId, BigDecimal creditAmount,
                                BigDecimal commission, String commissionCurrency,
                                String description) {
        this(fromAccountId, debitAmount, toAccountId, creditAmount,
                commission, commissionCurrency, description, null);
    }
}
