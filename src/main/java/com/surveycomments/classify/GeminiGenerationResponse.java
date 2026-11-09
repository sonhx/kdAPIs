package com.surveycomments.classify;

/**
 * Result returned by {@link GeminiClientService}, encapsulating the generated text
 * and token usage metrics.
 */
public class GeminiGenerationResponse {

    private final String text;
    private final int promptTokens;
    private final int candidateTokens;

    public GeminiGenerationResponse(String text, int promptTokens, int candidateTokens) {
        this.text = text;
        this.promptTokens = promptTokens;
        this.candidateTokens = candidateTokens;
    }

    public String getText() {
        return text;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCandidateTokens() {
        return candidateTokens;
    }
}

