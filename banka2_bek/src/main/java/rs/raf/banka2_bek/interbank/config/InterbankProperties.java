package rs.raf.banka2_bek.interbank.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/*
================================================================================
 TODO — KONFIGURACIJA PARTNERSKIH BANAKA
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 367 "dovoljno je da svaka banka komunicira
                                       sa jednom drugom"
--------------------------------------------------------------------------------
 Citaj iz application.properties:
   interbank.my-bank-code=BANKA2
   interbank.my-account-prefix=222
   interbank.partners[0].code=BANKA1
   interbank.partners[0].account-prefix=111
   interbank.partners[0].base-url=http://banka1-api:8080
   interbank.partners[0].auth-token=sekret1
   interbank.partners[1].code=BANKA3
   ...

 KORISNICI:
  - BankRoutingService: po prva 3 cifre racuna mapira na PartnerBank.
  - InterbankClient: na osnovu bankCode-a pronadje URL i salje HTTP zahtev.

 SECURITY:
  - auth-token se salje u Authorization header u inter-bank pozivima.
  - Inbound endpoint (InterbankInboundController) proverava token prema
    `partners[*].auth-token`. Nevalidan token → 401.
================================================================================
*/
@Configuration
@ConfigurationProperties(prefix = "interbank")
@Data
public class InterbankProperties {

    /** Kod nase banke; koristi se u `senderBankCode` svih outbound poruka. */
    private String myBankCode;

    /** Prefix svakog naseg racuna (prve 3 cifre). */
    private String myAccountPrefix;

    /** Lista partnerskih banaka sa kojima smo u komunikaciji. */
    private List<PartnerBank> partners = new ArrayList<>();

    @Data
    public static class PartnerBank {
        private String code;
        private String accountPrefix;
        private String baseUrl;
        private String authToken;
    }
}
