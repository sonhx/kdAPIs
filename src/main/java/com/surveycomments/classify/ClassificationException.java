package com.surveycomments.classify;

/**
 * Thrown by {@link CommentClassifier} when a comment cannot be classified after
 * exhausting retries, or when the response fails local schema validation.
 *
 * <p>The message MUST NOT contain comment text; use comment ids and error descriptions only.
 */
public class ClassificationException extends RuntimeException {

    public ClassificationException(String message) {
        super(message);
    }

    public ClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}

