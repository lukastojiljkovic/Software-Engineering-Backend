package rs.raf.banka2_bek.assistant.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Ollama-native {@code /api/generate} request body — koristi se za
 * structured outputs (JSON schema constraint kroz {@code format} polje)
 * koje OpenAI-compat sloj ne podrzava.
 *
 * <p>Plan v3.6 §Task 3 — koristimo Ollama format param umesto
 * tool_choice="required" za intent classification. Ollama backend
 * (llama.cpp) prevodi schema u GBNF grammar i forsira output da matchuje
 * — pouzdanost skoci sa ~60% (tool_choice na Gemma 4 E2B) na ~95%.</p>
 *
 * <p>Reference:
 * <a href="https://ollama.com/blog/structured-outputs">Ollama structured outputs</a></p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaGenerateRequest(
        String model,
        String prompt,
        String system,
        /**
         * JSON schema kao {@link java.util.Map} (Jackson serijalizuje u JSON
         * object) — Ollama backend prevodi u GBNF grammar.
         */
        Object format,
        Boolean stream,
        /**
         * Generation opcije ({@code temperature}, {@code num_predict},
         * {@code top_p}, ...). Map omogucava varijabilni payload bez
         * gomilanja polja u recordu.
         */
        Map<String, Object> options
) {}
