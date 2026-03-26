package rs.raf.banka2_bek.client.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rs.raf.banka2_bek.client.model.Client;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM Client c WHERE "
            + "(:firstName IS NULL AND :lastName IS NULL AND :email IS NULL) OR "
            + "(:firstName IS NOT NULL AND LOWER(c.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))) OR "
            + "(:lastName IS NOT NULL AND LOWER(c.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) OR "
            + "(:email IS NOT NULL AND LOWER(c.email) LIKE LOWER(CONCAT('%', :email, '%')))")
    Page<Client> findByFilters(@Param("firstName") String firstName,
                               @Param("lastName") String lastName,
                               @Param("email") String email,
                               Pageable pageable);
}
