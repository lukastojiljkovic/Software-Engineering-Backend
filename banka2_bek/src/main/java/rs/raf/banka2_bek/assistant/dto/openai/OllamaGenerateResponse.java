package rs.raf.banka2_bek.assistant.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ollama-native {@code /api/generate} response body.
 *
 * <p>Ollama vraca puno polja (created_at, total_duration, eval_count, ...);
 * koristimo samo {@code response} koji sadrzi modelov output (sa formatom
 * matched-uvanim po JSON schema-i ako je {@code format} polje bio postavljen
 * u request-u).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaGenerateResponse(
        String model,
        String response,
        Boolean done
) {}
