package rs.raf.banka2_bek.assistant.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit testovi za ContentLeakFilter — proverava da meta-reasoning preamble
 * + Gemma channel marker leak budu skinuti iz LLM odgovora pre nego sto
 * stignu FE-u.
 */
class ContentLeakFilterTest {

    private final ContentLeakFilter filter = new ContentLeakFilter();

    @Test
    void passesThroughCleanText() {
        String input = "BELIBOR je referentna kamatna stopa za RSD trziste.";
        String out = filter.filter(input);
        assertThat(out).isEqualTo(input);
    }

    @Test
    void passesThroughCleanTextWithLineBreaks() {
        String input = "Prvi paragraf odgovora.\n\nDrugi paragraf sa dodatnim informacijama.";
        String out = filter.filter(input);
        assertThat(out).isEqualTo(input);
    }

    @Test
    void stripsChannelClosingMarker() {
        String input = "<channel|>BELIBOR je glavna interbanka kamatna stopa.";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("<channel|>");
        assertThat(out).contains("BELIBOR");
    }

    @Test
    void stripsFullChannelBlock() {
        String input = "<|channel>thought\nThe user is asking about BELIBOR. I should respond.\n<channel|>BELIBOR je referentna kamatna stopa.";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("<|channel>");
        assertThat(out).doesNotContain("<channel|>");
        assertThat(out).doesNotContain("The user is asking");
        assertThat(out).contains("BELIBOR je referentna");
    }

    @Test
    void stripsThinkBlocks() {
        String input = "<think>Let me analyze this question step by step.</think>BELIBOR je referentna kamatna stopa za dinar.";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("<think>");
        assertThat(out).doesNotContain("</think>");
        assertThat(out).doesNotContain("step by step");
        assertThat(out).contains("BELIBOR");
    }

    @Test
    void stripsConstraintCheckPreamble() {
        String input = """
                Constraint Check: The prompt states: "KAD WIKIPEDIA SEARCH/SUMMARY VRATI PRAZAN REZULTAT..."

                The previous execution returned a fallback message, instructing me to use my own knowledge if the tool fails.

                BELIBOR je glavna interbanka tržišta za obveznice i derivate.""";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("Constraint Check");
        assertThat(out).doesNotContain("The prompt states");
        assertThat(out).doesNotContain("previous execution");
        assertThat(out).doesNotContain("KAD WIKIPEDIA");
        assertThat(out).contains("BELIBOR");
    }

    @Test
    void stripsFinalOutputGenerationPreamble() {
        String input = """
                Final Output Generation based on synthesized knowledge (since the tool failed to provide specific results in the previous turn): I must now rely on internal knowledge, as instructed by the fallback mechanism.

                BELIBOR je referentna stopa.""";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("Final Output Generation");
        assertThat(out).doesNotContain("synthesized knowledge");
        assertThat(out).doesNotContain("I must now rely");
        assertThat(out).contains("BELIBOR");
    }

    @Test
    void stripsFullObservedLeakFromLiveTest() {
        // Tacan tekst koji je korisnik prijavio iz live testa
        String input = """
                Constraint Check: The prompt states: "KAD WIKIPEDIA SEARCH/SUMMARY VRATI PRAZAN REZULTAT (results: [], summary: null, ili fallback_message polje): ... NIKADA ne odgovaraj samo "Nemam informacije" ako je pitanje opste — ili pokusaj jos jedan tool poziv ili koristi svoje znanje."

                The previous execution returned a fallback message, instructing me to use my own knowledge if the tool fails.

                Final Output Generation based on synthesized knowledge (since the tool failed to provide specific results in the previous turn): I must now rely on internal knowledge, as instructed by the fallback mechanism.<channel|>BELIBOR je glavna interbanka tržišta za obveznice i derivate koja se koristi kao referentna kamatna stopa za RSD instrumente.""";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("Constraint Check");
        assertThat(out).doesNotContain("Final Output Generation");
        assertThat(out).doesNotContain("<channel|>");
        assertThat(out).doesNotContain("previous execution");
        assertThat(out).doesNotContain("KAD WIKIPEDIA");
        assertThat(out).contains("BELIBOR");
        assertThat(out).contains("referentna kamatna stopa");
    }

    @Test
    void stripsMixedChannelAndPreamble() {
        String input = "<|channel>thought\nI need to call the tool.\n<channel|>\n\nFinal Output: Evo odgovora.\n\nDa, mozete da uradite transfer kroz menjacnicu.";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("<|channel>");
        assertThat(out).doesNotContain("<channel|>");
        assertThat(out).doesNotContain("Final Output:");
        assertThat(out).contains("transfer kroz menjacnicu");
    }

    @Test
    void handlesEmptyInput() {
        assertThat(filter.filter(null)).isEmpty();
        assertThat(filter.filter("")).isEmpty();
        assertThat(filter.filter("   ")).isEmpty();
    }

    @Test
    void idempotent() {
        String input = "<channel|>Constraint Check: Reasoning.\n\nBELIBOR je referentna stopa.";
        String once = filter.filter(input);
        String twice = filter.filter(once);
        assertThat(twice).isEqualTo(once);
    }

    @Test
    void doesNotStripLegitimateUsageOfStepWord() {
        String input = "Da biste poslali placanje, sledite korake na stranici 'Novo placanje'.";
        String out = filter.filter(input);
        assertThat(out).isEqualTo(input);
    }

    @Test
    void doesNotStripLegitimateConstraintWordInBankingContext() {
        String input = "Vasa kartica ima ogranicenje od 50000 RSD dnevno.";
        String out = filter.filter(input);
        assertThat(out).isEqualTo(input);
    }

    @Test
    void returnsPlaceholderWhenEverythingIsMeta() {
        // Sve paragrafi su meta — filter mora vratiti placeholder umesto praznog stringa
        String input = """
                Constraint Check: blah blah.

                Final Output Generation based on synthesized knowledge.

                The previous execution returned a fallback.""";
        String out = filter.filter(input);
        assertThat(out).isNotEmpty();
        assertThat(out).doesNotContain("Constraint Check");
        assertThat(out).doesNotContain("Final Output");
    }

    @Test
    void stripsImStartImEndMarkers() {
        String input = "<|im_start|>assistant\nOdgovor je 42.<|im_end|>";
        String out = filter.filter(input);
        assertThat(out).doesNotContain("<|im_start|>");
        assertThat(out).doesNotContain("<|im_end|>");
        assertThat(out).contains("Odgovor je 42");
    }

    @Test
    void filterStreamingBufferReturnsNullWhilePreamble() {
        // Kratki buffer sa preamble keyword-om — vraca null (signal "cekamo")
        String result = filter.filterStreamingBuffer("Constraint Check: The prompt", 400);
        assertThat(result).isNull();
    }

    @Test
    void filterStreamingBufferReturnsTextOncePastThreshold() {
        // Buffer prosao threshold — vraca filtriran tekst
        String longText = "Constraint Check: meta meta meta.\n\n" + "x".repeat(500) + "\n\nReal answer here.";
        String result = filter.filterStreamingBuffer(longText, 400);
        assertThat(result).isNotNull();
        assertThat(result).doesNotContain("Constraint Check");
    }
}
