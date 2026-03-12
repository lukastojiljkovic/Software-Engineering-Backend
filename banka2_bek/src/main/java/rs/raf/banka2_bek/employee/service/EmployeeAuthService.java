package rs.raf.banka2_bek.employee.service;

/**
 * Service for authentication and account activation.
 */
public interface EmployeeAuthService {

    /**
     * Activates an employee account using the one-time token sent by email.
     * Validates token (exists, not used, not invalidated, not expired), then sets the employee as active,
     * updates the password with the new one provided by the user, and marks the token as used and invalidated.
     *
     * @param tokenValue the activation token string from the activation link/request
     * @param newPassword the password chosen by the employee during activation
     * @throws IllegalArgumentException if token is invalid, already used, invalidated, or expired
     * @throws IllegalStateException    if the account is already active
     */
    void activateAccount(String tokenValue, String newPassword);
}
