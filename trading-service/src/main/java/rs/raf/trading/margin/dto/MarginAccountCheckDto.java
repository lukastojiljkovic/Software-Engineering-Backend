package rs.raf.trading.margin.dto;


import java.math.BigDecimal;

/**
 * Projekcija margin racuna koji prelaze u status BLOCKED tokom dnevne provere
 * maintenance margine.
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-D): u monolitu je native query
 * {@code findAccountsForMarginCheck} JOIN-ovao {@code clients} tabelu da odmah
 * popuni {@code ownerEmail}. Pod arhitekturom baza-po-servisu trading-service
 * nema {@code clients} tabelu — cross-schema JOIN bi pukao. Zato query sada bira
 * samo {@code margin}-owned kolone i puni {@code ownerUserId}; {@code ownerEmail}
 * ostaje {@code null} dok ga {@code MarginAccountService} ne razresi po
 * blokiranom racunu preko {@code BankaCoreClient.getUserById("CLIENT", ownerUserId)}.
 */
public record MarginAccountCheckDto(
        Long marginAccountId,
        /* vlasnik margin racuna — clients.id (margin_accounts.user_id) */
        Long ownerUserId,
        /* razresava se preko banka-core internog API-ja u servisu (null iz query-ja) */
        String ownerEmail,
        BigDecimal maintenanceMargin,
        BigDecimal initialMargin
) {
    public BigDecimal calculateMaintenanceDeficit() {
        return maintenanceMargin.subtract(initialMargin).max(BigDecimal.ZERO);
    }

    /** Vraca kopiju sa razresenim email-om vlasnika (banka-core lookup). */
    public MarginAccountCheckDto withOwnerEmail(String resolvedEmail) {
        return new MarginAccountCheckDto(
                marginAccountId, ownerUserId, resolvedEmail, maintenanceMargin, initialMargin);
    }
}
