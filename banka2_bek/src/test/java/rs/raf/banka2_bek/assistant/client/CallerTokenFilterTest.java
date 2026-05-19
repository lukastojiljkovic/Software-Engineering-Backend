package rs.raf.banka2_bek.assistant.client;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testovi za {@link CallerTokenFilter} + {@link CallerTokenHolder} —
 * mehanizam koji nosi JWT pozivaoca kroz Arbitro zahtev (faza 2f).
 */
class CallerTokenFilterTest {

    private final CallerTokenFilter filter = new CallerTokenFilter();

    @AfterEach
    void tearDown() {
        CallerTokenHolder.clear();
    }

    @Test
    @DisplayName("/assistant/** zahtev sa Bearer tokenom → token vidljiv u holder-u tokom lanca, obrisan posle")
    void assistantRequest_setsTokenForChain_clearsAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/assistant/actions/abc/confirm");
        request.addHeader("Authorization", "Bearer caller-jwt-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInChain.set(CallerTokenHolder.get());

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).isEqualTo("caller-jwt-123");
        // Posle lanca holder mora biti ociscen (recikliranje niti).
        assertThat(CallerTokenHolder.get()).isNull();
    }

    @Test
    @DisplayName("/assistant/** zahtev bez Bearer tokena → holder ostaje prazan")
    void assistantRequest_noBearer_holderEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assistant/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seenInChain = new AtomicReference<>("sentinel");
        FilterChain chain = (req, res) -> seenInChain.set(CallerTokenHolder.get());

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).isNull();
        assertThat(CallerTokenHolder.get()).isNull();
    }

    @Test
    @DisplayName("Ne-Arbitro ruta → shouldNotFilter preskace filter")
    void nonAssistantRoute_isSkipped() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts/my");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        MockHttpServletRequest assistantReq = new MockHttpServletRequest("POST", "/assistant/chat");
        assertThat(filter.shouldNotFilter(assistantReq)).isFalse();
    }

    @Test
    @DisplayName("Token se brise i kad lanac baci izuzetak")
    void tokenCleared_evenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/assistant/chat");
        request.addHeader("Authorization", "Bearer jwt-x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain throwingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        try {
            filter.doFilter(request, response, throwingChain);
        } catch (Exception ignored) {
            // ocekivano
        }
        assertThat(CallerTokenHolder.get()).isNull();
    }

    @Test
    @DisplayName("CallerTokenHolder: set blank/null tretira kao remove")
    void holder_setBlankRemoves() {
        CallerTokenHolder.set("x");
        assertThat(CallerTokenHolder.get()).isEqualTo("x");
        CallerTokenHolder.set("   ");
        assertThat(CallerTokenHolder.get()).isNull();
        CallerTokenHolder.set("y");
        CallerTokenHolder.set(null);
        assertThat(CallerTokenHolder.get()).isNull();
    }
}
