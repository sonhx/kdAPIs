package com.kpi;

public class KpiDefinition {
    private Integer kpiId;
    private String kpiCode;          // T1.01, T1.02, etc.
    private String kpiName;           // Description of KPI
    private String category;         // T – Đào tạo & Người học, G – Giảng viên, etc.
    private String unit;              // %, Điểm, Số lượng, Tỷ lệ, Bài, etc.
    private String formula;           // Calculation formula
    private String dataSource;        // Source of data collection
    private String frequency;         // Năm học, Học kỳ, Quý, Hàng năm, etc.

    public KpiDefinition() {
    }

    public KpiDefinition(Integer kpiId, String kpiCode, String kpiName, String category, 
                         String unit, String formula, String dataSource, String frequency) {
        this.kpiId = kpiId;
        this.kpiCode = kpiCode;
        this.kpiName = kpiName;
        this.category = category;
        this.unit = unit;
        this.formula = formula;
        this.dataSource = dataSource;
        this.frequency = frequency;
    }

    // Getters and Setters
    public Integer getKpiId() {
        return kpiId;
    }

    public void setKpiId(Integer kpiId) {
        this.kpiId = kpiId;
    }

    public String getKpiCode() {
        return kpiCode;
    }

    public void setKpiCode(String kpiCode) {
        this.kpiCode = kpiCode;
    }

    public String getKpiName() {
        return kpiName;
    }

    public void setKpiName(String kpiName) {
        this.kpiName = kpiName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    @Override
    public String toString() {
        return "KpiDefinition{" +
                "kpiId=" + kpiId +
                ", kpiCode='" + kpiCode + '\'' +
                ", kpiName='" + kpiName + '\'' +
                ", category='" + category + '\'' +
                ", unit='" + unit + '\'' +
                ", dataSource='" + dataSource + '\'' +
                ", frequency='" + frequency + '\'' +
                '}';
    }
}
