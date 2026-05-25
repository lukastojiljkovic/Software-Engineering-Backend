package rs.raf.trading.investmentfund.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.trading.common.UserContext;
import rs.raf.trading.common.UserRole;
import rs.raf.trading.investmentfund.controller.exception_handler.InvestmentFundExceptionHandler;
import rs.raf.trading.investmentfund.dto.InvestmentFundDtos.InvestmentFundDetailDto;
import rs.raf.trading.investmentfund.service.FundDividendService;
import rs.raf.trading.investmentfund.service.FundStatisticsService;
import rs.raf.trading.investmentfund.service.InvestmentFundService;
import rs.raf.trading.security.TradingUserResolver;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TODO_final C4 #14 / Sc 70: MockMvc testovi za
 * {@code PATCH /funds/{id}/dividend-policy} endpoint.
 *
 * <p>Pokriva admin success, supervisor success, ne-autorizovan (service baca
 * AccessDeniedException -> 403), missing fund (404), invalid body (400).
 *
 * <p>NAPOMENA: controller koristi {@link SecurityContextHolder} da otkrije ADMIN
 * authority pa pozove servis sa odgovarajucim {@code isAdminActor} flag-om. Test
 * setuje pravu Authentication u SecurityContext (per-test) i cisti je na kraju.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentFundControllerDividendPolicyTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private InvestmentFundService investmentFundService;
    @Mock private FundStatisticsService fundStatisticsService;
    @Mock private FundDividendService fundDividendService;
    @Mock private TradingUserResolver userResolver;

    @InjectMocks
    private InvestmentFundController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new InvestmentFundExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(String... authorities) {
        var auths = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", "n/a", auths));
    }

    private InvestmentFundDetailDto sampleFundDto(boolean reinvest) {
        return new InvestmentFundDetailDto(
                101L, "Alpha Growth", "desc",
                "Marko Petrovic", 10L,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000"), 5001L, "222000100000050010",
                Collections.emptyList(), Collections.emptyList(),
                java.time.LocalDate.of(2025, 1, 1),
                reinvest);
    }

    @Test
    @DisplayName("PATCH /funds/{id}/dividend-policy: admin moze prebaciti bilo koji fond")
    void updateDividendPolicy_adminAuthority_succeeds() throws Exception {
        setAuthentication("ROLE_ADMIN");
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(999L, UserRole.EMPLOYEE));
        when(investmentFundService.updateDividendPolicy(eq(101L), eq(true), eq(999L), eq(true)))
                .thenReturn(sampleFundDto(true));

        mockMvc.perform(patch("/funds/{id}/dividend-policy", 101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reinvest", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reinvestDividends").value(true))
                .andExpect(jsonPath("$.id").value(101));

        verify(investmentFundService).updateDividendPolicy(101L, true, 999L, true);
    }

    @Test
    @DisplayName("PATCH /funds/{id}/dividend-policy: supervizor-manager fonda moze prebaciti")
    void updateDividendPolicy_supervisorAuthority_succeeds() throws Exception {
        setAuthentication("SUPERVISOR");
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(10L, UserRole.EMPLOYEE));
        when(investmentFundService.updateDividendPolicy(eq(101L), eq(false), eq(10L), eq(false)))
                .thenReturn(sampleFundDto(false));

        mockMvc.perform(patch("/funds/{id}/dividend-policy", 101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reinvest", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reinvestDividends").value(false));

        verify(investmentFundService).updateDividendPolicy(101L, false, 10L, false);
    }

    @Test
    @DisplayName("PATCH /funds/{id}/dividend-policy: non-manager supervizor -> 403 (servis baca AccessDenied)")
    void updateDividendPolicy_nonManagerSupervisor_returnsForbidden() throws Exception {
        setAuthentication("SUPERVISOR");
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(22L, UserRole.EMPLOYEE));
        when(investmentFundService.updateDividendPolicy(eq(101L), eq(true), eq(22L), eq(false)))
                .thenThrow(new AccessDeniedException("Samo admin ili menadzer fonda moze menjati politiku dividendi."));

        mockMvc.perform(patch("/funds/{id}/dividend-policy", 101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reinvest", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("politiku dividendi")));
    }

    @Test
    @DisplayName("PATCH /funds/{id}/dividend-policy: missing reinvest field -> 400")
    void updateDividendPolicy_missingReinvestField_returnsBadRequest() throws Exception {
        setAuthentication("ROLE_ADMIN");
        // No stubbing of userResolver/service — validacija fail-uje pre nego sto controller pozove servis.

        mockMvc.perform(patch("/funds/{id}/dividend-policy", 101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /funds/{id}/dividend-policy: nepostojeci fund -> 404")
    void updateDividendPolicy_fundNotFound_returnsNotFound() throws Exception {
        setAuthentication("ROLE_ADMIN");
        when(userResolver.resolveCurrent()).thenReturn(new UserContext(999L, UserRole.EMPLOYEE));
        when(investmentFundService.updateDividendPolicy(eq(404L), anyBoolean(), eq(999L), eq(true)))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Investment fund #404 not found"));

        mockMvc.perform(patch("/funds/{id}/dividend-policy", 404L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reinvest", true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("404")));
    }
}
