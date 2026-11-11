package com.surveycomments.redact;

/**
 * A comment after the redaction stage. Personal identifiers have been replaced with
 * placeholders (e.g. {@code [EMAIL]}, {@code [PHONE]}, {@code [MSSV]}, {@code [NAME]}).
 *
 * <p>The original text is NOT stored here. Only redaction counts are retained.
 * Never log the redacted text — log ids and counts only (AGENT_RULES rule 5).
 */
public class RedactedComment {

    private final String commentId;
    private final String surveyId;
    private final String questionId;
    private final String questionText;
    private final String cohort;
    private final String redactedText;  // safe to send to Gemini (after approval of tier/policy)

    private final int emailsRedacted;
    private final int phonesRedacted;
    private final int mssvRedacted;
    private final int namesRedacted;

    private final boolean veryShort;
    private final boolean veryLong;

    public RedactedComment(String commentId, String surveyId, String questionId, String questionText,
                           String cohort, String redactedText,
                           int emailsRedacted, int phonesRedacted,
                           int mssvRedacted, int namesRedacted,
                           boolean veryShort, boolean veryLong) {
        this.commentId       = commentId;
        this.surveyId        = surveyId;
        this.questionId      = questionId != null ? questionId : "";
        this.questionText    = questionText != null ? questionText : "";
        this.cohort          = cohort != null ? cohort : "";
        this.redactedText    = redactedText;
        this.emailsRedacted  = emailsRedacted;
        this.phonesRedacted  = phonesRedacted;
        this.mssvRedacted    = mssvRedacted;
        this.namesRedacted   = namesRedacted;
        this.veryShort       = veryShort;
        this.veryLong        = veryLong;
    }

    public RedactedComment(String commentId, String surveyId, String cohort,
                           String redactedText,
                           int emailsRedacted, int phonesRedacted,
                           int mssvRedacted, int namesRedacted,
                           boolean veryShort, boolean veryLong) {
        this(commentId, surveyId, "", "", cohort, redactedText,
             emailsRedacted, phonesRedacted, mssvRedacted, namesRedacted,
             veryShort, veryLong);
    }

    public String getCommentId()      { return commentId; }
    public String getSurveyId()       { return surveyId; }
    public String getQuestionId()     { return questionId; }
    public String getQuestionText()   { return questionText; }
    public String getCohort()         { return cohort; }
    public String getRedactedText()   { return redactedText; }
    public int getEmailsRedacted()    { return emailsRedacted; }
    public int getPhonesRedacted()    { return phonesRedacted; }
    public int getMssvRedacted()      { return mssvRedacted; }
    public int getNamesRedacted()     { return namesRedacted; }
    public boolean isVeryShort()      { return veryShort; }
    public boolean isVeryLong()       { return veryLong; }

    /** True if any personal identifier was removed. */
    public boolean wasRedacted() {
        return emailsRedacted > 0 || phonesRedacted > 0 || mssvRedacted > 0 || namesRedacted > 0;
    }
}

