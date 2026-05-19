package rs.raf.banka2.contracts.internal;

/**
 * Identitet korisnika za trading-service.
 * trading-service JWT nosi samo email; ovaj DTO razresava numericki id + rolu.
 *
 * {@code position} je radno mesto zaposlenog (npr. "Direktor", "Agent") — banka-core
 * ga puni za EMPLOYEE; za CLIENT je {@code null}. trading-service actuary domen
 * koristi {@code position} da popuni {@code ActuaryInfoDto.employeePosition}.
 */
public record InternalUserDto(Long userId, String userRole, String email,
                              String firstName, String lastName, boolean active,
                              String position) {
}
