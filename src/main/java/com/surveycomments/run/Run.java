package com.surveycomments.run;

import java.time.Instant;

/**
 * Lightweight value object representing a survey-comment analysis run.
 *
 * <p>All mutable state transitions happen through {@link RunRepository}; this class is
 * effectively a snapshot / DTO of a run's current state.
 *
 * <p>Persistence: in T1 this is held in memory / serialised to JSON on disk by
 * {@link FileRunRepository}. A JDBC implementation can be substituted later without
 * touching any business or controller code.
 */
public class Run {

    private final String runId;
    private final String surveyId;
    private final String mode;        // "SYNC" or "BATCH"
    private RunStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    // Progress counters (counts only — no comment text, per AGENT_RULES rule 5)
    private int totalComments;
    private int cleanedComments;
    private int classifiedComments;
    private int reviewQueueSize;

    private String errorMessage; // brief error description if status == FAILED (no comment text)

    public Run(String runId, String surveyId, String mode) {
        this.runId     = runId;
        this.surveyId  = surveyId;
        this.mode      = mode;
        this.status    = RunStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // -------------------------------------------------------------------------
    // Getters (immutable fields) & mutable-state accessors
    // -------------------------------------------------------------------------

    public String getRunId()   { return runId; }
    public String getSurveyId(){ return surveyId; }
    public String getMode()    { return mode; }
    public Instant getCreatedAt() { return createdAt; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) {
        this.status    = status;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt()           { return updatedAt; }

    public int getTotalComments()           { return totalComments; }
    public void setTotalComments(int n)     { this.totalComments = n; }

    public int getCleanedComments()         { return cleanedComments; }
    public void setCleanedComments(int n)   { this.cleanedComments = n; }

    public int getClassifiedComments()      { return classifiedComments; }
    public void setClassifiedComments(int n){ this.classifiedComments = n; }

    public int getReviewQueueSize()         { return reviewQueueSize; }
    public void setReviewQueueSize(int n)   { this.reviewQueueSize = n; }

    public String getErrorMessage()         { return errorMessage; }
    public void setErrorMessage(String msg) { this.errorMessage = msg; }
}

