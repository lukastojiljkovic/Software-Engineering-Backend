package rs.raf.banka2_bek.interbank.service;

import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;

import java.util.Optional;

/*
================================================================================
 TODO — ROUTING PO PREFIXU RACUNA
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 377 "Banka A identifikuje banku primaoca
                                       na osnovu prve tri cifre broja racuna"
--------------------------------------------------------------------------------
 SVRHA:
 Za dato broj racuna, otkrij da li je to nasa banka ili koja partnerska.
 Koristi se u PaymentController.createPayment — ako je receiver racun
 u drugoj banci, redirektuj na InterbankPaymentService.initiate umesto
 obicnog transfer flow-a.

 METODE:
  Optional<InterbankProperties.PartnerBank> resolvePartner(String accountNumber);
  boolean isLocalAccount(String accountNumber);
  String myBankCode(); // proxy za properties.getMyBankCode()

 IMPLEMENTACIJA:
  - uzmi prve 3 cifre iz accountNumber
  - uporedi sa myAccountPrefix (ako je isto -> local)
  - pretrazi partners[*].accountPrefix
  - vrati first match ili Optional.empty() (npr. za nepoznatu banku)

 EDGE CASES:
  - accountNumber null/kratak -> throw IllegalArgumentException
  - accountNumber koji ne odgovara nijednoj banci -> throw (spec: transakcija
    ne moze da se obradi) -> vrati Optional.empty() i pusti caller-a da baci
================================================================================
*/
@Service
public class BankRoutingService {

    private final InterbankProperties properties;

    public BankRoutingService(InterbankProperties properties) {
        this.properties = properties;
    }

    public String myBankCode() {
        // TODO: fallback na default ako nije konfigurisano
        return properties.getMyBankCode();
    }

    public boolean isLocalAccount(String accountNumber) {
        // TODO: validacija null/duzine + poredjenje prefixa
        throw new UnsupportedOperationException("TODO: implementirati BankRoutingService.isLocalAccount");
    }

    public Optional<InterbankProperties.PartnerBank> resolvePartner(String accountNumber) {
        // TODO: uzmi prve 3 cifre, trazi u properties.partners
        throw new UnsupportedOperationException("TODO: implementirati BankRoutingService.resolvePartner");
    }

    public Optional<InterbankProperties.PartnerBank> resolvePartnerByCode(String bankCode) {
        // TODO: trazi partner po code-u (inverse of resolvePartner)
        throw new UnsupportedOperationException("TODO: implementirati BankRoutingService.resolvePartnerByCode");
    }
}
