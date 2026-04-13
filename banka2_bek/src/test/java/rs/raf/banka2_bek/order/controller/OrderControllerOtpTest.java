package rs.raf.banka2_bek.order.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.service.OrderService;
import rs.raf.banka2_bek.otp.service.OtpService;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit testovi za OTP integraciju u OrderController.create.
 * Phase 7 — Order Reservation + OTP plan.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerOtpTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private OrderController orderController;

    private static final String EMAIL = "stefan.jovanovic@gmail.com";

    @BeforeEach
    void setUp() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                EMAIL, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CreateOrderDto validDto() {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setListingId(1L);
        dto.setOrderType("MARKET");
        dto.setQuantity(5);
        dto.setContractSize(1);
        dto.setDirection("BUY");
        dto.setAccountId(10L);
        dto.setOtpCode("123456");
        return dto;
    }

    @Test
    void createOrder_withInvalidOtp_returns403() {
        CreateOrderDto dto = validDto();
        when(otpService.verify(eq(EMAIL), eq("123456"))).thenReturn(Map.of(
                "verified", false,
                "blocked", false,
                "message", "Pogresan verifikacioni kod. Preostalo pokusaja: 2"));

        ResponseEntity<?> response = orderController.createOrder(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("error")).isEqualTo("Pogresan verifikacioni kod. Preostalo pokusaja: 2");
        verify(orderService, never()).createOrder(any());
    }

    @Test
    void createOrder_withValidOtp_callsService_returns200() {
        CreateOrderDto dto = validDto();
        OrderDto created = new OrderDto();
        when(otpService.verify(eq(EMAIL), eq("123456"))).thenReturn(Map.of(
                "verified", true,
                "message", "Transakcija uspesno verifikovana"));
        when(orderService.createOrder(any(CreateOrderDto.class))).thenReturn(created);

        ResponseEntity<?> response = orderController.createOrder(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(created);
        verify(orderService, times(1)).createOrder(dto);
        verify(otpService, times(1)).verify(EMAIL, "123456");
    }

    @Test
    void createOrder_withMissingOtp_returns403AndDoesNotCallService() {
        // Simulira slucaj kad je DTO stigao do kontrolera (bean validation bi inace vratio 400,
        // ali to ide kroz MethodArgumentNotValidException u Spring MVC sloju — ovde simuliramo
        // scenario gde je otpCode prazan i OtpService vraca "nema aktivnog koda").
        CreateOrderDto dto = validDto();
        dto.setOtpCode("");
        when(otpService.verify(eq(EMAIL), eq(""))).thenReturn(Map.of(
                "verified", false,
                "blocked", false,
                "message", "Verifikacioni kod nije pronadjen. Zatrazite novi kod."));

        ResponseEntity<?> response = orderController.createOrder(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(orderService, never()).createOrder(any());
    }

    @Test
    void createOrder_withNoAuthentication_returns401() {
        SecurityContextHolder.clearContext();
        CreateOrderDto dto = validDto();

        ResponseEntity<?> response = orderController.createOrder(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(otpService);
        verifyNoInteractions(orderService);
    }
}
