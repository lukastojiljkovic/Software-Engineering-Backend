package rs.raf.banka2_bek.interbank.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.interbank.util.SecurityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Configuration
@ConfigurationProperties(prefix = "interbank")
@Data
public class InterbankProperties {

    /** Routing number nase banke (prve 3 cifre svakog naseg racuna). */
    private Integer myRoutingNumber;

    /** Display name nase banke koji se vraca u UserInformation.bankDisplayName (§3.7). */
    private String myBankDisplayName;

    /** Lista partnerskih banaka sa kojima smo u komunikaciji. */
    private List<PartnerBank> partners = new ArrayList<>();

    @Data
    public static class PartnerBank {
        /** Routing number partnerske banke. */
        private Integer routingNumber;

        /**
         * Prefiks broja racuna ove banke (prve 3 cifre racuna) AKO se razlikuje
         * od inter-bank routing broja. EXBanka 2: racuni pocinju sa 666, ali su
         * inter-bank routing postavili na 265 — pa je {@code accountPrefix=666},
         * {@code routingNumber=265}. Kad je null, prefiks racuna == routingNumber
         * (uobicajeno, npr. Banka 1: 111/111).
         *
         * <p>Bez ovog mapiranja, {@code BankRoutingService.routingForAccount} bi
         * iz racuna {@code 666...} izveo routing 666 i 2PC bi pao
         * ("Target routing number 666 could not be resolved").
         */
        private Integer accountPrefix;

        /** Display name partnerske banke (za UI). */
        private String displayName;

        /** Base URL partnerskog API-ja, npr. "http://banka1-api:8080". */
        private String baseUrl;

        /** Token koji partner banka izdaje nama; saljemo ga u X-Api-Key headeru. */
        private String outboundToken;

        /** Token koji mi izdajemo partner banci; verifikujemo ga u X-Api-Key headeru. */
        private String inboundToken;

        /**
         * §3.7 GET /user path template — po spec-u je <code>/user/{rn}/{id}</code>,
         * ali neki partneri (Tim 1) su rerouted-ovali endpoint na
         * <code>/interbank/user/{rn}/{id}</code> zbog internih path collision-a sa
         * frontend rutama. Default je per-spec; override po partneru u
         * <code>application.properties</code>:
         * <pre>interbank.partners[0].user-info-path=/interbank/user/{rn}/{id}</pre>
         * Placeholderi <code>{rn}</code> i <code>{id}</code> ce biti zamenjeni od
         * strane <code>InterbankClient.getUserInfo()</code> kroz RestClient URI
         * template substitution.
         */
        private String userInfoPath = "/user/{rn}/{id}";
    }

    public Optional<PartnerBank> findByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return partners.stream()
                .filter(p -> p.getInboundToken() != null
                        && SecurityUtils.constantTimeEquals(p.getInboundToken(), apiKey))
                .findFirst();
    }

    /** Pronalazenje partnera po routing broju (npr. iz Posting/TxAccount). */
    public Optional<PartnerBank> findByRoutingNumber(Integer routingNumber) {
        if (routingNumber == null) {
            return Optional.empty();
        }
        return partners.stream()
                .filter(p -> routingNumber.equals(p.getRoutingNumber()))
                .findFirst();
    }
}
