package com.surveycomments.cost;

/**
 * DTO representing token consumption and cost estimation for a run.
 */
public class CostReport {

    private final String runId;
    private final int totalClassifiedComments;
    private final int totalInputTokens;
    private final int totalOutputTokens;
    private final int totalTokens;
    private final double costUsd;
    private final long costVnd;
    private final double inputRatePerMillion;
    private final double outputRatePerMillion;

    public CostReport(String runId,
                      int totalClassifiedComments,
                      int totalInputTokens,
                      int totalOutputTokens,
                      double costUsd,
                      long costVnd,
                      double inputRatePerMillion,
                      double outputRatePerMillion) {
        this.runId                   = runId;
        this.totalClassifiedComments = totalClassifiedComments;
        this.totalInputTokens        = totalInputTokens;
        this.totalOutputTokens       = totalOutputTokens;
        this.totalTokens             = totalInputTokens + totalOutputTokens;
        this.costUsd                 = costUsd;
        this.costVnd                 = costVnd;
        this.inputRatePerMillion     = inputRatePerMillion;
        this.outputRatePerMillion    = outputRatePerMillion;
    }

    public String getRunId()                   { return runId; }
    public int getTotalClassifiedComments()    { return totalClassifiedComments; }
    public int getTotalInputTokens()           { return totalInputTokens; }
    public int getTotalOutputTokens()          { return totalOutputTokens; }
    public int getTotalTokens()                { return totalTokens; }
    public double getCostUsd()                 { return costUsd; }
    public long getCostVnd()                   { return costVnd; }
    public double getInputRatePerMillion()     { return inputRatePerMillion; }
    public double getOutputRatePerMillion()    { return outputRatePerMillion; }
}

