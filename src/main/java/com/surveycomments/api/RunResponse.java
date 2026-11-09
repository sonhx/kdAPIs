package com.surveycomments.api;

import com.surveycomments.run.RunStatus;
import java.time.Instant;

/**
 * Response body returned when a run is created (HTTP 202) or polled (HTTP 200).
 */
public class RunResponse {

    private final String runId;
    private final String surveyId;
    private final String mode;
    private final RunStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final int totalComments;
    private final int cleanedComments;
    private final int classifiedComments;
    private final int reviewQueueSize;
    private final String errorMessage;

    public RunResponse(String runId, String surveyId, String mode, RunStatus status,
                       Instant createdAt, Instant updatedAt,
                       int totalComments, int cleanedComments,
                       int classifiedComments, int reviewQueueSize, String errorMessage) {
        this.runId              = runId;
        this.surveyId           = surveyId;
        this.mode               = mode;
        this.status             = status;
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
        this.totalComments      = totalComments;
        this.cleanedComments    = cleanedComments;
        this.classifiedComments = classifiedComments;
        this.reviewQueueSize    = reviewQueueSize;
        this.errorMessage       = errorMessage;
    }

    public String getRunId()              { return runId; }
    public String getSurveyId()           { return surveyId; }
    public String getMode()               { return mode; }
    public RunStatus getStatus()          { return status; }
    public Instant getCreatedAt()         { return createdAt; }
    public Instant getUpdatedAt()         { return updatedAt; }
    public int getTotalComments()         { return totalComments; }
    public int getCleanedComments()       { return cleanedComments; }
    public int getClassifiedComments()    { return classifiedComments; }
    public int getReviewQueueSize()       { return reviewQueueSize; }
    public String getErrorMessage()       { return errorMessage; }

    /** Build a {@link RunResponse} from a {@link com.surveycomments.run.Run}. */
    public static RunResponse from(com.surveycomments.run.Run run) {
        return new RunResponse(
                run.getRunId(), run.getSurveyId(), run.getMode(), run.getStatus(),
                run.getCreatedAt(), run.getUpdatedAt(),
                run.getTotalComments(), run.getCleanedComments(),
                run.getClassifiedComments(), run.getReviewQueueSize(),
                run.getErrorMessage());
    }
}

