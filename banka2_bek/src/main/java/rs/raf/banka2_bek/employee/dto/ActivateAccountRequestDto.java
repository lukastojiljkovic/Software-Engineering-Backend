package rs.raf.banka2_bek.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivateAccountRequestDto {

    @NotBlank
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=(?:.*[0-9]){2,}).*$",
        message = "Password must contain at least 1 lowercase, 1 uppercase letter, and 2 digits"
    )
    private String password;
}
