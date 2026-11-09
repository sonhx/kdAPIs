package com.surveycomments.classify;

import com.surveycomments.redact.RedactedComment;

/**
 * Stage 4 – Classification interface.
 *
 * <p>Classifies a single redacted comment into themes (multi-label) and sentiment
 * using the Gemini API. This interface is the boundary that isolates Gemini from
 * business logic and allows the classifier to be mocked in tests (AGENT_RULES rule 9).
 *
 * <p>Implementation ({@link GeminiCommentClassifier}) is added in T4, after the
 * Google Gen AI SDK dependency is approved.
 */
public interface CommentClassifier {

    /**
     * Classify a single redacted comment.
     *
     * <p>Implementations must:
     * <ul>
     *   <li>Build a prompt from the codebook (version-stamped).</li>
     *   <li>Use structured output; validate every response locally against schema.json.</li>
     *   <li>Throttle to requests-per-minute; retry with backoff on HTTP 429.</li>
     *   <li>Cache by hash(redacted_text + codebook_version + prompt_version + model).</li>
     *   <li>Never log comment text; log ids and counts only.</li>
     * </ul>
     *
     * @param comment the redacted comment to classify
     * @return classification result including themes, sentiments, confidence, and usage
     * @throws ClassificationException on unrecoverable API or validation failure
     */
    ClassificationResult classify(RedactedComment comment);
}

