package com.surveycomments.cost;

import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.run.Run;
import com.surveycomments.run.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service calculating actual token consumption and estimating monetary cost in USD & VND.
 */
@Service
public class CostServiceImpl implements CostService {

    private static final Logger log = LoggerFactory.getLogger(CostServiceImpl.class);

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private SurveyCommentsProperties props;

    public CostServiceImpl() {}

    public CostServiceImpl(RunRepository runRepository, SurveyCommentsProperties props) {
        this.runRepository = runRepository;
        this.props = props;
    }

    @Override
    public CostReport calculateCost(String runId) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));

        List<ClassificationResult> results = runRepository.findResultsByRunId(runId);
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("No classified results found for runId: " + runId);
        }

        int totalInputTokens = 0;
        int totalOutputTokens = 0;

        for (ClassificationResult r : results) {
            totalInputTokens += r.getInputTokens();
            totalOutputTokens += r.getOutputTokens();
        }

        double inputRate = props.getPricing().getUsdPerMillionInputTokens();
        double outputRate = props.getPricing().getUsdPerMillionOutputTokens();
        double vndRate = props.getPricing().getVndExchangeRate();

        double inputCost = (totalInputTokens / 1_000_000.0) * inputRate;
        double outputCost = (totalOutputTokens / 1_000_000.0) * outputRate;
        double totalCostUsd = inputCost + outputCost;
        long totalCostVnd = Math.round(totalCostUsd * vndRate);

        log.info("[survey-comments] Cost calculated for runId={}: inTokens={} outTokens={} costUSD=${} costVND={}",
                runId, totalInputTokens, totalOutputTokens,
                String.format("%.4f", totalCostUsd), totalCostVnd);

        return new CostReport(
                runId,
                results.size(),
                totalInputTokens,
                totalOutputTokens,
                totalCostUsd,
                totalCostVnd,
                inputRate,
                outputRate
        );
    }
}

