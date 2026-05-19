package rs.raf.trading.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.order.exception.UnsupportedCurrencyException;

/**
 * Resolver koji vraca bankin trading racun u trazenoj valuti.
 * Koristi se u order flow-u kada treba odrediti sa kog bankinog racuna
 * agent trguje (rezervacija, isplata provizije, settlement).
 *
 * NAPOMENA (copy-first ekstrakcija, faza 2c): u monolitu je racun citan
 * direktno iz {@code AccountRepository}. U trading-service-u racuni zive u
 * banka-core domenu, pa se bankin trading racun razresava preko banka-core
 * internog seam-a ({@link BankaCoreClient#getBankTradingAccount(String)} —
 * {@code GET /internal/accounts/bank-trading/{ccy}}). Vraca
 * {@link InternalAccountDto} umesto {@code Account} entiteta.
 */
@Service
@RequiredArgsConstructor
public class BankTradingAccountResolver {

    private final BankaCoreClient bankaCoreClient;

    /**
     * Vraca prvi bankin trading racun u datoj valuti.
     *
     * @param listingCurrency ISO kod valute (npr. "RSD", "USD", "EUR")
     * @return {@link InternalAccountDto} bankinog {@code BANK_TRADING} racuna u datoj valuti
     * @throws UnsupportedCurrencyException ako banka nema racun u trazenoj valuti
     */
    public InternalAccountDto resolve(String listingCurrency) {
        try {
            return bankaCoreClient.getBankTradingAccount(listingCurrency);
        } catch (BankaCoreClientException ex) {
            // banka-core vraca 404 / UnsupportedCurrency kad nema BANK_TRADING racun
            // u datoj valuti — verno monolitovom orElseThrow ponasanju.
            throw new UnsupportedCurrencyException(
                    "Banka ne podrzava trgovinu u valuti: " + listingCurrency);
        }
    }
}
