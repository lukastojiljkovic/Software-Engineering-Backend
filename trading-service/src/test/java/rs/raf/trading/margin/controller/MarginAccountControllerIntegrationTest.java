package rs.raf.trading.margin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2.contracts.internal.DebitFundsRequest;
import rs.raf.banka2.contracts.internal.DebitFundsResponse;
import rs.raf.banka2.contracts.internal.InternalAccountDto;
import rs.raf.banka2.contracts.internal.InternalUserDto;
import rs.raf.trading.client.BankaCoreClient;
import rs.raf.trading.client.BankaCoreClientException;
import rs.raf.trading.margin.model.MarginAccount;
import rs.raf.trading.margin.model.MarginAccountStatus;
import rs.raf.trading.margin.model.MarginTransaction;
import rs.raf.trading.margin.model.MarginTransactionType;
import rs.raf.trading.margin.repository.MarginAccountRepository;
import rs.raf.trading.margin.repository.MarginTransactionRepository;
import rs.raf.trading.margin.service.MarginAccountService;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * HTTP integracioni test {@link MarginAccountController} — pun Spring kontekst
 * (H2 test profil), RANDOM_PORT, realan security filter chain (JWT validacija) +
 * realan {@link MarginAccountService} + JPA persistencija.
 *
 * <p>NAPOMENA (copy-first ekstrakcija, faza 2d-D): monolitni test je pravio prave
 * {@code Employee}/{@code Account}/{@code Client} zapise i koristio
 * {@code JwtService}. trading-service NEMA login endpoint ni
 * {@code Employee}/{@code Account}/{@code clients} tabele — JWT se generise
 * lokalno deljenim test secret-om (HS256, isto kao banka-core),
 * {@link BankaCoreClient} je {@code @MockitoBean} (razresava identitet preko
 * {@code getUserByEmail}, daje bazni racun preko {@code getAccount} i izvodi
 * debit preko {@code debitFunds}), a {@code MarginAccount} se seeduje direktno
 * preko repozitorijuma sa soft {@code accountId}/{@code accountNumber}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MarginAccountControllerIntegrationTest {

    private static final String TEST_SECRET =
            "test-jwt-secret-do-not-use-in-prod-32-bytes-minimum-required-for-hs256";

    private static final Long CLIENT_ID = 5101L;
    private static final Long OTHER_CLIENT_ID = 5102L;
    private static final Long EMPLOYEE_ID = 5103L;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private MarginAccountRepository marginAccountRepository;

    @Autowired
    private MarginTransactionRepository marginTransactionRepository;

    @Autowired
    private MarginAccountService marginAccountService;

    @MockitoBean
    private BankaCoreClient bankaCoreClient;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RestTemplate restTemplate = createRestTemplate();
    private final SecretKey secretKey =
            Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory());
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rt;
    }

    @BeforeEach
    void setUp() {
        marginTransactionRepository.deleteAll();
        marginAccountRepository.deleteAll();

        // Identitet — TradingUserResolver cita ovo preko BankaCoreClient.getUserByEmail.
        lenient().when(bankaCoreClient.getUserByEmail("client@test.com")).thenReturn(
                new InternalUserDto(CLIENT_ID, "CLIENT", "client@test.com", "Client", "User", true, null));
        lenient().when(bankaCoreClient.getUserByEmail("other@test.com")).thenReturn(
                new InternalUserDto(OTHER_CLIENT_ID, "CLIENT", "other@test.com", "Other", "User", true, null));
        lenient().when(bankaCoreClient.getUserByEmail("employee@test.com")).thenReturn(
                new InternalUserDto(EMPLOYEE_ID, "EMPLOYEE", "employee@test.com", "Emp", "Loyee", true, "Agent"));
    }

    // ── createMarginAccount ──────────────────────────────────────────────────

    @Test
    void createMarginAccount_returnsOK_andPersistsCalculatedFields() throws Exception {
        // bazni racun pripada klijentu, ima dovoljno sredstava — debit kroz banka-core.
        when(bankaCoreClient.getAccount(7771L)).thenReturn(
                clientAccount(7771L, CLIENT_ID, "ACTIVE", "777777777777777771",
                        "10000.00", "10000.00"));
        when(bankaCoreClient.debitFunds(any(), any(DebitFundsRequest.class)))
                .thenReturn(new DebitFundsResponse(7771L, new BigDecimal("5000.00"),
                        new BigDecimal("5000.00")));

        String payload = """
                {
                  "accountId": 7771,
                  "initialDeposit": 5000.00
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("id").asLong()).isPositive();
        assertThat(body.path("userId").asLong()).isEqualTo(CLIENT_ID);
        assertThat(body.path("accountNumber").asText()).isEqualTo("777777777777777771");
        assertThat(body.path("bankParticipation").decimalValue()).isEqualByComparingTo("0.50");
        assertThat(body.path("initialMargin").decimalValue()).isEqualByComparingTo("10000.0000");
        assertThat(body.path("loanValue").decimalValue()).isEqualByComparingTo("5000.0000");
        assertThat(body.path("maintenanceMargin").decimalValue()).isEqualByComparingTo("5000.0000");

        assertThat(marginAccountRepository.count()).isEqualTo(1L);
        assertThat(marginTransactionRepository.count()).isEqualTo(1L);
    }

    @Test
    void createMarginAccount_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>("{\"accountId\":1,\"initialDeposit\":100.00}", jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createMarginAccount_returnsForbidden_forAuthenticatedNonClient() {
        String payload = """
                {
                  "accountId": 7772,
                  "initialDeposit": 100.00
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(buildToken("employee@test.com", "EMPLOYEE"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Only clients can manage margin accounts.");
    }

    @Test
    void createMarginAccount_returnsBadRequest_whenInsufficientFunds() {
        when(bankaCoreClient.getAccount(7773L)).thenReturn(
                clientAccount(7773L, CLIENT_ID, "ACTIVE", "777777777777777773",
                        "10.00", "10.00"));

        String payload = """
                {
                  "accountId": 7773,
                  "initialDeposit": 100.00
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Insufficient available balance");
    }

    @Test
    void createMarginAccount_returnsBadRequest_whenBankaCoreDebitReturns409() {
        // pre-check prodje, ali banka-core odbije debit sa 409 — faithful poruka.
        when(bankaCoreClient.getAccount(7774L)).thenReturn(
                clientAccount(7774L, CLIENT_ID, "ACTIVE", "777777777777777774",
                        "10000.00", "10000.00"));
        when(bankaCoreClient.debitFunds(any(), any(DebitFundsRequest.class)))
                .thenThrow(new BankaCoreClientException(409, "insufficient funds"));

        String payload = """
                {
                  "accountId": 7774,
                  "initialDeposit": 5000.00
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Insufficient available balance");
        assertThat(marginAccountRepository.count()).isZero();
    }

    @Test
    void createMarginAccount_returnsBadRequest_whenPayloadInvalid() {
        String payload = """
                {
                  "accountId": 1
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Pocetni depozit je obavezan");
    }

    // ── getMyMarginAccounts ──────────────────────────────────────────────────

    @Test
    void getMyMarginAccounts_returnsOnlyCurrentClientAccounts() throws Exception {
        marginAccountRepository.save(marginAccount(8181L, "777777777777777781", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));
        marginAccountRepository.save(marginAccount(8182L, "777777777777777782", OTHER_CLIENT_ID,
                MarginAccountStatus.ACTIVE, "6000.0000", "3000.0000"));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/my"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).path("userId").asLong()).isEqualTo(CLIENT_ID);
        assertThat(body.get(0).path("accountNumber").asText()).isEqualTo("777777777777777781");
    }

    @Test
    void getMyMarginAccounts_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/margin-accounts/my"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getMyMarginAccounts_returnsForbidden_forAuthenticatedNonClient() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/my"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(buildToken("employee@test.com", "EMPLOYEE"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Only clients can manage margin accounts.");
    }

    // ── deposit ──────────────────────────────────────────────────────────────

    @Test
    void deposit_returnsOK_andUpdatesMarginAccountValues() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7791L, "777777777777777791", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>("{ \"amount\": 2000.00 }",
                        jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        MarginAccount updated = marginAccountRepository.findById(marginAccount.getId()).orElseThrow();
        assertThat(updated.getInitialMargin()).isEqualByComparingTo("12000.0000");
        assertThat(updated.getMaintenanceMargin()).isEqualByComparingTo("6000.0000");
        assertThat(marginTransactionRepository.count()).isEqualTo(1L);
        assertThat(marginTransactionRepository.findAll().get(0).getType())
                .isEqualTo(MarginTransactionType.DEPOSIT);
    }

    @Test
    void deposit_returnsOK_andActivatesBLOCKEDAccount() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7792L, "777777777777777792", CLIENT_ID,
                MarginAccountStatus.BLOCKED, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>("{ \"amount\": 1000.00 }",
                        jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        MarginAccount updated = marginAccountRepository.findById(marginAccount.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE);
    }

    @Test
    void deposit_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/1/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deposit_returnsForbidden_forNonClientUser() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/1/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(buildToken("employee@test.com", "EMPLOYEE"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deposit_returnsNotFound_whenMarginAccountNotFound() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/99999/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("99999");
    }

    @Test
    void deposit_returnsForbidden_whenCallerIsNotOwner() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7793L, "777777777777777793", OTHER_CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("can deposit funds");
    }

    @Test
    void deposit_returnsBadRequest_whenAmountIsZero() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7794L, "777777777777777794", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>("{\"amount\":0}", jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Amount must be a positive number.");
    }

    // ── withdraw ─────────────────────────────────────────────────────────────

    @Test
    void withdraw_returnsOK_andUpdatesMarginAccountValues() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7801L, "777777777777777801", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/withdraw"),
                new HttpEntity<>("{ \"amount\": 2000.00 }",
                        jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        MarginAccount updated = marginAccountRepository.findById(marginAccount.getId()).orElseThrow();
        assertThat(updated.getInitialMargin()).isEqualByComparingTo("8000.0000");
        assertThat(updated.getMaintenanceMargin()).isEqualByComparingTo("4000.0000");
        assertThat(marginTransactionRepository.count()).isEqualTo(1L);
        assertThat(marginTransactionRepository.findAll().get(0).getType())
                .isEqualTo(MarginTransactionType.WITHDRAWAL);
    }

    @Test
    void withdraw_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/1/withdraw"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void withdraw_returnsForbidden_whenCallerIsNotOwner() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7802L, "777777777777777802", OTHER_CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/withdraw"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("can withdraw funds");
    }

    @Test
    void withdraw_returnsForbidden_whenAccountIsBlocked() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7805L, "777777777777777805", CLIENT_ID,
                MarginAccountStatus.BLOCKED, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/withdraw"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("is not active");
    }

    @Test
    void withdraw_returnsBadRequest_whenWithdrawalDropsBelowMaintenanceMargin() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7806L, "777777777777777806", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        // 10000 - 6000 = 4000 < 5000 (maintenanceMargin)
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/withdraw"),
                new HttpEntity<>("{\"amount\":6000}", jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Funds in the account cannot be below");
    }

    // ── checkMaintenanceMargin (rucni poziv — scheduler je dormant) ──────────

    @Test
    void checkMaintenanceMargin_blocksAccountWhenMaintenanceExceedsInitial() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7901L, "777777777777777901", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "4000.0000", "5000.0000"));
        lenient().when(bankaCoreClient.getUserById("CLIENT", CLIENT_ID)).thenReturn(
                new InternalUserDto(CLIENT_ID, "CLIENT", "client@test.com", "Client", "User", true, null));

        marginAccountService.checkMaintenanceMargin();

        MarginAccount updated = marginAccountRepository.findById(marginAccount.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(MarginAccountStatus.BLOCKED);
    }

    @Test
    void checkMaintenanceMargin_doesNotBlockAccountWhenInitialExceedsMaintenance() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7902L, "777777777777777902", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        marginAccountService.checkMaintenanceMargin();

        MarginAccount updated = marginAccountRepository.findById(marginAccount.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE);
    }

    @Test
    void checkMaintenanceMargin_blocksOnlyEligibleAccounts() {
        MarginAccount eligible = marginAccountRepository.save(marginAccount(
                7903L, "777777777777777903", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "4000.0000", "5000.0000"));
        MarginAccount safe = marginAccountRepository.save(marginAccount(
                7904L, "777777777777777904", OTHER_CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));
        lenient().when(bankaCoreClient.getUserById(any(), any())).thenReturn(
                new InternalUserDto(CLIENT_ID, "CLIENT", "client@test.com", "Client", "User", true, null));

        marginAccountService.checkMaintenanceMargin();

        assertThat(marginAccountRepository.findById(eligible.getId()).orElseThrow().getStatus())
                .isEqualTo(MarginAccountStatus.BLOCKED);
        assertThat(marginAccountRepository.findById(safe.getId()).orElseThrow().getStatus())
                .isEqualTo(MarginAccountStatus.ACTIVE);
    }

    // ── getTransactions ──────────────────────────────────────────────────────

    @Test
    void getTransactions_returnsOK_withTransactionsSortedNewestFirst() throws Exception {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7910L, "777777777777777910", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        marginTransactionRepository.save(marginTransaction(marginAccount, MarginTransactionType.DEPOSIT,
                new BigDecimal("1000"), LocalDateTime.now().minusHours(1)));
        marginTransactionRepository.save(marginTransaction(marginAccount, MarginTransactionType.WITHDRAWAL,
                new BigDecimal("500"), LocalDateTime.now()));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/" + marginAccount.getId() + "/transactions"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get(0).path("type").asText()).isEqualTo("WITHDRAWAL");
    }

    @Test
    void getTransactions_returnsOK_withEmptyList() throws Exception {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7911L, "777777777777777911", CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/" + marginAccount.getId() + "/transactions"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(0);
    }

    @Test
    void getTransactions_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/1/transactions"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getTransactions_returnsNotFound_whenMarginAccountNotFound() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/99999/transactions"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("99999");
    }

    @Test
    void getTransactions_returnsForbidden_whenCallerIsNotOwner() {
        MarginAccount marginAccount = marginAccountRepository.save(marginAccount(
                7912L, "777777777777777912", OTHER_CLIENT_ID,
                MarginAccountStatus.ACTIVE, "10000.0000", "5000.0000"));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/" + marginAccount.getId() + "/transactions"),
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(buildToken("client@test.com", "CLIENT"))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("can access margin account transactions");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private InternalAccountDto clientAccount(Long accountId, Long ownerClientId, String status,
                                             String accountNumber, String available, String balance) {
        return new InternalAccountDto(accountId, accountNumber, "Client User",
                new BigDecimal(balance), new BigDecimal(available),
                BigDecimal.ZERO, "RSD", status, ownerClientId, null, "CHECKING");
    }

    private MarginAccount marginAccount(Long accountId, String accountNumber, Long userId,
                                        MarginAccountStatus status,
                                        String initialMargin, String maintenanceMargin) {
        return MarginAccount.builder()
                .accountId(accountId)
                .accountNumber(accountNumber)
                .userId(userId)
                .initialMargin(new BigDecimal(initialMargin))
                .loanValue(new BigDecimal(initialMargin).divide(new BigDecimal("2")))
                .maintenanceMargin(new BigDecimal(maintenanceMargin))
                .bankParticipation(new BigDecimal("0.50"))
                .status(status)
                .build();
    }

    private MarginTransaction marginTransaction(MarginAccount marginAccount, MarginTransactionType type,
                                                BigDecimal amount, LocalDateTime createdAt) {
        return MarginTransaction.builder()
                .marginAccount(marginAccount)
                .type(type)
                .amount(amount)
                .createdAt(createdAt)
                .build();
    }

    private String buildToken(String email, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + 3_600_000);
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("active", true)
                .issuedAt(now)
                .expiration(exp)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
