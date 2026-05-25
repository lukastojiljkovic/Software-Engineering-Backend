package rs.raf.banka2_bek.account.util;

import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.model.AccountType;

import java.security.SecureRandom;

public final class AccountNumberUtils {

    private static final String BANK_CODE = "222";
    private static final String BRANCH_CODE = "0001";
    /**
     * BE-ACC-02 (defense-in-depth): {@link java.util.Random} (LCG) je
     * predvidljiv iz nekoliko output-a — attacker bi mogao da generise par
     * racuna kroz javni register flow pa da predvidi sledecu sekvencu (broj
     * racuna je polu-tajan identifikator za payment/transfer/OTC operacije).
     * {@link SecureRandom} koristi OS CSPRNG (Windows {@code CryptGenRandom},
     * Linux {@code /dev/urandom}) — non-deterministic.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AccountNumberUtils() {}

    public static String generate(AccountType type, AccountSubtype subtype, boolean isBusiness) {
        String typeDigits = determineTypeDigits(type, subtype, isBusiness);
        while (true) {
            String randomPart = String.format("%09d", SECURE_RANDOM.nextInt(1_000_000_000));
            String candidate = BANK_CODE + BRANCH_CODE + randomPart + typeDigits;
            if (isValidMod11(candidate)) {
                return candidate;
            }
        }
    }

    private static String determineTypeDigits(AccountType type, AccountSubtype subtype, boolean isBusiness) {
        if (type == AccountType.FOREIGN) {
            return isBusiness ? "22" : "21";
        }
        if (type == AccountType.BUSINESS || isBusiness) {
            return "12";
        }
        if (type == AccountType.CHECKING && subtype != null) {
            return switch (subtype) {
                case PERSONAL -> "11";
                case SAVINGS -> "13";
                case PENSION -> "14";
                case YOUTH -> "15";
                case STUDENT -> "16";
                case UNEMPLOYED -> "17";
                default -> "10";
            };
        }
        return "10";
    }

    private static boolean isValidMod11(String accountNumber) {
        int sum = 0;
        for (char c : accountNumber.toCharArray()) {
            sum += Character.getNumericValue(c);
        }
        return sum % 11 == 0;
    }
}
