package rs.raf.banka2_bek.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateClientRequestDto {
    private String firstName;
    private String lastName;
    private String email;
    private String gender;
    @JsonAlias("phoneNumber")
    private String phone;
    private String address;
    private LocalDate dateOfBirth;
}
