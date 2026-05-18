package rs.raf.banka2_bek.employee.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import rs.raf.banka2_bek.employee.model.ActivationToken;
import rs.raf.banka2_bek.notification.NotificationPublisher;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.employee.service.implementation.EmployeeAuthServiceImpl;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeAuthServiceImplTest {

    @Mock private ActivationTokenRepository activationTokenRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationPublisher notificationPublisher;

    @InjectMocks private EmployeeAuthServiceImpl service;

    private Employee buildEmployee(boolean active) {
        return Employee.builder().id(1L).email("emp@b.rs").firstName("Marko").password("old")
                .saltPassword("salt123").active(active).build();
    }

    private ActivationToken buildToken(Employee emp, boolean used, boolean invalidated, LocalDateTime exp) {
        return ActivationToken.builder().id(1L).token("tok").employee(emp).used(used)
                .invalidated(invalidated).expiresAt(exp).build();
    }

    @Test void activateAccount_valid_activatesEmployee() {
        Employee emp = buildEmployee(false);
        ActivationToken tok = buildToken(emp, false, false, LocalDateTime.now().plusHours(1));
        when(activationTokenRepository.findByToken("tok")).thenReturn(Optional.of(tok));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        service.activateAccount("tok", "NewPass12");
        assertThat(emp.getActive()).isTrue();
        assertThat(tok.isUsed()).isTrue();
        verify(employeeRepository).save(emp);
        verify(activationTokenRepository).save(tok);
        verify(notificationPublisher).sendActivationConfirmationMail(anyString(), anyString());
    }

    @Test void activateAccount_invalidToken_throws() {
        when(activationTokenRepository.findByToken("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.activateAccount("bad", "P12")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void activateAccount_usedToken_throws() {
        Employee emp = buildEmployee(false);
        ActivationToken tok = buildToken(emp, true, false, LocalDateTime.now().plusHours(1));
        when(activationTokenRepository.findByToken("tok")).thenReturn(Optional.of(tok));
        assertThatThrownBy(() -> service.activateAccount("tok", "P12")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void activateAccount_invalidatedToken_throws() {
        Employee emp = buildEmployee(false);
        ActivationToken tok = buildToken(emp, false, true, LocalDateTime.now().plusHours(1));
        when(activationTokenRepository.findByToken("tok")).thenReturn(Optional.of(tok));
        assertThatThrownBy(() -> service.activateAccount("tok", "P12")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void activateAccount_expiredToken_throws() {
        Employee emp = buildEmployee(false);
        ActivationToken tok = buildToken(emp, false, false, LocalDateTime.now().minusHours(1));
        when(activationTokenRepository.findByToken("tok")).thenReturn(Optional.of(tok));
        assertThatThrownBy(() -> service.activateAccount("tok", "P12")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void activateAccount_alreadyActive_throwsIllegalState() {
        Employee emp = buildEmployee(true);
        ActivationToken tok = buildToken(emp, false, false, LocalDateTime.now().plusHours(1));
        when(activationTokenRepository.findByToken("tok")).thenReturn(Optional.of(tok));
        assertThatThrownBy(() -> service.activateAccount("tok", "P12")).isInstanceOf(IllegalStateException.class);
    }

    @Test void activateAccount_encodesWithSalt() {
        Employee emp = buildEmployee(false);
        ActivationToken tok = buildToken(emp, false, false, LocalDateTime.now().plusHours(1));
        when(activationTokenRepository.findByToken("tok")).thenReturn(Optional.of(tok));
        when(passwordEncoder.encode("NewPass12salt123")).thenReturn("encoded-salt");
        service.activateAccount("tok", "NewPass12");
        assertThat(emp.getPassword()).isEqualTo("encoded-salt");
    }
}
