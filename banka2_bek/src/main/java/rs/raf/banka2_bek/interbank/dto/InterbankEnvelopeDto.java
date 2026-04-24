package rs.raf.banka2_bek.interbank.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rs.raf.banka2_bek.interbank.model.InterbankMessageType;

import java.util.Map;

/*
================================================================================
 TODO — ZAJEDNICKI OMOT ZA SVE INTER-BANK PORUKE
 Zaduzen: BE tim
 Spec referenca: Celina 4, linija 424 "potrebno definisati format poruka (JSON)"
--------------------------------------------------------------------------------
 SVRHA:
 Svaka poruka koja putuje izmedju banaka ima isti spoljni oblik — metadata
 (ko, kome, koja transakcija, koja tip) + payload specifican za taj tip.

 FORMAT JSON-a (primer PREPARE za placanje):
  {
    "messageId": "uuid-poruke",
    "transactionId": "uuid-transakcije",
    "type": "PREPARE",
    "senderBankCode": "BANKA2",
    "receiverBankCode": "BANKA1",
    "sentAt": "2026-04-24T12:34:56Z",
    "payload": {
      "amount": 5000,
      "currency": "EUR",
      "senderAccountNumber": "222000...",
      "receiverAccountNumber": "111000..."
    }
  }

 IMPLEMENTACIJA:
  - ObjectMapper deserijalizuje u ovaj DTO.
  - Polje `payload` je Map<String, Object> za fleksibilnost — u servisu se
    parsuje u konkretan tip (PreparePayloadDto, ReadyPayloadDto, itd).
  - Alternativa: polymorphic deserialization sa @JsonTypeInfo — ali sa 14+
    tipova poruka bi bilo previse boilerplate-a; Map je praktican.

 VALIDACIJA:
  - messageId UUID, unique
  - transactionId UUID, iskorelisan sa InterbankTransaction tabelom
  - type mora biti u InterbankMessageType enum-u
  - senderBankCode mora biti konfigurisan kao partnerska banka
================================================================================
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterbankEnvelopeDto {
    private String messageId;
    private String transactionId;
    private InterbankMessageType type;
    private String senderBankCode;
    private String receiverBankCode;
    private String sentAt;
    private Map<String, Object> payload;
}
