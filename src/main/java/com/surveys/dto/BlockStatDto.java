package com.surveys.dto;

import java.math.BigDecimal;

public class BlockStatDto {
    private String blockId;
    private int totalResponses;
    private BigDecimal mean;
    private BigDecimal stdDev;

    public BlockStatDto() {}

    public BlockStatDto(String blockId, int totalResponses, BigDecimal mean, BigDecimal stdDev) {
        this.blockId = blockId;
        this.totalResponses = totalResponses;
        this.mean = mean;
        this.stdDev = stdDev;
    }

    // Getters and Setters
    public String getBlockId() { return blockId; }
    public void setBlockId(String blockId) { this.blockId = blockId; }
    public int getTotalResponses() { return totalResponses; }
    public void setTotalResponses(int totalResponses) { this.totalResponses = totalResponses; }
    public BigDecimal getMean() { return mean; }
    public void setMean(BigDecimal mean) { this.mean = mean; }
    public BigDecimal getStdDev() { return stdDev; }
    public void setStdDev(BigDecimal stdDev) { this.stdDev = stdDev; }
}
