package rs.raf.banka2_bek.assistant.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CreateOrderReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CreateOtcOfferReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.InvestFundReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsActuaryInfo;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundPosition;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundSummary;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsListing;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOrder;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOtcOffer;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.UpdateActuaryLimitReq;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Testovi za {@link TradingServiceClient} — {@link MockRestServiceServer}
 * stubira HTTP pozive ka {@code trading-service} PUBLIC API-ju (faza 2f).
 *
 * <p>Proverava da klijent: salje {@code Authorization: Bearer <token>} sa
 * tokenom iz {@link CallerTokenHolder}, gadja prave rute/metode, deserijalizuje
 * odgovore (uklj. {@code Page} oblik {@code GET /orders/my}), 404 na listing
 * lookup mapira u {@code Optional.empty()}, i ostale HTTP greske propagira kao
 * {@link TradingServiceClientException} sa porukom iz tela.
 */
class TradingServiceClientTest {

    private static final String BASE_URL = "http://localhost:18082";
    private static final String CALLER_JWT = "caller.jwt.token";

    private MockRestServiceServer mockServer;
    private TradingServiceClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new TradingServiceClient(builder.build(), objectMapper);
        CallerTokenHolder.set(CALLER_JWT);
    }

    @AfterEach
    void tearDown() {
        CallerTokenHolder.clear();
    }

    @Test
    @DisplayName("createOrder: POST /orders sa Bearer JWT pozivaoca, deserijalizuje TsOrder")
    void createOrder_sendsJwt_andDeserializes() throws Exception {
        TsOrder stub = new TsOrder(42L, 7L, "AAPL", "MARKET", 10, "BUY",
                "PENDING", null, false, 10, "2026-05-19T10:00:00");
        mockServer.expect(requestTo(BASE_URL + "/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CALLER_JWT))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsOrder resp = client.createOrder(new CreateOrderReq(
                7L, "MARKET", 10, 1, "BUY", null, null, false, false, 5L, "123456", null));

        assertThat(resp.id()).isEqualTo(42L);
        assertThat(resp.status()).isEqualTo("PENDING");
        mockServer.verify();
    }

    @Test
    @DisplayName("cancelOrder: PATCH /orders/{id}/decline?quantity=N za parcijalni cancel")
    void cancelOrder_partial_sendsQuantityQueryParam() throws Exception {
        TsOrder stub = new TsOrder(42L, 7L, "AAPL", "MARKET", 10, "BUY",
                "APPROVED", "supervizor", false, 7, null);
        mockServer.expect(requestTo(BASE_URL + "/orders/42/decline?quantity=3"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer " + CALLER_JWT))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsOrder resp = client.cancelOrder(42L, 3);

        assertThat(resp.remainingPortions()).isEqualTo(7);
        mockServer.verify();
    }

    @Test
    @DisplayName("cancelOrder: PATCH /orders/{id}/decline bez quantity za pun decline")
    void cancelOrder_full_noQuantityParam() throws Exception {
        TsOrder stub = new TsOrder(42L, 7L, "AAPL", "MARKET", 10, "BUY",
                "DECLINED", null, true, 0, null);
        mockServer.expect(requestTo(BASE_URL + "/orders/42/decline"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsOrder resp = client.cancelOrder(42L, null);

        assertThat(resp.status()).isEqualTo("DECLINED");
        mockServer.verify();
    }

    @Test
    @DisplayName("approveOrder: PATCH /orders/{id}/approve")
    void approveOrder_happyPath() throws Exception {
        TsOrder stub = new TsOrder(42L, 7L, "AAPL", "MARKET", 10, "BUY",
                "APPROVED", "supervizor@banka.rs", false, 10, null);
        mockServer.expect(requestTo(BASE_URL + "/orders/42/approve"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsOrder resp = client.approveOrder(42L);

        assertThat(resp.status()).isEqualTo("APPROVED");
        assertThat(resp.approvedBy()).isEqualTo("supervizor@banka.rs");
        mockServer.verify();
    }

    @Test
    @DisplayName("recentOrders: GET /orders/my?page=0&size=N → cita content stranice")
    void recentOrders_unwrapsPageContent() throws Exception {
        String pageJson = """
                {"content":[
                   {"id":1,"listingTicker":"AAPL","orderType":"MARKET","quantity":5,
                    "direction":"BUY","status":"DONE","isDone":true,"remainingPortions":0,
                    "lastModification":"2026-05-19T09:00:00"}
                ],"totalElements":1,"totalPages":1}""";
        mockServer.expect(requestTo(BASE_URL + "/orders/my?page=0&size=5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + CALLER_JWT))
                .andRespond(withSuccess(pageJson, MediaType.APPLICATION_JSON));

        List<TsOrder> orders = client.recentOrders(5);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).listingTicker()).isEqualTo("AAPL");
        assertThat(orders.get(0).isDone()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("findListingById: 200 → TsListing")
    void findListingById_present() throws Exception {
        TsListing stub = new TsListing(7L, "AAPL", "Apple Inc.",
                new BigDecimal("180.50"), "STOCK", "NASDAQ", "USD");
        mockServer.expect(requestTo(BASE_URL + "/listings/7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        Optional<TsListing> result = client.findListingById(7L);

        assertThat(result).isPresent();
        assertThat(result.get().ticker()).isEqualTo("AAPL");
        assertThat(result.get().price()).isEqualByComparingTo("180.50");
        mockServer.verify();
    }

    @Test
    @DisplayName("findListingById: 404 → Optional.empty")
    void findListingById_notFound_returnsEmpty() {
        mockServer.expect(requestTo(BASE_URL + "/listings/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Hartija ne postoji\"}"));

        Optional<TsListing> result = client.findListingById(999L);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("findListingByTicker: GET /listings?search → egzaktan ticker match (case-insensitive)")
    void findListingByTicker_exactMatch() throws Exception {
        TsListing[] page = {
                new TsListing(7L, "AAPL", "Apple Inc.", new BigDecimal("180"),
                        "STOCK", "NASDAQ", "USD")
        };
        String pageJson = "{\"content\":" + objectMapper.writeValueAsString(page)
                + ",\"totalElements\":1}";
        mockServer.expect(requestTo(BASE_URL + "/listings?type=STOCK&search=AAPL&page=0&size=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(pageJson, MediaType.APPLICATION_JSON));

        Optional<TsListing> result = client.findListingByTicker("AAPL");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(7L);
        mockServer.verify();
    }

    @Test
    @DisplayName("createOtcOffer: POST /otc/offers")
    void createOtcOffer_happyPath() throws Exception {
        TsOtcOffer stub = new TsOtcOffer(5L, 7L, 1L, 2L, 10,
                new BigDecimal("100"), new BigDecimal("5"), "ACTIVE");
        mockServer.expect(requestTo(BASE_URL + "/otc/offers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CALLER_JWT))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsOtcOffer resp = client.createOtcOffer(new CreateOtcOfferReq(
                7L, 2L, 10, new BigDecimal("100"), new BigDecimal("5"),
                java.time.LocalDate.of(2026, 12, 31)));

        assertThat(resp.id()).isEqualTo(5L);
        assertThat(resp.status()).isEqualTo("ACTIVE");
        mockServer.verify();
    }

    @Test
    @DisplayName("acceptOtcOffer: POST /otc/offers/{id}/accept?buyerAccountId=N")
    void acceptOtcOffer_sendsBuyerAccountIdQueryParam() throws Exception {
        TsOtcOffer stub = new TsOtcOffer(5L, 7L, 1L, 2L, 10,
                new BigDecimal("100"), new BigDecimal("5"), "ACCEPTED");
        mockServer.expect(requestTo(BASE_URL + "/otc/offers/5/accept?buyerAccountId=10"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsOtcOffer resp = client.acceptOtcOffer(5L, 10L);

        assertThat(resp.status()).isEqualTo("ACCEPTED");
        mockServer.verify();
    }

    @Test
    @DisplayName("investInFund: POST /funds/{id}/invest")
    void investInFund_happyPath() throws Exception {
        TsFundPosition stub = new TsFundPosition(3L,
                new BigDecimal("1000"), new BigDecimal("1050"));
        mockServer.expect(requestTo(BASE_URL + "/funds/1/invest"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CALLER_JWT))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsFundPosition resp = client.investInFund(1L,
                new InvestFundReq(new BigDecimal("1000"), "RSD", 4L));

        assertThat(resp.id()).isEqualTo(3L);
        assertThat(resp.totalInvested()).isEqualByComparingTo("1000");
        mockServer.verify();
    }

    @Test
    @DisplayName("listFunds: GET /funds → lista TsFundSummary")
    void listFunds_returnsList() throws Exception {
        TsFundSummary[] stub = {
                new TsFundSummary(1L, "Stable Income", new BigDecimal("10000"))
        };
        mockServer.expect(requestTo(BASE_URL + "/funds"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        List<TsFundSummary> funds = client.listFunds();

        assertThat(funds).hasSize(1);
        assertThat(funds.get(0).name()).isEqualTo("Stable Income");
        mockServer.verify();
    }

    @Test
    @DisplayName("runTaxCalculation: POST /tax/calculate (bez tela)")
    void runTaxCalculation_happyPath() {
        mockServer.expect(requestTo(BASE_URL + "/tax/calculate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CALLER_JWT))
                .andRespond(withSuccess());

        client.runTaxCalculation();

        mockServer.verify();
    }

    @Test
    @DisplayName("updateActuaryLimit: PATCH /actuaries/{id}/limit")
    void updateActuaryLimit_happyPath() throws Exception {
        TsActuaryInfo stub = new TsActuaryInfo(99L,
                new BigDecimal("200000"), BigDecimal.ZERO, true);
        mockServer.expect(requestTo(BASE_URL + "/actuaries/99/limit"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsActuaryInfo resp = client.updateActuaryLimit(99L,
                new UpdateActuaryLimitReq(new BigDecimal("200000"), true));

        assertThat(resp.dailyLimit()).isEqualByComparingTo("200000");
        assertThat(resp.needApproval()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("setExchangeTestMode: PATCH /exchanges/{acronym}/test-mode")
    void setExchangeTestMode_happyPath() {
        mockServer.expect(requestTo(BASE_URL + "/exchanges/NYSE/test-mode"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("{\"message\":\"ok\"}", MediaType.APPLICATION_JSON));

        client.setExchangeTestMode("NYSE", true);

        mockServer.verify();
    }

    @Test
    @DisplayName("setPublicQuantity: PATCH /portfolio/{id}/public")
    void setPublicQuantity_happyPath() {
        mockServer.expect(requestTo(BASE_URL + "/portfolio/3/public"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.setPublicQuantity(3L, 5);

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP greska → TradingServiceClientException sa porukom iz tela (message polje)")
    void httpError_throwsWithMessageFromBody() {
        mockServer.expect(requestTo(BASE_URL + "/orders"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Nedovoljno sredstava na racunu\"}"));

        assertThatThrownBy(() -> client.createOrder(new CreateOrderReq(
                7L, "MARKET", 10, 1, "BUY", null, null, false, false, 5L, "123456", null)))
                .isInstanceOf(TradingServiceClientException.class)
                .hasMessageContaining("Nedovoljno sredstava")
                .satisfies(e -> assertThat(((TradingServiceClientException) e).getHttpStatus())
                        .isEqualTo(400));
        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 403 → TradingServiceClientException sa porukom iz error polja")
    void httpForbidden_throwsWithErrorField() {
        mockServer.expect(requestTo(BASE_URL + "/tax/calculate"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Nemate dozvolu za ovu akciju\"}"));

        assertThatThrownBy(() -> client.runTaxCalculation())
                .isInstanceOf(TradingServiceClientException.class)
                .hasMessageContaining("Nemate dozvolu")
                .satisfies(e -> assertThat(((TradingServiceClientException) e).getHttpStatus())
                        .isEqualTo(403));
        mockServer.verify();
    }

    @Test
    @DisplayName("Bez tokena u CallerTokenHolder → zahtev ide bez Authorization zaglavlja")
    void noToken_omitsAuthorizationHeader() throws Exception {
        CallerTokenHolder.clear();
        TsOrder stub = new TsOrder(1L, 7L, "AAPL", "MARKET", 1, "BUY",
                "PENDING", null, false, 1, null);
        mockServer.expect(requestTo(BASE_URL + "/orders/1/approve"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(request -> assertThat(request.getHeaders().getFirst("Authorization"))
                        .as("Authorization zaglavlje ne sme biti poslato bez tokena")
                        .isNull())
                .andRespond(withSuccess(objectMapper.writeValueAsString(stub),
                        MediaType.APPLICATION_JSON));

        TsOrder resp = client.approveOrder(1L);

        assertThat(resp.id()).isEqualTo(1L);
        mockServer.verify();
    }
}
