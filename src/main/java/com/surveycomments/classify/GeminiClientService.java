package com.surveycomments.classify;

/**
 * High-level interface isolating the Google Gen AI / Gemini SDK calls.
 * Allows complete mocking in unit and integration tests without network access
 * or API key dependencies.
 */
public interface GeminiClientService {

    /**
     * Generate content using the specified Gemini model, prompt, and options.
     *
     * @param modelId           Gemini model ID (e.g. "gemini-2.5-flash", "gemini-1.5-flash")
     * @param prompt            User prompt content
     * @param systemInstruction Optional system instruction text (may be null)
     * @param responseMimeType  Optional response MIME type, e.g. "application/json" (may be null)
     * @param temperature       Optional sampling temperature (may be null)
     * @return {@link GeminiGenerationResponse} containing the response text and token metrics
     */
    GeminiGenerationResponse generate(String modelId, String prompt, String systemInstruction,
                                      String responseMimeType, Double temperature);
}

