package com.tckt;

import org.apache.poi.ss.usermodel.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/tckt/raw-data", "/tckt/raw-data"})
public class TcktRawDataController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * GET /api/tckt/raw-data/latest?year=2026
     * Fetches the latest raw financial record for the given year, including formatted tableValues for client display.
     */
    @GetMapping("/latest")
    public ResponseEntity<String> getLatestRawData(@RequestParam(value = "year", required = false, defaultValue = "2026") int year) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT TOP 1 * FROM tckt_raw_financial_data WHERE reporting_year = ? AND is_deleted = 0 ORDER BY id DESC",
                year
            );

            JSONObject res = new JSONObject();
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                JSONObject data = buildRawDataJsonObject(row);
                res.put("status", "SUCCESS");
                res.put("data", data);
            } else {
                res.put("status", "EMPTY");
                res.put("data", JSONObject.NULL);
            }
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err.toString());
        }
    }

    /**
     * POST /api/tckt/raw-data/save
     * Saves or updates a raw financial record for P. TCKT and syncs target KPIs to kpi_data_points & kpi_value_versions.
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveRawData(@RequestBody String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            int reportingYear = body.optInt("reportingYear", 2026);
            Double targetTotalRevenue = body.has("targetTotalRevenue") && !body.isNull("targetTotalRevenue") ? body.getDouble("targetTotalRevenue") : null;
            Double totalRevenue = body.has("totalRevenue") && !body.isNull("totalRevenue") ? body.getDouble("totalRevenue") : null;
            Double tuitionRevenue = body.has("tuitionRevenue") && !body.isNull("tuitionRevenue") ? body.getDouble("tuitionRevenue") : null;
            Double serviceActivityRevenue = body.has("serviceActivityRevenue") && !body.isNull("serviceActivityRevenue") ? body.getDouble("serviceActivityRevenue") : null;
            Double trainingExpenditure = body.has("trainingExpenditure") && !body.isNull("trainingExpenditure") ? body.getDouble("trainingExpenditure") : null;
            Double staffDevelopmentExpenditure = body.has("staffDevelopmentExpenditure") && !body.isNull("staffDevelopmentExpenditure") ? body.getDouble("staffDevelopmentExpenditure") : null;
            Double serviceActivityExpenditure = body.has("serviceActivityExpenditure") && !body.isNull("serviceActivityExpenditure") ? body.getDouble("serviceActivityExpenditure") : null;
            Double consultingNetworkingExpenditure = body.has("consultingNetworkingExpenditure") && !body.isNull("consultingNetworkingExpenditure") ? body.getDouble("consultingNetworkingExpenditure") : null;
            Double stateBudgetRevenue = body.has("stateBudgetRevenue") && !body.isNull("stateBudgetRevenue") ? body.getDouble("stateBudgetRevenue") : null;
            Double academyActivityRevenue = body.has("academyActivityRevenue") && !body.isNull("academyActivityRevenue") ? body.getDouble("academyActivityRevenue") : null;
            Double otherSourcesRevenue = body.has("otherSourcesRevenue") && !body.isNull("otherSourcesRevenue") ? body.getDouble("otherSourcesRevenue") : null;
            Double profit = body.has("profit") && !body.isNull("profit") ? body.getDouble("profit") : null;
            String unit = body.optString("unit", "billion");
            String submittedBy = body.optString("submittedBy", "TCKT");

            String sql = "INSERT INTO tckt_raw_financial_data (" +
                "reporting_year, target_total_revenue, total_revenue, tuition_revenue, " +
                "service_activity_revenue, training_expenditure, staff_development_expenditure, " +
                "service_activity_expenditure, consulting_networking_expenditure, state_budget_revenue, " +
                "academy_activity_revenue, other_sources_revenue, profit, unit, submitted_by, is_deleted" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

            jdbcTemplate.update(sql,
                reportingYear, targetTotalRevenue, totalRevenue, tuitionRevenue,
                serviceActivityRevenue, trainingExpenditure, staffDevelopmentExpenditure,
                serviceActivityExpenditure, consultingNetworkingExpenditure, stateBudgetRevenue,
                academyActivityRevenue, otherSourcesRevenue, profit, unit, submittedBy
            );

            // Auto-calculate percentage rates & sync to kpi_data_points & kpi_value_versions
            syncCalculatedKpisToDatabase(reportingYear, totalRevenue, trainingExpenditure,
                serviceActivityExpenditure, consultingNetworkingExpenditure, serviceActivityRevenue, submittedBy);

            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("message", "Đã lưu số liệu thô và đồng bộ kết quả KPI thành công.");
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err.toString());
        }
    }

    /**
     * POST /api/tckt/raw-data/upload
     * Uploads an Excel file containing raw TCKT financial report data, parses values, persists to DB,
     * and auto-syncs calculated KPI percentage values into kpi_data_points & kpi_value_versions.
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadRawDataFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "year", required = false, defaultValue = "2026") int year,
            @RequestParam(value = "unit", required = false, defaultValue = "billion") String unit,
            @RequestParam(value = "submittedBy", required = false, defaultValue = "TCKT") String submittedBy) {

        if (file == null || file.isEmpty()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Tệp đính kèm không hợp lệ hoặc rỗng.");
            return ResponseEntity.badRequest().body(err.toString());
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Double totalRevenue = null;
            Double tuitionRevenue = null;
            Double serviceActivityRevenue = null;
            Double trainingExpenditure = null;
            Double staffDevelopmentExpenditure = null;
            Double serviceActivityExpenditure = null;
            Double consultingNetworkingExpenditure = null;
            Double stateBudgetRevenue = null;
            Double academyActivityRevenue = null;
            Double otherSourcesRevenue = null;
            Double profit = null;

            for (Row row : sheet) {
                if (row == null) continue;
                String label = formatter.formatCellValue(row.getCell(1)).trim().toLowerCase();
                if (label.isEmpty()) label = formatter.formatCellValue(row.getCell(0)).trim().toLowerCase();

                Double numVal = null;
                for (int c = row.getLastCellNum() - 1; c >= 2; c--) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                        numVal = cell.getNumericCellValue();
                        break;
                    } else if (cell != null) {
                        String sVal = formatter.formatCellValue(cell).replaceAll("[^0-9.]", "");
                        if (!sVal.isEmpty()) {
                            try {
                                numVal = Double.parseDouble(sVal);
                                break;
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (numVal != null) {
                    if (label.contains("tổng thu") || label.contains("tong thu")) {
                        totalRevenue = numVal;
                    } else if (label.contains("học phí") || label.contains("hoc phi")) {
                        tuitionRevenue = numVal;
                    } else if (label.contains("chi đào tạo") || label.contains("dao tao")) {
                        trainingExpenditure = numVal;
                    } else if (label.contains("chi nckh") || label.contains("khoa học")) {
                        serviceActivityExpenditure = numVal;
                    } else if (label.contains("chuyển giao") || label.contains("tư vấn")) {
                        consultingNetworkingExpenditure = numVal;
                    } else if (label.contains("dịch vụ") || label.contains("hoạt động sự nghiệp")) {
                        serviceActivityRevenue = numVal;
                    } else if (label.contains("lợi nhuận") || label.contains("loi nhuan")) {
                        profit = numVal;
                    }
                }
            }

            String sql = "INSERT INTO tckt_raw_financial_data (" +
                "reporting_year, target_total_revenue, total_revenue, tuition_revenue, " +
                "service_activity_revenue, training_expenditure, staff_development_expenditure, " +
                "service_activity_expenditure, consulting_networking_expenditure, state_budget_revenue, " +
                "academy_activity_revenue, other_sources_revenue, profit, unit, submitted_by, is_deleted" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

            jdbcTemplate.update(sql,
                year, totalRevenue, totalRevenue, tuitionRevenue,
                serviceActivityRevenue, trainingExpenditure, staffDevelopmentExpenditure,
                serviceActivityExpenditure, consultingNetworkingExpenditure, stateBudgetRevenue,
                academyActivityRevenue, otherSourcesRevenue, profit, unit, submittedBy
            );

            // Auto-calculate percentage rates & sync to kpi_data_points & kpi_value_versions
            syncCalculatedKpisToDatabase(year, totalRevenue, trainingExpenditure,
                serviceActivityExpenditure, consultingNetworkingExpenditure, serviceActivityRevenue, submittedBy);

            // Fetch the inserted record to build complete tableValues payload
            List<Map<String, Object>> latestRows = jdbcTemplate.queryForList(
                "SELECT TOP 1 * FROM tckt_raw_financial_data WHERE reporting_year = ? AND is_deleted = 0 ORDER BY id DESC",
                year
            );

            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("message", "Đã tải lên tệp, tính toán số liệu và đồng bộ bảng KPI thành công.");

            if (!latestRows.isEmpty()) {
                JSONObject dataObj = buildRawDataJsonObject(latestRows.get(0));
                dataObj.put("fileName", file.getOriginalFilename());
                res.put("data", dataObj);
            } else {
                res.put("data", JSONObject.NULL);
            }

            return ResponseEntity.ok(res.toString());

        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Lỗi khi xử lý tệp Excel: " + e.getMessage());
            return ResponseEntity.badRequest().body(err.toString());
        }
    }

    /**
     * Helper method to construct JSON response with formatted tableValues map for UI table display.
     */
    private JSONObject buildRawDataJsonObject(Map<String, Object> row) {
        JSONObject data = new JSONObject();
        data.put("id", row.get("id"));
        data.put("reportingYear", row.get("reporting_year"));
        data.put("targetTotalRevenue", row.get("target_total_revenue"));
        data.put("totalRevenue", row.get("total_revenue"));
        data.put("tuitionRevenue", row.get("tuition_revenue"));
        data.put("serviceActivityRevenue", row.get("service_activity_revenue"));
        data.put("trainingExpenditure", row.get("training_expenditure"));
        data.put("staffDevelopmentExpenditure", row.get("staff_development_expenditure"));
        data.put("serviceActivityExpenditure", row.get("service_activity_expenditure"));
        data.put("consultingNetworkingExpenditure", row.get("consulting_networking_expenditure"));
        data.put("stateBudgetRevenue", row.get("state_budget_revenue"));
        data.put("academyActivityRevenue", row.get("academy_activity_revenue"));
        data.put("otherSourcesRevenue", row.get("other_sources_revenue"));
        data.put("profit", row.get("profit"));
        data.put("unit", row.get("unit"));
        data.put("submittedBy", row.get("submitted_by"));
        data.put("createdAt", row.get("created_at") != null ? row.get("created_at").toString() : null);

        // Build tableValues map matching React frontend TCKT_RAW_ROWS ids (BVH / BVS split)
        JSONObject tableValues = new JSONObject();

        double totRev = row.get("total_revenue") != null ? ((Number) row.get("total_revenue")).doubleValue() : 0.0;
        tableValues.put("A", new JSONObject().put("bvh", Math.round(totRev * 0.6 * 100.0) / 100.0).put("bvs", Math.round(totRev * 0.4 * 100.0) / 100.0));

        double tuiRev = row.get("tuition_revenue") != null ? ((Number) row.get("tuition_revenue")).doubleValue() : 0.0;
        tableValues.put("A_II_1", new JSONObject().put("bvh", Math.round(tuiRev * 0.6 * 100.0) / 100.0).put("bvs", Math.round(tuiRev * 0.4 * 100.0) / 100.0));

        double trainExp = row.get("training_expenditure") != null ? ((Number) row.get("training_expenditure")).doubleValue() : 0.0;
        tableValues.put("B_II_1", new JSONObject().put("bvh", Math.round(trainExp * 0.6 * 100.0) / 100.0).put("bvs", Math.round(trainExp * 0.4 * 100.0) / 100.0));

        double nckhExp = row.get("service_activity_expenditure") != null ? ((Number) row.get("service_activity_expenditure")).doubleValue() : 0.0;
        tableValues.put("B_II_2", new JSONObject().put("bvh", Math.round(nckhExp * 0.6 * 100.0) / 100.0).put("bvs", Math.round(nckhExp * 0.4 * 100.0) / 100.0));

        double transferRev = row.get("consulting_networking_expenditure") != null ? ((Number) row.get("consulting_networking_expenditure")).doubleValue() : 0.0;
        tableValues.put("A_III_1", new JSONObject().put("bvh", Math.round(transferRev * 0.6 * 100.0) / 100.0).put("bvs", Math.round(transferRev * 0.4 * 100.0) / 100.0));

        double servRev = row.get("service_activity_revenue") != null ? ((Number) row.get("service_activity_revenue")).doubleValue() : 0.0;
        tableValues.put("A_III", new JSONObject().put("bvh", Math.round(servRev * 0.6 * 100.0) / 100.0).put("bvs", Math.round(servRev * 0.4 * 100.0) / 100.0));

        data.put("tableValues", tableValues);
        return data;
    }

    /**
     * Helper method to calculate target KPIs (Q7.02, Q7.03, N3.04, Q7.07) from raw data
     * and auto-sync results into kpi_data_points and kpi_value_versions.
     */
    private void syncCalculatedKpisToDatabase(
            int year,
            Double totalRevenue,
            Double trainingExpenditure,
            Double serviceActivityExpenditure,
            Double consultingNetworkingExpenditure,
            Double serviceActivityRevenue,
            String submittedBy) {

        if (totalRevenue == null || totalRevenue <= 0) {
            return;
        }

        Map<String, Double> calculatedKpis = new HashMap<>();

        if (trainingExpenditure != null) {
            calculatedKpis.put("Q7.02", Math.round((trainingExpenditure / totalRevenue * 100.0) * 100.0) / 100.0);
        }
        if (serviceActivityExpenditure != null) {
            calculatedKpis.put("Q7.03", Math.round((serviceActivityExpenditure / totalRevenue * 100.0) * 100.0) / 100.0);
        }
        if (consultingNetworkingExpenditure != null) {
            calculatedKpis.put("N3.04", Math.round((consultingNetworkingExpenditure / totalRevenue * 100.0) * 100.0) / 100.0);
        }
        if (serviceActivityRevenue != null) {
            calculatedKpis.put("Q7.07", Math.round((serviceActivityRevenue / totalRevenue * 100.0) * 100.0) / 100.0);
        }

        for (Map.Entry<String, Double> entry : calculatedKpis.entrySet()) {
            String kpiCode = entry.getKey();
            Double actualValue = entry.getValue();

            try {
                // 1. Find kpi_id from kpi_definitions
                List<Map<String, Object>> kpiDefs = jdbcTemplate.queryForList(
                    "SELECT TOP 1 kpi_id FROM kpi_definitions WHERE kpi_code = ? AND (is_deleted = 0 OR is_deleted IS NULL)",
                    kpiCode
                );
                if (kpiDefs.isEmpty()) continue;
                int kpiId = (int) kpiDefs.get(0).get("kpi_id");

                String notes = String.format("[P. TCKT Tự động tính toán]: Nguồn thu = %,.2f, Giá trị = %s (Tỷ lệ = %.2f%%).",
                    totalRevenue, kpiCode, actualValue);

                // 2. Lookup existing kpi_data_points row
                List<Map<String, Object>> dpRows = jdbcTemplate.queryForList(
                    "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? ORDER BY data_id DESC",
                    kpiId
                );

                int dataId;
                if (!dpRows.isEmpty()) {
                    dataId = (int) dpRows.get(0).get("data_id");
                    String updateDpSql = "UPDATE kpi_data_points SET " +
                        "actual_value = ?, notes = ?, entry_source = 'AUTOMATED_SYNC', " +
                        "updated_by_system = 'TcktRawDataController', updated_at = SYSDATETIME() " +
                        "WHERE data_id = ?";
                    jdbcTemplate.update(updateDpSql, actualValue, notes, dataId);
                } else {
                    String insertDpSql = "INSERT INTO kpi_data_points (" +
                        "kpi_id, period_id, actual_value, target, status_id, notes, " +
                        "entry_source, updated_by_system, updated_at" +
                        ") VALUES (?, 1, ?, 0.0, 5, ?, 'AUTOMATED_SYNC', 'TcktRawDataController', SYSDATETIME())";
                    jdbcTemplate.update(insertDpSql, kpiId, actualValue, notes);

                    dataId = jdbcTemplate.queryForObject(
                        "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? ORDER BY data_id DESC",
                        Integer.class, kpiId
                    );
                }

                // 3. Insert audit entry into kpi_value_versions
                int nextVersionNum = jdbcTemplate.queryForObject(
                    "SELECT ISNULL(MAX(version_number), 0) + 1 FROM kpi_value_versions WHERE data_id = ?",
                    Integer.class, dataId
                );

                String versionSql = "INSERT INTO kpi_value_versions (" +
                    "data_id, actual_value, notes, updated_at, updated_by, " +
                    "change_type, version_number, entry_source, updated_by_system" +
                    ") VALUES (?, ?, ?, SYSDATETIME(), ?, 'AUTOMATED_UPDATE', ?, 'AUTOMATED_SYNC', 'TcktRawDataController')";

                jdbcTemplate.update(versionSql, dataId, actualValue, notes, submittedBy, nextVersionNum);

            } catch (Exception e) {
                System.err.println("Error auto-syncing KPI " + kpiCode + " to kpi_data_points: " + e.getMessage());
            }
        }
    }
}
