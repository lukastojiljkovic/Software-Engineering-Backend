package rs.raf.banka2_bek.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import rs.raf.banka2_bek.auth.dto.AuthResponseDto;
import rs.raf.banka2_bek.auth.dto.LoginRequestDto;
import rs.raf.banka2_bek.auth.dto.PasswordResetDto;
import rs.raf.banka2_bek.auth.dto.PasswordResetRequestDto;
import rs.raf.banka2_bek.auth.dto.RefreshTokenRequestDto;
import rs.raf.banka2_bek.auth.dto.RefreshTokenResponseDto;
import rs.raf.banka2_bek.auth.model.PasswordResetRequestedEvent;
import rs.raf.banka2_bek.auth.model.PasswordResetToken;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    private User user;
    private Employee employee;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setPassword("hashed-user");
        user.setActive(true);
        user.setRole("CLIENT");

        employee = Employee.builder()
                .id(2L)
                .firstName("Ana")
                .lastName("Test")
                .email("employee@test.com")
                .password("hashed-emp")
                .saltPassword("salt")
                .active(true)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("F")
                .phone("+38160111222")
                .address("Test")
                .username("ana")
                .position("QA")
                .department("IT")
                .permissions(Set.of("ADMIN"))
                .build();
    }

    @Test
    void loginUsesEmployeeWhenExists() {
        when(employeeRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(passwordEncoder.matches(eq("password" + employee.getSaltPassword()), eq(employee.getPassword())))
                .thenReturn(true);
        when(jwtService.generateAccessToken(employee)).thenReturn("access");
        when(jwtService.generateRefreshToken(employee)).thenReturn("refresh");

        AuthResponseDto response = authService.login(new LoginRequestDto(employee.getEmail(), "password"));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    void loginFallsBackToUser() {
        when(employeeRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("password"), eq(user.getPassword()))).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh");

        AuthResponseDto response = authService.login(new LoginRequestDto(user.getEmail(), "password"));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    void requestPasswordResetForEmployeePublishesEvent() {
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.empty());
        when(employeeRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.requestPasswordReset(new PasswordResetRequestDto(employee.getEmail()));

        ArgumentCaptor<PasswordResetRequestedEvent> eventCaptor = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo(employee.getEmail());
    }

    @Test
    void resetPasswordForEmployeeUsesSaltedHash() {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("token");
        token.setEmployee(employee);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        token.setUsed(false);

        when(passwordResetTokenRepository.findByToken("token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass12" + employee.getSaltPassword())).thenReturn("new-hash");

        authService.resetPassword(new PasswordResetDto("token", "NewPass12"));

        verify(employeeRepository).save(employee);
        verify(passwordResetTokenRepository).save(token);
        assertThat(token.getUsed()).isTrue();
    }

    @Test
    void refreshTokenWorksForEmployee() {
        when(jwtService.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtService.extractEmail("refresh-token")).thenReturn(employee.getEmail());
        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.empty());
        when(employeeRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(jwtService.generateAccessToken(employee)).thenReturn("new-access");

        RefreshTokenRequestDto request = new RefreshTokenRequestDto();
        request.setRefreshToken("refresh-token");
        RefreshTokenResponseDto response = authService.refreshToken(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(employeeRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("missing@test.com", "bad")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
