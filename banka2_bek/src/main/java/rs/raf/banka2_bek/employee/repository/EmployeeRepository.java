package rs.raf.banka2_bek.employee.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.employee.model.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmail(String email);

        boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByUsername(String username);

    Optional<Employee> findByEmail(String email);

    // PostgreSQL ne moze da zakljuci tip kad je `:param` null, zato eksplicitan cast
    // (vidi CLAUDE.md Runda 24.04 PG migracija — ista popravka za PaymentRepository/TransactionRepository).
    @Query("SELECT e FROM Employee e WHERE " +
            "(cast(:email as string) IS NULL OR LOWER(e.email) LIKE LOWER(CONCAT('%', cast(:email as string), '%'))) AND " +
            "(cast(:firstName as string) IS NULL OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', cast(:firstName as string), '%'))) AND " +
            "(cast(:lastName as string) IS NULL OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', cast(:lastName as string), '%'))) AND " +
            "(cast(:position as string) IS NULL OR LOWER(e.position) LIKE LOWER(CONCAT('%', cast(:position as string), '%')))")
    Page<Employee> findByFilters(
            @Param("email") String email,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("position") String position,
            Pageable pageable);

    // Ne-paginirana varijanta za interni API (faza2c-A) — actuary domen filtrira
    // zaposlene po atributima posle ekstrakcije. Isti casting obrazac kao iznad.
    @Query("SELECT e FROM Employee e WHERE " +
            "(cast(:email as string) IS NULL OR LOWER(e.email) LIKE LOWER(CONCAT('%', cast(:email as string), '%'))) AND " +
            "(cast(:firstName as string) IS NULL OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', cast(:firstName as string), '%'))) AND " +
            "(cast(:lastName as string) IS NULL OR LOWER(e.lastName) LIKE LOWER(CONCAT('%', cast(:lastName as string), '%'))) AND " +
            "(cast(:position as string) IS NULL OR LOWER(e.position) LIKE LOWER(CONCAT('%', cast(:position as string), '%')))")
    List<Employee> findByFilters(
            @Param("email") String email,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("position") String position);
}
