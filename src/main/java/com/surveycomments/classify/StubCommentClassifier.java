package com.surveycomments.classify;

import com.surveycomments.redact.RedactedComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder implementation of {@link CommentClassifier}.
 *
 * <p>This stub always throws {@link UnsupportedOperationException}.
 * It is replaced in T4 by {@code GeminiCommentClassifier} after the
 * {@code com.google.genai:google-genai} SDK dependency is approved and added.
 *
 * <p>Having this no-op allows the Spring context to start and the smoke test to pass
 * without any network access or API key.
 */
@Component
public class StubCommentClassifier implements CommentClassifier {

    private static final Logger log = LoggerFactory.getLogger(StubCommentClassifier.class);

    @Override
    public ClassificationResult classify(RedactedComment comment) {
        // ids only — no comment text in log (AGENT_RULES rule 5)
        log.warn("[survey-comments] StubCommentClassifier called for id={}; not yet implemented", comment.getCommentId());
        throw new UnsupportedOperationException(
                "GeminiCommentClassifier not yet implemented. Awaiting T4 and SDK dependency approval.");
    }
}

