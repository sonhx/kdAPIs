package com.capa.dto;

public class CapaSourceDto {
    private Integer sourceId;
    private String sourceCode;
    private String sourceName;
    private String description;
    private Integer sortOrder;
    private Boolean isActive;
    private String createdAt;
    private String updatedAt;

    public CapaSourceDto() {}

    public CapaSourceDto(Integer sourceId, String sourceCode, String sourceName, String description, Integer sortOrder, Boolean isActive, String createdAt, String updatedAt) {
        this.sourceId = sourceId;
        this.sourceCode = sourceCode;
        this.sourceName = sourceName;
        this.description = description;
        this.sortOrder = sortOrder;
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // --- Getters & Setters ---
    public Integer getSourceId() { return sourceId; }
    public void setSourceId(Integer sourceId) { this.sourceId = sourceId; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
