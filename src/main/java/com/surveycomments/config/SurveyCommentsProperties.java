package com.surveycomments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration for the survey-comments module.
 * Bound to the {@code survey-comments} prefix in application.properties.
 *
 * <p>IMPORTANT: The Gemini API key is NOT stored here. It is read exclusively from the
 * {@code GEMINI_API_KEY} environment variable at bean-creation time and held only in memory.
 * Never log, print, or commit the key value.
 */
@Component
@ConfigurationProperties(prefix = "survey-comments")
public class SurveyCommentsProperties {

    private final Gemini gemini = new Gemini();
    private String codebookVersion = "v0.2-draft";
    private String promptVersion = "p0";
    private boolean redactStaffNames = false;
    private final Upload upload = new Upload();
    private final Storage storage = new Storage();
    private final Pricing pricing = new Pricing();

    // -------------------------------------------------------------------------
    // Nested config sections
    // -------------------------------------------------------------------------

    public static class Pricing {
        private double usdPerMillionInputTokens = 0.15;
        private double usdPerMillionOutputTokens = 0.60;
        private double vndExchangeRate = 25400.0;

        public double getUsdPerMillionInputTokens() { return usdPerMillionInputTokens; }
        public void setUsdPerMillionInputTokens(double usdPerMillionInputTokens) { this.usdPerMillionInputTokens = usdPerMillionInputTokens; }

        public double getUsdPerMillionOutputTokens() { return usdPerMillionOutputTokens; }
        public void setUsdPerMillionOutputTokens(double usdPerMillionOutputTokens) { this.usdPerMillionOutputTokens = usdPerMillionOutputTokens; }

        public double getVndExchangeRate() { return vndExchangeRate; }
        public void setVndExchangeRate(double vndExchangeRate) { this.vndExchangeRate = vndExchangeRate; }
    }

    // -------------------------------------------------------------------------
    // Nested config sections
    // -------------------------------------------------------------------------

    public static class Gemini {
        /**
         * Exact Gemini model id (e.g. "gemini-1.5-flash-002"). Pin an exact version.
         * Injected from the GEMINI_MODEL environment variable; left blank to force explicit
         * configuration.
         */
        private String model = "";

        /** Maximum API calls per minute; free tier is strict (default: 10). */
        private int requestsPerMinute = 10;

        /** Maximum number of retry attempts on transient errors / HTTP 429. */
        private int maxRetries = 5;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

    public static class Upload {
        /** Maximum CSV upload size (default: 10 MB). */
        private long maxFileSizeBytes = 10L * 1024 * 1024;

        public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
        public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    }

    public static class Storage {
        /**
         * Root directory for file-based run/result storage.
         * Each run gets its own sub-directory: {baseDir}/runs/{runId}/
         */
        private String baseDir = "./survey-comments-data";

        /** How many days to retain stored run artefacts (default: 90). */
        private int retentionDays = 90;

        public String getBaseDir() { return baseDir; }
        public void setBaseDir(String baseDir) { this.baseDir = baseDir; }

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    // -------------------------------------------------------------------------
    // Top-level getters / setters
    // -------------------------------------------------------------------------

    public Gemini getGemini() { return gemini; }

    public String getCodebookVersion() { return codebookVersion; }
    public void setCodebookVersion(String codebookVersion) { this.codebookVersion = codebookVersion; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public boolean isRedactStaffNames() { return redactStaffNames; }
    public void setRedactStaffNames(boolean redactStaffNames) { this.redactStaffNames = redactStaffNames; }

    public Upload getUpload() { return upload; }

    public Storage getStorage() { return storage; }

    public Pricing getPricing() { return pricing; }
}

