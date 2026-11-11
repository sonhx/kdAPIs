package com.surveycomments.classify;

import java.util.List;

/**
 * Result of classifying one comment, matching the structure of {@code schema.json}.
 *
 * <p>Includes per-request token usage for cost estimation and version stamps for
 * reproducibility (PLAN.md §5 result record).
 */
public class ClassificationResult {

    private final String commentId;
    private final String questionId;
    private final String questionText;
    private final String cohort;
    private final String text;
    private final List<ThemeResult> themes;
    private final String overallSentiment;    // positive / neutral / negative / mixed
    private final double confidence;          // 0.0 – 1.0
    private final boolean needsHumanReview;
    private final boolean containsSuggestion;

    // Provenance
    private final String model;
    private final String promptVersion;
    private final String codebookVersion;
    private final java.time.Instant timestamp;

    // Token usage (from Gemini response metadata)
    private final int inputTokens;
    private final int outputTokens;

    public ClassificationResult(String commentId,
                                String questionId,
                                String questionText,
                                String cohort,
                                String text,
                                List<ThemeResult> themes,
                                String overallSentiment,
                                double confidence,
                                boolean needsHumanReview,
                                boolean containsSuggestion,
                                String model,
                                String promptVersion,
                                String codebookVersion,
                                int inputTokens,
                                int outputTokens) {
        this.commentId        = commentId;
        this.questionId       = questionId != null ? questionId : "";
        this.questionText     = questionText != null ? questionText : "";
        this.cohort           = cohort != null ? cohort : "";
        this.text             = text != null ? text : "";
        this.themes           = themes;
        this.overallSentiment = overallSentiment;
        this.confidence       = confidence;
        this.needsHumanReview = needsHumanReview;
        this.containsSuggestion = containsSuggestion;
        this.model            = model;
        this.promptVersion    = promptVersion;
        this.codebookVersion  = codebookVersion;
        this.timestamp        = java.time.Instant.now();
        this.inputTokens      = inputTokens;
        this.outputTokens     = outputTokens;
    }

    public ClassificationResult(String commentId,
                                String cohort,
                                String text,
                                List<ThemeResult> themes,
                                String overallSentiment,
                                double confidence,
                                boolean needsHumanReview,
                                boolean containsSuggestion,
                                String model,
                                String promptVersion,
                                String codebookVersion,
                                int inputTokens,
                                int outputTokens) {
        this(commentId, "", "", cohort, text, themes, overallSentiment, confidence,
             needsHumanReview, containsSuggestion, model, promptVersion, codebookVersion,
             inputTokens, outputTokens);
    }

    public ClassificationResult(String commentId,
                                String cohort,
                                List<ThemeResult> themes,
                                String overallSentiment,
                                double confidence,
                                boolean needsHumanReview,
                                boolean containsSuggestion,
                                String model,
                                String promptVersion,
                                String codebookVersion,
                                int inputTokens,
                                int outputTokens) {
        this(commentId, cohort, "", themes, overallSentiment, confidence,
                needsHumanReview, containsSuggestion, model, promptVersion, codebookVersion,
                inputTokens, outputTokens);
    }

    public ClassificationResult(String commentId,
                                List<ThemeResult> themes,
                                String overallSentiment,
                                double confidence,
                                boolean needsHumanReview,
                                boolean containsSuggestion,
                                String model,
                                String promptVersion,
                                String codebookVersion,
                                int inputTokens,
                                int outputTokens) {
        this(commentId, "", themes, overallSentiment, confidence,
                needsHumanReview, containsSuggestion, model, promptVersion, codebookVersion,
                inputTokens, outputTokens);
    }

    public String getCommentId()          { return commentId; }
    public String getQuestionId()         { return questionId; }
    public String getQuestionText()       { return questionText; }
    public String getCohort()             { return cohort; }
    public String getText()               { return text; }
    public List<ThemeResult> getThemes()  { return themes; }
    public String getOverallSentiment()   { return overallSentiment; }
    public double getConfidence()         { return confidence; }
    public boolean isNeedsHumanReview()   { return needsHumanReview; }
    public boolean isContainsSuggestion() { return containsSuggestion; }
    public String getModel()              { return model; }
    public String getPromptVersion()      { return promptVersion; }
    public String getCodebookVersion()    { return codebookVersion; }
    public java.time.Instant getTimestamp() { return timestamp; }
    public int getInputTokens()           { return inputTokens; }
    public int getOutputTokens()          { return outputTokens; }

    // -------------------------------------------------------------------------

    /** A single theme classification within a result. */
    public static class ThemeResult {
        private final String theme;
        private final String sentiment;   // positive / neutral / negative
        private final String evidence;    // exact quote from original comment text
        private final Boolean primary;    // optional

        public ThemeResult(String theme, String sentiment, String evidence, Boolean primary) {
            this.theme     = theme;
            this.sentiment = sentiment;
            this.evidence  = evidence;
            this.primary   = primary;
        }

        public String getTheme()     { return theme; }
        public String getSentiment() { return sentiment; }
        public String getEvidence()  { return evidence; }
        public Boolean getPrimary()  { return primary; }
    }
}

