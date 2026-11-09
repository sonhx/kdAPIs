package com.surveycomments.evaluate;

import java.util.List;
import java.util.Map;

/**
 * Report containing evaluation metrics of a run against a gold-standard dataset.
 */
public class EvaluationReport {

    private final String runId;
    private final int totalGoldItems;
    private final int matchedItems;
    private final double sentimentAccuracy;
    private final double sentimentCohenKappa;
    private final double themeMacroF1;
    private final double themeMicroF1;
    private final double suggestionAccuracy;
    private final List<ThemeMetric> themeMetrics;
    private final int disagreementsCount;
    private final List<Disagreement> disagreements;

    public EvaluationReport(String runId,
                            int totalGoldItems,
                            int matchedItems,
                            double sentimentAccuracy,
                            double sentimentCohenKappa,
                            double themeMacroF1,
                            double themeMicroF1,
                            double suggestionAccuracy,
                            List<ThemeMetric> themeMetrics,
                            int disagreementsCount,
                            List<Disagreement> disagreements) {
        this.runId               = runId;
        this.totalGoldItems      = totalGoldItems;
        this.matchedItems        = matchedItems;
        this.sentimentAccuracy   = sentimentAccuracy;
        this.sentimentCohenKappa = sentimentCohenKappa;
        this.themeMacroF1        = themeMacroF1;
        this.themeMicroF1        = themeMicroF1;
        this.suggestionAccuracy  = suggestionAccuracy;
        this.themeMetrics        = themeMetrics;
        this.disagreementsCount  = disagreementsCount;
        this.disagreements       = disagreements;
    }

    public String getRunId()                  { return runId; }
    public int getTotalGoldItems()            { return totalGoldItems; }
    public int getMatchedItems()              { return matchedItems; }
    public double getSentimentAccuracy()      { return sentimentAccuracy; }
    public double getSentimentCohenKappa()    { return sentimentCohenKappa; }
    public double getThemeMacroF1()           { return themeMacroF1; }
    public double getThemeMicroF1()           { return themeMicroF1; }
    public double getSuggestionAccuracy()     { return suggestionAccuracy; }
    public List<ThemeMetric> getThemeMetrics(){ return themeMetrics; }
    public int getDisagreementsCount()        { return disagreementsCount; }
    public List<Disagreement> getDisagreements() { return disagreements; }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    public static class ThemeMetric {
        private final String theme;
        private final double precision;
        private final double recall;
        private final double f1Score;
        private final int truePositives;
        private final int falsePositives;
        private final int falseNegatives;
        private final int support;

        public ThemeMetric(String theme, double precision, double recall, double f1Score,
                           int truePositives, int falsePositives, int falseNegatives, int support) {
            this.theme          = theme;
            this.precision      = precision;
            this.recall         = recall;
            this.f1Score        = f1Score;
            this.truePositives  = truePositives;
            this.falsePositives = falsePositives;
            this.falseNegatives = falseNegatives;
            this.support        = support;
        }

        public String getTheme()       { return theme; }
        public double getPrecision()   { return precision; }
        public double getRecall()      { return recall; }
        public double getF1Score()     { return f1Score; }
        public int getTruePositives()  { return truePositives; }
        public int getFalsePositives() { return falsePositives; }
        public int getFalseNegatives() { return falseNegatives; }
        public int getSupport()        { return support; }
    }

    public static class Disagreement {
        private final String commentId;
        private final String goldThemes;
        private final String predictedThemes;
        private final String goldSentiment;
        private final String predictedSentiment;
        private final boolean goldSuggestion;
        private final boolean predictedSuggestion;
        private final List<String> evidenceQuotes;

        public Disagreement(String commentId,
                            String goldThemes,
                            String predictedThemes,
                            String goldSentiment,
                            String predictedSentiment,
                            boolean goldSuggestion,
                            boolean predictedSuggestion,
                            List<String> evidenceQuotes) {
            this.commentId           = commentId;
            this.goldThemes          = goldThemes;
            this.predictedThemes     = predictedThemes;
            this.goldSentiment       = goldSentiment;
            this.predictedSentiment  = predictedSentiment;
            this.goldSuggestion      = goldSuggestion;
            this.predictedSuggestion = predictedSuggestion;
            this.evidenceQuotes      = evidenceQuotes;
        }

        public String getCommentId()          { return commentId; }
        public String getGoldThemes()         { return goldThemes; }
        public String getPredictedThemes()    { return predictedThemes; }
        public String getGoldSentiment()      { return goldSentiment; }
        public String getPredictedSentiment() { return predictedSentiment; }
        public boolean isGoldSuggestion()     { return goldSuggestion; }
        public boolean isPredictedSuggestion(){ return predictedSuggestion; }
        public List<String> getEvidenceQuotes() { return evidenceQuotes; }
    }
}

