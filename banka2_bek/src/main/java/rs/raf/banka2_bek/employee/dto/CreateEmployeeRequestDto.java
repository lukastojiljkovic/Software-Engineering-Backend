package rs.raf.banka2_bek.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class CreateEmployeeRequestDto {

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    /*
     * // TODO [B2 - Validacija + brute-force | Nosilac: Andjela Vilcek]
     *
     * Dodati Jakarta Validation anotacije na polja dateOfBirth i phone:
     *
     * 1. Datum rodjenja — ne sme biti u buducnosti:
     *    Polje je vec LocalDate (za razliku od RegisterRequestDto koji koristi Long),
     *    sto omogucava direktnu upotrebu @Past:
     *
     *      @Past(message = "Datum rodjenja mora biti u proslosti")
     *
     *    Dodati @Past ispred ili ispod @NotNull anotacije.
     *    Import: import jakarta.validation.constraints.Past;
     *
     * 2. Telefon — validacija formata:
     *    Trenutno: samo @NotBlank, nema provere da su cifre.
     *    Trebalo bi dodati @Pattern ispod @NotBlank:
     *
     *      @Pattern(
     *          regexp = "^\\+?[0-9]{6,20}$",
     *          message = "Telefon sme da sadrzi samo cifre uz opcioni vodeci znak +"
     *      )
     *
     *    Import: import jakarta.validation.constraints.Pattern;
     *    (import vec postoji ako je dodat za dateOfBirth — proveri).
     *
     * 3. Osigurati da odgovarajuci @RestController metod (CreateEmployee
     *    endpoint u EmployeeController ili AdminController) ima @Valid
     *    anotaciju na @RequestBody parametru, inace ove anotacije nemaju
     *    efekta pri pozivu.
     */
    @NotNull
    private LocalDate dateOfBirth;

    @NotBlank
    private String gender;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone;

    @NotBlank
    private String address;

    @NotBlank
    private String username;

    @NotBlank
    private String position;

    @NotBlank
    private String department;

    private Boolean active;

    private Set<String> permissions;
}
