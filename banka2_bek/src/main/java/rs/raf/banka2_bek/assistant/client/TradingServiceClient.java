package rs.raf.banka2_bek.assistant.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CounterOtcOfferReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CreateFundReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CreateOrderReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.CreateOtcOfferReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.InvestFundReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsActuaryInfo;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsError;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundDetail;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundPosition;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundSummary;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsFundTransaction;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsListing;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOrder;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOtcContract;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.TsOtcOffer;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.UpdateActuaryLimitReq;
import rs.raf.banka2_bek.assistant.client.TradingServiceDtos.WithdrawFundReq;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP klijent ka {@code trading-service} PUBLIC API-ju za Arbitro (faza 2f).
 *
 * <p>Posle 2f cutover-a trgovinski domen ({@code order}/{@code otc}/
 * {@code investmentfund}/{@code tax}/{@code actuary}/{@code berza}/
 * {@code portfolio}/{@code stock}) zivi u {@code trading-service}. Arbitro
 * agentic write handler-i i read tool-ovi vise ne zovu trgovinske servise
 * in-process — zovu javne kontrolere {@code trading-service}-a preko ovog
 * klijenta.
 *
 * <p><b>JWT pass-through:</b> svaki poziv u {@code Authorization} zaglavlje
 * stavlja sirov bearer token pozivaoca iz {@link CallerTokenHolder}, tako da
 * {@code trading-service} autentifikuje i autorizuje istog korisnika (klijent /
 * agent / supervizor / admin) — sve provere pristupa, OTP verifikacija i
 * ownership ostaju netaknute, samo se transport menja sa in-process poziva na
 * HTTP. Razlika u odnosu na {@code TradingServiceInternalClient} (interni
 * {@code /internal/portfolio/**} seam): tamo se salje {@code X-Internal-Key},
 * ovde JWT pozivaoca jer su PUBLIC kontroleri pod JWT security-jem.
 *
 * <p>HTTP greska trading-service-a se mapira u {@link TradingServiceClientException}
 * sa porukom iz tela odgovora — Arbitro je prikazuje kao {@code tool_result}
 * gresku, identicno kao raniji {@code RuntimeException} iz in-process servisa.
 */
@Component
public class TradingServiceClient {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public TradingServiceClient(RestClient tradingServicePublicRestClient,
                                ObjectMapper objectMapper) {
        this.client = tradingServicePublicRestClient;
        this.objectMapper = objectMapper;
    }

    /* ════════════════════════ Orders — write ════════════════════════ */

    /** {@code POST /orders} — kreira BUY/SELL order. OTP je u {@code req.otpCode()}. */
    public TsOrder createOrder(CreateOrderReq req) {
        return post("/orders", req, TsOrder.class);
    }

    /**
     * {@code PATCH /orders/{id}/decline} — pun decline ({@code quantityToCancel}
     * null) ili parcijalni cancel ({@code quantityToCancel != null}).
     */
    public TsOrder cancelOrder(Long orderId, Integer quantityToCancel) {
        return client.patch()
                .uri(uri -> {
                    var b = uri.path("/orders/{id}/decline");
                    if (quantityToCancel != null) {
                        b.queryParam("quantity", quantityToCancel);
                    }
                    return b.build(orderId);
                })
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("PATCH /orders/" + orderId + "/decline"))
                .body(TsOrder.class);
    }

    /** {@code PATCH /orders/{id}/approve} — supervizor odobrava order. */
    public TsOrder approveOrder(Long orderId) {
        return patchNoBody("/orders/{id}/approve", orderId, TsOrder.class);
    }

    /** {@code PATCH /orders/{id}/decline} (bez quantity) — supervizor odbija order. */
    public TsOrder declineOrder(Long orderId) {
        return patchNoBody("/orders/{id}/decline", orderId, TsOrder.class);
    }

    /* ════════════════════════ Orders — read ════════════════════════ */

    /**
     * {@code GET /orders/my} — poslednjih {@code limit} ordera pozivaoca
     * (trading-service filtrira po JWT korisniku). Vraca {@code content} stranice.
     */
    public List<TsOrder> recentOrders(int limit) {
        Page<TsOrder> page = client.get()
                .uri(uri -> uri.path("/orders/my")
                        .queryParam("page", 0)
                        .queryParam("size", limit)
                        .build())
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("GET /orders/my"))
                .body(new ParameterizedTypeReference<Page<TsOrder>>() { });
        return page == null || page.content() == null ? List.of() : page.content();
    }

    /* ════════════════════════ Listings — read ════════════════════════ */

    /**
     * {@code GET /listings/{id}} — listing po internom ID-u. {@code Optional.empty()}
     * ako trading-service vrati 404. Klijentska FOREX restrikcija je na strani
     * trading-service-a — propagira se kao izuzetak.
     */
    public Optional<TsListing> findListingById(Long id) {
        try {
            TsListing dto = client.get()
                    .uri("/listings/{id}", id)
                    .headers(this::authHeader)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this.errorHandler("GET /listings/" + id))
                    .body(TsListing.class);
            return Optional.ofNullable(dto);
        } catch (TradingServiceClientException e) {
            if (e.getHttpStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Listing po ticker-u — {@code GET /listings?search=<ticker>} pa egzaktan
     * (case-insensitive) match po ticker-u. {@code Optional.empty()} ako nema
     * tacnog poklapanja.
     */
    public Optional<TsListing> findListingByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Optional.empty();
        }
        String norm = ticker.trim();
        for (String type : List.of("STOCK", "FUTURES", "FOREX")) {
            List<TsListing> matches = searchListings(type, norm);
            for (TsListing l : matches) {
                if (l.ticker() != null && l.ticker().equalsIgnoreCase(norm)) {
                    return Optional.of(l);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * {@code GET /listings?type=&search=} — stranica hartija. Koristi se za
     * top-listings (slot resolver) i ticker lookup.
     *
     * @param type   STOCK / FUTURES / FOREX; {@code null} → STOCK (default ruta)
     * @param search opcioni pojam (ticker ili naziv); {@code null} = bez filtera
     */
    public List<TsListing> searchListings(String type, String search) {
        Page<TsListing> page = client.get()
                .uri(uri -> {
                    var b = uri.path("/listings");
                    if (type != null && !type.isBlank()) {
                        b.queryParam("type", type);
                    }
                    if (search != null && !search.isBlank()) {
                        b.queryParam("search", search);
                    }
                    b.queryParam("page", 0).queryParam("size", 50);
                    return b.build();
                })
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("GET /listings"))
                .body(new ParameterizedTypeReference<Page<TsListing>>() { });
        return page == null || page.content() == null ? List.of() : page.content();
    }

    /* ════════════════════════ OTC — write ════════════════════════ */

    /** {@code POST /otc/offers} — kreira OTC ponudu. */
    public TsOtcOffer createOtcOffer(CreateOtcOfferReq req) {
        return post("/otc/offers", req, TsOtcOffer.class);
    }

    /** {@code POST /otc/offers/{id}/counter} — salje kontraponudu. */
    public TsOtcOffer counterOtcOffer(Long offerId, CounterOtcOfferReq req) {
        return client.post()
                .uri("/otc/offers/{id}/counter", offerId)
                .headers(this::authHeader)
                .body(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST /otc/offers/" + offerId + "/counter"))
                .body(TsOtcOffer.class);
    }

    /** {@code POST /otc/offers/{id}/decline} — odustaje od OTC pregovora. */
    public TsOtcOffer declineOtcOffer(Long offerId) {
        return postNoBody("/otc/offers/{id}/decline", offerId, TsOtcOffer.class);
    }

    /**
     * {@code POST /otc/offers/{id}/accept} — kupac prihvata ponudu.
     * {@code buyerAccountId} opciono ({@code null} → trading-service auto-resolve).
     */
    public TsOtcOffer acceptOtcOffer(Long offerId, Long buyerAccountId) {
        return client.post()
                .uri(uri -> {
                    var b = uri.path("/otc/offers/{id}/accept");
                    if (buyerAccountId != null) {
                        b.queryParam("buyerAccountId", buyerAccountId);
                    }
                    return b.build(offerId);
                })
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST /otc/offers/" + offerId + "/accept"))
                .body(TsOtcOffer.class);
    }

    /** {@code POST /otc/contracts/{id}/exercise} — kupac iskoriscava ugovor. */
    public TsOtcContract exerciseOtcContract(Long contractId, Long buyerAccountId) {
        return client.post()
                .uri(uri -> {
                    var b = uri.path("/otc/contracts/{id}/exercise");
                    if (buyerAccountId != null) {
                        b.queryParam("buyerAccountId", buyerAccountId);
                    }
                    return b.build(contractId);
                })
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST /otc/contracts/" + contractId + "/exercise"))
                .body(TsOtcContract.class);
    }

    /* ════════════════════════ OTC — read ════════════════════════ */

    /** {@code GET /otc/offers/active} — aktivne OTC ponude pozivaoca. */
    public List<TsOtcOffer> myActiveOtcOffers() {
        TsOtcOffer[] arr = client.get()
                .uri("/otc/offers/active")
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("GET /otc/offers/active"))
                .body(TsOtcOffer[].class);
        return arr == null ? List.of() : List.of(arr);
    }

    /** {@code GET /otc/contracts?status=ACTIVE} — aktivni OTC ugovori pozivaoca. */
    public List<TsOtcContract> myActiveOtcContracts() {
        TsOtcContract[] arr = client.get()
                .uri(uri -> uri.path("/otc/contracts").queryParam("status", "ACTIVE").build())
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("GET /otc/contracts"))
                .body(TsOtcContract[].class);
        return arr == null ? List.of() : List.of(arr);
    }

    /* ════════════════════════ Investicioni fondovi ════════════════════════ */

    /** {@code POST /funds} — supervizor kreira fond. */
    public TsFundDetail createFund(CreateFundReq req) {
        return post("/funds", req, TsFundDetail.class);
    }

    /** {@code POST /funds/{id}/invest} — uplata u fond. */
    public TsFundPosition investInFund(Long fundId, InvestFundReq req) {
        return client.post()
                .uri("/funds/{id}/invest", fundId)
                .headers(this::authHeader)
                .body(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST /funds/" + fundId + "/invest"))
                .body(TsFundPosition.class);
    }

    /** {@code POST /funds/{id}/withdraw} — povlacenje iz fonda. */
    public TsFundTransaction withdrawFromFund(Long fundId, WithdrawFundReq req) {
        return client.post()
                .uri("/funds/{id}/withdraw", fundId)
                .headers(this::authHeader)
                .body(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST /funds/" + fundId + "/withdraw"))
                .body(TsFundTransaction.class);
    }

    /** {@code GET /funds} — lista investicionih fondova (Discovery). */
    public List<TsFundSummary> listFunds() {
        TsFundSummary[] arr = client.get()
                .uri("/funds")
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("GET /funds"))
                .body(TsFundSummary[].class);
        return arr == null ? List.of() : List.of(arr);
    }

    /* ════════════════════════ Tax ════════════════════════ */

    /** {@code POST /tax/calculate} — supervizor pokrece obracun poreza za sve. */
    public void runTaxCalculation() {
        client.post()
                .uri("/tax/calculate")
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST /tax/calculate"))
                .toBodilessEntity();
    }

    /* ════════════════════════ Aktuari ════════════════════════ */

    /** {@code PATCH /actuaries/{id}/limit} — supervizor postavlja limit agenta. */
    public TsActuaryInfo updateActuaryLimit(Long employeeId, UpdateActuaryLimitReq req) {
        return client.patch()
                .uri("/actuaries/{id}/limit", employeeId)
                .headers(this::authHeader)
                .body(req)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("PATCH /actuaries/" + employeeId + "/limit"))
                .body(TsActuaryInfo.class);
    }

    /** {@code PATCH /actuaries/{id}/reset-limit} — supervizor resetuje usedLimit. */
    public TsActuaryInfo resetActuaryUsedLimit(Long employeeId) {
        return patchNoBody("/actuaries/{id}/reset-limit", employeeId, TsActuaryInfo.class);
    }

    /* ════════════════════════ Berze ════════════════════════ */

    /** {@code PATCH /exchanges/{acronym}/test-mode} — toggle test moda berze. */
    public void setExchangeTestMode(String acronym, boolean enabled) {
        client.patch()
                .uri("/exchanges/{acronym}/test-mode", acronym)
                .headers(this::authHeader)
                .body(Map.of("enabled", enabled))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("PATCH /exchanges/" + acronym + "/test-mode"))
                .toBodilessEntity();
    }

    /* ════════════════════════ Portfolio ════════════════════════ */

    /** {@code PATCH /portfolio/{id}/public} — postavlja javnu kolicinu pozicije. */
    public void setPublicQuantity(Long portfolioId, Integer quantity) {
        client.patch()
                .uri("/portfolio/{id}/public", portfolioId)
                .headers(this::authHeader)
                .body(Map.of("quantity", quantity == null ? 0 : quantity))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("PATCH /portfolio/" + portfolioId + "/public"))
                .toBodilessEntity();
    }

    /* ════════════════════════ Pomocne metode ════════════════════════ */

    /**
     * Dodaje {@code Authorization: Bearer <token>} ako je token pozivaoca
     * dostupan u {@link CallerTokenHolder}. Ako nije, zahtev ide bez zaglavlja
     * i trading-service vraca 401 — Arbitro to prikazuje kao gresku akcije.
     */
    private void authHeader(org.springframework.http.HttpHeaders headers) {
        String token = CallerTokenHolder.get();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return client.post()
                .uri(path)
                .headers(this::authHeader)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST " + path))
                .body(responseType);
    }

    private <T> T postNoBody(String pathTemplate, Object pathVar, Class<T> responseType) {
        return client.post()
                .uri(pathTemplate, pathVar)
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("POST " + pathTemplate))
                .body(responseType);
    }

    private <T> T patchNoBody(String pathTemplate, Object pathVar, Class<T> responseType) {
        return client.patch()
                .uri(pathTemplate, pathVar)
                .headers(this::authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this.errorHandler("PATCH " + pathTemplate))
                .body(responseType);
    }

    /**
     * Vraca {@code onStatus} handler koji baca {@link TradingServiceClientException}
     * sa porukom iz {@link TsError} tela trading-service odgovora.
     */
    private RestClient.ResponseSpec.ErrorHandler errorHandler(String requestDescription) {
        return (request, response) -> {
            throw new TradingServiceClientException(
                    response.getStatusCode().value(),
                    errorMessage(response, requestDescription));
        };
    }

    private String errorMessage(ClientHttpResponse response, String requestDescription) {
        try {
            byte[] raw = response.getBody().readAllBytes();
            if (raw.length > 0) {
                TsError err = objectMapper.readValue(raw, TsError.class);
                if (err != null && err.bestMessage() != null) {
                    return err.bestMessage();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // telo nije TsError JSON — padamo na generic poruku
        }
        try {
            return requestDescription + " → " + response.getStatusCode();
        } catch (IOException e) {
            return requestDescription + " → trading-service error";
        }
    }

    /**
     * Minimalan mirror Spring-ovog {@code Page} JSON oblika — citamo samo
     * {@code content}. Spring serijalizuje {@code Page<X>} kao
     * {@code {"content": [...], "totalElements": N, ...}}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Page<T>(List<T> content) {
    }
}
