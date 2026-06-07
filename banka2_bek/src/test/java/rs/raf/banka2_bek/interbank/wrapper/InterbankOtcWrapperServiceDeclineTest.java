package rs.raf.banka2_bek.interbank.wrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.interbank.client.TradingServiceInternalClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcContractRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcNegotiationRepository;
import rs.raf.banka2_bek.interbank.service.OtcNegotiationService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9 / S10b — testovi za {@link InterbankOtcWrapperService#declineContract}: rucno
 * "Odbi" na sklopljenom buyer ugovoru pre settlement-a.
 *
 * <p>Po spec-u (Celina 5, Postignut dogovor / protokol §2.7.2): kad kupac odbije
 * ugovor pre exercise-a, oslobadja se njegova accept-time strike rezervacija
 * ("pare za kupovinu se vracaju"), status postaje DECLINED, premija OSTAJE prodavcu,
 * i hartije se NE prenose (sellerova rezervacija hartija se oslobadja na NJIHOVOM
 * expiry-ju — cross-bank poruka nije obavezna; ovo je cisto lokalno zatvaranje).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterbankOtcWrapperService — declineContract (T9 / S10b)")
class InterbankOtcWrapperServiceDeclineTest {

    private static final int OUR_RN = 222;
    private static final int SELLER_RN = 111;

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
    @Mock private rs.raf.banka2_bek.interbank.service.InterbankReservationApplier reservationApplier;

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
        // R1 209 — declineContract ima isti OTC access gate kao exercise: klijent
        // 7L sme da trguje. lenient jer neki testovi pucaju pre/posle gate-a.
        Client tradingClient = new Client();
        tradingClient.setId(7L);
        tradingClient.setCanTradeStocks(true);
        org.mockito.Mockito.lenient().when(clientRepository.findById(7L))
                .thenReturn(Optional.of(tradingClient));
        Client otherTradingClient = new Client();
        otherTradingClient.setId(999L);
        otherTradingClient.setCanTradeStocks(true);
        org.mockito.Mockito.lenient().when(clientRepository.findById(999L))
                .thenReturn(Optional.of(otherTradingClient));
    }

    @Test
    @DisplayName("S10b happy path: ACTIVE buyer ugovor pre settlementa → releaseMonas(strike) + status DECLINED, "
            + "bez prenosa hartija, premija netaknuta")
    void declineContract_activeBeforeSettlement_releasesStrikeAndDeclines() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        contract.setReservedStrikeAccountNumber("222000111111111111");
        contract.setReservedStrikeAmount(new BigDecimal("10000"));
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(InterbankOtcContract.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.declineContract(String.valueOf(contractId), 7L, "CLIENT");

        // "pare za kupovinu se vracaju" — strike rezervacija oslobodjena.
        verify(reservationApplier).releaseMonas(eq("222000111111111111"), eq(new BigDecimal("10000")));
        assertThat(contract.getStatus()).isEqualTo(InterbankOtcContractStatus.DECLINED);
        // Bez prenosa hartija (decline je cisto lokalno zatvaranje) — nema 2PC, nema stock commit.
        verify(transactionExecutor, never()).execute(any());
        verify(reservationApplier, never()).commitStock(anyString(), anyLong(), anyString(),
                anyString(), anyInt(), anyBoolean());
        verify(reservationApplier, never()).releaseStock(anyString(), anyLong(), anyString(),
                anyString(), anyInt());
        // Premija se NE refundira (ostaje prodavcu) — nema dodatnog money-pokreta sem strike release-a.
        verify(contractRepository).save(contract);
    }

    @Test
    @DisplayName("S10b: buyer ugovor BEZ rezervisanog strike-a → DECLINED bez releaseMonas")
    void declineContract_noReservedStrike_declinesWithoutMoneyRelease() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        // reservedStrike ostaje null (legacy/accept bez izabranog racuna).
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(InterbankOtcContract.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.declineContract(String.valueOf(contractId), 7L, "CLIENT");

        assertThat(contract.getStatus()).isEqualTo(InterbankOtcContractStatus.DECLINED);
        verify(reservationApplier, never()).releaseMonas(anyString(), any());
    }

    @Test
    @DisplayName("S10b: vec EXERCISED ugovor → 409 ExerciseConflict, BEZ release-a")
    void declineContract_alreadyExercised_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        contract.setStatus(InterbankOtcContractStatus.EXERCISED);
        contract.setReservedStrikeAccountNumber("222000111111111111");
        contract.setReservedStrikeAmount(new BigDecimal("10000"));
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.declineContract(String.valueOf(contractId), 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class);

        verify(reservationApplier, never()).releaseMonas(anyString(), any());
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("S10b: vec EXPIRED ugovor → 409 ExerciseConflict, BEZ release-a")
    void declineContract_alreadyExpired_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        contract.setStatus(InterbankOtcContractStatus.EXPIRED);
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.declineContract(String.valueOf(contractId), 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class);

        verify(reservationApplier, never()).releaseMonas(anyString(), any());
    }

    @Test
    @DisplayName("S10b: vec DECLINED ugovor (idempotent guard) → 409 ExerciseConflict")
    void declineContract_alreadyDeclined_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        contract.setStatus(InterbankOtcContractStatus.DECLINED);
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.declineContract(String.valueOf(contractId), 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class);

        verify(reservationApplier, never()).releaseMonas(anyString(), any());
    }

    @Test
    @DisplayName("S10b: settlementDate je prosao → 409 ExerciseConflict (decline samo pre dospeca)")
    void declineContract_pastSettlement_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        contract.setSettlementDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        contract.setReservedStrikeAccountNumber("222000111111111111");
        contract.setReservedStrikeAmount(new BigDecimal("10000"));
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.declineContract(String.valueOf(contractId), 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class);

        verify(reservationApplier, never()).releaseMonas(anyString(), any());
        verify(contractRepository, never()).save(any());
    }

    @Test
    @DisplayName("S10b: caller nije buyer (mi smo SELLER) → 409 ExerciseConflict")
    void declineContract_callerNotBuyer_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        contract.setLocalPartyType(InterbankPartyType.SELLER);
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.declineContract(String.valueOf(contractId), 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class);

        verify(reservationApplier, never()).releaseMonas(anyString(), any());
    }

    @Test
    @DisplayName("S10b: ugovor ne pripada caller-u → 409 ExerciseConflict")
    void declineContract_notOwned_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L); // localPartyId=7
        when(contractRepository.findByIdForUpdate(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.declineContract(String.valueOf(contractId), 999L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class);

        verify(reservationApplier, never()).releaseMonas(anyString(), any());
    }

    @Test
    @DisplayName("S10b: ugovor ne postoji → ProtocolException")
    void declineContract_notFound_throws() {
        when(contractRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.declineContract("99", 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class);
    }

    @Test
    @DisplayName("R1 209: klijent bez canTradeStocks → 403, ne dira contract")
    void declineContract_clientWithoutTradeStocks_forbidden() {
        Client noTrade = new Client();
        noTrade.setId(7L);
        noTrade.setCanTradeStocks(false);
        when(clientRepository.findById(7L)).thenReturn(Optional.of(noTrade));

        assertThatThrownBy(() -> service.declineContract("99", 7L, "CLIENT"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(contractRepository, never()).findByIdForUpdate(any());
        verify(reservationApplier, never()).releaseMonas(anyString(), any());
    }

    // ── helpers ──

    private InterbankOtcContract buildActiveBuyerSideContract(Long contractId, Long sourceNegId) {
        InterbankOtcContract c = new InterbankOtcContract();
        c.setId(contractId);
        c.setSourceNegotiationId(sourceNegId);
        c.setLocalPartyType(InterbankPartyType.BUYER); // mi smo BUYER
        c.setLocalPartyId(7L);
        c.setLocalPartyRole("CLIENT");
        c.setForeignPartyRoutingNumber(SELLER_RN);
        c.setForeignPartyIdString("C-seller-1");
        c.setTicker("AAPL");
        c.setQuantity(new BigDecimal("50"));
        c.setStrikePrice(new BigDecimal("200"));
        c.setStrikeCurrency("USD");
        c.setPremium(new BigDecimal("700"));
        c.setPremiumCurrency("USD");
        c.setSettlementDate(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30));
        c.setStatus(InterbankOtcContractStatus.ACTIVE);
        return c;
    }
}
