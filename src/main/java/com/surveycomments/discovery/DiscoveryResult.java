package com.surveycomments.discovery;

import java.util.List;

/**
 * Result returned by the codebook discovery service.
 */
public class DiscoveryResult {

    private String codebookBaseVersion;
    private int totalInputComments;
    private int processedComments;
    private String modelUsed;
    private List<DraftTheme> themes;
    private int promptTokens;
    private int candidateTokens;
    private String rawOutput;

    public DiscoveryResult() {}

    public DiscoveryResult(String codebookBaseVersion, int totalInputComments, int processedComments,
                           String modelUsed, List<DraftTheme> themes, int promptTokens,
                           int candidateTokens, String rawOutput) {
        this.codebookBaseVersion = codebookBaseVersion;
        this.totalInputComments = totalInputComments;
        this.processedComments = processedComments;
        this.modelUsed = modelUsed;
        this.themes = themes;
        this.promptTokens = promptTokens;
        this.candidateTokens = candidateTokens;
        this.rawOutput = rawOutput;
    }

    public String getCodebookBaseVersion() { return codebookBaseVersion; }
    public void setCodebookBaseVersion(String codebookBaseVersion) { this.codebookBaseVersion = codebookBaseVersion; }

    public int getTotalInputComments() { return totalInputComments; }
    public void setTotalInputComments(int totalInputComments) { this.totalInputComments = totalInputComments; }

    public int getProcessedComments() { return processedComments; }
    public void setProcessedComments(int processedComments) { this.processedComments = processedComments; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public List<DraftTheme> getThemes() { return themes; }
    public void setThemes(List<DraftTheme> themes) { this.themes = themes; }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCandidateTokens() { return candidateTokens; }
    public void setCandidateTokens(int candidateTokens) { this.candidateTokens = candidateTokens; }

    public String getRawOutput() { return rawOutput; }
    public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }
}

