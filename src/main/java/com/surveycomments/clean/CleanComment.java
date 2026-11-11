package com.surveycomments.clean;

/**
 * A comment that has passed the cleaning stage: no-content entries removed, text NFC-normalised.
 * The original text is preserved exactly (no diacritic "fixing"); only encoding normalisation applied.
 */
public class CleanComment {

    private final String commentId;
    private final String surveyId;
    private final String questionId;
    private final String questionText;
    private final String cohort;
    private final String commentText;  // NFC-normalised; original Vietnamese preserved

    // Structural flags (never log commentText alongside these — log ids and counts only)
    private final boolean veryShort;   // e.g. text length < 3 chars after trimming
    private final boolean veryLong;    // e.g. text length > 2000 chars

    public CleanComment(String commentId, String surveyId, String questionId, String questionText,
                        String cohort, String commentText, boolean veryShort, boolean veryLong) {
        this.commentId    = commentId;
        this.surveyId     = surveyId;
        this.questionId   = questionId != null ? questionId : "";
        this.questionText = questionText != null ? questionText : "";
        this.cohort       = cohort != null ? cohort : "";
        this.commentText  = commentText;
        this.veryShort    = veryShort;
        this.veryLong     = veryLong;
    }

    public CleanComment(String commentId, String surveyId, String cohort,
                        String commentText, boolean veryShort, boolean veryLong) {
        this(commentId, surveyId, "", "", cohort, commentText, veryShort, veryLong);
    }

    public String getCommentId()   { return commentId; }
    public String getSurveyId()    { return surveyId; }
    public String getQuestionId()   { return questionId; }
    public String getQuestionText() { return questionText; }
    public String getCohort()      { return cohort; }
    public String getCommentText() { return commentText; }
    public boolean isVeryShort()   { return veryShort; }
    public boolean isVeryLong()    { return veryLong; }
}

