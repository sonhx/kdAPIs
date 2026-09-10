package com.kpi;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class KpiK605CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiK605CalculationService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate; // Primary IQA database connection

    @Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate evidenceJdbcTemplate; // kiemdinh database connection

    @Autowired
    private kpiExtend kpiExtendService;

	/*@jakarta.annotation.PostConstruct
	public void initTableSchema() {
	    java.util.concurrent.CompletableFuture.runAsync(() -> {
	        try {
	            String notesColType = jdbcTemplate.queryForObject(
	                "SELECT TYPE_NAME(system_type_id) FROM sys.columns WHERE object_id = OBJECT_ID('kpi_data_points') AND name = 'notes'",
	                String.class
	            );
	            if ("varchar".equalsIgnoreCase(notesColType)) {
	                log.info("Altering kpi_data_points.notes column to NVARCHAR(MAX)...");
	                jdbcTemplate.execute("ALTER TABLE kpi_data_points ALTER COLUMN notes NVARCHAR(MAX);");
	            }
	        } catch (Exception e) {
	            log.warn("Could not check/alter kpi_data_points.notes column: {}", e.getMessage());
	        }
	    });
	}*/

    /**
     * Trigger initial calculation on application startup so live data points are available immediately.
     */
	/*@org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
	public void onApplicationReady() {
	    try {
	        log.info("Triggering initial calculation for KPI K6.05 on application startup...");
	        calculateAndSaveK605(null);
	    } catch (Exception e) {
	        log.error("Failed to calculate KPI K6.05 on startup", e);
	    }
	}*/

    /**
     * Scheduled Monthly Run for KPI K6.05 calculation.
     * Runs at 00:25 AM on the 1st of every month.
     */
    @Scheduled(cron = "0 25 0 1 * ?")
    public void scheduledMonthlyK605Calculation() {
        log.info("Executing scheduled monthly calculation for KPI K6.05 (Tỷ lệ minh chứng QA được số hóa)...");
        calculateAndSaveK605(null);
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NVARCHAR, val);
    }

    /**
     * Calculate and save KPI K6.05 data point into kpi_data_points.
     * Formula: (Digitized proofs with non-null path / Total active proofs registered in kiemdinh.TBL_Minhchung) * 100%, rounded to 1 decimal place.
     *
     * @param referenceDate Optional date reference for target period (defaults to current date if null).
     * @return JSONObject containing status, totalProofs, digitizedProofs, actualValue, notes, etc.
     */
    @Transactional
    public JSONObject calculateAndSaveK605(Date referenceDate) {
        JSONObject result = new JSONObject();
        try {
            Date refDate = referenceDate != null ? referenceDate : new Date();

            // 1. Fetch KPI definition for K6.05 (auto-insert if missing)
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'K6.05' AND (is_deleted = 0 OR is_deleted IS NULL)"
            );

            if (kpiRows.isEmpty()) {
                log.info("KPI K6.05 definition not found in kpi_definitions. Inserting default definition...");
                try {
                    jdbcTemplate.update(
                        "INSERT INTO kpi_definitions (kpi_code, name, category, unit, measurement, source, cycle, target) " +
                        "VALUES ('K6.05', N'Tỷ lệ minh chứng QA được số hóa', N'K – Kiểm định & Đánh giá', '%', N'Số minh chứng có file/link đã tải lên / Tổng minh chứng đã đăng ký × 100%', N'TBL_Minhchung (kiemdinh)', N'Hàng tháng', 80.0)"
                    );
                    kpiRows = jdbcTemplate.queryForList(
                        "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'K6.05' AND (is_deleted = 0 OR is_deleted IS NULL)"
                    );
                } catch (Exception ex) {
                    log.error("Failed to insert KPI K6.05 definition: {}", ex.getMessage());
                }
            }

            if (kpiRows.isEmpty()) {
                log.warn("KPI K6.05 definition not found in kpi_definitions table.");
                result.put("status", "ERROR");
                result.put("message", "KPI K6.05 definition not found in kpi_definitions.");
                return result;
            }

            int kpiId = ((Number) kpiRows.get(0).get("kpi_id")).intValue();
            Integer cycleId = kpiRows.get(0).get("cycle_id") != null ? ((Number) kpiRows.get(0).get("cycle_id")).intValue() : null;

            // Resolve cycle_type (defaults to monthly if not configured)
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

            // 2. Query kiemdinh.TBL_Minhchung for total proofs and digitized proofs (path != null and non-empty)
            String totalProofsSql = "SELECT COUNT(*) FROM TBL_Minhchung WHERE (IsDeleted = 0 OR IsDeleted IS NULL)";
            Integer totalCount = evidenceJdbcTemplate.queryForObject(totalProofsSql, Integer.class);
            int totalProofs = totalCount != null ? totalCount : 0;

            String digitizedProofsSql = "SELECT COUNT(*) FROM TBL_Minhchung WHERE (IsDeleted = 0 OR IsDeleted IS NULL) AND (path IS NOT NULL AND LTRIM(RTRIM(CAST(path AS NVARCHAR(MAX)))) <> '')";
            Integer digitizedCount = evidenceJdbcTemplate.queryForObject(digitizedProofsSql, Integer.class);
            int digitizedProofs = digitizedCount != null ? digitizedCount : 0;

            // 3. Calculate percentage ratio (digitizedProofs / totalProofs * 100) rounded to 1 decimal place
            double ratio = totalProofs > 0 ? ((digitizedProofs * 100.0) / totalProofs) : 0.0;
            double actualValue = Math.round(ratio * 10.0) / 10.0;

            String notes = String.format(
                "Tự động tính toán KPI K6.05 từ kiemdinh.TBL_Minhchung: %d/%d minh chứng QA đã số hóa (%.1f%%)",
                digitizedProofs, totalProofs, actualValue
            );

            // 4. Upsert into kpi_data_points table in primary IQA database
            String checkExistSql = "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
            List<Integer> existingDataIds = jdbcTemplate.query(checkExistSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

            int dataId;
            if (!existingDataIds.isEmpty()) {
                dataId = existingDataIds.get(0);
                String updateSql = "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ? WHERE data_id = ?";
                jdbcTemplate.update(updateSql, actualValue, toNVarChar(notes), dataId);
                log.info("Updated kpi_data_points (data_id: {}) for KPI K6.05: {}%", dataId, actualValue);
            } else {
                String insertSql = "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes) VALUES (?, ?, ?, GETDATE(), 5, ?)";
                jdbcTemplate.update(insertSql, kpiId, periodId, actualValue, toNVarChar(notes));
                dataId = jdbcTemplate.queryForObject(checkExistSql, Integer.class, kpiId, periodId);
                log.info("Inserted new kpi_data_points (data_id: {}) for KPI K6.05: {}%", dataId, actualValue);
            }

            result.put("status", "SUCCESS");
            result.put("kpi_code", "K6.05");
            result.put("kpi_id", kpiId);
            result.put("period_id", periodId);
            result.put("data_id", dataId);
            result.put("digitized_proofs", digitizedProofs);
            result.put("total_proofs", totalProofs);
            result.put("actual_value", actualValue);
            result.put("notes", notes);

        } catch (Exception e) {
            log.error("Error calculating KPI K6.05", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi tính toán KPI K6.05: " + e.getMessage());
        }

        return result;
    }
}
