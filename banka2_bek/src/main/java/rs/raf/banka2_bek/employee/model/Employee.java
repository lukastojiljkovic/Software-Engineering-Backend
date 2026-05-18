package rs.raf.banka2_bek.employee.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String saltPassword;

    @Column(nullable = false)
    private String position;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private Boolean active;

    /*
     * // TODO [B2 - Validacija + brute-force | Nosilac: Andjela Vilcek]
     *
     * Dodati sledeca dva polja radi DB-baziranog brute-force lockout-a
     * (zamena za trenutnu Caffeine in-memory implementaciju u
     * AccountLockoutService koja ne preziva restart i ne radi u
     * multi-instance deploy-u):
     *
     *   @org.hibernate.annotations.ColumnDefault("0")
     *   @Column(nullable = false)
     *   private Integer failedLoginAttempts = 0;
     *
     *   @Column(nullable = true)
     *   private java.time.LocalDateTime accountLockedUntil;
     *
     * Napomene:
     * - Lombok @Builder/@Getter/@Setter automatski pokriju nova polja.
     * - Hibernate ddl-auto=update automatski doda kolone pri sledecem startu.
     * - Lockout logiku preseliti u AccountLockoutService.java;
     *   videti TODO tamo za detalje.
     */

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_permissions", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "permission")
    @Builder.Default
    private Set<String> permissions = new HashSet<>();
}
