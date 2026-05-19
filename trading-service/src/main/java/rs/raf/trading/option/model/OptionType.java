package rs.raf.trading.option.model;

/**
 * Tip opcije - CALL (pravo kupovine) ili PUT (pravo prodaje).
 *
 * CALL - daje kupcu pravo da kupi akciju po strike ceni do settlement datuma.
 * PUT  - daje kupcu pravo da proda akciju po strike ceni do settlement datuma.
 */
public enum OptionType {
    CALL,
    PUT
}
