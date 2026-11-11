package com.surveycomments.run;

import java.util.Optional;

/**
 * Repository abstraction for {@link Run} persistence.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link FileRunRepository} – default; stores state as JSON on disk.</li>
 * </ul>
 *
 * <p>A JDBC implementation can be substituted later without touching business or
 * controller code.
 */
public interface RunRepository {

    /** Persist a newly created run. The run must not already exist. */
    void save(Run run);

    /** Overwrite an existing run record (status update, counter increment, etc.). */
    void update(Run run);

    /** Look up a run by id. Returns empty if not found. */
    Optional<Run> findById(String runId);

    /** Retrieve all runs, typically ordered by creation time descending. */
    java.util.List<Run> findAll();

    /** Persist classified results associated with a run. */
    void saveResults(String runId, java.util.List<com.surveycomments.classify.ClassificationResult> results);

    /** Retrieve classified results for a run. */
    java.util.List<com.surveycomments.classify.ClassificationResult> findResultsByRunId(String runId);

    /** Retrieve only items flagged for human review for a run. */
    java.util.List<com.surveycomments.classify.ClassificationResult> findReviewQueueByRunId(String runId);
}

