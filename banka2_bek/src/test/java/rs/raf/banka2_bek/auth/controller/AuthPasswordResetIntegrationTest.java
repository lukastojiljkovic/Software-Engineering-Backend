package rs.raf.banka2_bek.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.auth.model.PasswordResetToken;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthPasswordResetIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository actuaryInfoRepository;

    @Autowired
    private rs.raf.banka2_bek.employee.repository.ActivationTokenRepository activationTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        passwordResetTokenRepository.deleteAll();
        actuaryInfoRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void requestPasswordResetCreatesTokenForUser() throws Exception {
        User user = new User();
        user.setEmail("user@test.com");
        user.setPassword(passwordEncoder.encode("OldPass12"));
        user.setFirstName("User");
        user.setLastName("Test");
        user.setActive(true);
        userRepository.save(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
            url("/auth/password_reset/request"),
                new HttpEntity<>("{\"email\":\"user@test.com\"}", headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(OK);

        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
        PasswordResetToken token = passwordResetTokenRepository.findAll().get(0);
        assertThat(token.getUser()).isNotNull();
        assertThat(token.getEmployee()).isNull();
    }

    @Test
    void requestPasswordResetCreatesTokenForEmployee() throws Exception {
        Employee employee = Employee.builder()
                .firstName("Ana")
                .lastName("Test")
                .email("employee@test.com")
                .phone("+38160111222")
                .address("Test")
                .username("ana")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("F")
                .password(passwordEncoder.encode("Temp12" + "salt"))
                .saltPassword("salt")
                .position("QA")
                .department("IT")
                .active(true)
                .permissions(Set.of("VIEW_STOCKS"))
                .build();
        employeeRepository.save(employee);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
            url("/auth/password_reset/request"),
                new HttpEntity<>("{\"email\":\"employee@test.com\"}", headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(OK);

        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
        PasswordResetToken token = passwordResetTokenRepository.findAll().get(0);
        assertThat(token.getEmployee()).isNotNull();
        assertThat(token.getUser()).isNull();
    }

    @Test
    void confirmPasswordResetForUserUpdatesPassword() throws Exception {
        User user = new User();
        user.setEmail("reset@test.com");
        user.setPassword(passwordEncoder.encode("OldPass12"));
        user.setFirstName("Reset");
        user.setLastName("User");
        user.setActive(true);
        userRepository.save(user);

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("token-user");
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        passwordResetTokenRepository.save(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
            url("/auth/password_reset/confirm"),
                new HttpEntity<>("{\"token\":\"token-user\",\"newPassword\":\"NewPass12\"}", headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(OK);

        User updated = userRepository.findByEmail("reset@test.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass12", updated.getPassword())).isTrue();
        PasswordResetToken stored = passwordResetTokenRepository.findByToken("token-user").orElseThrow();
        assertThat(stored.getUsed()).isTrue();
    }

    @Test
    void confirmPasswordResetForEmployeeUpdatesPassword() throws Exception {
        Employee employee = Employee.builder()
                .firstName("Marko")
                .lastName("Test")
                .email("reset-emp@test.com")
                .phone("+38160111222")
                .address("Test")
                .username("marko")
                .dateOfBirth(LocalDate.of(1991, 1, 1))
                .gender("M")
                .password(passwordEncoder.encode("OldPass12" + "salt"))
                .saltPassword("salt")
                .position("Dev")
                .department("IT")
                .active(true)
                .permissions(Set.of("VIEW_STOCKS"))
                .build();
        employeeRepository.save(employee);

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("token-emp");
        token.setEmployee(employee);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);
        passwordResetTokenRepository.save(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
            url("/auth/password_reset/confirm"),
                new HttpEntity<>("{\"token\":\"token-emp\",\"newPassword\":\"NewPass12\"}", headers),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(OK);

        Employee updated = employeeRepository.findByEmail("reset-emp@test.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass12" + updated.getSaltPassword(), updated.getPassword())).isTrue();
        PasswordResetToken stored = passwordResetTokenRepository.findByToken("token-emp").orElseThrow();
        assertThat(stored.getUsed()).isTrue();
    }
}
