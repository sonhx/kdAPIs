package com.surveys.dto;

public class OverallStatDto {
    private String surveyId;
    private String campaignId;
    private int totalResponses;
    private String computedAt;

    public OverallStatDto() {}

    public OverallStatDto(String surveyId, String campaignId, int totalResponses, String computedAt) {
        this.surveyId = surveyId;
        this.campaignId = campaignId;
        this.totalResponses = totalResponses;
        this.computedAt = computedAt;
    }

    // Getters and Setters
    public String getSurveyId() { return surveyId; }
    public void setSurveyId(String surveyId) { this.surveyId = surveyId; }
    public String getCampaignId() { return campaignId; }
    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
    public int getTotalResponses() { return totalResponses; }
    public void setTotalResponses(int totalResponses) { this.totalResponses = totalResponses; }
    public String getComputedAt() { return computedAt; }
    public void setComputedAt(String computedAt) { this.computedAt = computedAt; }
}
