package com.surveycomments.cost;

/**
 * Service calculating token consumption and monetary cost for completed runs.
 */
public interface CostService {

    /**
     * Calculate token usage and estimated cost in USD and VND for the specified runId.
     *
     * @param runId identifier of the completed run
     * @return {@link CostReport}
     */
    CostReport calculateCost(String runId);
}

