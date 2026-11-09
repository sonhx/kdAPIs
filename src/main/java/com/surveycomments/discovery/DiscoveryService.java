package com.surveycomments.discovery;

import java.io.InputStream;

/**
 * Service for codebook discovery (Task T3 dev tool).
 * Reads cleaned & redacted comments and uses Gemini's long-context capabilities
 * to propose adjustments (merges, splits, new themes) starting from the existing codebook.
 */
public interface DiscoveryService {

    /**
     * Run theme discovery on an uploaded CSV file.
     *
     * @param inputStream CSV input stream
     * @param filename    original file name
     * @param surveyId    survey ID
     * @param modelId     optional model override (defaults to configured model)
     * @return {@link DiscoveryResult} with proposed draft themes
     */
    DiscoveryResult discoverThemes(InputStream inputStream, String filename, String surveyId, String modelId);
}

