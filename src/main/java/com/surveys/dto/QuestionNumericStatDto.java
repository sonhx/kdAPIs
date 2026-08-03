package com.surveys.dto;

import java.math.BigDecimal;

public class QuestionNumericStatDto {
    private String questionId;
    private int totalResponses;
    private BigDecimal mean;
    private BigDecimal stdDev;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private int textCount;

    public QuestionNumericStatDto() {}

    public QuestionNumericStatDto(String questionId, int totalResponses, BigDecimal mean, BigDecimal stdDev, BigDecimal minValue, BigDecimal maxValue, int textCount) {
        this.questionId = questionId;
        this.totalResponses = totalResponses;
        this.mean = mean;
        this.stdDev = stdDev;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.textCount = textCount;
    }

    // Getters and Setters
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public int getTotalResponses() { return totalResponses; }
    public void setTotalResponses(int totalResponses) { this.totalResponses = totalResponses; }
    public BigDecimal getMean() { return mean; }
    public void setMean(BigDecimal mean) { this.mean = mean; }
    public BigDecimal getStdDev() { return stdDev; }
    public void setStdDev(BigDecimal stdDev) { this.stdDev = stdDev; }
    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }
    public int getTextCount() { return textCount; }
    public void setTextCount(int textCount) { this.textCount = textCount; }
}
