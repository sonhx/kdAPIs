package com.surveys.dto;

import java.math.BigDecimal;

public class OptionStatDto {
    private String optionId;
    private String optionText;
    private int count;
    private BigDecimal percentage;

    public OptionStatDto() {}

    public OptionStatDto(String optionId, String optionText, int count, BigDecimal percentage) {
        this.optionId = optionId;
        this.optionText = optionText;
        this.count = count;
        this.percentage = percentage;
    }

    // Getters and Setters
    public String getOptionId() { return optionId; }
    public void setOptionId(String optionId) { this.optionId = optionId; }
    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
}
