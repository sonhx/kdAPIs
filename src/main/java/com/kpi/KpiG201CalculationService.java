package com.kpi;

import com.personnel.PersonnelSyncScheduler;
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
public class KpiG201CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiG201CalculationService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private kpiExtend kpiExtendService;

    @Autowired
    private PersonnelSyncScheduler personnelSyncScheduler;

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
	        log.info("Triggering initial calculation for KPI G2.01 on application startup...");
	        calculateAndSaveG201(null);
	    } catch (Exception e) {
	        log.error("Failed to calculate KPI G2.01 on startup", e);
	    }
	}*/

    /**
     * Scheduled Monthly Run for KPI G2.01 calculation.
     * Runs at 00:15 AM on the 1st of every month.
     */
    @Scheduled(cron = "0 15 0 1 * ?")
    public void scheduledMonthlyG201Calculation() {
        log.info("Executing scheduled monthly calculation for KPI G2.01 (Tỷ lệ GV có trình độ tiến sĩ)...");
        calculateAndSaveG201(null);
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NVARCHAR, val);
    }

    /**
     * Calculate and save KPI G2.01 data point into kpi_data_points.
     * Formula: Total PhD Lecturers / Total Lecturers from Personnel table * 100%, rounded to 1 decimal place.
     *
     * @param referenceDate Optional date reference for target period (defaults to current date if null).
     * @return JSONObject containing status, totalLecturers, phdLecturers, actualValue, notes, etc.
     */
    @Transactional
    public JSONObject calculateAndSaveG201(Date referenceDate) {
        JSONObject result = new JSONObject();
        try {
            Date refDate = referenceDate != null ? referenceDate : new Date();

            // 1. Fetch KPI definition for G2.01 (auto-insert if missing)
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'G2.01' AND (is_deleted = 0 OR is_deleted IS NULL)"
            );

            if (kpiRows.isEmpty()) {
                log.info("KPI G2.01 definition not found in kpi_definitions. Inserting default definition...");
                try {
                    jdbcTemplate.update(
                        "INSERT INTO kpi_definitions (kpi_code, name, category, unit, measurement, source, cycle, target) " +
                        "VALUES ('G2.01', N'Tỷ lệ GV có trình độ tiến sĩ', N'G – Giảng viên & Cán bộ', '%', N'Số GV tiến sĩ/ tổng GV cơ hữu × 100%', N'Personnel', N'Hàng tháng', 50.0)"
                    );
                    kpiRows = jdbcTemplate.queryForList(
                        "SELECT kpi_id, cycle_id FROM kpi_definitions WHERE kpi_code = 'G2.01' AND (is_deleted = 0 OR is_deleted IS NULL)"
                    );
                } catch (Exception ex) {
                    log.error("Failed to insert KPI G2.01 definition: {}", ex.getMessage());
                }
            }

            if (kpiRows.isEmpty()) {
                log.warn("KPI G2.01 definition not found in kpi_definitions table.");
                result.put("status", "ERROR");
                result.put("message", "KPI G2.01 definition not found in kpi_definitions.");
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

            // 2. Count total active lecturers and PhD lecturers from personnel table
            int totalLecturers = personnelSyncScheduler != null ? personnelSyncScheduler.getLecturerCount(false) : 0;
            int phdLecturers = personnelSyncScheduler != null ? personnelSyncScheduler.getPhdLecturerCount(false) : 0;

            if (totalLecturers == 0) {
                // Fallback query if service is not initialized
                String totalLecturersSql = "SELECT COUNT(*) FROM personnel WHERE (isDeleted = 0 OR isDeleted IS NULL) AND trangThai = N'Đang làm việc' AND tenChucVu LIKE N'%Giảng viên%'";
                Integer totalCount = jdbcTemplate.queryForObject(totalLecturersSql, Integer.class);
                totalLecturers = totalCount != null ? totalCount : 0;

                String phdLecturersSql = "SELECT COUNT(*) FROM personnel WHERE (isDeleted = 0 OR isDeleted IS NULL) AND trangThai = N'Đang làm việc' AND tenChucVu LIKE N'%Giảng viên%' AND (trinhDoDaoTao LIKE N'%Tiến sĩ%' OR trinhDoDaoTao LIKE N'%Tiến sỹ%' OR trinhDoDaoTao LIKE N'%Ph.D%' OR trinhDoDaoTao LIKE N'%PhD%' OR trinhDoDaoTao LIKE N'%TS%' OR hocHam LIKE N'%Tiến sĩ%' OR hocHam LIKE N'%Tiến sỹ%')";
                Integer phdCount = jdbcTemplate.queryForObject(phdLecturersSql, Integer.class);
                phdLecturers = phdCount != null ? phdCount : 0;
            }

            // 3. Calculate percentage ratio (Ph.D Lecturers / Total Lecturers * 100) and round to 1 decimal place
            double ratio = totalLecturers > 0 ? ((phdLecturers * 100.0) / totalLecturers) : 0.0;
            double actualValue = Math.round(ratio * 10.0) / 10.0; // Rounded to 1 decimal place

            String notes = String.format(
                "Tự động tính toán KPI G2.01 từ Personnel: %d/%d giảng viên có trình độ tiến sĩ (%.1f%%)",
                phdLecturers, totalLecturers, actualValue
            );

            // 4. Upsert into kpi_data_points table
            String checkExistSql = "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
            List<Integer> existingDataIds = jdbcTemplate.query(checkExistSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

            int dataId;
            if (!existingDataIds.isEmpty()) {
                dataId = existingDataIds.get(0);
                String updateSql = "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ? WHERE data_id = ?";
                jdbcTemplate.update(updateSql, actualValue, toNVarChar(notes), dataId);
                log.info("Updated kpi_data_points (data_id: {}) for KPI G2.01: {}%", dataId, actualValue);
            } else {
                String insertSql = "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes) VALUES (?, ?, ?, GETDATE(), 5, ?)";
                jdbcTemplate.update(insertSql, kpiId, periodId, actualValue, toNVarChar(notes));
                dataId = jdbcTemplate.queryForObject(checkExistSql, Integer.class, kpiId, periodId);
                log.info("Inserted new kpi_data_points (data_id: {}) for KPI G2.01: {}%", dataId, actualValue);
            }

            result.put("status", "SUCCESS");
            result.put("kpi_code", "G2.01");
            result.put("kpi_id", kpiId);
            result.put("period_id", periodId);
            result.put("data_id", dataId);
            result.put("phd_lecturers", phdLecturers);
            result.put("total_lecturers", totalLecturers);
            result.put("actual_value", actualValue);
            result.put("notes", notes);

        } catch (Exception e) {
            log.error("Error calculating KPI G2.01", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi tính toán KPI G2.01: " + e.getMessage());
        }

        return result;
    }
}
