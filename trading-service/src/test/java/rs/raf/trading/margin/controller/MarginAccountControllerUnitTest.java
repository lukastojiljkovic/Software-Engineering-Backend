package rs.raf.trading.margin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.trading.common.TradingGlobalExceptionHandler;
import rs.raf.trading.margin.dto.CreateMarginAccountDto;
import rs.raf.trading.margin.dto.MarginAccountDto;
import rs.raf.trading.margin.dto.MarginTransactionDto;
import rs.raf.trading.margin.service.MarginAccountService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit testovi {@link MarginAccountController} — copy-first ekstrakcija (faza 2d-D).
 *
 * <p>NAPOMENA: monolitni kontroler je sam razresavao klijenta preko
 * {@code ClientRepository}; trading-service kontroler delegira identitet
 * {@code MarginAccountService}-u (banka-core seam). Zato vise nema kontroler-nivo
 * testova za "blank email"/"user not client" — to su sad servisni testovi.
 * Greske se mapiraju preko app-wide {@link TradingGlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("MarginAccountController — unit tests")
class MarginAccountControllerUnitTest {

    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken auth;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private MarginAccountService marginAccountService;

    @InjectMocks
    private MarginAccountController marginAccountController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(marginAccountController)
                .setControllerAdvice(new TradingGlobalExceptionHandler())
                .build();
        auth = new UsernamePasswordAuthenticationToken("client@example.com", null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MarginAccountDto sampleDto(Long id) {
        return MarginAccountDto.builder()
                .id(id)
                .accountId(100L)
                .accountNumber("265000000000000001")
                .userId(42L)
                .initialMargin(new BigDecimal("5000.00"))
                .loanValue(new BigDecimal("5000.00"))
                .maintenanceMargin(new BigDecimal("3500.00"))
                .bankParticipation(new BigDecimal("0.50"))
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ========== POST /margin-accounts ==========

    @Test
    @DisplayName("POST /margin-accounts — success returns 200 with dto")
    void create_success() throws Exception {
        when(marginAccountService.createForUser(any(CreateMarginAccountDto.class)))
                .thenReturn(sampleDto(1L));

        CreateMarginAccountDto req = new CreateMarginAccountDto(100L, new BigDecimal("5000.00"));

        mockMvc.perform(post("/margin-accounts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.accountNumber").value("265000000000000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(marginAccountService).createForUser(any(CreateMarginAccountDto.class));
    }

    @Test
    @DisplayName("POST /margin-accounts — service IllegalStateException (caller not client) returns 403")
    void create_callerNotClient() throws Exception {
        when(marginAccountService.createForUser(any()))
                .thenThrow(new IllegalStateException("Only clients can manage margin accounts."));

        CreateMarginAccountDto req = new CreateMarginAccountDto(100L, new BigDecimal("5000.00"));

        mockMvc.perform(post("/margin-accounts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only clients can manage margin accounts."));
    }

    @Test
    @DisplayName("POST /margin-accounts — validation failure returns 400")
    void create_invalidBody() throws Exception {
        // initialDeposit missing — @NotNull triggers MethodArgumentNotValidException
        String json = "{\"accountId\": 100}";

        mockMvc.perform(post("/margin-accounts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /margin-accounts — service throws IllegalArgumentException returns 400")
    void create_serviceBadRequest() throws Exception {
        when(marginAccountService.createForUser(any()))
                .thenThrow(new IllegalArgumentException("Invalid deposit"));

        CreateMarginAccountDto req = new CreateMarginAccountDto(100L, new BigDecimal("5000.00"));

        mockMvc.perform(post("/margin-accounts")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid deposit"));
    }

    // ========== GET /margin-accounts/my ==========

    @Test
    @DisplayName("GET /margin-accounts/my — returns list")
    void getMyMarginAccounts_success() throws Exception {
        when(marginAccountService.getMyMarginAccounts())
                .thenReturn(List.of(sampleDto(1L), sampleDto(2L)));

        mockMvc.perform(get("/margin-accounts/my").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @DisplayName("GET /margin-accounts/my — empty list")
    void getMyMarginAccounts_empty() throws Exception {
        when(marginAccountService.getMyMarginAccounts()).thenReturn(List.of());

        mockMvc.perform(get("/margin-accounts/my").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /margin-accounts/my — service throws IllegalStateException returns 403")
    void getMyMarginAccounts_forbidden() throws Exception {
        when(marginAccountService.getMyMarginAccounts())
                .thenThrow(new IllegalStateException("Only clients can manage margin accounts."));

        mockMvc.perform(get("/margin-accounts/my").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only clients can manage margin accounts."));
    }

    // ========== GET /margin-accounts/{id} ==========

    @Test
    @DisplayName("GET /margin-accounts/{id} — found returns dto")
    void getById_found() throws Exception {
        when(marginAccountService.getMyMarginAccounts())
                .thenReturn(List.of(sampleDto(1L), sampleDto(2L)));

        mockMvc.perform(get("/margin-accounts/2").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    @DisplayName("GET /margin-accounts/{id} — not owned returns 404")
    void getById_notFound() throws Exception {
        when(marginAccountService.getMyMarginAccounts())
                .thenReturn(List.of(sampleDto(1L)));

        mockMvc.perform(get("/margin-accounts/999").principal(auth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /margin-accounts/{id} — empty list returns 404")
    void getById_emptyList() throws Exception {
        when(marginAccountService.getMyMarginAccounts()).thenReturn(List.of());

        mockMvc.perform(get("/margin-accounts/1").principal(auth))
                .andExpect(status().isNotFound());
    }

    // ========== POST /margin-accounts/{id}/deposit ==========

    @Test
    @DisplayName("POST /margin-accounts/{id}/deposit — success")
    void deposit_success() throws Exception {
        doNothing().when(marginAccountService)
                .deposit(eq(1L), eq(new BigDecimal("5000.00")));

        mockMvc.perform(post("/margin-accounts/1/deposit")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deposit successful"));

        verify(marginAccountService).deposit(eq(1L), eq(new BigDecimal("5000.00")));
    }

    @Test
    @DisplayName("POST /margin-accounts/{id}/deposit — IllegalArgumentException returns 400")
    void deposit_invalidAmount() throws Exception {
        doThrow(new IllegalArgumentException("Amount must be greater than zero."))
                .when(marginAccountService).deposit(anyLong(), any());

        mockMvc.perform(post("/margin-accounts/1/deposit")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Amount must be greater than zero."));
    }

    @Test
    @DisplayName("POST /margin-accounts/{id}/deposit — IllegalStateException returns 403")
    void deposit_notOwner() throws Exception {
        doThrow(new IllegalStateException("Not account owner."))
                .when(marginAccountService).deposit(anyLong(), any());

        mockMvc.perform(post("/margin-accounts/1/deposit")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not account owner."));
    }

    @Test
    @DisplayName("POST /margin-accounts/{id}/deposit — EntityNotFoundException returns 404")
    void deposit_marginAccountNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Account with id: 99 not found."))
                .when(marginAccountService).deposit(anyLong(), any());

        mockMvc.perform(post("/margin-accounts/99/deposit")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account with id: 99 not found."));
    }

    // ========== POST /margin-accounts/{id}/withdraw ==========

    @Test
    @DisplayName("POST /margin-accounts/{id}/withdraw — success")
    void withdraw_success() throws Exception {
        doNothing().when(marginAccountService)
                .withdraw(eq(1L), eq(new BigDecimal("2000.00")));

        mockMvc.perform(post("/margin-accounts/1/withdraw")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 2000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Withdrawal successful"));
    }

    @Test
    @DisplayName("POST /margin-accounts/{id}/withdraw — account blocked returns 403")
    void withdraw_blocked() throws Exception {
        doThrow(new IllegalStateException("Margin account is blocked."))
                .when(marginAccountService).withdraw(anyLong(), any());

        mockMvc.perform(post("/margin-accounts/1/withdraw")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 2000.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Margin account is blocked."));
    }

    @Test
    @DisplayName("POST /margin-accounts/{id}/withdraw — below maintenance returns 400")
    void withdraw_belowMaintenance() throws Exception {
        doThrow(new IllegalArgumentException("Withdrawal would drop below maintenance margin."))
                .when(marginAccountService).withdraw(anyLong(), any());

        mockMvc.perform(post("/margin-accounts/1/withdraw")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 99999.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Withdrawal would drop below maintenance margin."));
    }

    // ========== GET /margin-accounts/{id}/transactions ==========

    @Test
    @DisplayName("GET /margin-accounts/{id}/transactions — returns list")
    void getTransactions_success() throws Exception {
        MarginTransactionDto tx = new MarginTransactionDto();
        when(marginAccountService.getTransactions(eq(1L)))
                .thenReturn(List.of(tx));

        mockMvc.perform(get("/margin-accounts/1/transactions").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /margin-accounts/{id}/transactions — not owner returns 403")
    void getTransactions_notOwner() throws Exception {
        when(marginAccountService.getTransactions(anyLong()))
                .thenThrow(new IllegalStateException("Not owner."));

        mockMvc.perform(get("/margin-accounts/1/transactions").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Not owner."));
    }
}
