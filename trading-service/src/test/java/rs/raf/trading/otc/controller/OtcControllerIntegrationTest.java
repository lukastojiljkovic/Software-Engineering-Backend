package rs.raf.trading.otc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.trading.order.exception.InsufficientFundsException;
import rs.raf.trading.otc.controller.exception_handler.OtcExceptionHandler;
import rs.raf.trading.otc.dto.CreateOtcOfferDto;
import rs.raf.trading.otc.dto.OtcContractDto;
import rs.raf.trading.otc.dto.OtcOfferDto;
import rs.raf.trading.otc.service.OtcService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc test {@link OtcController}.
 *
 * <p>NAPOMENA (faza 2d-B): standalone setup + scoped {@link OtcExceptionHandler}
 * ({@code @Order(HIGHEST_PRECEDENCE)} — {@code IllegalStateException} → 409,
 * {@code AccessDeniedException} → 403, {@code InsufficientFundsException} → 400).
 * {@link OtcService} je {@code @Mock} — money-seam (banka-core transferi/
 * rezervacije) pokriva {@code OtcServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class OtcControllerIntegrationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Mock private OtcService otcService;

    @InjectMocks
    private OtcController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new OtcExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /otc/listings — 200 sa listom")
    void listDiscovery_ok() throws Exception {
        when(otcService.listDiscoveryListings()).thenReturn(List.of());

        mockMvc.perform(get("/otc/listings"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /otc/offers — 200 i prosledjuje DTO servisu")
    void createOffer_ok() throws Exception {
        CreateOtcOfferDto dto = new CreateOtcOfferDto();
        dto.setListingId(100L);
        dto.setSellerId(2L);
        dto.setQuantity(5);
        dto.setPricePerStock(new BigDecimal("160.00"));
        dto.setPremium(new BigDecimal("50.00"));
        dto.setSettlementDate(LocalDate.now().plusDays(30));

        OtcOfferDto response = new OtcOfferDto();
        response.setId(11L);
        response.setStatus("ACTIVE");
        when(otcService.createOffer(any(CreateOtcOfferDto.class))).thenReturn(response);

        mockMvc.perform(post("/otc/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(otcService).createOffer(any(CreateOtcOfferDto.class));
    }

    @Test
    @DisplayName("POST /otc/offers — nevalidan DTO → 400")
    void createOffer_invalidDto() throws Exception {
        CreateOtcOfferDto dto = new CreateOtcOfferDto();
        // nedostaju obavezna polja

        mockMvc.perform(post("/otc/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /otc/offers/{id}/accept — prosledjuje buyerAccountId query param")
    void acceptOffer_passesBuyerAccountId() throws Exception {
        OtcOfferDto response = new OtcOfferDto();
        response.setId(1L);
        response.setStatus("ACCEPTED");
        when(otcService.acceptOffer(eq(1L), eq(10L))).thenReturn(response);

        mockMvc.perform(post("/otc/offers/1/accept").param("buyerAccountId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(otcService).acceptOffer(1L, 10L);
    }

    @Test
    @DisplayName("POST /otc/contracts/{id}/exercise — 200")
    void exerciseContract_ok() throws Exception {
        OtcContractDto response = new OtcContractDto();
        response.setId(7L);
        response.setStatus("EXERCISED");
        when(otcService.exerciseContract(eq(7L), eq(10L))).thenReturn(response);

        mockMvc.perform(post("/otc/contracts/7/exercise").param("buyerAccountId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXERCISED"));
    }

    @Test
    @DisplayName("POST /otc/contracts/{id}/abandon — 200")
    void abandonContract_ok() throws Exception {
        OtcContractDto response = new OtcContractDto();
        response.setId(7L);
        response.setStatus("EXPIRED");
        when(otcService.abandonContract(7L)).thenReturn(response);

        mockMvc.perform(post("/otc/contracts/7/abandon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("OtcExceptionHandler — EntityNotFoundException → 404")
    void handler_notFound() throws Exception {
        when(otcService.listMyContracts(null))
                .thenThrow(new EntityNotFoundException("ne postoji"));

        mockMvc.perform(get("/otc/contracts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ne postoji"));
    }

    @Test
    @DisplayName("OtcExceptionHandler — IllegalStateException → 409")
    void handler_illegalState() throws Exception {
        when(otcService.exerciseContract(eq(7L), any()))
                .thenThrow(new IllegalStateException("ugovor istekao"));

        mockMvc.perform(post("/otc/contracts/7/exercise"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ugovor istekao"));
    }

    @Test
    @DisplayName("OtcExceptionHandler — AccessDeniedException → 403")
    void handler_accessDenied() throws Exception {
        when(otcService.listDiscoveryListings())
                .thenThrow(new AccessDeniedException("OTC zabranjen agentu"));

        mockMvc.perform(get("/otc/listings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OTC zabranjen agentu"));
    }

    @Test
    @DisplayName("OtcExceptionHandler — InsufficientFundsException → 400")
    void handler_insufficientFunds() throws Exception {
        when(otcService.acceptOffer(eq(1L), any()))
                .thenThrow(new InsufficientFundsException("nedovoljno sredstava"));

        mockMvc.perform(post("/otc/offers/1/accept"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("nedovoljno sredstava"));
    }
}
