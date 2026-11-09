package com.surveycomments.evaluate;

import java.io.InputStream;

/**
 * Service for evaluating a run's classification results against a gold dataset.
 */
public interface EvaluationService {

    /**
     * Evaluate the run results against an uploaded gold CSV file.
     *
     * @param runId      ID of the completed run
     * @param goldCsvIn  InputStream of the gold CSV file
     * @return {@link EvaluationReport} containing metrics and disagreement list
     */
    EvaluationReport evaluate(String runId, InputStream goldCsvIn);
}
