package rs.raf.banka2_bek.internalapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Stiti /internal/** rute deljenim X-Internal-Key tajnim kljucem. */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private final String apiKey;

    public InternalAuthFilter(@Value("${internal.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader("X-Internal-Key");
        if (!keyMatches(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Nevazeci X-Internal-Key\"}");
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    /**
     * Konstantno-vremensko poredjenje prosledjenog X-Internal-Key sa konfigurisanim
     * kljucem ({@link MessageDigest#isEqual} ne kratko-spaja na prvoj razlici, pa
     * ne curi duzinu/prefiks kljuca kroz timing). Null/blank prosledjeni ili
     * nekonfigurisan kljuc se odbijaju pre poredjenja.
     */
    private boolean keyMatches(String provided) {
        if (provided == null || apiKey == null || apiKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8));
    }
}
