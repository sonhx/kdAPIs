package com.capa.dto;

public class CapaStatsDto {
    private int total;
    private int pendingClosure; // Chờ duyệt đóng
    private int processing;     // Đang xử lý
    private int overdue;        // Quá hạn
    private int closed;         // Đã khép vòng
    private double effectivenessRate; // Rate of effectiveness achieved (e.g. 85.0)

    public CapaStatsDto() {}

    public CapaStatsDto(int total, int pendingClosure, int processing, int overdue, int closed, double effectivenessRate) {
        this.total = total;
        this.pendingClosure = pendingClosure;
        this.processing = processing;
        this.overdue = overdue;
        this.closed = closed;
        this.effectivenessRate = effectivenessRate;
    }

    // --- Getters & Setters ---
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPendingClosure() { return pendingClosure; }
    public void setPendingClosure(int pendingClosure) { this.pendingClosure = pendingClosure; }

    public int getProcessing() { return processing; }
    public void setProcessing(int processing) { this.processing = processing; }

    public int getOverdue() { return overdue; }
    public void setOverdue(int overdue) { this.overdue = overdue; }

    public int getClosed() { return closed; }
    public void setClosed(int closed) { this.closed = closed; }

    public double getEffectivenessRate() { return effectivenessRate; }
    public void setEffectivenessRate(double effectivenessRate) { this.effectivenessRate = effectivenessRate; }
}
