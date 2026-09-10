package com.kpi;

import com.khcn.openalex.OpenAlexService;
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
public class KpiN308CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiN308CalculationService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private kpiExtend kpiExtendService;

    @Autowired(required = false)
    private OpenAlexService openAlexService;

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
	                log.info("Successfully altered kpi_data_points.notes column to NVARCHAR(MAX).");
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
	        log.info("Triggering initial calculation for KPI N3.08 on application startup...");
	        calculateAndSaveN308(null);
	    } catch (Exception e) {
	        log.error("Failed to calculate KPI N3.08 on startup", e);
	    }
	}*/

    /**
     * Scheduled Monthly Run for KPI N3.08 calculation.
     * Runs at 00:15 AM on the 1st of every month.
     */
    @Scheduled(cron = "0 15 0 1 * ?")
    public void scheduledMonthlyN308Calculation() {
        log.info("Executing scheduled monthly calculation for KPI N3.08 (Tỷ lệ bài báo đăng tạp chí uy tín)...");
        calculateAndSaveN308(null);
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NVARCHAR, val);
    }

    /**
     * Calculate and save KPI N3.08 data point into kpi_data_points.
     * Formula: (Q1-Q2 Papers in Scimago / Total OpenAlex Research Papers) * 100%
     *
     * @param referenceDate Optional date reference for target period (defaults to current date if null).
     * @return JSONObject containing status, totalWorks, q1q2Works, actualValue, notes, etc.
     */
    @Transactional
    public JSONObject calculateAndSaveN308(Date referenceDate) {
        JSONObject result = new JSONObject();
        try {
            Date refDate = referenceDate != null ? referenceDate : new Date();

            // 1. Fetch KPI definition for N3.08 (auto-insert if missing)
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'N3.08' AND (is_deleted = 0 OR is_deleted IS NULL)"
            );

            if (kpiRows.isEmpty()) {
                log.info("KPI N3.08 definition not found in kpi_definitions. Inserting default definition...");
                try {
                    jdbcTemplate.update(
                        "INSERT INTO kpi_definitions (kpi_code, name, category, unit, measurement, source, cycle, target) " +
                        "VALUES ('N3.08', N'Tỷ lệ bài báo đăng tạp chí uy tín', N'N – Nghiên cứu & Chuyển giao', '%', N'Số bài báo Q1, Q2/ tổng số bài báo NCKH × 100%', N'OpenAlex / SCImago Rankings', N'Hàng tháng', 50.0)"
                    );
                    kpiRows = jdbcTemplate.queryForList(
                        "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'N3.08' AND (is_deleted = 0 OR is_deleted IS NULL)"
                    );
                } catch (Exception ex) {
                    log.error("Failed to insert KPI N3.08 definition: {}", ex.getMessage());
                }
            }

            if (kpiRows.isEmpty()) {
                log.warn("KPI N3.08 definition not found in kpi_definitions table.");
                result.put("status", "ERROR");
                result.put("message", "KPI N3.08 definition not found in kpi_definitions.");
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

            // 2. Count total research papers from openalex_works
            String totalWorksSql = "SELECT COUNT(*) FROM openalex_works";
            Integer totalWorks = jdbcTemplate.queryForObject(totalWorksSql, Integer.class);
            if (totalWorks == null) totalWorks = 0;

            if (totalWorks == 0 && openAlexService != null) {
                try {
                    log.info("openalex_works is empty. Triggering OpenAlex works sync...");
                    openAlexService.syncOpenAlexWorks(false);
                    totalWorks = jdbcTemplate.queryForObject(totalWorksSql, Integer.class);
                    if (totalWorks == null) totalWorks = 0;
                } catch (Exception syncEx) {
                    log.error("Failed to sync OpenAlex works: {}", syncEx.getMessage());
                }
            }

            // 3. Count papers ranked Q1-Q2 in Scimago by mapping to scimago_rankings
            String q1q2WorksSql = 
                "SELECT COUNT(*) FROM openalex_works w " +
                "CROSS APPLY ( " +
                "    SELECT TOP 1 best_quartile " +
                "    FROM scimago_rankings " +
                "    WHERE issn_clean = w.issn_clean AND issn_clean IS NOT NULL AND issn_clean <> '' " +
                "    ORDER BY ABS(ranking_year - w.publication_year) ASC, ranking_year DESC " +
                ") r " +
                "WHERE r.best_quartile IN ('Q1', 'Q2')";

            Integer q1q2Works = jdbcTemplate.queryForObject(q1q2WorksSql, Integer.class);
            if (q1q2Works == null) q1q2Works = 0;

            // Calculate percentage
            double ratio = totalWorks > 0 ? (q1q2Works * 100.0 / totalWorks) : 0.0;
            double actualValue = Math.round(ratio * 100.0) / 100.0; // Rounded to 2 decimal places

            String notes = String.format(
                "Tự động tính toán KPI N3.08 từ OpenAlex & SCImago: %d/%d bài báo đăng tạp chí uy tín Q1-Q2 (%.2f%%)",
                q1q2Works, totalWorks, actualValue
            );

            // 4. Upsert into kpi_data_points table
            String checkExistSql = "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
            List<Integer> existingDataIds = jdbcTemplate.query(checkExistSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

            int dataId;
            if (!existingDataIds.isEmpty()) {
                dataId = existingDataIds.get(0);
                String updateSql = "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ? WHERE data_id = ?";
                jdbcTemplate.update(updateSql, actualValue, toNVarChar(notes), dataId);
                log.info("Updated kpi_data_points (data_id: {}) for KPI N3.08: {}%", dataId, actualValue);
            } else {
                String insertSql = "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes) VALUES (?, ?, ?, GETDATE(), 5, ?)";
                jdbcTemplate.update(insertSql, kpiId, periodId, actualValue, toNVarChar(notes));
                dataId = jdbcTemplate.queryForObject(checkExistSql, Integer.class, kpiId, periodId);
                log.info("Inserted new kpi_data_points (data_id: {}) for KPI N3.08: {}%", dataId, actualValue);
            }

            result.put("status", "SUCCESS");
            result.put("kpi_code", "N3.08");
            result.put("kpi_id", kpiId);
            result.put("period_id", periodId);
            result.put("data_id", dataId);
            result.put("total_works", totalWorks);
            result.put("q1_q2_works", q1q2Works);
            result.put("actual_value", actualValue);
            result.put("notes", notes);

        } catch (Exception e) {
            log.error("Error calculating KPI N3.08", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi tính toán KPI N3.08: " + e.getMessage());
        }

        return result;
    }
}
