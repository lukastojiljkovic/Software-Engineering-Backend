package rs.raf.banka2_bek.internalapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2.contracts.internal.FxRateDto;
import rs.raf.banka2_bek.TestObjectMapperConfig;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(TestObjectMapperConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalFxControllerIntegrationTest {

    @Value("${internal.api-key}")
    private String internalKey;

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @MockitoBean
    private ExchangeService exchangeService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final List<ExchangeRateDto> STUB_RATES = List.of(
            new ExchangeRateDto("RSD", 1.0),
            new ExchangeRateDto("EUR", 0.0085),
            new ExchangeRateDto("USD", 0.0091)
    );

    @BeforeEach
    void setUp() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        when(exchangeService.getAllRates()).thenReturn(STUB_RATES);
    }

    // ─── Happy path: valid X-Internal-Key returns 200 + rate list ────────────

    @Test
    void getRates_withInternalKey_returns200AndRates() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/fx/rates"),
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(resp.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(3);

        boolean foundRsd = false;
        for (JsonNode node : body) {
            if ("RSD".equals(node.path("currency").asText())) {
                assertThat(node.path("rate").asDouble()).isEqualTo(1.0);
                foundRsd = true;
            }
        }
        assertThat(foundRsd).as("RSD rate must be present with rate=1.0").isTrue();
    }

    // ─── Missing X-Internal-Key → 401 ────────────────────────────────────────

    @Test
    void getRates_missingInternalKey_returns401() {
        HttpHeaders headers = new HttpHeaders();
        // No X-Internal-Key header — InternalAuthFilter must reject with 401

        ResponseEntity<String> resp = restTemplate.exchange(
                url("/internal/fx/rates"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Verify delegation to ExchangeService ────────────────────────────────

    @Test
    void getRates_delegatesToExchangeService() throws Exception {
        restTemplate.exchange(
                url("/internal/fx/rates"),
                HttpMethod.GET,
                new HttpEntity<>(internalHeaders()),
                String.class);

        verify(exchangeService).getAllRates();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", internalKey);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
