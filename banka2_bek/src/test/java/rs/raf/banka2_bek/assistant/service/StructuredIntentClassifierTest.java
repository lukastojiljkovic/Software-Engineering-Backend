package rs.raf.banka2_bek.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.assistant.config.AssistantProperties;
import rs.raf.banka2_bek.assistant.dto.openai.OllamaGenerateRequest;
import rs.raf.banka2_bek.assistant.dto.openai.OllamaGenerateResponse;
import rs.raf.banka2_bek.assistant.tool.client.LlmHttpClient;
import rs.raf.banka2_bek.auth.util.UserContext;
import rs.raf.banka2_bek.auth.util.UserRole;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan v3.6 §Task 3 — testovi za StructuredIntentClassifier sa Ollama format
 * param + JSON schema. Mock-uje LlmHttpClient.generate() jer nam ne treba pravi
 * LLM — proveravamo da klasifikator pravilno parsira JSON odgovor i primenjuje
 * confidence threshold.
 */
class StructuredIntentClassifierTest {

    private LlmHttpClient llmHttpClient;
    private AssistantProperties properties;
    private StructuredIntentClassifier classifier;

    private static final UserContext USER = new UserContext(42L, UserRole.CLIENT);

    @BeforeEach
    void setUp() {
        llmHttpClient = mock(LlmHttpClient.class);
        properties = new AssistantProperties();
        properties.setModel("gemma4:e2b-gpu");
        classifier = new StructuredIntentClassifier(llmHttpClient, properties, new ObjectMapper());
    }

    @Test
    void classifyReturnsValidTool() {
        // Mock LLM vraca "create_payment" sa visokim confidence-om
        when(llmHttpClient.generate(any(OllamaGenerateRequest.class)))
                .thenReturn(new OllamaGenerateResponse(
                        "gemma4:e2b-gpu",
                        "{\"tool\":\"create_payment\",\"confidence\":0.95}",
                        true));

        Optional<StructuredIntentClassifier.IntentResult> result =
                classifier.classify("uplati Mariji 1000 RSD", USER);

        assertThat(result).isPresent();
        assertThat(result.get().tool()).isEqualTo("create_payment");
        assertThat(result.get().confidence()).isEqualTo(0.95);
    }

    @Test
    void classifyRejectsLowConfidence() {
        when(llmHttpClient.generate(any(OllamaGenerateRequest.class)))
                .thenReturn(new OllamaGenerateResponse(
                        "gemma4:e2b-gpu",
                        "{\"tool\":\"create_payment\",\"confidence\":0.3}",
                        true));

        Optional<StructuredIntentClassifier.IntentResult> result =
                classifier.classify("hmm mozda neki order?", USER);

        assertThat(result).isEmpty();
    }

    @Test
    void classifyRejectsNoneTool() {
        when(llmHttpClient.generate(any(OllamaGenerateRequest.class)))
                .thenReturn(new OllamaGenerateResponse(
                        "gemma4:e2b-gpu",
                        "{\"tool\":\"none\",\"confidence\":0.9}",
                        true));

        Optional<StructuredIntentClassifier.IntentResult> result =
                classifier.classify("zdravo", USER);

        assertThat(result).isEmpty();
    }

    @Test
    void classifyReturnsEmptyOnLlmFailure() {
        when(llmHttpClient.generate(any(OllamaGenerateRequest.class)))
                .thenThrow(new RuntimeException("ollama unreachable"));

        Optional<StructuredIntentClassifier.IntentResult> result =
                classifier.classify("kupi 5 AAPL", USER);

        assertThat(result).isEmpty();
    }

    @Test
    void supportedToolsListIsNotEmpty() {
        assertThat(classifier.supportedTools())
                .isNotEmpty()
                .contains("create_payment", "create_buy_order", "block_card");
    }
}
