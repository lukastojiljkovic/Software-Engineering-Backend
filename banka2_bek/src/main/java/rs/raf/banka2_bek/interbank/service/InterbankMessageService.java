package rs.raf.banka2_bek.interbank.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageDirection;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.protocol.IdempotenceKey;
import rs.raf.banka2_bek.interbank.protocol.MessageType;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;
import rs.raf.banka2_bek.monitoring.BusinessMetrics;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class InterbankMessageService {

    /**
     * Faza-1 (NEW_TX) STUCK prag: posle ovoliko transientnih neuspeha NEW_TX
     * (gde koordinator jos nije doneo odluku) eskalira na STUCK (presumed-abort
     * bezbedan). NE vazi za fazu-2 (vidi {@link #markOutboundFailed}).
     */
    private static final int MAX_RETRIES = 5;

    private final InterbankMessageRepository repository;
    private final BankRoutingService bankRoutingService;
    private final BusinessMetrics businessMetrics;

    /**
     * Facet c — dead-letter backstop. Posle ovoliko transientnih neuspeha SVAKA
     * poruka (ukljucujuci COMMIT_TX/ROLLBACK_TX, koje se inace po §2.9 retransmituju
     * neograniceno) prelazi u {@link InterbankMessageStatus#DEAD_LETTER} i loguje se
     * jednom na WARN. Ogranicava beskonacni log-spam kad je partner TRAJNO mrtav, a
     * default je namerno visok (50) da se ne napusti partner koji je samo nakratko pao.
     * Override preko {@code interbank.retry.max-attempts}.
     */
    private final int maxAttempts;

    public InterbankMessageService(InterbankMessageRepository repository,
                                   BankRoutingService bankRoutingService,
                                   BusinessMetrics businessMetrics,
                                   @Value("${interbank.retry.max-attempts:50}") int maxAttempts) {
        this.repository = repository;
        this.bankRoutingService = bankRoutingService;
        this.businessMetrics = businessMetrics;
        this.maxAttempts = maxAttempts;
    }


    public Optional<String> findCachedResponse(IdempotenceKey key) {

        Optional<InterbankMessage> messageOpt = repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                key.routingNumber(),
                key.locallyGeneratedKey()
        );
        return messageOpt.map(InterbankMessage::getResponseBody);
    }

    /**
     * 1377 — type-aware idempotency cache lookup. §2.2 idempotency kljuc je
     * (senderRoutingNumber, locallyGeneratedKey) i posiljalac generise SVEZ kljuc po
     * poruci (vidi {@code generateKey} — random 64-hex), pa svaki messageType ima
     * distinktan kljuc. Ako ipak stigne poruka cija se vrsta NE poklapa sa vec
     * kesiranom vrstom pod istim kljucem (key-collision: npr. COMMIT_TX pod kljucem
     * koji vec ima kesiran NEW_TX vote), vracanje sirovog kesiranog body-ja bi bilo
     * pogresno — COMMIT_TX bi dobio kesiran TransactionVote umesto da commit-uje, i
     * commit se NIKAD ne bi izvrsio (novac zaglavljen u rezervaciji).
     *
     * <p>Vraca kesirani {@link InterbankMessage} (ako postoji) tako da pozivalac
     * (inbound controller) moze da uporedi {@code getMessageType()} sa dolazecom
     * vrstom i da odbije neuskladjenost kao protocol violation, umesto da slepo
     * vrati kesiran odgovor.
     */
    public Optional<InterbankMessage> findCachedMessage(IdempotenceKey key) {
        return repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                key.routingNumber(),
                key.locallyGeneratedKey());
    }

    @Transactional
    public void recordInboundResponse(IdempotenceKey key,
                                       MessageType messageType,
                                       String requestBody,
                                       Integer httpStatus,
                                       String responseBody,
                                      String transactionId) {

        repository.save(
                InterbankMessage.builder()
                        .transactionId(transactionId)
                        .direction(InterbankMessageDirection.INBOUND)
                        .status(InterbankMessageStatus.INBOUND)
                        .senderRoutingNumber(key.routingNumber())
                        .locallyGeneratedKey(key.locallyGeneratedKey())
                        .messageType(messageType)
                        .requestBody(requestBody)
                        .responseBody(responseBody)
                        .httpStatus(httpStatus)
                        .peerRoutingNumber(key.routingNumber())
                        .createdAt(LocalDateTime.now())
                        .lastAttemptAt(LocalDateTime.now())
                        .retryCount(0).build()
        );

    }

    public IdempotenceKey generateKey() {

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for(byte b : bytes) sb.append(String.format("%02x", b));
        return new IdempotenceKey(bankRoutingService.myRoutingNumber(), sb.toString());
    }

    /**
     * §2.11 — Logs an outbound message with status=PENDING so the retry scheduler can pick it up.
     * Must be called inside the same @Transactional as the business operation that triggered the send
     * (e.g. prepareLocal) so that the log entry and the reservation commit or rollback together.
     */
    @Transactional
    public InterbankMessage recordOutbound(IdempotenceKey key,
                                            int targetRouting,
                                            MessageType type,
                                            String body,
                                            String transactionId) {

        return repository.save(
                InterbankMessage.builder()
                    .direction(InterbankMessageDirection.OUTBOUND)
                    .status(InterbankMessageStatus.PENDING)
                    .senderRoutingNumber(key.routingNumber())
                    .locallyGeneratedKey(key.locallyGeneratedKey())
                    .messageType(type)
                    .requestBody(body)
                    .transactionId(transactionId)
                    .peerRoutingNumber(targetRouting)
                    .createdAt(LocalDateTime.now())
                    .lastAttemptAt(LocalDateTime.now())
                    .retryCount(0).build()
        );
    }

    /**
     * Returns true for 4xx status codes that are transient / retry-able.
     * 408 Request Timeout, 425 Too Early, 429 Too Many Requests — these are
     * temporary conditions where a retry may succeed.
     * All other 4xx codes indicate a permanent protocol or content error —
     * retrying will not help and the message should be marked FAILED_PERMANENT.
     */
    private static boolean isTransient4xx(int status) {
        return status == 408 || status == 425 || status == 429;
    }

    /**
     * 1977 — outbound poruka je u stanju koje NE sme da se menja zakasnelim
     * markOutboundSent/markOutboundFailed pozivom:
     * <ul>
     *   <li>{@code SENT} — partner potvrdio (200/204), terminalno.</li>
     *   <li>{@code FAILED_PERMANENT} — permanentni 4xx (NEW_TX), terminalno.</li>
     *   <li>{@code SENT_WAITING_ASYNC} — 202; partner obradjuje async, retry-resolvovano
     *       (ne smemo regresirati na PENDING ni SENT).</li>
     * </ul>
     * {@code PENDING} i {@code STUCK} se i dalje smeju menjati (PENDING je aktivan
     * retry, STUCK eskalacija moze biti dodatno logovana po potrebi).
     */
    private static boolean isTerminalSentState(InterbankMessageStatus status) {
        return status == InterbankMessageStatus.SENT
                || status == InterbankMessageStatus.FAILED_PERMANENT
                || status == InterbankMessageStatus.SENT_WAITING_ASYNC;
    }

    @Transactional
    public void markOutboundSent(IdempotenceKey key, Integer httpStatus, String responseBody) {
        InterbankMessage ibMessage =
                repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                        key.routingNumber(), key.locallyGeneratedKey()
                ).orElseThrow(() ->
                        new InterbankExceptions.InterbankProtocolException(
                                "No outbound message for key " + key + " was found."
                        )
                );

        // 1977 — terminal-state guard. SENT i FAILED_PERMANENT su TERMINALNI;
        // SENT_WAITING_ASYNC (202) je vec resolvovan kao "ne retry-uj". Bez ovog
        // guard-a, zakasneli/duplirani markOutboundSent poziv (npr. iz race-a
        // izmedju sendPhase2Network i retry scheduler-a) bi mogao da pregazi vec-SENT
        // poruku — npr. SENT_WAITING_ASYNC→SENT regres, ili da resetuje response body.
        // Idempotentno: za vec-terminalni red, no-op.
        if (isTerminalSentState(ibMessage.getStatus())) {
            log.debug("markOutboundSent no-op: message key={} already in terminal/resolved status {}",
                    key, ibMessage.getStatus());
            return;
        }

        if (httpStatus.equals(HttpStatus.OK.value()) || httpStatus.equals(HttpStatus.NO_CONTENT.value())) {
            ibMessage.setStatus(InterbankMessageStatus.SENT);
            ibMessage.setHttpStatus(httpStatus);
            ibMessage.setResponseBody(responseBody);
            ibMessage.setLastAttemptAt(LocalDateTime.now());
            repository.save(ibMessage);
            // R7 observability: partner potvrdio prijem (banka2_interbank_outbound_total{status="sent"}).
            businessMetrics.recordInterbankOutboundSent();
        } else if (httpStatus.equals(HttpStatus.ACCEPTED.value())) {
            // BE-INT-02 fix: 202 Accepted znaci da je partner prihvatio poruku i
            // obradjuje je asinhrono. Pre fix-a status je ostajao PENDING, sto je
            // gurnulo poruku u retry ciklus → MAX_RETRY=5 → STUCK (lazno alarm).
            // Sad markiramo SENT_WAITING_ASYNC — partner ce nas obavestiti kasnije
            // sopstvenim COMMIT_TX/ROLLBACK_TX porukom; retry scheduler ovo preskace.
            ibMessage.setStatus(InterbankMessageStatus.SENT_WAITING_ASYNC);
            ibMessage.setHttpStatus(httpStatus);
            ibMessage.setResponseBody(responseBody);
            ibMessage.setLastAttemptAt(LocalDateTime.now());
            repository.save(ibMessage);
        } else if (httpStatus >= 400 && httpStatus < 500 && !isTransient4xx(httpStatus)
                && ibMessage.getMessageType() != MessageType.COMMIT_TX
                && ibMessage.getMessageType() != MessageType.ROLLBACK_TX) {
            // Permanent 4xx: the partner rejected our message due to a protocol/content error.
            // Retrying will not help — mark terminal so the retry scheduler skips it.
            //
            // N2 exception: phase-2 (COMMIT_TX / ROLLBACK_TX) is NEVER abandoned on a
            // permanent 4xx. Per §2.9 the outcome must be retransmitted until the
            // recipient acknowledges with 200/204 — a 4xx here (e.g. partner has not
            // yet recorded the NEW_TX, or a transient validation state) must not strand
            // a recipient that already voted YES (PREPARED). Phase-2 falls through to
            // markOutboundFailed → stays PENDING → keeps retrying.
            ibMessage.setStatus(InterbankMessageStatus.FAILED_PERMANENT);
            ibMessage.setHttpStatus(httpStatus);
            ibMessage.setLastAttemptAt(LocalDateTime.now());
            ibMessage.setLastError("Partner returned permanent " + httpStatus + " — will not retry.");
            log.warn("Interbank outbound message FAILED_PERMANENT for key={}, HTTP {}", key, httpStatus);
            repository.save(ibMessage);
            // R7 observability: trajni partner-reject (banka2_interbank_outbound_total{status="failed"}, OtcInterbankFailures alert).
            businessMetrics.recordInterbankOutboundFailed();
        } else {
            // 5xx or transient 4xx — keep existing retry behaviour
            markOutboundFailed(key, "Outbound message sending failed with HTTP " + httpStatus + ".");
        }

    }

    @Transactional
    public void markOutboundFailed(IdempotenceKey key, String errorMessage) {
        InterbankMessage ibMessage =
                repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                        key.routingNumber(), key.locallyGeneratedKey()
                ).orElseThrow(() ->
                        new InterbankExceptions.InterbankProtocolException(
                                "No outbound message for the key " + key + " was found."
                        )
                );

        // 1977 — terminal-state guard. Poruka koja je vec SENT (partner potvrdio
        // 200/204) ili FAILED_PERMANENT ne sme da regresira na PENDING zbog
        // zakasnelog/dupliranog failed signala (race izmedju glavnog send-a i retry
        // scheduler-a) — to bi je gurnulo u retransmisiju i lazni STUCK alarm
        // (NEW_TX) odnosno duplu dostavu (phase-2). SENT_WAITING_ASYNC (202) je
        // takodje terminalno-resolvovan za retry svrhe. No-op za vec-resolvovan red.
        if (isTerminalSentState(ibMessage.getStatus())) {
            log.debug("markOutboundFailed no-op: message key={} already in terminal/resolved status {}",
                    key, ibMessage.getStatus());
            return;
        }

        // R7 observability: neuspela outbound dostava (komunikacioni/5xx/transient).
        // banka2_interbank_outbound_total{status="failed"} → OtcInterbankFailures alert.
        businessMetrics.recordInterbankOutboundFailed();

        ibMessage.setRetryCount(ibMessage.getRetryCount() + 1);
        ibMessage.setLastError(errorMessage);
        ibMessage.setLastAttemptAt(LocalDateTime.now());

        // N2 (§2.9 "retransmit until acknowledged" + §2.8.7 presumed-abort):
        // Faza-2 poruke (COMMIT_TX / ROLLBACK_TX) MORAJU se retransmitovati
        // NEOGRANICENO dok recipient ne potvrdi (200/204). Posle YES vote-a
        // recipient je u PREPARED i ceka ishod od koordinatora; ako COMMIT_TX/
        // ROLLBACK_TX izadje iz retry pool-a (STUCK), recipient nikad ne sazna
        // ishod → rezervacija zakljucana / novac unisten. Zato STUCK eskalacija
        // vazi SAMO za fazu-1 (NEW_TX) — gde koordinator jos nije doneo odluku i
        // gde je odustajanje (presumed-abort koordinatora) bezbedno.
        boolean isPhaseTwo = ibMessage.getMessageType() == MessageType.COMMIT_TX
                || ibMessage.getMessageType() == MessageType.ROLLBACK_TX;

        if (ibMessage.getRetryCount() >= maxAttempts) {
            // Facet c — dead-letter backstop. VAZI ZA SVE tipove (ukljucujuci fazu-2).
            // §2.9 retransmituje fazu-2 neograniceno, ali kad partner TRAJNO ne
            // odgovara (maxAttempts, default 50 — daleko iznad kratkotrajnog ispada)
            // beskonacni retry samo puni logove ERROR-ima svaka 2 min i zagadjuje
            // payments view. DEAD_LETTER je terminalan (scheduler ga preskace) i
            // zahteva operativnu intervenciju; logujemo JEDNOM na WARN.
            ibMessage.setStatus(InterbankMessageStatus.DEAD_LETTER);
            log.warn("Interbank outbound {} for key={} moved to DEAD_LETTER after {} attempts "
                            + "(maxAttempts={}); requires operator intervention. Last error: {}",
                    ibMessage.getMessageType(), key, ibMessage.getRetryCount(), maxAttempts, errorMessage);
        } else if (!isPhaseTwo && ibMessage.getRetryCount() >= MAX_RETRIES) {
            ibMessage.setStatus(InterbankMessageStatus.STUCK);

            log.error("Interbank outbound message STUCK after {} for key={}, error message: {} ", MAX_RETRIES, key, errorMessage);

        } else if (isPhaseTwo && ibMessage.getRetryCount() >= MAX_RETRIES) {
            // Phase-2 ostaje PENDING (retry-uje se i dalje) sve do maxAttempts; logujemo
            // da operativci znaju da partner dugo ne potvrdjuje, ali NE napustamo
            // retransmisiju pre dead-letter backstop-a (§2.9).
            log.warn("Interbank phase-2 {} for key={} still unacknowledged after {} attempts — "
                            + "continuing retransmission per §2.9 (NOT escalating to STUCK; dead-letter at {}).",
                    ibMessage.getMessageType(), key, ibMessage.getRetryCount(), maxAttempts);
        }

        repository.save(ibMessage);

    }

    /**
     * Facet a — terminalizuje outbound NEW_TX red ciju je transakciju koordinator
     * definitivno ABORTOVAO u fazi-1 (partner glasao NO / partner 4xx / mrezna greska
     * tretirana kao NO → {@code rollbackLocal}).
     *
     * <p>Pre fix-a, abort putanja u {@code sendPhase1Network} je zvala
     * {@link #markOutboundFailed} koja za NEW_TX inkrementira {@code retryCount} i
     * ostavlja red PENDING (do MAX_RETRIES → STUCK). To znaci da je
     * {@code InterbankRetryScheduler} re-slao NEW_TX za transakciju koja vise NE
     * postoji (lokalno rollback-ovana) — beskonacni/visekratni re-send i ERROR spam.
     * Re-send NEW_TX-a za abortovanu transakciju je i logicki pogresan.
     *
     * <p>Resenje: red se prebacuje u {@link InterbankMessageStatus#FAILED_PERMANENT}
     * (terminalno; scheduler ga ne pokupi). Idempotentno: no-op ako je red vec
     * terminalan/resolvovan (race sa glavnim send-om).
     *
     * <p>VAZNO (durability): poziva se SAMO za fazu-1 (NEW_TX), gde koordinator jos
     * nije commit-ovao i presumed-abort je bezbedan. Phase-2 (COMMIT/ROLLBACK) se
     * NIKAD ne terminalizuje ovim putem.
     */
    @Transactional
    public void markOutboundAborted(IdempotenceKey key, String reason) {
        InterbankMessage ibMessage =
                repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                        key.routingNumber(), key.locallyGeneratedKey()
                ).orElseThrow(() ->
                        new InterbankExceptions.InterbankProtocolException(
                                "No outbound message for the key " + key + " was found."
                        )
                );

        if (isTerminalState(ibMessage.getStatus())) {
            log.debug("markOutboundAborted no-op: message key={} already in terminal status {}",
                    key, ibMessage.getStatus());
            return;
        }

        ibMessage.setStatus(InterbankMessageStatus.FAILED_PERMANENT);
        ibMessage.setLastError("Phase-1 NEW_TX aborted (terminal): " + reason);
        ibMessage.setLastAttemptAt(LocalDateTime.now());
        repository.save(ibMessage);
        businessMetrics.recordInterbankOutboundFailed();
        log.warn("Interbank outbound NEW_TX key={} terminalized (aborted) — not retried. Reason: {}",
                key, reason);
    }

    /**
     * Facets b + d — terminalizuje outbound red koji NIKAD ne moze da uspe, bez obzira
     * na tip poruke (ukljucujuci COMMIT_TX/ROLLBACK_TX):
     * <ul>
     *   <li>nerazresivo target routing (npr. "Target routing number 666 could not be
     *       resolved" — {@code InterbankProtocolException} iz {@code InterbankClient}),</li>
     *   <li>{@code requestBody} koje je null/prazno ili se ne moze deserijalizovati u
     *       validan {@code Message<...>} envelope.</li>
     * </ul>
     *
     * <p>Pre fix-a, ovakve greske su escape-ovale inner {@code try/catch} u
     * {@code InterbankRetryScheduler.retryOne} (koji je hvatao samo Communication/Auth)
     * pa su padale u generic {@code catch} u {@code retryStaleMessages} koji SAMO loguje
     * → {@code markOutboundFailed} se NIKAD ne pozove → {@code retryCount} se ne
     * inkrementira → poruka ostaje PENDING <b>zauvek</b> (retry svaka 2 min).
     *
     * <p>Posto su ovi uslovi trajni (ni jedan retry ih ne moze popraviti), red se odmah
     * prebacuje u {@link InterbankMessageStatus#FAILED_PERMANENT}. Ovo je izuzetak od
     * §2.9 "phase-2 retransmituj zauvek" pravila: poruka koja je fizicki neisporuciva
     * (nerazresiva ruta / neispravno telo) ne moze biti dostavljena ni jednim brojem
     * pokusaja, pa je zadrzavanje u retry pool-u cista buka. Idempotentno: no-op ako je
     * red vec terminalan.
     */
    @Transactional
    public void markOutboundNonRetryable(IdempotenceKey key, String reason) {
        InterbankMessage ibMessage =
                repository.findBySenderRoutingNumberAndLocallyGeneratedKey(
                        key.routingNumber(), key.locallyGeneratedKey()
                ).orElseThrow(() ->
                        new InterbankExceptions.InterbankProtocolException(
                                "No outbound message for the key " + key + " was found."
                        )
                );

        if (isTerminalState(ibMessage.getStatus())) {
            log.debug("markOutboundNonRetryable no-op: message key={} already in terminal status {}",
                    key, ibMessage.getStatus());
            return;
        }

        ibMessage.setStatus(InterbankMessageStatus.FAILED_PERMANENT);
        ibMessage.setLastError("Non-retryable (terminal): " + reason);
        ibMessage.setLastAttemptAt(LocalDateTime.now());
        repository.save(ibMessage);
        businessMetrics.recordInterbankOutboundFailed();
        log.warn("Interbank outbound {} key={} terminalized (non-retryable) — not retried. Reason: {}",
                ibMessage.getMessageType(), key, reason);
    }

    /**
     * Svi stvarno-terminalni statusi (poruka je razresena i scheduler je vise ne
     * pokupi). Koristi se kao no-op guard u {@link #markOutboundAborted} /
     * {@link #markOutboundNonRetryable} da zakasneli/dupli signal ne pregazi vec
     * razresen red. Sira od {@link #isTerminalSentState} — ukljucuje i STUCK/DEAD_LETTER.
     */
    private static boolean isTerminalState(InterbankMessageStatus status) {
        return status == InterbankMessageStatus.SENT
                || status == InterbankMessageStatus.SENT_WAITING_ASYNC
                || status == InterbankMessageStatus.FAILED_PERMANENT
                || status == InterbankMessageStatus.STUCK
                || status == InterbankMessageStatus.DEAD_LETTER;
    }

}
