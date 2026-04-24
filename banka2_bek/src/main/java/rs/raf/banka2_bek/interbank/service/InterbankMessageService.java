package rs.raf.banka2_bek.interbank.service;

import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.interbank.dto.InterbankEnvelopeDto;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;

/*
================================================================================
 TODO — PERSISTENCIJA + IDEMPOTENTNOST ZA INTER-BANK PORUKE
 Zaduzen: BE tim
--------------------------------------------------------------------------------
 METODE:

 1. InterbankMessage recordOutbound(InterbankEnvelopeDto envelope);
    Upise novu OUTBOUND poruku pre slanja (status=null).

 2. void updateOutboundResponse(String messageId, Integer httpStatus,
                                InterbankEnvelopeDto response);
    Posle primljenog odgovora: azurira outbound poruku sa httpStatus-om
    i kreira INBOUND zapis za odgovor.

 3. boolean isDuplicate(String messageId);
    Za inbound: ako smo vec primili poruku sa istim messageId-jem, ignorise
    se. Spec (Celina 4 linija 424): "mehanizme za resavanje grešaka i
    ponavljanje transakcija". Idempotentnost je kljucna kod retry-a.

 4. InterbankMessage recordInbound(InterbankEnvelopeDto envelope, int httpStatus);
    Upise INBOUND poruku posle prijema.
================================================================================
*/
@Service
public class InterbankMessageService {

    private final InterbankMessageRepository repository;

    public InterbankMessageService(InterbankMessageRepository repository) {
        this.repository = repository;
    }

    public InterbankMessage recordOutbound(InterbankEnvelopeDto envelope) {
        // TODO: kreiraj entitet iz envelope, setuj direction=OUTBOUND, httpStatus=null
        throw new UnsupportedOperationException("TODO");
    }

    public void updateOutboundResponse(String messageId, Integer httpStatus,
                                       InterbankEnvelopeDto response) {
        // TODO: ucitaj poruku po messageId, setuj httpStatus
        //       posle toga upise odgovor kao novu INBOUND poruku (recordInbound)
        throw new UnsupportedOperationException("TODO");
    }

    public boolean isDuplicate(String messageId) {
        // TODO: repository.findByMessageId(messageId).isPresent()
        throw new UnsupportedOperationException("TODO");
    }

    public InterbankMessage recordInbound(InterbankEnvelopeDto envelope, int httpStatus) {
        // TODO: kreiraj entitet iz envelope, direction=INBOUND, httpStatus
        throw new UnsupportedOperationException("TODO");
    }
}
