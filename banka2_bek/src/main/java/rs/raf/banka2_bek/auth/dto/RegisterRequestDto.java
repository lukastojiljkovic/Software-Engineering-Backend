package rs.raf.banka2_bek.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public class RegisterRequestDto {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    @Pattern(
            regexp = "^(?=(?:.*\\d){2,})(?=.*[a-z])(?=.*[A-Z]).{8,32}$",
            message = "Password must contain at least 2 digits, 1 uppercase and 1 lowercase letter"
    )
    private String password;

    @Size(max = 50)
    private String username;

    /*
     * // TODO [B2 - Validacija + brute-force | Nosilac: Andjela Vilcek]
     *
     * Dodati Jakarta Validation anotacije na polja phone i dateOfBirth:
     *
     * 1. Telefon — validacija formata:
     *    Trenutno: samo @Size(max = 20), nema provere da su cifre.
     *    Trebalo bi dodati @Pattern ispod @Size:
     *
     *      @Pattern(
     *          regexp = "^\\+?[0-9]{6,20}$",
     *          message = "Telefon sme da sadrzi samo cifre uz opcioni vodeci znak +"
     *      )
     *
     *    Regex objasnjenje:
     *      ^\+?   — opcioni vodeci plus (medjunarodni format)
     *      [0-9]+ — samo cifre
     *      {6,20} — min 6 (kratki lokalni brojevi), max 20 (E.164 max je 15 + prefix)
     *
     * 2. Datum rodjenja — ne sme biti u buducnosti:
     *    Trenutno: Long (Unix ms), nema validacije opsega.
     *    Opcija A (preporucena za Long tip) — custom constraint ili
     *    @AssertTrue metoda u samom DTO-u:
     *
     *      @AssertTrue(message = "Datum rodjenja ne sme biti u buducnosti")
     *      public boolean isDateOfBirthValid() {
     *          return dateOfBirth == null
     *              || dateOfBirth <= java.time.Instant.now().toEpochMilli();
     *      }
     *
     *    Opcija B — promeniti tip polja na LocalDate i koristiti @Past:
     *
     *      @Past(message = "Datum rodjenja mora biti u proslosti")
     *      private LocalDate dateOfBirth;
     *
     *    Uz Opciju B potrebno je azurirati AuthService.register() koji
     *    poziva user.setDateOfBirth(request.getDateOfBirth()) i ocekuje Long.
     *
     * 3. Osigurati da RegisterController (ili odgovarajuci @RestController)
     *    ima @Valid/@Validated anotaciju na parametru requestBody,
     *    inace ove anotacije nemaju efekta pri pozivu.
     */
    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String address;

    private Long dateOfBirth;

    @Size(max = 10)
    private String gender;

    public RegisterRequestDto() {
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Long getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(Long dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}