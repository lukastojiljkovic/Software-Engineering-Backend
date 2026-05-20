package rs.raf.banka2_bek.interbank.wrapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.interbank.client.TradingServiceInternalClient;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContract;
import rs.raf.banka2_bek.interbank.model.InterbankOtcContractStatus;
import rs.raf.banka2_bek.interbank.model.InterbankOtcNegotiation;
import rs.raf.banka2_bek.interbank.model.InterbankPartyType;
import rs.raf.banka2_bek.interbank.protocol.Asset;
import rs.raf.banka2_bek.interbank.protocol.ForeignBankId;
import rs.raf.banka2_bek.interbank.protocol.Posting;
import rs.raf.banka2_bek.interbank.protocol.Transaction;
import rs.raf.banka2_bek.interbank.protocol.TxAccount;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcContractRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankOtcNegotiationRepository;
import rs.raf.banka2_bek.interbank.service.OtcNegotiationService;
import rs.raf.banka2_bek.interbank.service.TransactionExecutorService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C-2 fix po Celini 5 audit-u: tests za {@link InterbankOtcWrapperService#exerciseContract}.
 *
 * <p>Pre fix-a, exerciseContract je samo postavljao status=EXERCISED lokalno — bez
 * ikakvog 2PC poziva ka prodavcevoj banci. Posle fix-a, formira 4-posting
 * transakciju po §2.7.2 i izvrsava je kroz {@link TransactionExecutorService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InterbankOtcWrapperService — exerciseContract (C-2 fix)")
class InterbankOtcWrapperServiceExerciseTest {

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
                accountRepository, transactionExecutor);
    }

    @Test
    @DisplayName("§2.7.2 happy path: formira 4-posting tx (Option↔Person, Stock+Monas), zove 2PC")
    void exerciseContract_happyPath_formsAndExecutes2PC() {
        Long contractId = 99L;
        Long sourceNegId = 42L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, sourceNegId);
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        InterbankOtcNegotiation neg = new InterbankOtcNegotiation();
        neg.setId(sourceNegId);
        neg.setForeignNegotiationIdString("source-neg-uuid");
        when(negotiationRepository.findById(sourceNegId)).thenReturn(Optional.of(neg));

        Account buyerAccount = buildAccount(101L, "222000111111111111", "USD",
                new BigDecimal("10000.00"), AccountStatus.ACTIVE);
        Client buyer = new Client();
        buyer.setId(7L);
        buyerAccount.setClient(buyer);
        when(accountRepository.findById(101L)).thenReturn(Optional.of(buyerAccount));

        // formTransaction passthrough
        when(transactionExecutor.formTransaction(any(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<Posting> postings = (List<Posting>) inv.getArgument(0);
                    return new Transaction(postings,
                            new ForeignBankId(OUR_RN, "tx-ex"),
                            (String) inv.getArgument(1), (String) inv.getArgument(2),
                            (String) inv.getArgument(3), (String) inv.getArgument(4));
                });
        doNothing().when(transactionExecutor).execute(any(Transaction.class));

        service.exerciseContract(String.valueOf(contractId), 101L, 7L, "CLIENT");

        // C-2: 2PC pozvan sa 4-posting tx
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionExecutor).execute(txCaptor.capture());
        Transaction tx = txCaptor.getValue();

        assertThat(tx.postings()).hasSize(4);

        // Po §2.7.2:
        //   (Monas, Option) +pi*k  → option pseudo-account "receives" money
        //   (Monas, Account) -pi*k → buyer pays
        //   (Stock, Option) -k     → option pseudo-account "gives" stocks
        //   (Stock, Person) +k     → buyer receives stocks
        BigDecimal money = new BigDecimal("10000"); // 50 × 200
        BigDecimal qty = new BigDecimal("50");

        long monasOptionCount = tx.postings().stream()
                .filter(p -> p.asset() instanceof Asset.Monas
                        && p.account() instanceof TxAccount.Option
                        && p.amount().compareTo(money) == 0)
                .count();
        assertThat(monasOptionCount).isEqualTo(1L);

        long monasAccountCount = tx.postings().stream()
                .filter(p -> p.asset() instanceof Asset.Monas
                        && p.account() instanceof TxAccount.Account
                        && p.amount().compareTo(money.negate()) == 0)
                .count();
        assertThat(monasAccountCount).isEqualTo(1L);

        long stockOptionCount = tx.postings().stream()
                .filter(p -> p.asset() instanceof Asset.Stock
                        && p.account() instanceof TxAccount.Option
                        && p.amount().compareTo(qty.negate()) == 0)
                .count();
        assertThat(stockOptionCount).isEqualTo(1L);

        long stockPersonCount = tx.postings().stream()
                .filter(p -> p.asset() instanceof Asset.Stock
                        && p.account() instanceof TxAccount.Person
                        && p.amount().compareTo(qty) == 0)
                .count();
        assertThat(stockPersonCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("§2.7.2 settlement-past: vraca 409 ExerciseConflict, ne zove 2PC")
    void exerciseContract_settlementPast_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        // settlementDate u proslosti
        contract.setSettlementDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.exerciseContract(String.valueOf(contractId), 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("istekao");

        verify(transactionExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("§2.7.2 status != ACTIVE: vraca 409 ExerciseConflict")
    void exerciseContract_alreadyExercised_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        contract.setStatus(InterbankOtcContractStatus.EXERCISED);
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.exerciseContract(String.valueOf(contractId), 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("nije ACTIVE");

        verify(transactionExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("§2.7.2 caller nije buyer (seller pokusava): 409 ExerciseConflict")
    void exerciseContract_callerNotBuyer_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        // Mi smo SELLER u ovom ugovoru — exercise je samo za BUYER
        contract.setLocalPartyType(InterbankPartyType.SELLER);
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.exerciseContract(String.valueOf(contractId), 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("SELLER");

        verify(transactionExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("§2.7.2 ugovor ne pripada caller-u: 409 ExerciseConflict")
    void exerciseContract_notOwned_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.exerciseContract(String.valueOf(contractId), 101L, 999L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("ne pripada");

        verify(transactionExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("§2.7.2 nedovoljno sredstava: 409 ExerciseConflict")
    void exerciseContract_insufficientFunds_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        Account buyerAccount = buildAccount(101L, "222000111111111111", "USD",
                new BigDecimal("100.00"), // contract trazi 50*200=10000 USD
                AccountStatus.ACTIVE);
        Client buyer = new Client();
        buyer.setId(7L);
        buyerAccount.setClient(buyer);
        when(accountRepository.findById(101L)).thenReturn(Optional.of(buyerAccount));

        assertThatThrownBy(() -> service.exerciseContract(String.valueOf(contractId), 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("Nedovoljno");

        verify(transactionExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("§2.7.2 racun u pogresnoj valuti: 409 ExerciseConflict")
    void exerciseContract_wrongCurrency_throws409() {
        Long contractId = 99L;
        InterbankOtcContract contract = buildActiveBuyerSideContract(contractId, 42L);
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

        Account buyerAccount = buildAccount(101L, "222000111111111111", "RSD",
                new BigDecimal("999999.00"), AccountStatus.ACTIVE);
        Client buyer = new Client();
        buyer.setId(7L);
        buyerAccount.setClient(buyer);
        when(accountRepository.findById(101L)).thenReturn(Optional.of(buyerAccount));

        assertThatThrownBy(() -> service.exerciseContract(String.valueOf(contractId), 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankExerciseConflictException.class)
                .hasMessageContaining("nije u valuti");

        verify(transactionExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("§2.7.2: contract ne postoji: ProtocolException (400)")
    void exerciseContract_notFound_throws() {
        when(contractRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exerciseContract("99", 101L, 7L, "CLIENT"))
                .isInstanceOf(InterbankExceptions.InterbankProtocolException.class)
                .hasMessageContaining("ne postoji");
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

    private Account buildAccount(Long id, String number, String currencyCode,
                                  BigDecimal availableBalance, AccountStatus status) {
        Account a = new Account();
        a.setId(id);
        a.setAccountNumber(number);
        Currency c = new Currency();
        c.setCode(currencyCode);
        a.setCurrency(c);
        a.setAvailableBalance(availableBalance);
        a.setStatus(status);
        return a;
    }
}
