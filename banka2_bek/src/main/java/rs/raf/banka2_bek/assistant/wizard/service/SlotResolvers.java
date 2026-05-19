package rs.raf.banka2_bek.assistant.wizard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsListing;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOrder;
import rs.raf.banka2_bek.assistant.wizard.model.SlotOption;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.card.model.Card;
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRecipientRepository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Centralized DB-lookup helpers for slot resolvers.
 *
 * Each method produces a list of {@link SlotOption} that the wizard can
 * render as buttons. Robust to missing data (empty list returned, never null).
 *
 * <p>Faza 2f: trgovinski lookup-i (listinzi, orderi, fondovi, OTC ponude i
 * ugovori) vise ne idu preko in-process trgovinskih repozitorijuma — trgovinski
 * domen je iseljen u {@code trading-service}. Resolveri tih slot-ova zovu
 * {@link TradingServiceClient} (HTTP, JWT pozivaoca). Bankarski resolveri
 * (racuni, primaoci placanja, kartice) su netaknuti.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SlotResolvers {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final PaymentRecipientRepository paymentRecipientRepository;
    private final CardRepository cardRepository;
    private final TradingServiceClient tradingServiceClient;

    /* ============================== ACCOUNTS ============================== */

    /**
     * User's own accounts (clients only). Label includes type, masked number, balance.
     */
    public List<SlotOption> userAccounts(UserContext user) {
        if (!UserRole.isClient(user.userRole())) return List.of();
        var accounts = accountRepository.findByClientId(user.userId());
        return accounts.stream()
                .map(a -> new SlotOption(
                        a.getAccountNumber(),
                        formatAccountLabel(a),
                        a.getAvailableBalance() == null ? null
                                : formatBalance(a) + " dostupno"
                ))
                .toList();
    }

    /**
     * User's accounts but value is account.id (Long) instead of accountNumber.
     * Used by order handlers that expect Long accountId in DTO (vs payment handlers
     * which use accountNumber string).
     */
    public List<SlotOption> userAccountsByIdValue(UserContext user) {
        if (!UserRole.isClient(user.userRole())) return List.of();
        var accounts = accountRepository.findByClientId(user.userId());
        return accounts.stream()
                .map(a -> new SlotOption(
                        String.valueOf(a.getId()),
                        formatAccountLabel(a),
                        a.getAvailableBalance() == null ? null
                                : formatBalance(a) + " dostupno"
                ))
                .toList();
    }

    /**
     * User's accounts that have at least the given amount available (for sender slot).
     * If no account has enough, returns ALL accounts (so user can still pick — preview will warn).
     */
    public List<SlotOption> userAccountsWithMinBalance(UserContext user, BigDecimal minBalance) {
        if (!UserRole.isClient(user.userRole())) return List.of();
        var accounts = accountRepository.findByClientId(user.userId());
        if (minBalance == null) return userAccounts(user);
        var ok = accounts.stream()
                .filter(a -> a.getAvailableBalance() != null
                        && a.getAvailableBalance().compareTo(minBalance) >= 0)
                .toList();
        if (ok.isEmpty()) ok = accounts;
        return ok.stream()
                .map(a -> new SlotOption(
                        a.getAccountNumber(),
                        formatAccountLabel(a),
                        formatBalance(a)
                ))
                .toList();
    }

    /* ============================== RECIPIENTS ============================== */

    /**
     * User's saved payment recipients (clients only). Returns options where
     * value=accountNumber, label=name, hint=masked account.
     */
    public List<SlotOption> userRecipients(UserContext user) {
        if (!UserRole.isClient(user.userRole())) return List.of();
        var clientOpt = clientRepository.findById(user.userId());
        if (clientOpt.isEmpty()) return List.of();
        var recipients = paymentRecipientRepository.findByClientOrderByCreatedAtDesc(clientOpt.get());
        return recipients.stream()
                .map(r -> new SlotOption(
                        r.getAccountNumber(),
                        r.getName(),
                        maskAccount(r.getAccountNumber())
                ))
                .toList();
    }

    /* ============================== CARDS ============================== */

    /**
     * User's cards. Label includes brand/last4 + status. Used for block/unblock wizards.
     */
    public List<SlotOption> userCards(UserContext user, boolean onlyActive) {
        if (!UserRole.isClient(user.userRole())) return List.of();
        var cards = cardRepository.findByClientId(user.userId());
        return cards.stream()
                .filter(c -> !onlyActive || isActive(c))
                .map(c -> new SlotOption(
                        String.valueOf(c.getId()),
                        formatCardLabel(c),
                        formatCardStatus(c)
                ))
                .toList();
    }

    /* ============================== LISTINGS / TICKERS ============================== */

    /**
     * Top N listings by type — for ticker selection slot. Includes ticker,
     * name, current price. Faza 2f: {@code GET /listings} na trading-service.
     */
    public List<SlotOption> topListings(String listingTypeName, int limit) {
        try {
            List<TsListing> listings = tradingServiceClient.searchListings(listingTypeName, null);
            return listings.stream()
                    .filter(l -> listingTypeName == null
                            || (l.listingType() != null && listingTypeName.equalsIgnoreCase(l.listingType())))
                    .limit(limit)
                    .map(this::listingToOption)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load listings for slot resolver: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Lookup listing by ticker (case-insensitive). Returns 0/1 options for confirmation.
     * Faza 2f: {@code GET /listings?search=} na trading-service.
     */
    public List<SlotOption> listingByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) return List.of();
        try {
            return tradingServiceClient.findListingByTicker(ticker)
                    .map(l -> List.of(listingToOption(l)))
                    .orElse(List.of());
        } catch (Exception e) {
            log.warn("Failed to look up listing by ticker: {}", e.getMessage());
            return List.of();
        }
    }

    private SlotOption listingToOption(TsListing l) {
        String label = (l.ticker() == null ? "?" : l.ticker())
                + " — " + (l.name() == null ? "(bez naziva)" : l.name());
        String hint = (l.price() == null ? "?" : l.price().toPlainString())
                + " " + (l.quoteCurrency() == null ? "" : l.quoteCurrency())
                + (l.exchangeAcronym() == null ? "" : " · " + l.exchangeAcronym());
        return new SlotOption(String.valueOf(l.id()), label, hint);
    }

    /* ============================== ORDERS ============================== */

    /**
     * Cancelable orders (PENDING / APPROVED with remainingPortions > 0) of the user.
     * Faza 2f: {@code GET /orders/my} na trading-service vraca samo ordere
     * pozivaoca, pa se userId vise ne filtrira ovde.
     */
    public List<SlotOption> userCancelableOrders(UserContext user) {
        try {
            List<TsOrder> all = tradingServiceClient.recentOrders(20);
            return all.stream()
                    .filter(this::isCancelable)
                    .sorted(Comparator.comparing(TsOrder::id).reversed())
                    .limit(20)
                    .map(this::orderToOption)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load cancelable orders: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isCancelable(TsOrder o) {
        if (o == null || o.id() == null) return false;
        if (o.status() == null) return false;
        String s = o.status();
        if (!"PENDING".equals(s) && !"APPROVED".equals(s)) return false;
        Integer remaining = o.remainingPortions();
        return remaining != null && remaining > 0;
    }

    private SlotOption orderToOption(TsOrder o) {
        String label = "Nalog #" + o.id() + " — " + safe(o.direction())
                + " " + safe(o.orderType());
        String hint = "Preostalo: " + (o.remainingPortions() == null ? "?" : o.remainingPortions())
                + " · status: " + safe(o.status());
        return new SlotOption(String.valueOf(o.id()), label, hint);
    }

    /* ============================== FUNDS ============================== */

    /** Faza 2f: {@code GET /funds} na trading-service. */
    public List<SlotOption> investmentFunds(int limit) {
        try {
            return tradingServiceClient.listFunds().stream()
                    .limit(limit)
                    .map(f -> new SlotOption(
                            String.valueOf(f.id()),
                            f.name() == null ? "Fond #" + f.id() : f.name(),
                            f.minimumContribution() == null ? null
                                    : "Min ulog: " + f.minimumContribution().toPlainString()
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load funds: {}", e.getMessage());
            return List.of();
        }
    }

    /* ============================== OTC ============================== */

    /**
     * Faza 2f: {@code GET /otc/offers/active} na trading-service vraca aktivne
     * ponude pozivaoca (kupac ili prodavac), pa se status/ucesnik vise ne
     * filtriraju ovde.
     */
    public List<SlotOption> userActiveOtcOffers(UserContext user) {
        try {
            return tradingServiceClient.myActiveOtcOffers().stream()
                    .limit(20)
                    .map(o -> new SlotOption(
                            String.valueOf(o.id()),
                            "OTC ponuda #" + o.id(),
                            "Cena/akcija: " + (o.pricePerStock() == null ? "?" : o.pricePerStock().toPlainString())
                                    + " · premium: " + (o.premium() == null ? "?" : o.premium().toPlainString())
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load OTC offers: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Faza 2f: {@code GET /otc/contracts?status=ACTIVE} na trading-service
     * vraca ugovore pozivaoca; samo kupac moze da iskoristi ugovor pa se
     * {@code buyerId == user} filter zadrzava.
     */
    public List<SlotOption> userExercisableOtcContracts(UserContext user) {
        try {
            return tradingServiceClient.myActiveOtcContracts().stream()
                    .filter(c -> c.buyerId() != null && c.buyerId().equals(user.userId()))
                    .limit(20)
                    .map(c -> new SlotOption(
                            String.valueOf(c.id()),
                            "Ugovor #" + c.id(),
                            "Strike: " + (c.strikePrice() == null ? "?" : c.strikePrice().toPlainString())
                    ))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load OTC contracts: {}", e.getMessage());
            return List.of();
        }
    }

    /* ============================== STATIC ============================== */

    public List<SlotOption> orderTypeOptions() {
        return List.of(
                new SlotOption("MARKET", "Market", "Trenutna trzisna cena, instant izvrsenje"),
                new SlotOption("LIMIT", "Limit", "Max BUY / min SELL cena, ceka da trziste dodje"),
                new SlotOption("STOP", "Stop", "Aktivira se kad cena dostigne stop, onda Market"),
                new SlotOption("STOP_LIMIT", "Stop-Limit", "Aktivira se na stop, onda Limit")
        );
    }

    public List<SlotOption> directionOptions() {
        return List.of(
                new SlotOption("BUY", "Kupovina", null),
                new SlotOption("SELL", "Prodaja", null)
        );
    }

    public List<SlotOption> yesNoOptions() {
        return List.of(
                new SlotOption("YES", "Da", null),
                new SlotOption("NO", "Ne", null)
        );
    }

    public List<SlotOption> paymentCodeOptions() {
        // Standardni kodovi placanja u SR. 289 = ostalo (default).
        return List.of(
                new SlotOption("289", "Ostalo (289)", "Generalno placanje"),
                new SlotOption("221", "Plata (221)", null),
                new SlotOption("253", "Komunalije (253)", null),
                new SlotOption("160", "Otplata kredita (160)", null),
                new SlotOption("290", "Drugi transfer (290)", null)
        );
    }

    /* ============================== UTIL ============================== */

    public static String maskAccount(String n) {
        if (n == null || n.length() < 8) return n;
        return n.substring(0, 3) + "..." + n.substring(n.length() - 4);
    }

    private String formatAccountLabel(Account a) {
        String type = a.getAccountCategory() == null ? "" : labelType(a.getAccountCategory().name());
        String currency = a.getCurrency() == null ? "" : a.getCurrency().getCode();
        return type + " " + currency + " — " + maskAccount(a.getAccountNumber());
    }

    private String formatBalance(Account a) {
        String code = a.getCurrency() == null ? "" : a.getCurrency().getCode();
        return (a.getAvailableBalance() == null ? "0" : a.getAvailableBalance().toPlainString()) + " " + code;
    }

    private String formatCardLabel(Card c) {
        String last4 = "????";
        try {
            String num = c.getCardNumber();
            if (num != null && num.length() >= 4) last4 = num.substring(num.length() - 4);
        } catch (Exception ignored) { /* skip */ }
        String type = c.getCardType() == null ? "Kartica" : c.getCardType().name();
        return type + " ****" + last4;
    }

    private String formatCardStatus(Card c) {
        try {
            return c.getStatus() == null ? "" : "Status: " + c.getStatus().name();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isActive(Card c) {
        try {
            return c.getStatus() != null && "ACTIVE".equalsIgnoreCase(c.getStatus().name());
        } catch (Exception ignored) {
            return true;
        }
    }

    private String labelType(String accountCategory) {
        if (accountCategory == null) return "Racun";
        return switch (accountCategory) {
            case "CHECKING" -> "Tekuci";
            case "FOREIGN" -> "Devizni";
            case "BUSINESS" -> "Poslovni";
            case "SAVINGS" -> "Stedni";
            case "RESERVED" -> "Rezervisan";
            default -> accountCategory;
        };
    }

    private String safe(Object o) { return o == null ? "?" : o.toString(); }

    /**
     * Normalize string for fuzzy match — lowercase + diacritic strip + trim.
     */
    public static String normalizeForMatch(String s) {
        if (s == null) return null;
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .trim();
    }

    /**
     * Fuzzy lookup — finds first option whose label normalized prefix-matches every
     * 3+ char token of the user query. Used for prefill from natural language.
     * Returns null if no match.
     */
    public static SlotOption fuzzyMatch(List<SlotOption> options, String userQuery) {
        if (options == null || options.isEmpty() || userQuery == null) return null;
        String norm = normalizeForMatch(userQuery);
        if (norm == null) return null;
        String[] tokens = norm.split("\\s+");
        List<String> meaningful = new ArrayList<>();
        for (String t : tokens) if (t.length() >= 3) meaningful.add(t);
        if (meaningful.isEmpty()) return null;
        for (SlotOption opt : options) {
            String labelNorm = normalizeForMatch(opt.label());
            if (labelNorm == null) continue;
            boolean allMatch = true;
            for (String t : meaningful) {
                String prefix = t.substring(0, Math.min(t.length(), 4));
                if (!labelNorm.contains(prefix)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return opt;
        }
        return null;
    }
}
