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
    private final String questionId;
    private final String questionText;
    private final String cohort;
    private final String commentText;

    public RawComment(String commentId, String surveyId, String questionId, String questionText,
                      String cohort, String commentText) {
        this.commentId    = commentId;
        this.surveyId     = surveyId;
        this.questionId   = questionId != null ? questionId : "";
        this.questionText = questionText != null ? questionText : "";
        this.cohort       = cohort != null ? cohort : "";
        this.commentText  = commentText;
    }

    public RawComment(String commentId, String surveyId, String cohort, String commentText) {
        this(commentId, surveyId, "", "", cohort, commentText);
    }

    public String getCommentId()   { return commentId; }
    public String getSurveyId()    { return surveyId; }
    public String getQuestionId()   { return questionId; }
    public String getQuestionText() { return questionText; }
    public String getCohort()      { return cohort; }
    public String getCommentText() { return commentText; }
}

