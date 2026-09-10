package com.kpi;

import com.khcn.openalex.OpenAlexService;
import com.khcn.openalex.ScopusWosIngestionService;
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
public class KpiN303CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiN303CalculationService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private kpiExtend kpiExtendService;

    @Autowired(required = false)
    private OpenAlexService openAlexService;

    @Autowired(required = false)
    private ScopusWosIngestionService scopusWosIngestionService;

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
	    java.util.concurrent.CompletableFuture.runAsync(() -> {
	        try {
	            log.info("Triggering initial calculation for KPI N3.03 on application startup...");
	            calculateAndSaveN303(null);
	        } catch (Exception e) {
	            log.error("Failed to calculate KPI N3.03 on startup", e);
	        }
	    });
	}*/

    /**
     * Scheduled Monthly Run for KPI N3.03 calculation.
     * Runs at 00:20 AM on the 1st of every month.
     */
    @Scheduled(cron = "0 20 0 1 * ?")
    public void scheduledMonthlyN303Calculation() {
        log.info("Executing scheduled monthly calculation for KPI N3.03 (Tỷ lệ công bố quốc tế)...");
        calculateAndSaveN303(null);
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NVARCHAR, val);
    }

    /**
     * Calculate and save KPI N3.03 data point into kpi_data_points.
     * Formula: (Scopus/WoS Papers / Total OpenAlex Research Papers) * 100%
     *
     * @param referenceDate Optional date reference for target period (defaults to current date if null).
     * @return JSONObject containing status, totalWorks, scopusWosWorks, actualValue, notes, etc.
     */
    public JSONObject calculateAndSaveN303(Date referenceDate) {
        JSONObject result = new JSONObject();
        try {
            Date refDate = referenceDate != null ? referenceDate : new Date();

            // 1. Fetch KPI definition for N3.03 (auto-insert if missing)
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'N3.03' AND (is_deleted = 0 OR is_deleted IS NULL)"
            );

            if (kpiRows.isEmpty()) {
                log.info("KPI N3.03 definition not found in kpi_definitions. Inserting default definition...");
                try {
                    jdbcTemplate.update(
                        "INSERT INTO kpi_definitions (kpi_code, name, category, unit, measurement, source, cycle, target) " +
                        "VALUES ('N3.03', N'Tỷ lệ công bố quốc tế', N'N – Nghiên cứu & Chuyển giao', '%', N'Số bài báo Scopus/ WoS/ tổng bài báo × 100%', N'OpenAlex / Scopus / WoS', N'Hàng tháng', 70.0)"
                    );
                    kpiRows = jdbcTemplate.queryForList(
                        "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'N3.03' AND (is_deleted = 0 OR is_deleted IS NULL)"
                    );
                } catch (Exception ex) {
                    log.error("Failed to insert KPI N3.03 definition: {}", ex.getMessage());
                }
            }

            if (kpiRows.isEmpty()) {
                log.warn("KPI N3.03 definition not found in kpi_definitions table.");
                result.put("status", "ERROR");
                result.put("message", "KPI N3.03 definition not found in kpi_definitions.");
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

            // Ensure Scopus and WoS database tables are pre-populated
			/*if (scopusWosIngestionService != null) {
			    scopusWosIngestionService.initDatabaseTables();
			}*/

            // 3. Count papers belonging to Scopus or WoS lists by ISSN/eISSN or Journal Source title
            String scopusWosWorksSql = 
                "SELECT COUNT(DISTINCT w.id) " +
                "FROM openalex_works w " +
                "WHERE (w.issn_clean IS NOT NULL AND w.issn_clean <> '' AND ( " +
                "    EXISTS (SELECT 1 FROM scopus_journals s WHERE s.issn_clean = w.issn_clean OR s.eissn_clean = w.issn_clean) " +
                "    OR EXISTS (SELECT 1 FROM wos_journals wos WHERE wos.issn_clean = w.issn_clean OR wos.eissn_clean = w.issn_clean) " +
                ")) " +
                "OR (w.journal_source IS NOT NULL AND w.journal_source <> '' AND ( " +
                "    EXISTS (SELECT 1 FROM scopus_journals s WHERE LOWER(s.title) = LOWER(w.journal_source)) " +
                "    OR EXISTS (SELECT 1 FROM wos_journals wos WHERE LOWER(wos.journal_title) = LOWER(w.journal_source)) " +
                "))";

            Integer scopusWosWorks = jdbcTemplate.queryForObject(scopusWosWorksSql, Integer.class);
            if (scopusWosWorks == null) scopusWosWorks = 0;

            // Calculate percentage
            double ratio = totalWorks > 0 ? (scopusWosWorks * 100.0 / totalWorks) : 0.0;
            double actualValue = Math.round(ratio * 100.0) / 100.0; // Rounded to 2 decimal places

            String notes = String.format(
                "Tự động tính toán KPI N3.03 từ OpenAlex & Scopus/WoS: %d/%d bài báo thuộc Scopus hoặc WoS (%.2f%%)",
                scopusWosWorks, totalWorks, actualValue
            );

            // 4. Upsert into kpi_data_points table
            String checkExistSql = "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
            List<Integer> existingDataIds = jdbcTemplate.query(checkExistSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

            int dataId;
            if (!existingDataIds.isEmpty()) {
                dataId = existingDataIds.get(0);
                String updateSql = "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ? WHERE data_id = ?";
                jdbcTemplate.update(updateSql, actualValue, toNVarChar(notes), dataId);
                log.info("Updated kpi_data_points (data_id: {}) for KPI N3.03: {}%", dataId, actualValue);
            } else {
                String insertSql = "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes) VALUES (?, ?, ?, GETDATE(), 5, ?)";
                jdbcTemplate.update(insertSql, kpiId, periodId, actualValue, toNVarChar(notes));
                dataId = jdbcTemplate.queryForObject(checkExistSql, Integer.class, kpiId, periodId);
                log.info("Inserted new kpi_data_points (data_id: {}) for KPI N3.03: {}%", dataId, actualValue);
            }

            result.put("status", "SUCCESS");
            result.put("kpi_code", "N3.03");
            result.put("kpi_id", kpiId);
            result.put("period_id", periodId);
            result.put("data_id", dataId);
            result.put("total_works", totalWorks);
            result.put("scopus_wos_works", scopusWosWorks);
            result.put("actual_value", actualValue);
            result.put("notes", notes);

        } catch (Exception e) {
            log.error("Error calculating KPI N3.03", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi tính toán KPI N3.03: " + e.getMessage());
        }

        return result;
    }
}
