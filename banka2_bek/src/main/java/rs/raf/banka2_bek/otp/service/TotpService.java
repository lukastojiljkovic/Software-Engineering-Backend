package rs.raf.banka2_bek.otp.service;

import org.springframework.stereotype.Service;

// ============================================================
// TODO [B3 - TOTP verifikacioni kod | Nosilac: Nikola Stamenkovic]
//
// Servis koji upravlja TOTP tajnim kljucevima i verifikacijom kodova
// po RFC 6238 sa 30-sekundnim prozorom i tolerancijom +/-1 susednog prozora.
//
// IMPLEMENTIRATI:
//   - Polje: TotpSecretRepository totpSecretRepository (injectovati @RequiredArgsConstructor)
//
//   - String generateSecret(Long userId):
//       Kreira ili rotira TOTP secret za korisnika.
//       Koraci:
//         1. Pozovi totpSecretRepository.deleteByUserId(userId) da pocistis stari secret
//            (idempotentno — ne puca ako ne postoji).
//         2. Generi base32 secret pomocu npr.
//            new GoogleAuthenticator().createCredentials().getKey()
//            (biblioteka com.warrenstrange:googleauth:1.5.0).
//         3. Snimi novi TotpSecret(userId=userId, secret=generisaniKljuc) u repozitorijum.
//         4. Vrati base32 string da ga caller moze prikazati korisniku / QR kodu.
//       Anotacija: @Transactional
//
//   - boolean verify(Long userId, String code):
//       Proverava da li je code validan TOTP token za datog korisnika.
//       Koraci:
//         1. Pozovi totpSecretRepository.findByUserId(userId)
//            .orElseThrow(() -> new IllegalStateException("TOTP nije podesен za korisnika " + userId))
//         2. Koristeci GoogleAuthenticator s prozorskom tolerancijom (window size 1 = +/-1):
//               GoogleAuthenticator gAuth = new GoogleAuthenticatorBuilder()
//                   .setWindowSize(1)   // ukupno 3 prozora: prosli, tekuci, sledeci
//                   .build();
//            pozovi:
//               gAuth.authorize(secret.getSecret(), Integer.parseInt(code))
//            Vrati rezultat (true/false).
//         3. Ako code nije broj (NumberFormatException), vrati false (ne bacaj iznimku).
//       Anotacija: @Transactional(readOnly = true)
//
//   NAPOMENA O INTEGRACIJI (ovo radi koordinator, NIJE tvoj zadatak):
//     Pozivna mesta koja danas koriste OtpService.verify(email, code) ostaju
//     nepromenjena — koordinator odlucuje gde se uvodi TotpService kao zamena.
//     Tvoj TotpService.verify(userId, code) ima isti semanticki ugovor:
//     vraca boolean, baca IllegalStateException ako secret ne postoji.
//
//   DEPENDENCY (dodati u pom.xml pre implementacije):
//     <dependency>
//       <groupId>com.warrenstrange</groupId>
//       <artifactId>googleauth</artifactId>
//       <version>1.5.0</version>
//     </dependency>
//
// Konvencija: pratiti paket `savings` kao sablon
//   (@Service, @RequiredArgsConstructor, @Slf4j, @Transactional po metodi).
// Spec: Zadaci_Backend.pdf, zadatak B3.
// ============================================================
@Service
public class TotpService {
}
