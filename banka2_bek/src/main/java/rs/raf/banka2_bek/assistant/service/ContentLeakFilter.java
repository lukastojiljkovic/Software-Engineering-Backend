package rs.raf.banka2_bek.assistant.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Defensive filter koji uklanja Gemma 4 meta-reasoning preamble + leak-ovane
 * sistemske direktive iz finalnog odgovora pre nego sto stigne FE-u.
 *
 * <p>Tipicni leak-ovi koje smo videli u live testovima:</p>
 * <pre>
 *   "Constraint Check: The prompt states: ..."
 *   "Final Output Generation based on synthesized knowledge..."
 *   "based on the fallback mechanism, I must now rely on internal knowledge..."
 *   "<|channel>thought ... <channel|>"   (Gemma channel marker leak)
 *   "<channel|>BELIBOR je ..."           (samo zatvarac, model je preskocio otvarac)
 * </pre>
 *
 * <p>Strategija u 2 koraka (idempotentna, safe za vise prolaza):</p>
 * <ol>
 *   <li>Strip svih Gemma channel markera + thinking blokova
 *       ({@code <|channel>thought...<channel|>}, {@code <think>...</think>})</li>
 *   <li>Pre-amble heuristika: ako tekst pocinje sa meta-keywords i pre prvog
 *       paragraph break-a ima jos meta-keywords, odbaci sve do prvog
 *       paragraph break-a koji ne sadrzi te keywords.</li>
 * </ol>
 *
 * <p>Filter NIKAD ne menja sadrzaj posle prvog "stvarnog" paragrafa — samo
 * trim-uje preamble. Ako tekst NEMA preamble (uobicajeno), pass-through.</p>
 */
@Component
public final class ContentLeakFilter {

    /**
     * Kljucne fraze koje signaliziraju da model ulazi u meta-reasoning preamble.
     * Sve case-insensitive, prefix match na liniji ili paragrafu.
     */
    private static final String[] META_PREAMBLE_KEYWORDS = {
            "constraint check",
            "the prompt states",
            "final output generation",
            "final output:",
            "based on synthesized knowledge",
            "based on the fallback mechanism",
            "i must now rely on internal knowledge",
            "i must now rely on my internal knowledge",
            "previous execution returned",
            "the previous execution returned",
            "instructing me to use",
            "as instructed by the fallback",
            "according to the fallback",
            "according to the system prompt",
            "according to the instructions",
            "kao sto sistem prompt kaze",
            "po pravilima",
            "po sistem promptu",
            "po master promptu",
            "thinking step by step",
            "let me think",
            "let me analyze",
            "step 1:",
            "step 2:",
            "analysis:",
            "reasoning:"
    };

    /**
     * Direktni leak tokeni — uvek strip (cak i posle prvog paragrafa).
     * Zatvarac {@code <channel|>} se zamenjuje sa newline-om umesto praznim
     * stringom da preamble + clean content ne ostanu u istom paragrafu.
     */
    private static final Pattern[] DIRECT_LEAK_TOKENS = {
            // Gemma channel markers — i otvarac i zatvarac sami, plus pun blok
            Pattern.compile("<\\|channel>thought.*?<channel\\|>", Pattern.DOTALL),
            Pattern.compile("<\\|channel>analysis.*?<channel\\|>", Pattern.DOTALL),
            Pattern.compile("<\\|channel>[a-z_]*.*?<channel\\|>", Pattern.DOTALL),
            Pattern.compile("<\\|channel>[a-z_]*\\b"),
            // <think>...</think> blokovi
            Pattern.compile("<think>.*?</think>", Pattern.DOTALL),
            Pattern.compile("</think>"),
            Pattern.compile("<think>"),
            // OpenAI-style <|im_start|> markers ako procure
            Pattern.compile("<\\|im_start\\|>[a-z]*"),
            Pattern.compile("<\\|im_end\\|>"),
            // Direktan citat iz nase NEPROMENLJIVA PRAVILA sekcije
            Pattern.compile("(?i)NEPROMENLJIVA PRAVILA:.*?(?=\\n\\n|$)", Pattern.DOTALL),
            Pattern.compile("(?i)KAD WIKIPEDIA SEARCH/SUMMARY.*?(?=\\n\\n|$)", Pattern.DOTALL)
    };

    /**
     * Zatvarac {@code <channel|>} se zamenjuje sa duplim newline-om umesto
     * praznim stringom — ovo razbije preamble + clean answer u zasebne
     * paragrafe pa preamble-stripper moze da odbaci samo prvi.
     */
    private static final Pattern CHANNEL_CLOSE_MARKER = Pattern.compile("<channel\\|>");

    /**
     * Glavna metoda — vraca filtriran tekst. Idempotentna (vise prolaza daju
     * isti rezultat). Ne menja sadrzaj koji nije leak (citav text pass-through
     * ako nije detektovan preamble).
     *
     * @param input sirov LLM output (moze biti null/prazno)
     * @return filtriran tekst, nikad null (vraca "" za null input)
     */
    public String filter(String input) {
        if (input == null) return "";
        String out = input;
        // Phase 1a: strip puni Gemma blokovi (otvarac + sadrzaj + zatvarac)
        // — moraju ici PRE samostalnog CHANNEL_CLOSE_MARKER replace-a jer
        // bi zatvarac inace nestao i blok ostao otvoren.
        for (Pattern p : DIRECT_LEAK_TOKENS) {
            out = p.matcher(out).replaceAll("");
        }
        // Phase 1b: preostali samostalni <channel|> zatvaraci (model je
        // emit-ovao zatvarac bez otvaraca) — zamenjeni paragraph break-om
        // da preamble + clean answer budu razdvojeni u faze 2.
        out = CHANNEL_CLOSE_MARKER.matcher(out).replaceAll("\n\n");
        // Phase 2: preamble stripping — ako tekst pocinje sa meta keyword-om,
        // pronadji prvi paragraph break i odbaci sve pre toga.
        out = stripMetaPreamble(out);
        // Cleanup: visestruki blank lines → max 2 newline.
        // VAZNO: ne radimo .trim() — ovaj filter se zove per-chunk u streaming
        // mode-u, a chunk-ovi iz Ollama-e cesto imaju vodecu space (" Prema",
        // " Wikipediji"). trim() bi strip-ovao space → FE bi konkatenirala
        // chunk-ove bez razmaka ("PremaWikipediji"). Konzument koji zeli
        // trim na finalnom rezultatu radi to eksplicitno (vidi filterFinal()).
        out = out.replaceAll("\\n{3,}", "\n\n");
        return out;
    }

    /**
     * Varijanta za jednokratnu obradu kompletnog odgovora (npr. persist u DB,
     * audit log). Radi sve sto i {@link #filter}, plus trim leading/trailing
     * whitespace. Ne koristiti u streaming-u jer strip-uje space iz chunk-ova.
     */
    public String filterFinal(String input) {
        return filter(input).trim();
    }

    private String stripMetaPreamble(String text) {
        if (text == null || text.isEmpty()) return text;
        String trimmed = text.stripLeading();
        if (trimmed.isEmpty()) return trimmed;
        String lower = trimmed.toLowerCase();
        boolean startsWithMeta = startsWithAnyKeyword(lower);
        if (!startsWithMeta) {
            // Mozda nije bas na pocetku, ali u prvih ~200 chars-a — provera za
            // poklapanje sa nekoliko keywords (multiplicity signals leak vs.
            // legitno spominjanje rec "step" u odgovoru).
            int hits = countKeywordHits(lower, 300);
            if (hits < 2) return trimmed;  // pass-through, nije leak
        }
        // Ima preamble — pronadji prvi paragraph break (dvostruki newline)
        // posle kog nema meta keyword-ova.
        String[] paragraphs = trimmed.split("\\n\\s*\\n");
        StringBuilder out = new StringBuilder();
        boolean foundClean = false;
        for (String p : paragraphs) {
            String paragraphLower = p.toLowerCase();
            if (!foundClean && hasMetaKeyword(paragraphLower)) {
                // Paragraf je meta — ali mozda na kraju ima i clean nastavak
                // (npr. "Final Output: meta meta. Stvaran odgovor pocinje ovde.").
                // Pokusaj da pronadjes prelaz: poslednji konec recenice posle
                // poslednjeg meta keyword-a → ostavi sve posle te tacke.
                String tail = extractCleanTailAfterMeta(p, paragraphLower);
                if (tail != null && !tail.isBlank()) {
                    foundClean = true;
                    if (out.length() > 0) out.append("\n\n");
                    out.append(tail.strip());
                }
                // ako tail prazan, paragraf ostaje skipovan
                continue;
            }
            foundClean = true;
            if (out.length() > 0) out.append("\n\n");
            out.append(p);
        }
        if (out.length() == 0) {
            // Sve paragrafe smo odbacili — verovatno kompletno meta. Vrati
            // generic placeholder umesto praznog stringa.
            return "Izvinjavam se, ne mogu da formulisem odgovor. Pokusajte ponovo.";
        }
        return out.toString();
    }

    /**
     * Pronadji poslednji meta-keyword u paragraf-u; ako posle njega ima jos
     * sadrzaja koji izgleda kao stvaran odgovor (sledece poglavlje, prelaz
     * preko "\n", ili zatvarac recenice), vrati taj suffix. Inace null.
     *
     * <p>Tipican primer: "Final Output Generation based on synthesized
     * knowledge: I must now rely on internal knowledge.\nBELIBOR je ..."
     * — vraca "BELIBOR je ...".</p>
     */
    private String extractCleanTailAfterMeta(String paragraph, String paragraphLower) {
        if (paragraph == null || paragraph.isEmpty()) return null;
        // Nadji poslednju poziciju bilo kog meta keyword-a u paragrafu
        int lastMetaEnd = -1;
        for (String kw : META_PREAMBLE_KEYWORDS) {
            int idx = paragraphLower.lastIndexOf(kw);
            if (idx >= 0) {
                int end = idx + kw.length();
                if (end > lastMetaEnd) lastMetaEnd = end;
            }
        }
        if (lastMetaEnd < 0) return null;
        // Posle poslednjeg meta keyword-a, pronadji prvi konec recenice
        // (period+space, period+newline, ili newline newline ili newline sam)
        String suffix = paragraph.substring(lastMetaEnd);
        // Strategija A: ako u suffix-u ima novi red, sve posle novog reda je clean
        int nl = suffix.indexOf('\n');
        if (nl >= 0 && nl < suffix.length() - 1) {
            String afterNl = suffix.substring(nl + 1).strip();
            if (!afterNl.isEmpty() && !hasMetaKeyword(afterNl.toLowerCase())) {
                return afterNl;
            }
        }
        // Strategija B: pronadji prvi tacka/uskliknik/upitnik + space, posle
        // koga sledece ne-meta sadrzaj
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[.!?]\\s+([A-Z\\u017D\\u0160\\u0106\\u010C\\u0110a-z].*)$",
                        java.util.regex.Pattern.DOTALL)
                .matcher(suffix);
        if (m.find()) {
            String tail = m.group(1).strip();
            if (!hasMetaKeyword(tail.toLowerCase())) {
                return tail;
            }
        }
        return null;
    }

    private boolean startsWithAnyKeyword(String lower) {
        // Provera prvih ~120 chars-a
        String head = lower.length() > 120 ? lower.substring(0, 120) : lower;
        for (String kw : META_PREAMBLE_KEYWORDS) {
            if (head.contains(kw)) return true;
        }
        return false;
    }

    private boolean hasMetaKeyword(String lower) {
        for (String kw : META_PREAMBLE_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private int countKeywordHits(String lower, int charLimit) {
        String head = lower.length() > charLimit ? lower.substring(0, charLimit) : lower;
        int n = 0;
        for (String kw : META_PREAMBLE_KEYWORDS) {
            if (head.contains(kw)) n++;
        }
        return n;
    }

    /**
     * Streaming-aware variant: drzi buffer prvih ~400 chars-a; kada se preamble
     * detektuje, vraca null (signal "join idemo, jos nismo prosli preamble").
     * Kad detektuje pocetak "stvarnog" sadrzaja (paragraph break + ne-meta
     * keyword), vraca filtriran cumulative tekst.
     *
     * <p>Koristi se pre nego sto se token emit-uje FE-u: drzi buffer dok ne
     * znamo da nismo u preamble-u, pa onda flush-uje preostali stream.</p>
     *
     * <p><b>Trenutno nije aktivno koriscen</b> jer fleksibilniji pristup u
     * {@link #filter} primenjuje se na pun finalContent posle generisanja.
     * Ostavljen za buduce token-streaming flow gde batching nije moguc.</p>
     */
    public String filterStreamingBuffer(String cumulativeContent, int bufferThreshold) {
        if (cumulativeContent == null) return null;
        if (cumulativeContent.length() < bufferThreshold) {
            // Premalo da odlucimo — drzi buffer
            String lower = cumulativeContent.toLowerCase();
            if (startsWithAnyKeyword(lower)) {
                return null;  // signal: jos cekamo
            }
        }
        return filter(cumulativeContent);
    }
}
