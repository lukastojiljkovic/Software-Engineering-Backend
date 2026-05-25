package rs.raf.banka2_bek.internalapi;

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
import rs.raf.banka2_bek.internalapi.repository.InternalRequestRepository;
import rs.raf.banka2_bek.notification.model.Notification;
import rs.raf.banka2_bek.notification.repository.NotificationRepository;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracioni testovi za {@code POST /internal/notifications} cross-DB ulaza
 * iz trading-service-a (i drugih mikroservisa) u banka-core
 * {@code notifications} tabelu.
 */
@Import(TestObjectMapperConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalNotificationsControllerIntegrationTest {

    @Value("${internal.api-key}")
    private String internalKey;

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private InternalRequestRepository internalRequestRepository;
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

    @Test
    void postNotification_withoutInternalKey_returns401() {
        String body = """
                {
                  "recipientId": 7,
                  "recipientType": "CLIENT",
                  "type": "ORDER_EXECUTED",
                  "title": "Order izvrsen",
                  "message": "Vas BUY order je popunjen"
                }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // bez X-Internal-Key

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/internal/notifications"),
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void postNotification_happyPath_returns201AndPersistsNotification() {
        String body = """
                {
                  "recipientId": 7,
                  "recipientType": "CLIENT",
                  "type": "ORDER_EXECUTED",
                  "title": "Order izvrsen",
                  "message": "Vas BUY order je popunjen",
                  "referenceType": "ORDER",
                  "referenceId": 42,
                  "idempotencyKey": "test-notif-001"
                }
                """;

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/internal/notifications"),
                new HttpEntity<>(body, internalHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        Notification saved = all.get(0);
        assertThat(saved.getRecipientId()).isEqualTo(7L);
        assertThat(saved.getRecipientType()).isEqualTo("CLIENT");
        assertThat(saved.getNotificationType().name()).isEqualTo("ORDER_EXECUTED");
        assertThat(saved.getTitle()).isEqualTo("Order izvrsen");
        assertThat(saved.getBody()).isEqualTo("Vas BUY order je popunjen");
        assertThat(saved.getReferenceType()).isEqualTo("ORDER");
        assertThat(saved.getReferenceId()).isEqualTo(42L);
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();

        // Idempotency record persisted
        assertThat(internalRequestRepository.findByIdempotencyKey("test-notif-001")).isPresent();
    }

    @Test
    void postNotification_repeatedIdempotencyKey_persistsOnlyOnce() {
        String body = """
                {
                  "recipientId": 7,
                  "recipientType": "CLIENT",
                  "type": "ORDER_EXECUTED",
                  "title": "Order izvrsen",
                  "message": "Body",
                  "idempotencyKey": "test-notif-idem-001"
                }
                """;

        // Prvi POST → 201
        ResponseEntity<String> first = restTemplate.postForEntity(
                url("/internal/notifications"),
                new HttpEntity<>(body, internalHeaders()),
                String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Drugi POST sa istim kljucem → 200 OK (idempotent replay), bez novog reda
        ResponseEntity<String> second = restTemplate.postForEntity(
                url("/internal/notifications"),
                new HttpEntity<>(body, internalHeaders()),
                String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void postNotification_unknownType_fallsBackToGeneral() {
        String body = """
                {
                  "recipientId": 11,
                  "recipientType": "EMPLOYEE",
                  "type": "THIS_TYPE_DOES_NOT_EXIST_IN_BANKACORE",
                  "title": "Test",
                  "message": "Body",
                  "idempotencyKey": "test-notif-unknown-001"
                }
                """;

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/internal/notifications"),
                new HttpEntity<>(body, internalHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getNotificationType().name()).isEqualTo("GENERAL");
    }

    @Test
    void postNotification_missingRecipient_returns400() {
        String body = """
                {
                  "recipientType": "CLIENT",
                  "type": "ORDER_EXECUTED",
                  "title": "Test",
                  "message": "Body"
                }
                """;

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/internal/notifications"),
                new HttpEntity<>(body, internalHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void postNotification_withoutIdempotencyKey_stillPersists() {
        // Kad idempotencyKey nije prosledjen, idempotency provera se preskace —
        // svaki poziv pravi nov red. Verifikujemo da to radi.
        String body = """
                {
                  "recipientId": 5,
                  "recipientType": "CLIENT",
                  "type": "OTC_ACCEPTED",
                  "title": "OTC prihvacen",
                  "message": "Body"
                }
                """;

        ResponseEntity<String> resp = restTemplate.postForEntity(
                url("/internal/notifications"),
                new HttpEntity<>(body, internalHeaders()),
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Key", internalKey);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
