package rs.raf.banka2_bek.interbank.wrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.interbank.client.TradingServiceInternalClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiationStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcContractRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcNegotiationRepository;
import rs.raf.banka2_bek.interbank.service.InterbankReservationApplier;
import rs.raf.banka2_bek.interbank.service.OtcNegotiationService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R6 1976 + R1 209 — state-machine guard i access gate na inter-bank OTC wrapper
 * mutirajucim akcijama (accept/decline).
 *
 * <ul>
 *   <li><b>1976:</b> accept/decline flip-uju status SAMO iz ACTIVE. Ilegalan prelaz
 *       (ACCEPTED→DECLINED, vec-DECLINED→ACCEPTED) → 409, i kriticno: drugi accept
 *       NE pokrece outbound 2PC accept (sprecava dupli premium debit).</li>
 *   <li><b>209:</b> agent (EMPLOYEE bez SUPERVISOR) i klijent bez canTradeStocks
 *       dobijaju 403 na accept/decline.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterbankOtcWrapperService — state-machine guard (1976) + access gate (209)")
class InterbankOtcWrapperServiceStateGuardTest {

    private static final int OUR_RN = 222;
    private static final int SELLER_RN = 111;
    private static final String OFFER_ID = SELLER_RN + ":neg-1";

    @Mock private OtcNegotiationService negotiationService;
    @Mock private InterbankOtcNegotiationRepository negotiationRepository;
    @Mock private InterbankOtcContractRepository contractRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private TradingServiceInternalClient tradingServiceClient;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionExecutorService transactionExecutor;
    @Mock private rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository interbankTransactionRepository;
    @Mock private rs.raf.banka2_bek.payment.repository.PaymentRepository paymentRepository;
    @Mock private InterbankReservationApplier reservationApplier;

    private InterbankProperties properties;
    private InterbankOtcWrapperService service;

    @BeforeEach
    void setUp() {
        properties = new InterbankProperties();
        properties.setMyRoutingNumber(OUR_RN);
        service = new InterbankOtcWrapperService(
                negotiationService, properties,
                negotiationRepository, contractRepository,
                clientRepository, employeeRepository, tradingServiceClient,
                accountRepository, transactionExecutor,
                interbankTransactionRepository, paymentRepository,
                reservationApplier);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);

        // Default: klijent 7L sme da trguje.
        Client c = new Client();
        c.setId(7L);
        c.setCanTradeStocks(true);
        lenient().when(clientRepository.findById(7L)).thenReturn(Optional.of(c));
    }

    // ── 1976 state-machine guard ──

    @Test
    @DisplayName("1976: acceptOffer na vec-ACCEPTED pregovor → 409, NE pokrece outbound 2PC accept")
    void acceptOffer_alreadyAccepted_conflict_noDoubleAccept() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACCEPTED);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));

        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankNegotiationConflictException.class)
                .hasMessageContaining("nije ACTIVE");

        // Kriticno: dupli premium debit sprecen — outbound accept NIJE pozvan.
        verify(negotiationService, never()).acceptOffer(any());
    }

    @Test
    @DisplayName("T2/F1: acceptOffer (BUYER) — vlasnistvo + ACTIVE + valuta OK → cuva buyerSettlementAccountNumber pre outbound accept-a")
    void acceptOffer_storesBuyerSettlementAccountNumber() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Buyer je na FE-u izabrao SVOJ ACTIVE USD racun id=101 → cuvamo njegov broj racuna.
        // Balans pokriva premium (100) + strike (2000) — T4 funds-guard prolazi.
        Account usdAccount = buildOwnedAccount(OUR_RN + "700002", 7L, AccountStatus.ACTIVE, "USD");
        usdAccount.setAvailableBalance(new java.math.BigDecimal("5000"));
        when(accountRepository.findById(101L)).thenReturn(Optional.of(usdAccount));
        // T3: posle accept-a 2PC commit kreira buyer ugovor — strike rezervacija ga nalazi.
        when(contractRepository.findBySourceNegotiationId(1L)).thenReturn(Optional.of(buildBuyerContractForNeg(neg)));
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acceptOffer(OFFER_ID, 101L, 7L, "CLIENT");

        // Pre outbound accept-a, pregovor mora nositi izabrani settlement racun.
        assertThat(neg.getBuyerSettlementAccountNumber()).isEqualTo(OUR_RN + "700002");
        verify(negotiationService).acceptOffer(any());
    }

    @Test
    @DisplayName("F1 IDOR: acceptOffer sa racunom DRUGOG klijenta → 409, nista se ne cuva, NEMA outbound accept")
    void acceptOffer_accountOwnedByAnotherClient_rejected() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));

        // Tudji racun (vlasnik 999) — kupac (7) NE sme da naplati premiju s njega.
        when(accountRepository.findById(202L)).thenReturn(Optional.of(buildOwnedAccount(
                OUR_RN + "700099", 999L, AccountStatus.ACTIVE, "USD")));

        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, 202L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("ne pripada");

        // Nista se ne cuva, dupli/tudji premium debit sprecen — outbound accept NIJE pozvan.
        assertThat(neg.getBuyerSettlementAccountNumber()).isNull();
        verify(negotiationRepository, never()).save(any());
        verify(negotiationService, never()).acceptOffer(any());
    }

    @Test
    @DisplayName("F1: acceptOffer sa racunom u POGRESNOJ valuti (RSD≠premija USD) → 409, nista se ne cuva")
    void acceptOffer_nonPremiumCurrencyAccount_rejected() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));

        // Sopstveni ACTIVE racun, ali RSD — premija je u USD (vidi buildBuyerSideNegotiation).
        when(accountRepository.findById(303L)).thenReturn(Optional.of(buildOwnedAccount(
                OUR_RN + "700003", 7L, AccountStatus.ACTIVE, "RSD")));

        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, 303L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("valuti");

        assertThat(neg.getBuyerSettlementAccountNumber()).isNull();
        verify(negotiationRepository, never()).save(any());
        verify(negotiationService, never()).acceptOffer(any());
    }

    // ── T3 strike rezervacija @accept + T4 accept-funds-guard ──

    @Test
    @DisplayName("T3: acceptOffer (same-ccy) — posle 2PC commit-a rezervise strike (pi*k) na racunu kupca i upisuje ga na ugovor")
    void acceptOffer_reservesStrikeOnContract() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Premium (100 USD) i strike (200*10 = 2000 USD) su u istoj valuti (USD).
        // Racun id=101 ima dovoljno za premium + strike (2100 USD).
        Account usdAccount = buildOwnedAccount(OUR_RN + "700002", 7L, AccountStatus.ACTIVE, "USD");
        usdAccount.setAvailableBalance(new java.math.BigDecimal("5000"));
        when(accountRepository.findById(101L)).thenReturn(Optional.of(usdAccount));

        // Posle uspesnog outbound accept-a (2PC commit) buyer ugovor postoji.
        InterbankOtcContract buyerContract = buildBuyerContractForNeg(neg);
        when(contractRepository.findBySourceNegotiationId(1L)).thenReturn(Optional.of(buyerContract));
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acceptOffer(OFFER_ID, 101L, 7L, "CLIENT");

        // Strike = pricePerUnit (200) * amount (10) = 2000 USD, rezervisan na settlement racunu.
        // BUG-1: rezervacija sad ide kroz self.reserveStrikeAfterAccept (REQUIRES_NEW), ali posto
        // je u unit testu self==service, real metod se izvrsi inline → reserveMonas/save i dalje pozvani.
        verify(reservationApplier).reserveMonas(eq(OUR_RN + "700002"), eq(new java.math.BigDecimal("2000")));
        assertThat(buyerContract.getReservedStrikeAccountNumber()).isEqualTo(OUR_RN + "700002");
        assertThat(buyerContract.getReservedStrikeAmount()).isEqualByComparingTo("2000");
    }

    // ── BUG-1 [MONEY CONSERVATION] strike rezervacija u zasebnom REQUIRES_NEW ──
    // Lost-update: acceptOffer @Transactional ucita kupcev racun (stari balans), a
    // konkurentan inbound COMMIT_TX thread commit-uje premium debit. Inline reserveMonas
    // (join istog tx/persistence-context-a) je vracao KESIRANI stari entitet i pregazio
    // premium debit → novac se stvarao. Fix: strike reserve u self.reserveStrikeAfterAccept
    // (REQUIRES_NEW → svez context → reserveMonas-ov findForUpdate cita svez balans).

    @Test
    @DisplayName("BUG-1: acceptOffer delegira strike rezervaciju na reserveStrikeAfterAccept (REQUIRES_NEW), ne inline")
    void acceptOffer_delegatesStrikeReserveToRequiresNew() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account usdAccount = buildOwnedAccount(OUR_RN + "700002", 7L, AccountStatus.ACTIVE, "USD");
        usdAccount.setAvailableBalance(new java.math.BigDecimal("5000"));
        when(accountRepository.findById(101L)).thenReturn(Optional.of(usdAccount));

        // self je spy → mozemo verifikovati delegaciju, a real metod se i dalje izvrsi.
        InterbankOtcWrapperService spySelf = org.mockito.Mockito.spy(service);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", spySelf);

        InterbankOtcContract buyerContract = buildBuyerContractForNeg(neg);
        when(contractRepository.findBySourceNegotiationId(1L)).thenReturn(Optional.of(buyerContract));
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acceptOffer(OFFER_ID, 101L, 7L, "CLIENT");

        // Strike reserve MORA ici kroz REQUIRES_NEW metod (sa svezim persistence context-om),
        // a NE kroz acceptOffer-ovu sopstvenu (stale) tx. Argumenti: sourceNeg=1, racun, strike=2000.
        verify(spySelf).reserveStrikeAfterAccept(
                eq(1L), eq(OUR_RN + "700002"), eq(new java.math.BigDecimal("2000")));
    }

    @Test
    @DisplayName("BUG-1: reserveStrikeAfterAccept rezervise strike i upisuje (racun+iznos) na ugovor")
    void reserveStrikeAfterAccept_reservesAndPersists() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        InterbankOtcContract buyerContract = buildBuyerContractForNeg(neg);
        when(contractRepository.findBySourceNegotiationId(1L)).thenReturn(Optional.of(buyerContract));
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reserveStrikeAfterAccept(1L, OUR_RN + "700002", new java.math.BigDecimal("2000"));

        verify(reservationApplier).reserveMonas(eq(OUR_RN + "700002"), eq(new java.math.BigDecimal("2000")));
        assertThat(buyerContract.getReservedStrikeAccountNumber()).isEqualTo(OUR_RN + "700002");
        assertThat(buyerContract.getReservedStrikeAmount()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("BUG-1: reserveStrikeAfterAccept — ako upis na ugovor padne, oslobadja monas hold (bez visece rezervacije)")
    void reserveStrikeAfterAccept_compensatesOnSaveFailure() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        InterbankOtcContract buyerContract = buildBuyerContractForNeg(neg);
        when(contractRepository.findBySourceNegotiationId(1L)).thenReturn(Optional.of(buyerContract));
        when(contractRepository.save(any())).thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() ->
                service.reserveStrikeAfterAccept(1L, OUR_RN + "700002", new java.math.BigDecimal("2000")))
                .isInstanceOf(RuntimeException.class);

        // Kompenzacija: rezervacija oslobodjena (nije ostala viseca).
        verify(reservationApplier).reserveMonas(eq(OUR_RN + "700002"), eq(new java.math.BigDecimal("2000")));
        verify(reservationApplier).releaseMonas(eq(OUR_RN + "700002"), eq(new java.math.BigDecimal("2000")));
    }

    @Test
    @DisplayName("T4: acceptOffer (same-ccy) — nedovoljno za premium+strike → 409, NEMA outbound accept, NEMA rezervacije")
    void acceptOffer_insufficientFundsForPremiumPlusStrike_rejected() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));
        lenient().when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Potrebno premium (100) + strike (2000) = 2100 USD; racun ima samo 2050.
        Account usdAccount = buildOwnedAccount(OUR_RN + "700002", 7L, AccountStatus.ACTIVE, "USD");
        usdAccount.setAvailableBalance(new java.math.BigDecimal("2050"));
        when(accountRepository.findById(101L)).thenReturn(Optional.of(usdAccount));

        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("Nedovoljno sredstava");

        // Nista se ne salje napolje (premium debit kod partnera sprecen) i nista se ne rezervise.
        verify(negotiationService, never()).acceptOffer(any());
        verify(reservationApplier, never()).reserveMonas(any(), any());
    }

    @Test
    @DisplayName("T3: acceptOffer (cross-ccy, strike EUR≠premium USD) — kupac nema racun u strike valuti → 409, NEMA outbound accept")
    void acceptOffer_noAccountInStrikeCurrency_rejected() {
        // Strike (price) u EUR, premium u USD — kupac ima samo USD racun, nema EUR.
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        neg.setPriceCurrency("EUR");
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));
        lenient().when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Settlement racun (premium, USD) je validan; ali nema racuna u EUR (strike valuti).
        Account usdAccount = buildOwnedAccount(OUR_RN + "700002", 7L, AccountStatus.ACTIVE, "USD");
        usdAccount.setAvailableBalance(new java.math.BigDecimal("5000"));
        when(accountRepository.findById(101L)).thenReturn(Optional.of(usdAccount));
        // Vlasnikova lista ACTIVE racuna ima samo USD — nema EUR racuna za strike.
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(7L, AccountStatus.ACTIVE))
                .thenReturn(java.util.List.of(usdAccount));

        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("EUR");

        verify(negotiationService, never()).acceptOffer(any());
        verify(reservationApplier, never()).reserveMonas(any(), any());
    }

    // ── P0 [MONEY CREATED] settlement racun OBAVEZAN pre outbound 2PC za sve role ──
    // Live failure: EMPLOYEE/ADMIN kupac (ili kupac bez prosledjenog buyerAccountId)
    // ostane bez settlementAccount-a → ceo funds-guard se preskoci → outbound §3.6
    // accept se svejedno posalje → partner kreditira prodavca +premium, a inbound
    // premium-debit kod nas resolve-uje kupca preko findByClientId (prazno za zaposlenog)
    // → premium debit tiho no-op → NOVAC SE STVARA. Fix: reject PRE outbound-a.

    @Test
    @DisplayName("P0: EMPLOYEE kupac bez settlement racuna (buyerAccountId=null) → 400, NEMA outbound accept, NEMA pomeranja novca")
    void acceptOffer_employeeBuyerNoSettlementAccount_rejectedBeforeOutbound() {
        // Pregovor gde smo MI buyer, ali lokalna strana je zaposleni (EMPLOYEE).
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        neg.setLocalPartyRole("EMPLOYEE");
        neg.setLocalPartyId(50L);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));

        // 209 access gate: supervizor (EMPLOYEE sa SUPERVISOR) sme da prihvati OTC.
        Employee supervisor = new Employee();
        supervisor.setId(50L);
        supervisor.setPermissions(new HashSet<>(Set.of("SUPERVISOR")));
        when(employeeRepository.findById(50L)).thenReturn(Optional.of(supervisor));

        // Zaposleni nema klijentske racune → deterministicka resolucija vraca prazno.
        lenient().when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(50L, AccountStatus.ACTIVE))
                .thenReturn(java.util.List.of());

        // buyerAccountId = null (nije prosledjen) → settlementAccount ostaje null →
        // mandatory gate mora odbiti PRE outbound-a.
        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, null, 50L, "EMPLOYEE"))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class)
                .hasMessageContaining("nedostaje nalog kupca");

        // KRITICNO (money conservation): nista nije islo napolje, nista nije rezervisano,
        // nikakav settlement racun nije zapamcen → premium debit kod partnera nikad pokrenut.
        verify(negotiationService, never()).acceptOffer(any());
        verify(reservationApplier, never()).reserveMonas(any(), any());
        assertThat(neg.getBuyerSettlementAccountNumber()).isNull();
    }

    @Test
    @DisplayName("P0: CLIENT kupac bez prosledjenog buyerAccountId i bez racuna u valuti premije → 400, NEMA outbound accept")
    void acceptOffer_clientBuyerNoAccountInPremiumCcy_rejectedBeforeOutbound() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));

        // Klijent 7 (canTradeStocks=true iz setUp) nema USD (premium) racun — samo prazno.
        lenient().when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(7L, AccountStatus.ACTIVE))
                .thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, null, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class)
                .hasMessageContaining("nedostaje nalog kupca");

        verify(negotiationService, never()).acceptOffer(any());
        verify(reservationApplier, never()).reserveMonas(any(), any());
        assertThat(neg.getBuyerSettlementAccountNumber()).isNull();
    }

    @Test
    @DisplayName("P0: CLIENT kupac bez buyerAccountId ali SA deterministicki resolvabilnim USD racunom → settlement zapamcen, outbound prolazi")
    void acceptOffer_clientBuyerNoAccountId_resolvesDeterministically_proceeds() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Klijent 7 ima ACTIVE USD racun sa dovoljno za premium (100) + strike (2000).
        Account usdAccount = buildOwnedAccount(OUR_RN + "700002", 7L, AccountStatus.ACTIVE, "USD");
        usdAccount.setAvailableBalance(new java.math.BigDecimal("5000"));
        when(accountRepository.findByClientIdAndStatusOrderByAvailableBalanceDesc(7L, AccountStatus.ACTIVE))
                .thenReturn(java.util.List.of(usdAccount));
        when(contractRepository.findBySourceNegotiationId(1L)).thenReturn(Optional.of(buildBuyerContractForNeg(neg)));
        when(contractRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acceptOffer(OFFER_ID, null, 7L, "CLIENT");

        // Deterministicki resolvovan racun je zapamcen na pregovoru (inbound premium-debit
        // ga koristi → nema no-op-a), i outbound accept je pokrenut.
        assertThat(neg.getBuyerSettlementAccountNumber()).isEqualTo(OUR_RN + "700002");
        verify(negotiationService).acceptOffer(any());
    }

    @Test
    @DisplayName("1976: declineOffer na vec-DECLINED pregovor → 409 (ilegalan prelaz)")
    void declineOffer_alreadyDeclined_conflict() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.DECLINED);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));

        assertThatThrownBy(() -> service.declineOffer(OFFER_ID, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankNegotiationConflictException.class)
                .hasMessageContaining("nije ACTIVE");

        verify(negotiationService, never()).closeNegotiation(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("1976: declineOffer na ACTIVE pregovor prolazi (status→DECLINED)")
    void declineOffer_active_succeeds() {
        InterbankOtcNegotiation neg = buildBuyerSideNegotiation(InterbankOtcNegotiationStatus.ACTIVE);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(SELLER_RN), eq("neg-1"))).thenReturn(Optional.of(neg));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tradingServiceClient.findListingByTicker(any())).thenReturn(Optional.empty());

        service.declineOffer(OFFER_ID, 7L, "CLIENT");

        verify(negotiationRepository).save(any());
        // outbound DELETE pokusan (best-effort).
        verify(negotiationService).closeNegotiation(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ── 209 access gate ──

    @Test
    @DisplayName("209: agent (EMPLOYEE bez SUPERVISOR) → 403 na acceptOffer, ne dira pregovor")
    void acceptOffer_agent_forbidden() {
        Employee agent = new Employee();
        agent.setId(50L);
        agent.setPermissions(new HashSet<>(Set.of("AGENT")));
        when(employeeRepository.findById(50L)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.acceptOffer(OFFER_ID, 101L, 50L, "EMPLOYEE"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(negotiationRepository, never())
                .findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(any(), any());
        verify(negotiationService, never()).acceptOffer(any());
    }

    @Test
    @DisplayName("209: klijent bez canTradeStocks → 403 na declineOffer")
    void declineOffer_clientNoTrade_forbidden() {
        Client noTrade = new Client();
        noTrade.setId(8L);
        noTrade.setCanTradeStocks(false);
        when(clientRepository.findById(8L)).thenReturn(Optional.of(noTrade));

        assertThatThrownBy(() -> service.declineOffer(OFFER_ID, 8L, "CLIENT"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(negotiationService, never()).closeNegotiation(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    // ── helpers ──

    private InterbankOtcNegotiation buildBuyerSideNegotiation(InterbankOtcNegotiationStatus status) {
        InterbankOtcNegotiation n = new InterbankOtcNegotiation();
        n.setId(1L);
        n.setForeignNegotiationRoutingNumber(SELLER_RN);
        n.setForeignNegotiationIdString("neg-1");
        n.setLocalPartyType(InterbankPartyType.BUYER);
        n.setLocalPartyId(7L);
        n.setLocalPartyRole("CLIENT");
        n.setForeignPartyRoutingNumber(SELLER_RN);
        n.setForeignPartyIdString("C-seller-1");
        n.setTicker("AAPL");
        n.setAmount(new java.math.BigDecimal("10"));
        n.setPricePerUnit(new java.math.BigDecimal("200"));
        n.setPriceCurrency("USD");
        n.setPremium(new java.math.BigDecimal("100"));
        n.setPremiumCurrency("USD");
        n.setSettlementDate(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(30));
        n.setLastModifiedByRoutingNumber(SELLER_RN);
        n.setLastModifiedByIdString("C-seller-1");
        n.setOngoing(status == InterbankOtcNegotiationStatus.ACTIVE);
        n.setStatus(status);
        return n;
    }

    /** Account sa vlasnikom (Client), statusom i valutom — za F1 settlement-account guard. */
    private Account buildOwnedAccount(String accountNumber, Long ownerId, AccountStatus status, String ccyCode) {
        Account a = new Account();
        a.setAccountNumber(accountNumber);
        a.setStatus(status);
        Client owner = new Client();
        owner.setId(ownerId);
        a.setClient(owner);
        Currency ccy = new Currency();
        ccy.setCode(ccyCode);
        a.setCurrency(ccy);
        return a;
    }

    /**
     * Buyer-strana ugovor koji 2PC commit (TransactionExecutorService.commitLocal)
     * kreira posle uspesnog accept-a — koristi se za T3 strike-rezervaciju.
     * Strike = pricePerUnit/priceCurrency pregovora (kao u commitLocal mapiranju).
     */
    private InterbankOtcContract buildBuyerContractForNeg(InterbankOtcNegotiation neg) {
        InterbankOtcContract c = new InterbankOtcContract();
        c.setId(900L);
        c.setSourceNegotiationId(neg.getId());
        c.setLocalPartyType(InterbankPartyType.BUYER);
        c.setLocalPartyId(neg.getLocalPartyId());
        c.setLocalPartyRole(neg.getLocalPartyRole());
        c.setForeignPartyRoutingNumber(neg.getForeignPartyRoutingNumber());
        c.setForeignPartyIdString(neg.getForeignPartyIdString());
        c.setTicker(neg.getTicker());
        c.setQuantity(neg.getAmount());
        c.setStrikePrice(neg.getPricePerUnit());
        c.setStrikeCurrency(neg.getPriceCurrency());
        c.setPremium(neg.getPremium());
        c.setPremiumCurrency(neg.getPremiumCurrency());
        c.setSettlementDate(neg.getSettlementDate());
        c.setStatus(InterbankOtcContractStatus.ACTIVE);
        return c;
    }
}
