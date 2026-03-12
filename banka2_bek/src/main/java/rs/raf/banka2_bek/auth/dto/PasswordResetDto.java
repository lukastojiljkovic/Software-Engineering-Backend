package rs.raf.banka2_bek.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordResetDto {


    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=(?:.*[0-9]){2,}).*$",
        message = "Password must contain at least 1 lowercase, 1 uppercase letter, and 2 digits"
    )
    private String newPassword;

    public PasswordResetDto() {
    }

    public PasswordResetDto(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
    public String getNewPassword() {
        return newPassword;
    }
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
