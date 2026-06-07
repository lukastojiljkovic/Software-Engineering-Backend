package rs.raf.banka2_bek.interbank.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;
import rs.raf.banka2_bek.interbank.service.InterbankClient;
import rs.raf.banka2_bek.interbank.service.InterbankMessageService;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterbankRetryScheduler {

    private final InterbankMessageRepository messageRepository;
    private final InterbankClient client;
    private final InterbankMessageService messageService;
    private final ObjectMapper objectMapper;

    /**
     * R1-685: stale-message minimalna starost pre retry-a (sekunde). Default 120s
     * (2 min) — usklađeno sa intervalom skeniranja. Override preko
     * {@code interbank.retry.stale-after-seconds} (npr. brže u testu/demo-u).
     */
    @Value("${interbank.retry.stale-after-seconds:120}")
    private long staleAfterSeconds;

    /**
     * R1-685: interval skeniranja PENDING poruka (ms). Ranije hardkodiran 120000.
     * {@code fixedRateString} cita property-placeholder pre starta scheduler-a.
     */
    @Scheduled(fixedRateString = "${interbank.retry.interval-ms:120000}")
    public void retryStaleMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(staleAfterSeconds);
        List<InterbankMessage> pending =
                messageRepository.findPendingForRetry(InterbankMessageStatus.PENDING, cutoff);

        for (InterbankMessage msg : pending) {
            try {
                retryOne(msg);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException oneTried) {
                // Drugi scheduler instance je vec uzeo ovu poruku — preskoci tiho,
                // sledeci ciklus ce videti sveze stanje.
                log.debug("Retry skipped (concurrent worker took msg id={}): {}",
                        msg.getId(), oneTried.getMessage());
            } catch (Exception e) {
                log.error("Retry error msg id={} type={}: {}",
                        msg.getId(), msg.getMessageType(), e.getMessage());
            }
        }
    }

    private void retryOne(InterbankMessage msg) {
        IdempotenceKey key = new IdempotenceKey(
                msg.getSenderRoutingNumber(), msg.getLocallyGeneratedKey());
        int targetRn = msg.getPeerRoutingNumber();

        // Facet d (defensive guard): poruka sa null/praznim requestBody-jem ne moze
        // NIKAD da se posalje (nema sta da se deserijalizuje). Terminalizujemo je
        // odmah umesto da je ostavimo da escape-uje deserijalizaciju i ostane PENDING.
        if (msg.getRequestBody() == null || msg.getRequestBody().isBlank()) {
            messageService.markOutboundNonRetryable(key,
                    "requestBody is null/blank — message can never be delivered");
            return;
        }

        // Facet b/d: deserijalizacija tela je IZVAN inner try/catch-a bila uzrok buga —
        // neispravno telo je bacalo IllegalArgumentException ("argument \"content\" is
        // null") / JsonProcessingException koji niko nije hvatao kao "failed", pa je red
        // ostajao PENDING zauvek. Sad deserijalizujemo unutar try-a i mapiramo
        // deserialize-grešku u non-retryable terminalizaciju.
        try {
            switch (msg.getMessageType()) {
                case NEW_TX -> {
                    Message<Transaction> env = objectMapper.readValue(
                            msg.getRequestBody(), new TypeReference<Message<Transaction>>() {});
                    TransactionVote vote = client.sendMessage(
                            targetRn, MessageType.NEW_TX, env, TransactionVote.class);
                    if (vote != null) {
                        // Serijalizacija vote-a ne sme da obori send kao "non-retryable":
                        // poruka JE uspesno dostavljena (200). Ako (krajnje neverovatno)
                        // serijalizacija padne, snimi SENT bez response body-ja.
                        String voteJson;
                        try {
                            voteJson = objectMapper.writeValueAsString(vote);
                        } catch (com.fasterxml.jackson.core.JsonProcessingException ser) {
                            voteJson = null;
                        }
                        messageService.markOutboundSent(key, 200, voteJson);
                    } else {
                        messageService.markOutboundSent(key, 202, null);
                    }
                }
                case COMMIT_TX -> {
                    Message<CommitTransaction> env = objectMapper.readValue(
                            msg.getRequestBody(), new TypeReference<Message<CommitTransaction>>() {});
                    client.sendMessage(targetRn, MessageType.COMMIT_TX, env, Void.class);
                    messageService.markOutboundSent(key, 204, null);
                }
                case ROLLBACK_TX -> {
                    Message<RollbackTransaction> env = objectMapper.readValue(
                            msg.getRequestBody(), new TypeReference<Message<RollbackTransaction>>() {});
                    client.sendMessage(targetRn, MessageType.ROLLBACK_TX, env, Void.class);
                    messageService.markOutboundSent(key, 204, null);
                }
            }
        } catch (InterbankExceptions.InterbankCommunicationException |
                 InterbankExceptions.InterbankAuthException e) {
            // TRANSIENT: mrezna greska / partner 5xx / 401 — retry moze uspeti.
            // markOutboundFailed inkrementira retryCount i drzi PENDING (faza-2 do
            // dead-letter backstop-a; faza-1 do STUCK). NE terminalizujemo ovde —
            // ovo je 2PC durability invarijanta za COMMIT_TX/ROLLBACK_TX.
            messageService.markOutboundFailed(key, e.getMessage());
        } catch (InterbankExceptions.InterbankProtocolException e) {
            // NON-RETRYABLE (facet b): npr. "Target routing number 666 could not be
            // resolved" iz InterbankClient.sendMessage. Ruta se nikad nece razresiti
            // retry-em → terminalizuj umesto beskonacnog PENDING-a. Vazi i za phase-2:
            // fizicki neisporuciva poruka ne moze da ispuni §2.9 dostavu ni jednim
            // brojem pokusaja.
            messageService.markOutboundNonRetryable(key, e.getMessage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException |
                 IllegalArgumentException e) {
            // NON-RETRYABLE (facet d): telo se ne moze deserijalizovati u validan
            // envelope (korumpiran/null sadrzaj). Pre fix-a je ovo escape-ovalo u
            // generic catch i ostavljalo red PENDING zauvek.
            messageService.markOutboundNonRetryable(key,
                    "Un-deserializable requestBody: " + e.getMessage());
        }
    }
}
