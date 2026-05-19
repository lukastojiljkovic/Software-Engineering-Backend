package rs.raf.banka2_bek.assistant.tool.handlers.agentic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.assistant.client.TradingServiceClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsListing;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Pomocni komponent za WriteToolHandler-e — pretvaranje LLM args u BE entitete.
 * Centralizuje resolver-e koji se cesto koriste (broj racuna → Account, listingId
 * → Listing, ime primaoca, itd).
 *
 * <p>Faza 2f: listing lookup vise ne ide preko in-process {@code ListingRepository}
 * (trgovinski domen je iseljen u {@code trading-service}) nego preko
 * {@link TradingServiceClient} (HTTP, JWT pozivaoca). {@code findListing*} sada
 * vracaju {@link TsListing} record umesto {@code stock.model.Listing} entiteta.
 * Bankarski resolver-i ({@code findAccountByNumber}, {@code resolveOwnerName}) su
 * netaknuti — koriste ih i ne-trgovinski handler-i (placanja, kartice, krediti).
 */
@Component
@RequiredArgsConstructor
public class AgenticHandlerSupport {

    final AccountRepository accountRepository;
    final ClientRepository clientRepository;
    final EmployeeRepository employeeRepository;
    final TradingServiceClient tradingServiceClient;

    public BigDecimal getBigDecimal(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s && !s.isBlank()) {
            try { return new BigDecimal(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public Long getLong(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public Integer getInt(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public Boolean getBool(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return null;
    }

    public String getString(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return null;
        return v.toString().trim();
    }

    public String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 6) return accountNumber;
        return accountNumber.substring(0, 3) + "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    public Optional<Account> findAccountByNumber(String number) {
        if (number == null) return Optional.empty();
        return accountRepository.findByAccountNumber(number);
    }

    /**
     * Hartija po internom ID-u — {@code GET /listings/{id}} na trading-service
     * (faza 2f). {@code Optional.empty()} ako ne postoji.
     */
    public Optional<TsListing> findListing(Long id) {
        if (id == null) return Optional.empty();
        return tradingServiceClient.findListingById(id);
    }

    /**
     * Hartija po ticker-u — {@code GET /listings?search=} na trading-service
     * (faza 2f). {@code Optional.empty()} ako nema tacnog poklapanja.
     */
    public Optional<TsListing> findListingByTicker(String ticker) {
        if (ticker == null) return Optional.empty();
        return tradingServiceClient.findListingByTicker(ticker.trim().toUpperCase());
    }

    public String resolveOwnerName(String accountNumber) {
        Account a = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (a == null) return null;
        Client c = a.getClient();
        if (c != null) return c.getFirstName() + " " + c.getLastName();
        if (a.getName() != null && !a.getName().isBlank()) return a.getName();
        return null;
    }
}
