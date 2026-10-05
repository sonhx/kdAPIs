package com.kpi;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KpiVertexZoneController — REST API Controller for managing per-vertex 8-Dimension threshold zones.
 * Base Path: /kpi-vertex-zones
 */
@RestController
@RequestMapping("/kpi-vertex-zones")
public class KpiVertexZoneController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Helper method to ensure the table dbo.kpi_vertex_threshold_zones exists and is seeded with defaults.
     */
    private void ensureTableExists() {
        try {
            String checkTableSql = "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'kpi_vertex_threshold_zones') " +
                "BEGIN " +
                "    CREATE TABLE dbo.kpi_vertex_threshold_zones ( " +
                "        id INT IDENTITY(1,1) PRIMARY KEY, " +
                "        dimension_code VARCHAR(10) NOT NULL UNIQUE, " +
                "        dimension_name NVARCHAR(100) NOT NULL, " +
                "        target_value DECIMAL(10,2) NOT NULL DEFAULT 100.0, " +
                "        unit NVARCHAR(50) NULL, " +
                "        ok_min DECIMAL(10,2) NOT NULL DEFAULT 100.0, " +
                "        warning_min DECIMAL(10,2) NOT NULL DEFAULT 85.0, " +
                "        danger_max DECIMAL(10,2) NOT NULL DEFAULT 84.9, " +
                "        notes NVARCHAR(500) NULL, " +
                "        created_at DATETIME2 DEFAULT SYSDATETIME(), " +
                "        updated_at DATETIME2 DEFAULT SYSDATETIME() " +
                "    ); " +
                "END";
            jdbcTemplate.execute(checkTableSql);

            String seedSql = "IF NOT EXISTS (SELECT 1 FROM dbo.kpi_vertex_threshold_zones WHERE dimension_code = 'T') " +
                "BEGIN " +
                "    INSERT INTO dbo.kpi_vertex_threshold_zones (dimension_code, dimension_name, target_value, unit, ok_min, warning_min, danger_max, notes) " +
                "    VALUES " +
                "    ('T', N'Đào tạo & Người học', 80.0, '%', 100.0, 85.0, 84.9, N'Chỉ số chính T1.03'), " +
                "    ('G', N'Giảng viên & Cán bộ', 45.0, '%', 100.0, 85.0, 84.9, N'Chỉ số chính G2.01'), " +
                "    ('N', N'Nghiên cứu & Chuyển giao', 3.0, 'papers', 100.0, 85.0, 84.9, N'Chỉ số chính N3.03'), " +
                "    ('H', N'Hỗ trợ sinh viên', 90.0, '%', 100.0, 85.0, 84.9, N'Chỉ số chính H4.08'), " +
                "    ('C', N'Cải tiến liên tục', 85.0, '%', 100.0, 85.0, 84.9, N'Chỉ số chính C5.02'), " +
                "    ('K', N'Kiểm định & Đánh giá', 70.0, '%', 100.0, 85.0, 84.9, N'Chỉ số chính K6.01'), " +
                "    ('Q', N'Quản trị & Nguồn lực', 75.0, '%', 100.0, 85.0, 84.9, N'Chỉ số chính Q7.02'), " +
                "    ('D', N'Chất lượng số & Dữ liệu', 90.0, '%', 100.0, 85.0, 84.9, N'Chỉ số chính D8.01'); " +
                "END";
            jdbcTemplate.execute(seedSql);
        } catch (Exception e) {
            System.err.println("Error ensuring kpi_vertex_threshold_zones table exists: " + e.getMessage());
        }
    }

    /**
     * GET /kpi-vertex-zones/list
     */
    @GetMapping("/list")
    public String getThresholds() {
        System.out.println("-------getThresholds");
        JSONObject jout = new JSONObject();
        try {
            ensureTableExists();

            String sql = "SELECT dimension_code, dimension_name, target_value, unit, ok_min, warning_min, danger_max FROM dbo.kpi_vertex_threshold_zones";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            JSONObject thresholdsObj = new JSONObject();
            for (Map<String, Object> row : rows) {
                String code = String.valueOf(row.get("dimension_code")).trim();
                JSONObject item = new JSONObject();
                item.put("code", code);
                item.put("name", row.get("dimension_name") != null ? row.get("dimension_name") : "");
                item.put("target", row.get("target_value") != null ? ((Number) row.get("target_value")).doubleValue() : 100.0);
                item.put("unit", row.get("unit") != null ? row.get("unit") : "");
                item.put("okMin", row.get("ok_min") != null ? ((Number) row.get("ok_min")).doubleValue() : 100.0);
                item.put("warningMin", row.get("warning_min") != null ? ((Number) row.get("warning_min")).doubleValue() : 85.0);
                item.put("dangerMax", row.get("danger_max") != null ? ((Number) row.get("danger_max")).doubleValue() : 84.9);
                thresholdsObj.put(code, item);
            }

            jout.put("code", 200);
            jout.put("status", "SUCCESS");
            jout.put("description", "Thành công");
            jout.put("thresholds", thresholdsObj);

        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("status", "ERROR");
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }

    /**
     * POST /kpi-vertex-zones/save
     */
    @PostMapping("/save")
    public String saveThresholds(@RequestBody String sReq) {
        System.out.println("-------saveThresholds: " + sReq);
        JSONObject jout = new JSONObject();
        try {
            ensureTableExists();

            JSONObject jin = new JSONObject(sReq);
            JSONObject thresholdsObj = jin.optJSONObject("thresholds");

            if (thresholdsObj == null || thresholdsObj.length() == 0) {
                jout.put("code", 400);
                jout.put("status", "ERROR");
                jout.put("description", "Dữ liệu thresholds không hợp lệ");
                return jout.toString();
            }

            for (String code : thresholdsObj.keySet()) {
                JSONObject item = thresholdsObj.getJSONObject(code);
                double okMin = item.optDouble("okMin", 100.0);
                double warningMin = item.optDouble("warningMin", 85.0);
                double dangerMax = item.optDouble("dangerMax", warningMin);

                String mergeSql = "IF EXISTS (SELECT 1 FROM dbo.kpi_vertex_threshold_zones WHERE dimension_code = ?) " +
                    "BEGIN " +
                    "    UPDATE dbo.kpi_vertex_threshold_zones " +
                    "    SET ok_min = ?, warning_min = ?, danger_max = ?, updated_at = SYSDATETIME() " +
                    "    WHERE dimension_code = ?; " +
                    "END " +
                    "ELSE " +
                    "BEGIN " +
                    "    INSERT INTO dbo.kpi_vertex_threshold_zones (dimension_code, dimension_name, ok_min, warning_min, danger_max) " +
                    "    VALUES (?, ?, ?, ?, ?); " +
                    "END";

                jdbcTemplate.update(mergeSql, code, okMin, warningMin, dangerMax, code, code, code, okMin, warningMin, dangerMax);
            }

            jout.put("code", 200);
            jout.put("status", "SUCCESS");
            jout.put("description", "Lưu cấu hình ngưỡng thành công!");

        } catch (Exception e) {
            e.printStackTrace();
            jout.put("code", 500);
            jout.put("status", "ERROR");
            jout.put("description", "Server error: " + e.getMessage());
        }
        return jout.toString();
    }
}
