package rs.raf.banka2_bek.interbank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankRoutingService {

    private final InterbankProperties properties;

    public int myRoutingNumber() {
        if (properties.getMyRoutingNumber() != null)
            return properties.getMyRoutingNumber();

        throw new InterbankExceptions.InterbankProtocolException("My routing number is not defined!");

    }

    public int parseRoutingNumber(String accountNumber) {
        // if argument is null or empty
        if (accountNumber == null || accountNumber.isBlank()) // arg validation
            throw new InterbankExceptions.InterbankProtocolException("Empty accountNumber!");

        if (accountNumber.length() < 3)
            throw new InterbankExceptions.InterbankProtocolException("AccountNumber not valid!");

        // extract first 3 characters from string
        String first3digits = accountNumber.substring(0, 3);

        try {
            return Integer.parseInt(first3digits); // parsing digits to a number
        } catch (NumberFormatException e) { // if parse failed protocol exception is thrown
            throw new InterbankExceptions.InterbankProtocolException("First 3 characters of accountNumber not parsable to integer!");
        }
    }

    public boolean isLocalAccount(String accountNumber) {

        int routingNumber = parseRoutingNumber(accountNumber);
        int ourBankRoutingNumber = myRoutingNumber();

        return routingNumber == ourBankRoutingNumber;
    }

    public Optional<InterbankProperties.PartnerBank> resolvePartner(String accountNumber) {
        return resolvePartnerByRouting(routingForAccount(accountNumber));
    }

    /**
     * Vraca PROTOKOL routing broj banke koja je vlasnik datog racuna.
     * <p>
     * Vecina banaka koristi account-prefix == routing (prve 3 cifre racuna su
     * routing broj). Izuzetak su partneri kojima se account-prefix razlikuje od
     * inter-bank routing-a — npr. EXBanka 2: racuni pocinju sa 666, ali je njihov
     * protokol routing 265 (vidi {@code interbank.partners[*].account-prefix} u
     * application.properties). Za takve banke mapiramo prefix -> routing iz
     * konfiguracije.
     * <p>
     * Bez ovog, outbound 2PC bi rezolvovao partnera po sirovom prefiksu (666) i
     * pao sa "Target routing number 666 could not be resolved", pa bi SVAKO
     * placanje/transakcija ka EXBanka 2 racunu bilo odbijeno.
     *
     * @param accountNumber broj racuna (min 3 cifre)
     * @return routing broj banke-vlasnika (preveden iz account-prefiksa ako treba),
     *         ili sirov prefiks ako nijedan partner ne odgovara (nepoznata banka)
     */
    public int routingForAccount(String accountNumber) {
        int prefix = parseRoutingNumber(accountNumber);
        return properties.getPartners().stream()
                .filter(p -> p.getRoutingNumber() != null && effectivePrefix(p) == prefix)
                .map(InterbankProperties.PartnerBank::getRoutingNumber)
                .findFirst()
                .orElse(prefix);
    }

    /** Efektivni account-prefix partnera: {@code accountPrefix} ako je zadat, inace {@code routingNumber}. */
    private static int effectivePrefix(InterbankProperties.PartnerBank partner) {
        return partner.getAccountPrefix() != null
                ? partner.getAccountPrefix()
                : partner.getRoutingNumber();
    }

    public Optional<InterbankProperties.PartnerBank> resolvePartnerByRouting(int routingNumber) {

        return properties
                .getPartners()
                .stream()
                .filter(partnerBank -> partnerBank.getRoutingNumber() == routingNumber)
                .findFirst();
    }
}
