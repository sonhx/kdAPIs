package com.surveycomments.clean;

/**
 * A comment that has passed the cleaning stage: no-content entries removed, text NFC-normalised.
 * The original text is preserved exactly (no diacritic "fixing"); only encoding normalisation applied.
 */
public class CleanComment {

    private final String commentId;
    private final String surveyId;
    private final String cohort;
    private final String commentText;  // NFC-normalised; original Vietnamese preserved

    // Structural flags (never log commentText alongside these — log ids and counts only)
    private final boolean veryShort;   // e.g. text length < 3 chars after trimming
    private final boolean veryLong;    // e.g. text length > 2000 chars

    public CleanComment(String commentId, String surveyId, String cohort,
                        String commentText, boolean veryShort, boolean veryLong) {
        this.commentId   = commentId;
        this.surveyId    = surveyId;
        this.cohort      = cohort;
        this.commentText = commentText;
        this.veryShort   = veryShort;
        this.veryLong    = veryLong;
    }

    public String getCommentId()   { return commentId; }
    public String getSurveyId()    { return surveyId; }
    public String getCohort()      { return cohort; }
    public String getCommentText() { return commentText; }
    public boolean isVeryShort()   { return veryShort; }
    public boolean isVeryLong()    { return veryLong; }
}

