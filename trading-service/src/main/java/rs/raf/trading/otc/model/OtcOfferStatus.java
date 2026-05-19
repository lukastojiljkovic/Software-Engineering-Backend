package rs.raf.trading.otc.model;

/**
 * Stanje OTC ponude tokom pregovora.
 *
 * Spec: Celina 4 - OTC Trgovina / Aktivne ponude.
 */
public enum OtcOfferStatus {
    /** Pregovaranje u toku — ponuda ceka odgovor druge strane. */
    ACTIVE,
    /** Obe strane su prihvatile trenutne uslove — kreiran je OtcContract. */
    ACCEPTED,
    /** Jedna strana je otkazala pregovor. */
    DECLINED
}
