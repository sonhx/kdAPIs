package com.surveycomments.ingest;

/**
 * A raw comment record as parsed directly from the uploaded CSV.
 * No cleaning or redaction has been applied yet.
 *
 * <p>All fields are Strings as read from the file; nulls are allowed for optional columns.
 */
public class RawComment {

    private final String commentId;
    private final String surveyId;
    private final String cohort;
    private final String commentText;

    public RawComment(String commentId, String surveyId, String cohort, String commentText) {
        this.commentId   = commentId;
        this.surveyId    = surveyId;
        this.cohort      = cohort;
        this.commentText = commentText;
    }

    public String getCommentId()   { return commentId; }
    public String getSurveyId()    { return surveyId; }
    public String getCohort()      { return cohort; }
    public String getCommentText() { return commentText; }
}

