package rs.raf.banka2_bek.otp;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

// ============================================================
// TODO [B3 - TOTP verifikacioni kod | Nosilac: Nikola Stamenkovic]
//
// JUnit 5 / Mockito unit testovi za TotpService.
//
// IMPLEMENTIRATI (@Test metode — po jedna za svaki slucaj):
//
//   generateSecret_createsAndPersistsSecret:
//     Verifikuje da generateSecret(userId) brise stari secret
//     (verify deleteByUserId) i snima novi (verify save),
//     te da vraca ne-null, ne-prazan base32 string.
//
//   generateSecret_rotatesExistingSecret:
//     Kada za userId vec postoji TotpSecret u repozitorijumu,
//     generateSecret treba da ga najpre obrise pa kreira novi.
//     Proveri da deleteByUserId bude pozvan pre save.
//
//   verify_returnsTrueForValidCode:
//     Stub totpSecretRepository.findByUserId(userId) da vrati
//     TotpSecret sa poznatim secretom (generisan u testu),
//     pa pozovi TotpService.verify(userId, validCode) i ocekuj true.
//     Koristiti GoogleAuthenticator.getTotpPassword(secret) da dobijes
//     validan kod za tekuci 30-sekundni prozor.
//
//   verify_returnsFalseForInvalidCode:
//     Isti stub, ali kao code prosledi "000000" (gotovo sigurno pogresan).
//     Ocekuj false. Ne sme da baci iznimku.
//
//   verify_returnsFalseForNonNumericCode:
//     Prosledi code = "abc" (nije broj).
//     Ocekuj false — servis mora da uhvati NumberFormatException interno.
//
//   verify_throwsWhenSecretNotFound:
//     Stub findByUserId da vrati Optional.empty().
//     Ocekuj da verify baci IllegalStateException
//     sa porukom koja sadrzi "nije podesen".
//
//   verify_acceptsAdjacentWindowCode:
//     Kreiraj secret, generi kod za prethodni 30-sekundni prozor
//     (timestamp - 30s) pomocu GoogleAuthenticator.getTotpPassword(secret, time-30000).
//     Ocekuj true — tolerancija prozora +/-1 mora da prihvati susedni kod.
//
// KONVENCIJA PISANJA TESTOVA:
//   @ExtendWith(MockitoExtension.class)
//   @Mock TotpSecretRepository totpSecretRepository
//   @InjectMocks TotpService totpService
//   Koristiti AssertJ: assertThat(...).isTrue() / isFalse() / isNotBlank()
//   Koristiti Mockito strict stubs (default sa MockitoExtension).
//
// Konvencija: pratiti paket `savings` kao sablon za organizaciju testova.
// Spec: Zadaci_Backend.pdf, zadatak B3.
// ============================================================
@ExtendWith(MockitoExtension.class)
public class TotpServiceTest {
}
