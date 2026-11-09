package com.surveycomments.discovery;

import java.util.List;

/**
 * Representation of a discovered / proposed theme in the draft codebook.
 */
public class DraftTheme {

    private String id;
    private String nameVi;
    private String definition;
    private List<String> examples;
    private String boundaryRules;
    private String action; // KEEP, MERGE, SPLIT, NEW, REMOVE
    private int evidenceCount;
    private String notes;

    public DraftTheme() {}

    public DraftTheme(String id, String nameVi, String definition, List<String> examples,
                      String boundaryRules, String action, int evidenceCount, String notes) {
        this.id = id;
        this.nameVi = nameVi;
        this.definition = definition;
        this.examples = examples;
        this.boundaryRules = boundaryRules;
        this.action = action;
        this.evidenceCount = evidenceCount;
        this.notes = notes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNameVi() { return nameVi; }
    public void setNameVi(String nameVi) { this.nameVi = nameVi; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }

    public String getBoundaryRules() { return boundaryRules; }
    public void setBoundaryRules(String boundaryRules) { this.boundaryRules = boundaryRules; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public int getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(int evidenceCount) { this.evidenceCount = evidenceCount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

