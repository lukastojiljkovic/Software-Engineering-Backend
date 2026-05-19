package rs.raf.trading.margin.model;

/**
 * Tip transakcije na margin racunu.
 *
 * DEPOSIT    - uplata sredstava na margin racun (povecava initialMargin)
 * WITHDRAWAL - isplata sredstava sa margin racuna (smanjuje initialMargin)
 * BUY        - kupovina hartija od vrednosti putem margin racuna
 * SELL       - prodaja hartija od vrednosti putem margin racuna
 */
public enum MarginTransactionType {
    DEPOSIT,
    WITHDRAWAL,
    BUY,
    SELL
}
