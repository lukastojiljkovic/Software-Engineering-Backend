package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.interbank.client.TradingServiceInternalClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiationStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;
import rs.raf.banka2_bek.interbank.protocol.Asset;
import rs.raf.banka2_bek.interbank.protocol.ForeignBankId;
import rs.raf.banka2_bek.interbank.protocol.Posting;
import rs.raf.banka2_bek.interbank.protocol.Transaction;
import rs.raf.banka2_bek.interbank.protocol.TxAccount;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcContractRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcNegotiationRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Inbound tests za {@link OtcNegotiationService} — fokus na §3.6
 * {@code acceptReceivedNegotiation} posle C-1/C-3/I-3 fix-eva iz Celine 5
 * audit-a (vidi commit poruku za detalje).
 *
 * <p>Pokriva:
 * <ul>
 *   <li>Happy path: 4-posting tx sa PERSON↔PERSON option contract postings (C-1)</li>
 *   <li>Stock reservation pri accept-u (C-3)</li>
 *   <li>I-3: 2PC se ne izvrsava unutar @Transactional bloka — kratke faze</li>
 *   <li>Compensacija pri 2PC fail-u (negotiacija vracena u ACTIVE, contract izbrisan)</li>
 *   <li>Settlement-past pregovor odbacen sa 409 (NegotiationConflictException)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OtcNegotiationService — inbound §3.6 acceptReceivedNegotiation (C-1/C-3/I-3 fix)")
class OtcNegotiationServiceInboundTest {

    private static final int OUR_RN = 222;
    private static final int BUYER_RN = 111;

    @Mock private InterbankClient client;
    @Mock private InterbankOtcNegotiationRepository negotiationRepository;
    @Mock private InterbankOtcContractRepository contractRepository;
    @Mock private TradingServiceInternalClient tradingServiceClient;
    @Mock private ClientRepository clientRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private TransactionExecutorService transactionExecutor;
    @Mock private InterbankReservationApplier reservationApplier;

    /** Self-proxy: I-3 split koristi self.persistAcceptArtifacts(...) i self.compensateAccept(...).
     * Mockujemo ga da bismo izolovali metodno ponasanje (i da @Transactional ne bi pucao bez konteksta). */
    @Mock private OtcNegotiationService selfMock;

    private InterbankProperties properties;
    private OtcNegotiationService service;

    @BeforeEach
    void setUp() {
        properties = new InterbankProperties();
        properties.setMyRoutingNumber(OUR_RN);
        properties.setMyBankDisplayName("Banka 2");
        service = new OtcNegotiationService(client, properties,
                negotiationRepository, contractRepository, tradingServiceClient,
                clientRepository, employeeRepository, transactionExecutor, reservationApplier);
        ReflectionTestUtils.setField(service, "self", selfMock);
    }

    @Test
    @DisplayName("§3.6 happy path: 4 postinga PERSON↔PERSON (C-1), stock reservation pozvana (C-3)")
    void acceptReceivedNegotiation_happyPath_formsFourPostingsAndReservesStock() {
        ForeignBankId negotiationId = new ForeignBankId(OUR_RN, "neg-1");
        InterbankOtcNegotiation entity = buildAcceptableNegotiation();

        // Simuliraj persistAcceptArtifacts vracanje validnog AcceptPrep-a sa
        // mockovanim tx-om (delegacija u self).
        Transaction expectedTx = buildExpectedAcceptTx(negotiationId);
        OtcNegotiationService.AcceptPrep prep = new OtcNegotiationService.AcceptPrep(
                42L, 99L, expectedTx, 7L, "CLIENT", "AAPL", 50);
        when(selfMock.persistAcceptArtifacts(negotiationId)).thenReturn(prep);

        // 2PC izvrsenje uspeva (no exception).
        doNothing().when(transactionExecutor).execute(any(Transaction.class));

        service.acceptReceivedNegotiation(negotiationId);

        // C-3: stock reservation pozvana sa deterministickim kljucem.
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(reservationApplier).reserveStock(
                keyCaptor.capture(), eq(7L), eq("CLIENT"), eq("AAPL"), eq(50));
        assertThat(keyCaptor.getValue())
                .startsWith("otc-accept-")
                .contains(String.valueOf(OUR_RN))
                .contains("neg-1")
                .contains("stock-reserve");

        // I-3: 2PC izvrsen.
        verify(transactionExecutor).execute(expectedTx);

        // Bez kompenzacije (uspesan flow).
        verify(selfMock, never()).compensateAccept(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("§3.6: 2PC fail (NO glas) -> compensateAccept pozvan")
    void acceptReceivedNegotiation_2pcFails_compensates() {
        ForeignBankId negotiationId = new ForeignBankId(OUR_RN, "neg-fail");
        Transaction tx = buildExpectedAcceptTx(negotiationId);
        OtcNegotiationService.AcceptPrep prep = new OtcNegotiationService.AcceptPrep(
                42L, 99L, tx, 7L, "CLIENT", "AAPL", 50);
        when(selfMock.persistAcceptArtifacts(negotiationId)).thenReturn(prep);

        // Stock reservation uspeva, 2PC pukne.
        doNothing().when(reservationApplier).reserveStock(anyString(), any(), anyString(), anyString(), anyInt());
        doThrow(new RuntimeException("partner vote=NO")).when(transactionExecutor).execute(any(Transaction.class));

        assertThatThrownBy(() -> service.acceptReceivedNegotiation(negotiationId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("partner vote=NO");

        // Compensacija pozvana sa pravim entitetskim id-evima.
        verify(selfMock).compensateAccept(42L, 99L, 7L, "CLIENT", "AAPL", 50);
    }

    @Test
    @DisplayName("§3.6: reservation fail -> compensateAccept pozvan i protokol exception izbacen")
    void acceptReceivedNegotiation_reservationFails_compensatesAndThrowsProtocol() {
        ForeignBankId negotiationId = new ForeignBankId(OUR_RN, "neg-resfail");
        Transaction tx = buildExpectedAcceptTx(negotiationId);
        OtcNegotiationService.AcceptPrep prep = new OtcNegotiationService.AcceptPrep(
                42L, 99L, tx, 7L, "CLIENT", "AAPL", 50);
        when(selfMock.persistAcceptArtifacts(negotiationId)).thenReturn(prep);

        doThrow(new RuntimeException("insufficient stock"))
                .when(reservationApplier).reserveStock(anyString(), any(), anyString(), anyString(), anyInt());

        assertThatThrownBy(() -> service.acceptReceivedNegotiation(negotiationId))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class)
                .hasMessageContaining("Rezervacija hartija");

        verify(selfMock).compensateAccept(42L, 99L, 7L, "CLIENT", "AAPL", 50);
        verify(transactionExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("§3.6 persistAcceptArtifacts: settlement-past pregovor odbacen sa 409 NegotiationConflict")
    void persistAcceptArtifacts_settlementPast_throwsNegotiationConflict() {
        // Direktan poziv real-ne persistAcceptArtifacts (bez selfMock) za precizniju proveru
        // preconditions logike (C-1, M-2 UTC compare).
        ReflectionTestUtils.setField(service, "self", service); // koristimo realan service za ovaj test
        ForeignBankId negotiationId = new ForeignBankId(OUR_RN, "neg-past");
        InterbankOtcNegotiation entity = buildAcceptableNegotiation();
        entity.setSettlementDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(OUR_RN), eq("neg-past"))).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.persistAcceptArtifacts(negotiationId))
                .isInstanceOf(InterbankExceptions.InterbankNegotiationConflictException.class)
                .hasMessageContaining("settlementDate");
    }

    @Test
    @DisplayName("§3.6 persistAcceptArtifacts: pregovor nije ACTIVE -> ProtocolException")
    void persistAcceptArtifacts_notActive_throwsProtocol() {
        ReflectionTestUtils.setField(service, "self", service);
        ForeignBankId negotiationId = new ForeignBankId(OUR_RN, "neg-closed");
        InterbankOtcNegotiation entity = buildAcceptableNegotiation();
        entity.setStatus(InterbankOtcNegotiationStatus.CLOSED);
        entity.setOngoing(false);
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(OUR_RN), eq("neg-closed"))).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.persistAcceptArtifacts(negotiationId))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class)
                .hasMessageContaining("nije aktivan");
    }

    @Test
    @DisplayName("§3.6 persistAcceptArtifacts: 4-posting transakcija (C-1) — PERSON+OptionAsset, qty=1, ne k")
    void persistAcceptArtifacts_formsCorrectFourPostings() {
        ReflectionTestUtils.setField(service, "self", service);
        ForeignBankId negotiationId = new ForeignBankId(OUR_RN, "neg-shape");
        InterbankOtcNegotiation entity = buildAcceptableNegotiation();
        when(negotiationRepository.findByForeignNegotiationRoutingNumberAndForeignNegotiationIdString(
                eq(OUR_RN), eq("neg-shape"))).thenReturn(Optional.of(entity));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Seller (client_id=7L) ima RSD racun u premium valuti
        Client seller = new Client();
        seller.setId(7L);
        Account sellerAcc = new Account();
        sellerAcc.setAccountNumber("222000123456789011");
        Currency usd = new Currency();
        usd.setCode("USD");
        sellerAcc.setCurrency(usd);
        seller.setAccounts(new ArrayList<>(List.of(sellerAcc)));
        when(clientRepository.findById(7L)).thenReturn(Optional.of(seller));

        InterbankOtcContract savedContract = new InterbankOtcContract();
        savedContract.setId(99L);
        when(contractRepository.save(any())).thenReturn(savedContract);

        // formTransaction vrati svoju proslednjenu listu (zelimo da inspect-ujemo)
        // — koristimo realan transactionExecutor mock koji vrati Transaction
        when(transactionExecutor.formTransaction(any(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<Posting> postings = (List<Posting>) inv.getArgument(0);
                    return new Transaction(postings,
                            new ForeignBankId(OUR_RN, "tx-test"),
                            (String) inv.getArgument(1), (String) inv.getArgument(2),
                            (String) inv.getArgument(3), (String) inv.getArgument(4));
                });

        OtcNegotiationService.AcceptPrep prep = service.persistAcceptArtifacts(negotiationId);

        // C-1 verify: 4 postinga; seller side OptionAsset+Person sa qty=-1; buyer side qty=+1
        assertThat(prep.tx().postings()).hasSize(4);
        // Pronadji opcione postings
        List<Posting> optionPostings = prep.tx().postings().stream()
                .filter(p -> p.asset() instanceof Asset.OptionAsset)
                .toList();
        assertThat(optionPostings).hasSize(2);

        // Oba moraju biti na TxAccount.Person (NE Option) — to je C-1 fix
        assertThat(optionPostings).allMatch(p -> p.account() instanceof TxAccount.Person);

        // Apsolutna vrednost = 1 (ne 50 = k); C-1 fix: "Debit ONE optionContract"
        assertThat(optionPostings).allSatisfy(p ->
                assertThat(p.amount().abs()).isEqualByComparingTo(BigDecimal.ONE));

        // Suma postinga po grupi mora biti 0 (balansirana po OptionAsset)
        BigDecimal optionSum = optionPostings.stream()
                .map(Posting::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(optionSum).isEqualByComparingTo(BigDecimal.ZERO);

        // Negotiation flip-uje na ACCEPTED i ongoing=false (sinhrono u toj fazi)
        assertThat(entity.getStatus()).isEqualTo(InterbankOtcNegotiationStatus.ACCEPTED);
        assertThat(entity.isOngoing()).isFalse();
    }

    @Test
    @DisplayName("§3.6 compensateAccept: vraca pregovor u ACTIVE, brise contract, oslobadja stock")
    void compensateAccept_rollsBackState() {
        ReflectionTestUtils.setField(service, "self", service);
        InterbankOtcNegotiation entity = buildAcceptableNegotiation();
        entity.setStatus(InterbankOtcNegotiationStatus.ACCEPTED);
        entity.setOngoing(false);
        when(negotiationRepository.findById(42L)).thenReturn(Optional.of(entity));
        when(negotiationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterbankOtcContract contract = new InterbankOtcContract();
        contract.setId(99L);
        when(contractRepository.findById(99L)).thenReturn(Optional.of(contract));

        service.compensateAccept(42L, 99L, 7L, "CLIENT", "AAPL", 50);

        // Pregovor je vracen u ACTIVE
        assertThat(entity.getStatus()).isEqualTo(InterbankOtcNegotiationStatus.ACTIVE);
        assertThat(entity.isOngoing()).isTrue();
        verify(negotiationRepository).save(entity);

        // Contract je obrisan
        verify(contractRepository).delete(contract);

        // Stock je oslobodjen (best-effort, sa kljucem "compensate")
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(reservationApplier).releaseStock(keyCaptor.capture(),
                eq(7L), eq("CLIENT"), eq("AAPL"), eq(50));
        assertThat(keyCaptor.getValue())
                .contains("otc-accept-compensate-")
                .contains("42")
                .contains("stock-release");
    }

    // ── helpers ──

    private InterbankOtcNegotiation buildAcceptableNegotiation() {
        InterbankOtcNegotiation entity = new InterbankOtcNegotiation();
        entity.setId(42L);
        entity.setForeignNegotiationRoutingNumber(OUR_RN);
        entity.setForeignNegotiationIdString("neg-1");
        entity.setLocalPartyType(InterbankPartyType.SELLER);
        entity.setLocalPartyId(7L);
        entity.setLocalPartyRole("CLIENT");
        entity.setForeignPartyRoutingNumber(BUYER_RN);
        entity.setForeignPartyIdString("C-101");
        entity.setTicker("AAPL");
        entity.setAmount(new BigDecimal("50")); // k = 50 akcija
        entity.setPricePerUnit(new BigDecimal("200"));
        entity.setPriceCurrency("USD");
        entity.setPremium(new BigDecimal("700"));
        entity.setPremiumCurrency("USD");
        entity.setSettlementDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        entity.setStatus(InterbankOtcNegotiationStatus.ACTIVE);
        entity.setOngoing(true);
        entity.setLastModifiedByRoutingNumber(BUYER_RN);
        entity.setLastModifiedByIdString("C-101");
        return entity;
    }

    private Transaction buildExpectedAcceptTx(ForeignBankId negotiationId) {
        // Konstruisan kao §3.6 nakon C-1 fix-a — koristi se kao "expected" return.
        ForeignBankId buyer = new ForeignBankId(BUYER_RN, "C-101");
        ForeignBankId seller = new ForeignBankId(OUR_RN, "C-7");
        BigDecimal premium = new BigDecimal("700");
        return new Transaction(List.of(
                new Posting(new TxAccount.Person(buyer), premium.negate(),
                        new Asset.Monas(new rs.raf.banka2_bek.interbank.protocol.MonetaryAsset(
                                rs.raf.banka2_bek.interbank.protocol.CurrencyCode.USD))),
                new Posting(new TxAccount.Account("222000123456789011"), premium,
                        new Asset.Monas(new rs.raf.banka2_bek.interbank.protocol.MonetaryAsset(
                                rs.raf.banka2_bek.interbank.protocol.CurrencyCode.USD))),
                new Posting(new TxAccount.Person(buyer), BigDecimal.ONE,
                        new Asset.OptionAsset(new rs.raf.banka2_bek.interbank.protocol.OptionDescription(
                                negotiationId, new rs.raf.banka2_bek.interbank.protocol.StockDescription("AAPL"),
                                new rs.raf.banka2_bek.interbank.protocol.MonetaryValue(
                                        rs.raf.banka2_bek.interbank.protocol.CurrencyCode.USD, new BigDecimal("200")),
                                OffsetDateTime.now(ZoneOffset.UTC).plusDays(30), new BigDecimal("50")))),
                new Posting(new TxAccount.Person(seller), BigDecimal.ONE.negate(),
                        new Asset.OptionAsset(new rs.raf.banka2_bek.interbank.protocol.OptionDescription(
                                negotiationId, new rs.raf.banka2_bek.interbank.protocol.StockDescription("AAPL"),
                                new rs.raf.banka2_bek.interbank.protocol.MonetaryValue(
                                        rs.raf.banka2_bek.interbank.protocol.CurrencyCode.USD, new BigDecimal("200")),
                                OffsetDateTime.now(ZoneOffset.UTC).plusDays(30), new BigDecimal("50"))))
        ), new ForeignBankId(OUR_RN, "tx-test"), "OTC accept", null, "OTC", "Premium");
    }
}
