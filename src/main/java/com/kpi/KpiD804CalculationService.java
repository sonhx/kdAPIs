package com.kpi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class KpiD804CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiD804CalculationService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate; // Primary IQA database connection

    @Autowired
    private kpiExtend kpiExtendService;

    /**
     * Scheduled Daily Run for KPI D8.04 calculation.
     * Runs at 01:00 AM every day (cron = "0 0 1 * * ?").
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void scheduledDailyD804Calculation() {
        log.info("Executing scheduled daily calculation for KPI D8.04 (Tỷ lệ dữ liệu cập nhật trên Dashboard QA)...");
        calculateAndSaveD804(null);
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NVARCHAR, val);
    }

    /**
     * Calculate and save KPI D8.04 data point into kpi_data_points.
     * Formula: (Số lượng KPI được cập nhật / Tổng số KPI) * 100%, rounded to 1 decimal place.
     *
     * Note: "Sau khi chỉ số này được tính toán thì bản thân nó cũng được tính vào là một chỉ số đã được cập nhật."
     * Therefore, D8.04 itself is unconditionally counted as an updated KPI.
     *
     * @param referenceDate Optional date reference for target period (defaults to current date if null).
     * @return JSONObject containing status, totalKpis, updatedKpis, actualValue, notes, etc.
     */
    @Transactional
    public JSONObject calculateAndSaveD804(Date referenceDate) {
        JSONObject result = new JSONObject();
        try {
            Date refDate = referenceDate != null ? referenceDate : new Date();

            // 1. Fetch KPI definition for D8.04 (auto-insert if missing)
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'D8.04' AND (is_deleted = 0 OR is_deleted IS NULL)"
            );

            if (kpiRows.isEmpty()) {
                log.info("KPI D8.04 definition not found in kpi_definitions. Inserting default definition...");
                try {
                    jdbcTemplate.update(
                        "INSERT INTO kpi_definitions (kpi_code, name, category, unit, measurement, source, cycle, target) " +
                        "VALUES ('D8.04', N'Tỷ lệ dữ liệu cập nhật trên Dashboard QA', N'D – Chất lượng số & Dữ liệu', '%', N'KPI được cập nhật/ tổng KPI × 100%', N'Dashboard QA', N'Hàng ngày', 100.0)"
                    );
                    kpiRows = jdbcTemplate.queryForList(
                        "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'D8.04' AND (is_deleted = 0 OR is_deleted IS NULL)"
                    );
                } catch (Exception ex) {
                    log.error("Failed to insert KPI D8.04 definition: {}", ex.getMessage());
                }
            }

            if (kpiRows.isEmpty()) {
                log.warn("KPI D8.04 definition not found in kpi_definitions table.");
                result.put("status", "ERROR");
                result.put("message", "KPI D8.04 definition not found in kpi_definitions.");
                return result;
            }

            int kpiId = ((Number) kpiRows.get(0).get("kpi_id")).intValue();
            Integer cycleId = kpiRows.get(0).get("cycle_id") != null ? ((Number) kpiRows.get(0).get("cycle_id")).intValue() : null;

            // Resolve cycle_type (defaults to monthly/daily if not configured)
            String cycleType = "monthly";
            if (cycleId != null) {
                List<Map<String, Object>> cycleRows = jdbcTemplate.queryForList(
                    "SELECT cycle_type FROM cycle_definitions WHERE cycle_id = ?", cycleId
                );
                if (!cycleRows.isEmpty() && cycleRows.get(0).get("cycle_type") != null) {
                    cycleType = (String) cycleRows.get(0).get("cycle_type");
                }
            } else {
                List<Map<String, Object>> monthlyCycles = jdbcTemplate.queryForList(
                    "SELECT TOP 1 cycle_id FROM cycle_definitions WHERE LOWER(cycle_type) = 'monthly'"
                );
                if (!monthlyCycles.isEmpty()) {
                    cycleId = ((Number) monthlyCycles.get(0).get("cycle_id")).intValue();
                } else {
                    cycleId = 1;
                }
            }

            // Get or create period_instance ID for current period
            int periodId = kpiExtendService.getOrCreatePeriodInstance(cycleId, cycleType, refDate);

            // 2. Refresh cache and fetch all active KPIs with current period data
            kpiExtendService.invalidateKpisWithAssignmentsCache();
            JSONArray allKpis = kpiExtendService.getKpisWithAssignments();
            int totalKpis = allKpis != null ? allKpis.length() : 0;

            if (totalKpis == 0) {
                Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kpi_definitions WHERE (is_deleted = 0 OR is_deleted IS NULL)",
                    Integer.class
                );
                totalKpis = dbCount != null ? dbCount : 0;
            }

            int updatedKpis = 0;
            if (allKpis != null) {
                for (int i = 0; i < allKpis.length(); i++) {
                    JSONObject k = allKpis.getJSONObject(i);
                    String code = k.optString("kpi_code", "").trim();

                    // Requirement: Sau khi D8.04 được tính toán thì bản thân nó cũng được tính vào là một chỉ số đã được cập nhật
                    if ("D8.04".equalsIgnoreCase(code)) {
                        updatedKpis++;
                        continue;
                    }

                    // Check if this KPI has updated value (not without input / not empty)
                    boolean hasUpdatedData = false;
                    JSONArray dps = k.optJSONArray("data_points");
                    Integer targetPeriodId = k.has("period_id") && !k.isNull("period_id") ? k.getInt("period_id") : null;
                    String targetPeriodCode = k.optString("period", "");

                    if (dps != null && dps.length() > 0) {
                        for (int j = 0; j < dps.length(); j++) {
                            JSONObject dp = dps.getJSONObject(j);
                            if (!dp.isNull("actual_value")) {
                                int dpPeriodId = dp.optInt("period_id", -1);
                                String dpPeriodCode = dp.optString("period", "");
                                if ((targetPeriodId != null && targetPeriodId == dpPeriodId) ||
                                    (!targetPeriodCode.isEmpty() && targetPeriodCode.equalsIgnoreCase(dpPeriodCode)) ||
                                    targetPeriodId == null) {
                                    hasUpdatedData = true;
                                    break;
                                }
                            }
                        }

                        // Fallback: check if any recent data point has actual_value
                        if (!hasUpdatedData) {
                            for (int j = 0; j < dps.length(); j++) {
                                JSONObject dp = dps.getJSONObject(j);
                                if (!dp.isNull("actual_value")) {
                                    hasUpdatedData = true;
                                    break;
                                }
                            }
                        }
                    }

                    if (hasUpdatedData) {
                        updatedKpis++;
                    }
                }
            } else {
                // Fallback direct SQL calculation if allKpis is null
                Integer countUpdatedDb = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT dp.kpi_id) FROM kpi_data_points dp " +
                    "INNER JOIN kpi_definitions kd ON dp.kpi_id = kd.kpi_id " +
                    "WHERE (kd.is_deleted = 0 OR kd.is_deleted IS NULL) " +
                    "  AND dp.actual_value IS NOT NULL " +
                    "  AND kd.kpi_code <> 'D8.04'",
                    Integer.class
                );
                updatedKpis = (countUpdatedDb != null ? countUpdatedDb : 0) + 1; // +1 for D8.04 itself
            }

            // Ensure updatedKpis does not exceed totalKpis
            if (updatedKpis > totalKpis && totalKpis > 0) {
                totalKpis = updatedKpis;
            }

            // 3. Calculate percentage ratio rounded to 1 decimal place
            double ratio = totalKpis > 0 ? ((updatedKpis * 100.0) / totalKpis) : 0.0;
            double actualValue = Math.round(ratio * 10.0) / 10.0;

            String notes = String.format(
                "Tự động cập nhật từ hệ thống Dashboard QA: %d/%d KPI đã cập nhật dữ liệu (%.1f%%)",
                updatedKpis, totalKpis, actualValue
            );

            // 4. Upsert into kpi_data_points table in primary IQA database
            String checkExistSql = "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
            List<Integer> existingDataIds = jdbcTemplate.query(checkExistSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

            int dataId;
            if (!existingDataIds.isEmpty()) {
                dataId = existingDataIds.get(0);
                String updateSql = "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ?, entry_source = 'AUTOMATED_SYNC', updated_by_system = 'KpiD804CalculationService' WHERE data_id = ?";
                jdbcTemplate.update(updateSql, actualValue, toNVarChar(notes), dataId);
                log.info("Updated kpi_data_points (data_id: {}) for KPI D8.04: {}%", dataId, actualValue);
            } else {
                String insertSql = "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes, entry_source, updated_by_system) VALUES (?, ?, ?, GETDATE(), 5, ?, 'AUTOMATED_SYNC', 'KpiD804CalculationService')";
                jdbcTemplate.update(insertSql, kpiId, periodId, actualValue, toNVarChar(notes));
                dataId = jdbcTemplate.queryForObject(checkExistSql, Integer.class, kpiId, periodId);
                log.info("Inserted new kpi_data_points (data_id: {}) for KPI D8.04: {}%", dataId, actualValue);
            }

            // 4b. Update last_synced_at on kpi_assignments
            try {
                jdbcTemplate.update("UPDATE kpi_assignments SET last_synced_at = GETDATE(), sync_status = 'SUCCESS' WHERE kpi_id = ?", kpiId);
            } catch (Exception ignored) {}

            // 5. Invalidate cache so UI fetches latest value immediately
            kpiExtendService.invalidateKpisWithAssignmentsCache();

            result.put("status", "SUCCESS");
            result.put("kpi_code", "D8.04");
            result.put("kpi_id", kpiId);
            result.put("period_id", periodId);
            result.put("data_id", dataId);
            result.put("updated_kpis", updatedKpis);
            result.put("total_kpis", totalKpis);
            result.put("actual_value", actualValue);
            result.put("notes", notes);

        } catch (Exception e) {
            log.error("Error calculating KPI D8.04", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi tính toán KPI D8.04: " + e.getMessage());
        }

        return result;
    }
}
