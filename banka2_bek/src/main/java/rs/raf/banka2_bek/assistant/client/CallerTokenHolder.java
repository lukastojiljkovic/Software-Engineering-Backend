package rs.raf.banka2_bek.assistant.client;

/**
 * Drzi sirov JWT bearer token pozivaoca za trajanje obrade jednog Arbitro
 * zahteva (faza 2f).
 *
 * <p><b>Zasto postoji:</b> posle 2f cutover-a trgovinski domen zivi u
 * {@code trading-service}, pa {@code assistant} (Arbitro) write handler-i vise
 * ne zovu trgovinske servise in-process — zovu {@link TradingServiceClient} koji
 * prosledjuje token pozivaoca tako da trading-service autentifikuje istog
 * korisnika. {@code SecurityContextHolder} ne cuva sirov token (JWT filter
 * postavlja {@code credentials = null}), a Arbitro chat tok ({@code buildPreview},
 * {@code get_recent_orders}, wizard slot resolveri) trci na zasebnoj niti
 * ({@code assistantTaskExecutor}) gde {@code RequestContextHolder} nije dostupan.
 *
 * <p><b>Kako se popunjava:</b>
 * <ul>
 *   <li>{@code CallerTokenFilter} ga postavi na nit zahteva za sve
 *       {@code /assistant/**} rute — pokriva agentic confirm endpoint
 *       ({@code AgentActionController.confirm}) gde {@code executeFinal} trci
 *       sinhrono na niti zahteva.</li>
 *   <li>{@code AssistantConfig} {@code assistantTaskExecutor} ima
 *       {@code TaskDecorator} koji snimi vrednost sa niti koja predaje zadatak
 *       i re-instalira je na radnu nit — pokriva {@code buildPreview} i read
 *       tool-ove koji trce u {@code runChat} na pool niti.</li>
 * </ul>
 *
 * <p>Holder se uvek brise u {@code finally} (filter + task decorator), tako da
 * recikliranje niti iz pool-a ne moze da iznese token u sledeci zahtev.
 */
public final class CallerTokenHolder {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private CallerTokenHolder() {
    }

    /** Postavlja sirov JWT (bez {@code "Bearer "} prefiksa) za tekucu nit. */
    public static void set(String token) {
        if (token == null || token.isBlank()) {
            TOKEN.remove();
        } else {
            TOKEN.set(token);
        }
    }

    /** Vraca sirov JWT tekuce niti, ili {@code null} ako nije postavljen. */
    public static String get() {
        return TOKEN.get();
    }

    /** Brise token sa tekuce niti — MORA se zvati u {@code finally}. */
    public static void clear() {
        TOKEN.remove();
    }
}
