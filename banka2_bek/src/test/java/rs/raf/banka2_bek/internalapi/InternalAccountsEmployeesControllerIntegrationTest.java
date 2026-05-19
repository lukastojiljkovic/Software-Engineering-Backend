package rs.raf.banka2_bek.internalapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.IntegrationTestCleanup;
import rs.raf.banka2_bek.TestObjectMapperConfig;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracioni testovi za interne racun + zaposleni rute (faza2c-A):
 *   POST /internal/accounts/fund
 *   GET  /internal/accounts/bank-trading/{currencyCode}
 *   GET  /internal/employees
 */
@Import(TestObjectMapperConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalAccountsEmployeesControllerIntegrationTest {

    @Value("${internal.api-key}")
    private String internalKey;

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @Autowired private AccountRepository accountRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private CurrencyRepository currencyRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        IntegrationTestCleanup.truncateAllTables(dataSource);
    }

    // ─── provisionFundAccount: happy path ────────────────────────────────────

    @Test
    void provisionFundAccount_happyPath_createsFundAccount() throws Exception {
        persistCurrency("RSD");
        persistBankCompany();
        Employee manager = persistEmployee("fund-mgr@test.com", "QA");

        String body = """
                { "fundName": "Internal Growth Fund", "managerEmployeeId": %d }
                """.formatted(manager.getId());

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/internal/accounts/fund"),
                new HttpEntity<>(body, jsonInternalHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.path("id").asLong()).isPositive();
        assertThat(json.path("accountNumber").asText()).isNotBlank();
        assertThat(json.path("currencyCode").asText()).isEqualTo("RSD");
        assertThat(json.path("balance").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(json.path("status").asText()).isEqualTo("ACTIVE");

        // Eager (non-lazy) entity polja se proveravaju direktno; valuta je
        // lazy proxy van transakcije pa se njen kod cita iz response JSON-a iznad.
        Account saved = accountRepository.findById(json.path("id").asLong()).orElseThrow();
        assertThat(saved.getAccountCategory()).isEqualTo(AccountCategory.FUND);
        assertThat(saved.getAccountType()).isEqualTo(AccountType.BUSINESS);
        assertThat(saved.getName()).isEqualTo("Fund: Internal Growth Fund");
    }

    // ─── provisionFundAccount: manager not found → error ─────────────────────

    @Test
    void provisionFundAccount_managerNotFound_returnsError() {
        persistCurrency("RSD");
        persistBankCompany();

        String body = """
                { "fundName": "No Manager Fund", "managerEmployeeId": 999999 }
                """;

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/internal/accounts/fund"),
                new HttpEntity<>(body, jsonInternalHeaders()), String.class);

        // IllegalArgumentException → 404 (InternalApiExceptionHandler)
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── getBankTradingAccount: found ────────────────────────────────────────

    @Test
    void getBankTradingAccount_found_returnsAccount() throws Exception {
        Currency rsd = persistCurrency("RSD");
        Company bank = persistBankCompany();
        Employee employee = persistEmployee("bt-emp@test.com", "QA");
        Account bankTrading = accountRepository.save(Account.builder()
                .accountNumber("222000000000000010")
                .accountType(AccountType.BUSINESS)
                .currency(rsd)
                .company(bank)
                .employee(employee)
                .balance(new BigDecimal("123456.00"))
                .availableBalance(new BigDecimal("123456.00"))
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.BANK_TRADING)
                .status(AccountStatus.ACTIVE)
                .build());

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/accounts/bank-trading/RSD"),
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.path("id").asLong()).isEqualTo(bankTrading.getId());
        assertThat(json.path("accountNumber").asText()).isEqualTo("222000000000000010");
        assertThat(json.path("currencyCode").asText()).isEqualTo("RSD");
    }

    // ─── getBankTradingAccount: not found → 404 ──────────────────────────────

    @Test
    void getBankTradingAccount_notFound_returns404() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/accounts/bank-trading/RSD"),
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── getEmployees: no filter → all ───────────────────────────────────────

    @Test
    void getEmployees_noFilter_returnsAll() throws Exception {
        persistEmployee("emp-all-1@test.com", "Agent");
        persistEmployee("emp-all-2@test.com", "Supervizor");

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/employees"),
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.isArray()).isTrue();
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get(0).path("userRole").asText()).isEqualTo("EMPLOYEE");
    }

    // ─── getEmployees: filter by firstName ───────────────────────────────────

    @Test
    void getEmployees_filterByFirstName_returnsMatching() throws Exception {
        persistEmployeeNamed("filter-fn-1@test.com", "Aleksandar", "Petrovic", "Agent");
        persistEmployeeNamed("filter-fn-2@test.com", "Marko", "Jovanovic", "Agent");

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/employees?firstName=aleks"),
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get(0).path("firstName").asText()).isEqualTo("Aleksandar");
        assertThat(json.get(0).path("email").asText()).isEqualTo("filter-fn-1@test.com");
    }

    // ─── getEmployees: filter by position ────────────────────────────────────

    @Test
    void getEmployees_filterByPosition_returnsMatching() throws Exception {
        persistEmployee("filter-pos-1@test.com", "Supervizor");
        persistEmployee("filter-pos-2@test.com", "Agent");
        persistEmployee("filter-pos-3@test.com", "Agent");

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/employees?position=agent"),
                HttpMethod.GET, new HttpEntity<>(internalHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.size()).isEqualTo(2);
    }

    // ─── getEmployees: missing X-Internal-Key → 401 ──────────────────────────

    @Test
    void getEmployees_missingInternalKey_returns401() {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/employees"),
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalKey);
        return headers;
    }

    private HttpHeaders jsonInternalHeaders() {
        HttpHeaders headers = internalHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private Currency persistCurrency(String code) {
        Currency c = new Currency();
        c.setCode(code);
        c.setName(code);
        c.setSymbol(code);
        c.setCountry("Serbia");
        c.setActive(true);
        return currencyRepository.save(c);
    }

    private Company persistBankCompany() {
        return companyRepository.save(Company.builder()
                .name("Banka 2 d.o.o.")
                .registrationNumber("22200022")
                .taxNumber("100000001")
                .address("Bulevar Kralja Aleksandra 1, Beograd")
                .isBank(true)
                .build());
    }

    private Employee persistEmployee(String email, String position) {
        return persistEmployeeNamed(email, "Internal", "Employee", position);
    }

    private Employee persistEmployeeNamed(String email, String firstName,
                                          String lastName, String position) {
        return employeeRepository.save(Employee.builder()
                .firstName(firstName).lastName(lastName)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .email(email)
                .phone("+381600000000")
                .address("Test")
                .username("internal-emp-" + email)
                .password("x")
                .saltPassword("salt")
                .position(position)
                .department("IT")
                .active(true)
                .permissions(Set.of())
                .build());
    }
}
