package com.surveycomments.ingest;

/**
 * Thrown when an uploaded file cannot be ingested (wrong encoding, missing required columns,
 * or empty file). The message is safe to return to the caller (it describes the structural
 * problem, never the comment text).
 */
public class IngestException extends RuntimeException {

    public IngestException(String message) {
        super(message);
    }

    public IngestException(String message, Throwable cause) {
        super(message, cause);
    }
}

