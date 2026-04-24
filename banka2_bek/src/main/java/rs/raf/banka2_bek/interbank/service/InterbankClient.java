package rs.raf.banka2_bek.interbank.service;

import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.dto.InterbankEnvelopeDto;

/*
================================================================================
 TODO — HTTP KLIJENT ZA SLANJE PORUKA PARTNERSKIM BANKAMA
 Zaduzen: BE tim
 Spec referenca: Celina 4, "potrebno definisati format poruka (JSON)"
--------------------------------------------------------------------------------
 SVRHA:
 Apstrakcija preko HTTP poziva ka drugim bankama. Svaki servis koji hoce da
 salje (PaymentService, OtcService, RetryScheduler) poziva samo ovde metod
 `send(envelope)` — klijent resolvuje URL iz `receiverBankCode`, dodaje
 Authorization header, timeout, serializuje u JSON, upise u
 InterbankMessage audit log i vrati odgovor.

 OBAVEZNE METODE:
  InterbankEnvelopeDto send(InterbankEnvelopeDto request);
    - Sinhroni poziv, cekamo odgovor. Za timeout/4xx/5xx baca se
      InterbankCommunicationException.
    - Upise outbound poruku u InterbankMessage tabelu pre poziva.
    - Upise odgovor sa httpStatus-om u InterbankMessage.

 PREPORUKA IMPLEMENTACIJE:
  - Koristi RestClient (Spring 6.1+) ili WebClient (async verzija).
  - Jednostavan pool: jedan Bean sa connection pool-om; per-partner URL
    se resolvuje pri svakom pozivu (nije thread-local).
  - Timeout: 10s default, konfigurabilan.
  - Retries NA OVOM NIVOU nemoj — retry radi InterbankRetryScheduler na
    transakcijskom nivou, ne na HTTP.

 AUTHORIZATION:
  - Authorization: Bearer <partner.authToken>
  - Naslovi: X-Interbank-Message-Id: <envelope.messageId>

 ENDPOINT:
  POST {partner.baseUrl}/bank-api/message
================================================================================
*/
@Service
public class InterbankClient {

    private final InterbankProperties properties;
    private final BankRoutingService routing;
    // TODO: injectovati ObjectMapper, InterbankMessageService (audit), RestClient

    public InterbankClient(InterbankProperties properties, BankRoutingService routing) {
        this.properties = properties;
        this.routing = routing;
    }

    /**
     * TODO — implementiraj:
     * 1. Resolve partner preko routing.resolvePartnerByCode(env.receiverBankCode)
     *    → ako null, baci InterbankCommunicationException("unknown bank").
     * 2. Upise outbound InterbankMessage u bazu sa httpStatus=null (jos ne znamo).
     * 3. Postavi senderBankCode = properties.myBankCode
     * 4. Postavi sentAt = now()
     * 5. POST {partner.baseUrl}/bank-api/message, body = envelope, Bearer token.
     * 6. Upise response kao INBOUND InterbankMessage (ili azuriraj outbound
     *    zapis sa httpStatus-om).
     * 7. Parse telo odgovora u InterbankEnvelopeDto i vrati.
     *
     * GRESKA: InterbankCommunicationException (RuntimeException) za sve
     * mrezne/timeout/5xx greske. Pustaj gore.
     */
    public InterbankEnvelopeDto send(InterbankEnvelopeDto request) {
        throw new UnsupportedOperationException("TODO: implementirati InterbankClient.send");
    }
}
