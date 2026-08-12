package com.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NotificationScheduler {

    @Autowired
    private NotificationExtend notificationExtend;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Audit KPI risk thresholds daily and dispatch notifications to Lãnh đạo Học viện & Trưởng đơn vị.
     * Runs every 6 hours or on startup background loop.
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void auditKpiRiskThresholds() {
        try {
            System.out.println("[NotificationScheduler] Running KPI risk threshold audit...");

            String sql =
                "SELECT dp.data_id, k.kpi_code, k.kpi_name, dp.actual_value, k.target, k.unit, dp.department_id, o.ten AS dept_name " +
                "FROM kpi_data_points dp " +
                "JOIN kpi_definitions k ON k.kpi_id = dp.kpi_id " +
                "LEFT JOIN orgs o ON CAST(o.id AS VARCHAR(100)) = CAST(dp.department_id AS VARCHAR(100)) " +
                "WHERE dp.actual_value IS NOT NULL AND k.target IS NOT NULL AND k.target > 0 " +
                "  AND (dp.actual_value < (k.target * 0.85)) " + // Less than 85% of target
                "  AND (dp.is_approved IS NULL OR dp.is_approved = 0)";

            List<Map<String, Object>> riskyRows = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : riskyRows) {
                String kpiCode  = row.get("kpi_code")  != null ? row.get("kpi_code").toString()  : "N/A";
                String kpiName  = row.get("kpi_name")  != null ? row.get("kpi_name").toString()  : "Chỉ số KPI";
                String deptName = row.get("dept_name") != null ? row.get("dept_name").toString() : "Đơn vị";
                String deptId   = row.get("department_id") != null ? row.get("department_id").toString() : null;
                Object actual   = row.get("actual_value");
                Object target   = row.get("target");
                Object unit     = row.get("unit") != null ? row.get("unit").toString() : "%";

                // Check if notification already dispatched recently to prevent duplicate spam
                Integer existingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE notification_code = 'KPI_RISK_HIGH' AND entity_id = ? AND created_at > DATEADD(day, -3, GETDATE())",
                    Integer.class, kpiCode
                );

                if (existingCount != null && existingCount > 0) continue;

                // 1. Strategic alert to Lãnh đạo Học viện
                notificationExtend.dispatchNotification(
                    "KPI_RISK_HIGH", "KPI", "danger",
                    "Cảnh báo rủi ro KPI: " + kpiCode + " (" + deptName + ")",
                    "Chỉ số '" + kpiName + "' giảm dưới ngưỡng an toàn (" + actual + " " + unit + " < " + target + " " + unit + "). Yêu cầu rà soát.",
                    "LANH_DAO", null, "KPI_DATA", kpiCode, "/kpi?code=" + kpiCode, "SYSTEM", null
                );

                // 2. Departmental alert to Trưởng đơn vị
                if (deptId != null) {
                    notificationExtend.dispatchNotification(
                        "KPI_RISK_HIGH", "KPI", "warning",
                        "Cảnh báo rủi ro chỉ số đơn vị: " + kpiCode,
                        "Số liệu thực tế chỉ số '" + kpiName + "' của đơn vị chưa đạt chỉ tiêu (" + actual + " / " + target + " " + unit + ").",
                        "TRUONG_DON_VI", deptId, "KPI_DATA", kpiCode, "/kpi?code=" + kpiCode, "SYSTEM", null
                    );
                }
            }
            System.out.println("[NotificationScheduler] Risk audit completed. Evaluated " + riskyRows.size() + " risk item(s).");
        } catch (Exception e) {
            System.err.println("[NotificationScheduler] Risk audit error: " + e.getMessage());
        }
    }

    /**
     * Audit KPI data entry deadlines (7d, 3d, 1d warning).
     */
    @Scheduled(cron = "0 0 8 * * ?") // Every morning at 8:00 AM
    public void auditDeadlineReminders() {
        try {
            System.out.println("[NotificationScheduler] Running daily deadline audit...");

            String sql =
                "SELECT ka.assignment_id, ka.kpi_code, k.kpi_name, ka.department_id, ka.role, ka.assigned_by, o.ten AS dept_name " +
                "FROM kpi_assignments ka " +
                "JOIN kpi_definitions k ON k.kpi_code = ka.kpi_code " +
                "LEFT JOIN orgs o ON CAST(o.id AS VARCHAR(100)) = CAST(ka.department_id AS VARCHAR(100))";

            List<Map<String, Object>> assignments = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : assignments) {
                String kpiCode  = row.get("kpi_code") != null ? row.get("kpi_code").toString() : "";
                String kpiName  = row.get("kpi_name") != null ? row.get("kpi_name").toString() : "";
                String deptId   = row.get("department_id") != null ? row.get("department_id").toString() : null;
                String role     = row.get("role") != null ? row.get("role").toString() : "A";

                if ("B".equalsIgnoreCase(role)) {
                    // Approver assignment reminder
                    notificationExtend.dispatchNotification(
                        "KPI_DEADLINE_APPROVE", "KPI", "warning",
                        "Sắp đến hạn phê duyệt KPI: " + kpiCode,
                        "Vui lòng kiểm tra và phê duyệt số liệu báo cáo cho chỉ số '" + kpiName + "'.",
                        "TRUONG_DON_VI", deptId, "KPI_DATA", kpiCode, "/kpi?code=" + kpiCode, "SYSTEM", null
                    );
                } else {
                    // Data entry assignment reminder
                    notificationExtend.dispatchNotification(
                        "KPI_DEADLINE_INPUT", "KPI", "info",
                        "Nhắc nhở hạn nộp số liệu KPI: " + kpiCode,
                        "Sắp đến hạn nhập liệu số liệu báo cáo cho chỉ số '" + kpiName + "'.",
                        "CHUYEN_VIEN", deptId, "KPI_DATA", kpiCode, "/kpi?code=" + kpiCode, "SYSTEM", null
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("[NotificationScheduler] Deadline audit error: " + e.getMessage());
        }
    }

    /**
     * Nightly scheduled purge at 2:00 AM based on user retention settings.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void purgeRetentionNotifications() {
        try {
            System.out.println("[NotificationScheduler] Running nightly retention purge...");
            notificationExtend.purgeExpiredNotifications();
        } catch (Exception e) {
            System.err.println("[NotificationScheduler] Retention purge task error: " + e.getMessage());
        }
    }
}
