package com.surveycomments.classify;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Production implementation of {@link GeminiClientService} wrapping the official
 * Google Gen AI Java SDK ({@code com.google.genai:google-genai}).
 *
 * <p>Security & Privacy:
 * <ul>
 *   <li>The API key is retrieved strictly from the {@code GEMINI_API_KEY} environment variable
 *       at runtime; it is NEVER hardcoded, logged, or printed (AGENT_RULES rule 4).</li>
 *   <li>Never logs comment content; logs model ID, token usage counts, and status only.</li>
 * </ul>
 */
@Service
public class GeminiClientServiceImpl implements GeminiClientService {

    private static final Logger log = LoggerFactory.getLogger(GeminiClientServiceImpl.class);

    private volatile Client client;

    /**
     * Lazily initialize the SDK Client instance using the GEMINI_API_KEY environment variable.
     */
    private Client getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    String apiKey = System.getenv("GEMINI_API_KEY");
                    if (apiKey == null || apiKey.isBlank()) {
                        throw new IllegalStateException(
                                "GEMINI_API_KEY environment variable is not set. "
                                + "Please set GEMINI_API_KEY in your runtime environment.");
                    }
                    this.client = Client.builder()
                            .apiKey(apiKey)
                            .vertexAI(false)
                            .build();
                    log.info("[survey-comments] Google Gen AI Client initialized for Gemini Developer API");
                }
            }
        }
        return client;
    }

    @Override
    public GeminiGenerationResponse generate(String modelId, String prompt, String systemInstruction,
                                            String responseMimeType, Double temperature) {
        log.info("[survey-comments] Gemini API call starting: model={}", modelId);

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemInstruction)));
        }

        if (responseMimeType != null && !responseMimeType.isBlank()) {
            configBuilder.responseMimeType(responseMimeType);
        }

        if (temperature != null) {
            configBuilder.temperature(temperature.floatValue());
        }

        // Set thinkingBudget=0 for fast deterministic classification without multi-minute reasoning delay
        configBuilder.thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build());

        GenerateContentConfig config = configBuilder.build();

        try {
            GenerateContentResponse response = getClient().models.generateContent(modelId, prompt, config);

            String text = response.text();
            int promptTokens = 0;
            int candidateTokens = 0;

            if (response.usageMetadata().isPresent()) {
                var usage = response.usageMetadata().get();
                promptTokens = usage.promptTokenCount().orElse(0);
                candidateTokens = usage.candidatesTokenCount().orElse(0);
            }

            log.info("[survey-comments] Gemini API call completed: promptTokens={} candidateTokens={}",
                    promptTokens, candidateTokens);

            return new GeminiGenerationResponse(text, promptTokens, candidateTokens);

        } catch (Exception e) {
            log.error("[survey-comments] Gemini API call failed for model {}: {}", modelId, e.getMessage());
            throw new ClassificationException("Gemini generation failed: " + e.getMessage(), e);
        }
    }
}

