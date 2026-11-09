package com.surveycomments.retention;

/**
 * Service managing data lifecycle and scheduled purging of expired run artifacts.
 */
public interface RetentionService {

    /**
     * Purge stored runs and result files older than retention period.
     *
     * @return number of deleted run directories
     */
    int purgeExpiredRuns();
}

