package rs.raf.banka2_bek.assistant.client;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter koji za {@code /assistant/**} rute kopira sirov JWT iz
 * {@code Authorization} zaglavlja u {@link CallerTokenHolder} (faza 2f).
 *
 * <p>Posle 2f cutover-a Arbitro write handler-i zovu {@code trading-service}
 * preko {@link TradingServiceClient}, koji prosledjuje token pozivaoca. Ovaj
 * filter postavlja token na niti zahteva — to direktno pokriva agentic confirm
 * endpoint ({@code POST /assistant/actions/{id}/confirm}) gde handler-ov
 * {@code executeFinal} trci sinhrono na niti zahteva. Za chat tok
 * ({@code buildPreview}, read tool-ovi) token se dalje propagira na
 * {@code assistantTaskExecutor} pool nit kroz {@code TaskDecorator}.
 *
 * <p>Token se uvek brise u {@code finally} — recikliranje niti ne sme da
 * iznese token van zahteva.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} — ovaj filter samo cita zaglavlje, ne
 * mora da trci pre Spring Security filtera; redosled je nebitan za korektnost.
 */
@Component
@Order(Integer.MAX_VALUE)
public class CallerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        boolean set = false;
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            CallerTokenHolder.set(authHeader.substring(BEARER_PREFIX.length()));
            set = true;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (set) {
                CallerTokenHolder.clear();
            }
        }
    }

    /**
     * Filter radi samo za Arbitro rute — ostatak aplikacije ne treba caller
     * token holder. SSE ({@code /assistant/chat}) i confirm endpoint su pod
     * {@code /assistant/**}.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.contains("/assistant/");
    }
}
