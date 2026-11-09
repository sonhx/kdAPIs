package com.surveycomments.run;

/**
 * Processing lifecycle states for a survey-comment analysis run.
 *
 * <pre>
 * PENDING  -> CLEANING -> CLASSIFYING -> DONE
 *                      -> WAITING_BATCH -> DONE   (Batch API path, T6)
 * Any state -> FAILED
 * </pre>
 */
public enum RunStatus {
    PENDING,
    CLEANING,
    CLASSIFYING,
    WAITING_BATCH,
    DONE,
    FAILED
}

