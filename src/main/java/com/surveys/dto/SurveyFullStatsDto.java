package com.surveys.dto;

import java.util.List;
import java.util.Map;

public class SurveyFullStatsDto {
    private String surveyId;
    private String campaignId;
    private OverallStatDto overallStats;
    private Map<String, BlockStatDto> blockStats;
    private Map<String, QuestionFullStatDto> questionStats;

    public SurveyFullStatsDto() {}

    public SurveyFullStatsDto(String surveyId, String campaignId, OverallStatDto overallStats, 
                              Map<String, BlockStatDto> blockStats, 
                              Map<String, QuestionFullStatDto> questionStats) {
        this.surveyId = surveyId;
        this.campaignId = campaignId;
        this.overallStats = overallStats;
        this.blockStats = blockStats;
        this.questionStats = questionStats;
    }

    public String getSurveyId() { return surveyId; }
    public void setSurveyId(String surveyId) { this.surveyId = surveyId; }

    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    public OverallStatDto getOverallStats() { return overallStats; }
    public void setOverallStats(OverallStatDto overallStats) { this.overallStats = overallStats; }

    public Map<String, BlockStatDto> getBlockStats() { return blockStats; }
    public void setBlockStats(Map<String, BlockStatDto> blockStats) { this.blockStats = blockStats; }

    public Map<String, QuestionFullStatDto> getQuestionStats() { return questionStats; }
    public void setQuestionStats(Map<String, QuestionFullStatDto> questionStats) { this.questionStats = questionStats; }

    public static class QuestionFullStatDto {
        private String questionId;
        private QuestionNumericStatDto numericStats;
        private List<OptionStatDto> optionStats;

        public QuestionFullStatDto() {}

        public QuestionFullStatDto(String questionId, QuestionNumericStatDto numericStats, List<OptionStatDto> optionStats) {
            this.questionId = questionId;
            this.numericStats = numericStats;
            this.optionStats = optionStats;
        }

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public QuestionNumericStatDto getNumericStats() { return numericStats; }
        public void setNumericStats(QuestionNumericStatDto numericStats) { this.numericStats = numericStats; }

        public List<OptionStatDto> getOptionStats() { return optionStats; }
        public void setOptionStats(List<OptionStatDto> optionStats) { this.optionStats = optionStats; }
    }
}
