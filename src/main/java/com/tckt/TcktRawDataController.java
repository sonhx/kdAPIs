package com.tckt;

import org.apache.poi.ss.usermodel.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/tckt/raw-data", "/tckt/raw-data", "/api/tckt"})
public class TcktRawDataController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * GET /api/tckt/raw-data/template or /api/tckt/template
     * Downloads the standard Excel template containing 'KetQuaThuChi' (5 indicators with BVH, BVS & Toàn Học viện) and 'Huongdan' sheet.
     */
    @GetMapping({"/template", "/download-template"})
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            String[] possiblePaths = {
                "e:/eclipse-workspace/qa-ims/apps/qa-dashboard/public/templates/template_ket_qua_thu_chi.xlsx",
                "e:/eclipse-workspace/qa-ims/public/templates/template_ket_qua_thu_chi.xlsx",
                "src/main/resources/templates/template_ket_qua_thu_chi.xlsx",
                "src/main/resources/static/templates/template_ket_qua_thu_chi.xlsx"
            };

            for (String p : possiblePaths) {
                File f = new File(p);
                if (f.exists() && f.canRead()) {
                    byte[] data = Files.readAllBytes(Paths.get(p));
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                    headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"template_ket_qua_thu_chi.xlsx\"");
                    headers.setContentLength(data.length);
                    return ResponseEntity.ok().headers(headers).body(data);
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/tckt/raw-data/latest?year=2026
     * Fetches the latest raw financial record for the given year, including BVH, BVS and Toàn Học viện figures.
     */
    @GetMapping("/latest")
    public ResponseEntity<String> getLatestRawData(@RequestParam(value = "year", required = false) Integer year) {
        try {
            List<Map<String, Object>> rows = (year != null)
                ? jdbcTemplate.queryForList(
                    "SELECT TOP 1 * FROM tckt_raw_financial_data WHERE reporting_year = ? AND is_deleted = 0 ORDER BY id DESC", year)
                : jdbcTemplate.queryForList(
                    "SELECT TOP 1 * FROM tckt_raw_financial_data WHERE is_deleted = 0 ORDER BY id DESC");

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
     * Saves or updates financial figures (BVH, BVS, Toàn Học viện) and auto-syncs the 4 target KPIs (N3.04, Q7.02, Q7.03, Q7.07).
     */
    @PostMapping("/save")
    public ResponseEntity<String> saveRawData(@RequestBody String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            int reportingYear = body.optInt("reportingYear", 2026);

            // 1. Tổng doanh thu (Mẫu số chung)
            Double totalRevenueBvh = optDouble(body, "totalRevenueBvh", "tongDoanhThuBvh");
            Double totalRevenueBvs = optDouble(body, "totalRevenueBvs", "tongDoanhThuBvs");
            Double totalRevenue = optDouble(body, "totalRevenue", "tongDoanhThu");
            if (totalRevenue == null && (totalRevenueBvh != null || totalRevenueBvs != null)) {
                totalRevenue = (totalRevenueBvh != null ? totalRevenueBvh : 0.0) + (totalRevenueBvs != null ? totalRevenueBvs : 0.0);
            }

            // 2. Doanh thu từ hoạt động chuyển giao (Tử số N3.04)
            Double transferRevenueBvh = optDouble(body, "transferRevenueBvh", "doanhThuChuyenGiaoBvh");
            Double transferRevenueBvs = optDouble(body, "transferRevenueBvs", "doanhThuChuyenGiaoBvs");
            Double transferRevenue = optDouble(body, "transferRevenue", "doanhThuChuyenGiao", "consultingNetworkingExpenditure");
            if (transferRevenue == null && (transferRevenueBvh != null || transferRevenueBvs != null)) {
                transferRevenue = (transferRevenueBvh != null ? transferRevenueBvh : 0.0) + (transferRevenueBvs != null ? transferRevenueBvs : 0.0);
            }

            // 3. Chi cho đào tạo (Tử số Q7.02)
            Double trainingExpenditureBvh = optDouble(body, "trainingExpenditureBvh", "chiDaoTaoBvh");
            Double trainingExpenditureBvs = optDouble(body, "trainingExpenditureBvs", "chiDaoTaoBvs");
            Double trainingExpenditure = optDouble(body, "trainingExpenditure", "chiDaoTao");
            if (trainingExpenditure == null && (trainingExpenditureBvh != null || trainingExpenditureBvs != null)) {
                trainingExpenditure = (trainingExpenditureBvh != null ? trainingExpenditureBvh : 0.0) + (trainingExpenditureBvs != null ? trainingExpenditureBvs : 0.0);
            }

            // 4. Chi cho NCKH (Tử số Q7.03)
            Double researchExpenditureBvh = optDouble(body, "researchExpenditureBvh", "chiNckhBvh");
            Double researchExpenditureBvs = optDouble(body, "researchExpenditureBvs", "chiNckhBvs");
            Double researchExpenditure = optDouble(body, "researchExpenditure", "chiNckh", "serviceActivityExpenditure");
            if (researchExpenditure == null && (researchExpenditureBvh != null || researchExpenditureBvs != null)) {
                researchExpenditure = (researchExpenditureBvh != null ? researchExpenditureBvh : 0.0) + (researchExpenditureBvs != null ? researchExpenditureBvs : 0.0);
            }

            // 5. Thu ngoài ngân sách (Tử số Q7.07)
            Double nonBudgetRevenueBvh = optDouble(body, "nonBudgetRevenueBvh", "thuNgoaiNganSachBvh");
            Double nonBudgetRevenueBvs = optDouble(body, "nonBudgetRevenueBvs", "thuNgoaiNganSachBvs");
            Double nonBudgetRevenue = optDouble(body, "nonBudgetRevenue", "thuNgoaiNganSach", "serviceActivityRevenue");
            if (nonBudgetRevenue == null && (nonBudgetRevenueBvh != null || nonBudgetRevenueBvs != null)) {
                nonBudgetRevenue = (nonBudgetRevenueBvh != null ? nonBudgetRevenueBvh : 0.0) + (nonBudgetRevenueBvs != null ? nonBudgetRevenueBvs : 0.0);
            }

            String unit = body.optString("unit", "tỷ VNĐ");
            String submittedBy = body.optString("submittedBy", "TCKT");
            String notes = body.optString("notes", null);
            String evidenceLink = body.optString("evidenceLink", null);
            String evidenceFileName = body.optString("evidenceFileName", null);
            String tableValuesJson = (body.has("tableValues") && !body.isNull("tableValues"))
                ? body.getJSONObject("tableValues").toString() : null;

            String sql = "INSERT INTO tckt_raw_financial_data (" +
                "reporting_year, target_total_revenue, total_revenue, " +
                "total_revenue_bvh, total_revenue_bvs, " +
                "transfer_revenue, transfer_revenue_bvh, transfer_revenue_bvs, " +
                "training_expenditure, training_expenditure_bvh, training_expenditure_bvs, " +
                "research_expenditure, research_expenditure_bvh, research_expenditure_bvs, " +
                "non_budget_revenue, non_budget_revenue_bvh, non_budget_revenue_bvs, " +
                "service_activity_revenue, service_activity_expenditure, consulting_networking_expenditure, " +
                "unit, submitted_by, table_values_json, notes, evidence_link, evidence_file_name, is_deleted" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

            jdbcTemplate.update(sql,
                reportingYear, totalRevenue, totalRevenue,
                totalRevenueBvh, totalRevenueBvs,
                transferRevenue, transferRevenueBvh, transferRevenueBvs,
                trainingExpenditure, trainingExpenditureBvh, trainingExpenditureBvs,
                researchExpenditure, researchExpenditureBvh, researchExpenditureBvs,
                nonBudgetRevenue, nonBudgetRevenueBvh, nonBudgetRevenueBvs,
                nonBudgetRevenue, researchExpenditure, transferRevenue,
                unit, submittedBy, tableValuesJson, notes, evidenceLink, evidenceFileName
            );

            // Auto-calculate percentage rates & sync to kpi_data_points & kpi_value_versions for the whole institute
            syncCalculatedKpisToDatabase(reportingYear, totalRevenue, trainingExpenditure,
                researchExpenditure, transferRevenue, nonBudgetRevenue,
                totalRevenueBvh, totalRevenueBvs,
                trainingExpenditureBvh, trainingExpenditureBvs,
                researchExpenditureBvh, researchExpenditureBvs,
                transferRevenueBvh, transferRevenueBvs,
                nonBudgetRevenueBvh, nonBudgetRevenueBvs,
                submittedBy);

            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("message", "Đã lưu số liệu tài chính BVH, BVS và Toàn Học viện, đồng bộ kết quả KPI thành công.");
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
     * Uploads an Excel file containing BVH, BVS and Toàn Học viện columns,
     * parses values, persists to DB, and auto-syncs calculated KPI percentage values.
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadRawDataFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "year", required = false, defaultValue = "2026") int year,
            @RequestParam(value = "unit", required = false, defaultValue = "tỷ VNĐ") String unit,
            @RequestParam(value = "submittedBy", required = false, defaultValue = "TCKT") String submittedBy) {

        if (file == null || file.isEmpty()) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", "Tệp đính kèm không hợp lệ hoặc rỗng.");
            return ResponseEntity.badRequest().body(err.toString());
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            // Locate data sheet (ignore sheets whose name contains 'huong' or 'guide')
            Sheet dataSheet = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String sheetName = workbook.getSheetName(i).trim().toLowerCase();
                if (!sheetName.contains("huong") && !sheetName.contains("guide") && !sheetName.contains("hướng")) {
                    dataSheet = workbook.getSheetAt(i);
                    break;
                }
            }
            if (dataSheet == null) {
                dataSheet = workbook.getSheetAt(0);
            }

            DataFormatter formatter = new DataFormatter();

            // Extract unit from metadata rows (e.g. Row 3 or 4) if present
            for (int r = 0; r <= 6; r++) {
                Row metaRow = dataSheet.getRow(r);
                if (metaRow == null) continue;
                for (int c = 0; c < metaRow.getLastCellNum(); c++) {
                    String cellText = formatter.formatCellValue(metaRow.getCell(c)).trim().toLowerCase();
                    if (cellText.contains("đơn vị tính") || cellText.contains("don vi tinh")) {
                        if (c + 1 < metaRow.getLastCellNum()) {
                            String metaUnit = formatter.formatCellValue(metaRow.getCell(c + 1)).trim();
                            if (!metaUnit.isEmpty()) {
                                unit = metaUnit;
                            }
                        }
                    }
                }
            }

            Double totalRevenueBvh = null, totalRevenueBvs = null, totalRevenue = null;
            Double transferRevenueBvh = null, transferRevenueBvs = null, transferRevenue = null;
            Double trainingExpenditureBvh = null, trainingExpenditureBvs = null, trainingExpenditure = null;
            Double researchExpenditureBvh = null, researchExpenditureBvs = null, researchExpenditure = null;
            Double nonBudgetRevenueBvh = null, nonBudgetRevenueBvs = null, nonBudgetRevenue = null;

            for (Row row : dataSheet) {
                if (row == null) continue;
                String label = formatter.formatCellValue(row.getCell(1)).trim().toLowerCase();
                if (label.isEmpty()) label = formatter.formatCellValue(row.getCell(0)).trim().toLowerCase();

                // Extract cells: Col 2 (BVH), Col 3 (BVS), Col 4 (Toàn Học viện)
                Double c2Val = getNumericCellValue(row.getCell(2), formatter);
                Double c3Val = getNumericCellValue(row.getCell(3), formatter);
                Double c4Val = getNumericCellValue(row.getCell(4), formatter);

                // If c4Val (Toàn Học viện) is null but c2 and c3 are given, sum them
                if (c4Val == null && (c2Val != null || c3Val != null)) {
                    c4Val = (c2Val != null ? c2Val : 0.0) + (c3Val != null ? c3Val : 0.0);
                }

                // If only 1 number was provided in the row (e.g., old single-column template)
                if (c2Val == null && c3Val == null && c4Val == null) {
                    for (int c = row.getLastCellNum() - 1; c >= 2; c--) {
                        Double val = getNumericCellValue(row.getCell(c), formatter);
                        if (val != null) {
                            c4Val = val;
                            break;
                        }
                    }
                }

                if (c4Val != null || c2Val != null || c3Val != null) {
                    if (label.contains("tổng doanh thu") || label.contains("tong doanh thu") || label.contains("tổng thu") || label.contains("tong thu")) {
                        totalRevenueBvh = c2Val;
                        totalRevenueBvs = c3Val;
                        totalRevenue = c4Val;
                    } else if (label.contains("chuyển giao") || label.contains("chuyen giao")) {
                        transferRevenueBvh = c2Val;
                        transferRevenueBvs = c3Val;
                        transferRevenue = c4Val;
                    } else if (label.contains("đào tạo") || label.contains("dao tao")) {
                        trainingExpenditureBvh = c2Val;
                        trainingExpenditureBvs = c3Val;
                        trainingExpenditure = c4Val;
                    } else if (label.contains("nckh") || label.contains("khoa học") || label.contains("nghiên cứu")) {
                        researchExpenditureBvh = c2Val;
                        researchExpenditureBvs = c3Val;
                        researchExpenditure = c4Val;
                    } else if (label.contains("ngoài ngân sách") || label.contains("thu ngoài") || label.contains("ngoai ngan sach") || label.contains("dịch vụ")) {
                        nonBudgetRevenueBvh = c2Val;
                        nonBudgetRevenueBvs = c3Val;
                        nonBudgetRevenue = c4Val;
                    }
                }
            }

            if (totalRevenue == null || totalRevenue <= 0) {
                JSONObject err = new JSONObject();
                err.put("status", "ERROR");
                err.put("message", "Tệp Excel không chứa giá trị 'Tổng doanh thu' hợp lệ (> 0).");
                return ResponseEntity.badRequest().body(err.toString());
            }

            String sql = "INSERT INTO tckt_raw_financial_data (" +
                "reporting_year, target_total_revenue, total_revenue, " +
                "total_revenue_bvh, total_revenue_bvs, " +
                "transfer_revenue, transfer_revenue_bvh, transfer_revenue_bvs, " +
                "training_expenditure, training_expenditure_bvh, training_expenditure_bvs, " +
                "research_expenditure, research_expenditure_bvh, research_expenditure_bvs, " +
                "non_budget_revenue, non_budget_revenue_bvh, non_budget_revenue_bvs, " +
                "service_activity_revenue, service_activity_expenditure, consulting_networking_expenditure, " +
                "unit, submitted_by, is_deleted" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";

            jdbcTemplate.update(sql,
                year, totalRevenue, totalRevenue,
                totalRevenueBvh, totalRevenueBvs,
                transferRevenue, transferRevenueBvh, transferRevenueBvs,
                trainingExpenditure, trainingExpenditureBvh, trainingExpenditureBvs,
                researchExpenditure, researchExpenditureBvh, researchExpenditureBvs,
                nonBudgetRevenue, nonBudgetRevenueBvh, nonBudgetRevenueBvs,
                nonBudgetRevenue, researchExpenditure, transferRevenue,
                unit, submittedBy
            );

            // Auto-calculate percentage rates & sync to kpi_data_points & kpi_value_versions for the whole institute
            syncCalculatedKpisToDatabase(year, totalRevenue, trainingExpenditure,
                researchExpenditure, transferRevenue, nonBudgetRevenue,
                totalRevenueBvh, totalRevenueBvs,
                trainingExpenditureBvh, trainingExpenditureBvs,
                researchExpenditureBvh, researchExpenditureBvs,
                transferRevenueBvh, transferRevenueBvs,
                nonBudgetRevenueBvh, nonBudgetRevenueBvs,
                submittedBy);

            // Fetch the inserted record to return updated payload
            List<Map<String, Object>> latestRows = jdbcTemplate.queryForList(
                "SELECT TOP 1 * FROM tckt_raw_financial_data WHERE reporting_year = ? AND is_deleted = 0 ORDER BY id DESC",
                year
            );

            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("message", "Đã tải lên tệp Excel (BVH, BVS & Toàn Học viện), tính toán số liệu và đồng bộ 4 chỉ số KPI thành công.");

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

    private Double getNumericCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (Exception ignored) {
                String sVal = formatter.formatCellValue(cell).replaceAll("[^0-9.]", "");
                if (!sVal.isEmpty()) {
                    try { return Double.parseDouble(sVal); } catch (Exception e) { return null; }
                }
            }
        } else {
            String sVal = formatter.formatCellValue(cell).replaceAll("[^0-9.]", "");
            if (!sVal.isEmpty()) {
                try { return Double.parseDouble(sVal); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Double optDouble(JSONObject obj, String... keys) {
        for (String k : keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                try {
                    return obj.getDouble(k);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Helper method to construct JSON response with BVH, BVS and Toàn Học viện financial figures.
     */
    private JSONObject buildRawDataJsonObject(Map<String, Object> row) {
        JSONObject data = new JSONObject();
        data.put("id", row.get("id"));
        data.put("reportingYear", row.get("reporting_year"));
        data.put("targetTotalRevenue", row.get("target_total_revenue"));

        // 1. Tổng doanh thu
        data.put("totalRevenueBvh", row.get("total_revenue_bvh"));
        data.put("totalRevenueBvs", row.get("total_revenue_bvs"));
        data.put("totalRevenue", row.get("total_revenue"));

        // 2. Doanh thu từ hoạt động chuyển giao (N3.04)
        Object tr = row.get("transfer_revenue");
        if (tr == null) tr = row.get("consulting_networking_expenditure");
        data.put("transferRevenue", tr);
        data.put("transferRevenueBvh", row.get("transfer_revenue_bvh"));
        data.put("transferRevenueBvs", row.get("transfer_revenue_bvs"));
        data.put("consultingNetworkingExpenditure", tr);

        // 3. Chi cho đào tạo (Q7.02)
        data.put("trainingExpenditure", row.get("training_expenditure"));
        data.put("trainingExpenditureBvh", row.get("training_expenditure_bvh"));
        data.put("trainingExpenditureBvs", row.get("training_expenditure_bvs"));

        // 4. Chi cho NCKH (Q7.03)
        Object re = row.get("research_expenditure");
        if (re == null) re = row.get("service_activity_expenditure");
        data.put("researchExpenditure", re);
        data.put("researchExpenditureBvh", row.get("research_expenditure_bvh"));
        data.put("researchExpenditureBvs", row.get("research_expenditure_bvs"));
        data.put("serviceActivityExpenditure", re);

        // 5. Thu ngoài ngân sách (Q7.07)
        Object nb = row.get("non_budget_revenue");
        if (nb == null) nb = row.get("service_activity_revenue");
        data.put("nonBudgetRevenue", nb);
        data.put("nonBudgetRevenueBvh", row.get("non_budget_revenue_bvh"));
        data.put("nonBudgetRevenueBvs", row.get("non_budget_revenue_bvs"));
        data.put("serviceActivityRevenue", nb);

        data.put("unit", row.get("unit") != null ? row.get("unit") : "tỷ VNĐ");
        data.put("notes", row.get("notes"));
        data.put("evidenceLink", row.get("evidence_link"));
        data.put("evidenceFileName", row.get("evidence_file_name"));
        data.put("submittedBy", row.get("submitted_by"));
        data.put("createdAt", row.get("created_at") != null ? row.get("created_at").toString() : null);

        // Build clean tableValues representation for UI
        JSONObject tv = new JSONObject();
        tv.put("totalRevenue", new JSONObject()
            .put("bvh", row.get("total_revenue_bvh"))
            .put("bvs", row.get("total_revenue_bvs"))
            .put("total", row.get("total_revenue")));
        tv.put("transferRevenue", new JSONObject()
            .put("bvh", row.get("transfer_revenue_bvh"))
            .put("bvs", row.get("transfer_revenue_bvs"))
            .put("total", tr));
        tv.put("trainingExpenditure", new JSONObject()
            .put("bvh", row.get("training_expenditure_bvh"))
            .put("bvs", row.get("training_expenditure_bvs"))
            .put("total", row.get("training_expenditure")));
        tv.put("researchExpenditure", new JSONObject()
            .put("bvh", row.get("research_expenditure_bvh"))
            .put("bvs", row.get("research_expenditure_bvs"))
            .put("total", re));
        tv.put("nonBudgetRevenue", new JSONObject()
            .put("bvh", row.get("non_budget_revenue_bvh"))
            .put("bvs", row.get("non_budget_revenue_bvs"))
            .put("total", nb));
        data.put("tableValues", tv);

        return data;
    }

    /**
     * Helper method to calculate target KPIs (N3.04, Q7.02, Q7.03, Q7.07) for the whole institute from raw data
     * and auto-sync results into kpi_data_points and kpi_value_versions.
     */
    private void syncCalculatedKpisToDatabase(
            int year,
            Double totalRevenue,
            Double trainingExpenditure,
            Double researchExpenditure,
            Double transferRevenue,
            Double nonBudgetRevenue,
            Double totalRevenueBvh, Double totalRevenueBvs,
            Double trainingExpenditureBvh, Double trainingExpenditureBvs,
            Double researchExpenditureBvh, Double researchExpenditureBvs,
            Double transferRevenueBvh, Double transferRevenueBvs,
            Double nonBudgetRevenueBvh, Double nonBudgetRevenueBvs,
            String submittedBy) {

        if (totalRevenue == null || totalRevenue <= 0) {
            return;
        }

        Map<String, Object[]> calculatedKpis = new HashMap<>();

        if (transferRevenue != null) {
            calculatedKpis.put("N3.04", new Object[]{
                Math.round((transferRevenue / totalRevenue * 100.0) * 100.0) / 100.0,
                "Doanh thu từ hoạt động chuyển giao", transferRevenue,
                transferRevenueBvh, transferRevenueBvs
            });
        }
        if (trainingExpenditure != null) {
            calculatedKpis.put("Q7.02", new Object[]{
                Math.round((trainingExpenditure / totalRevenue * 100.0) * 100.0) / 100.0,
                "Chi cho đào tạo", trainingExpenditure,
                trainingExpenditureBvh, trainingExpenditureBvs
            });
        }
        if (researchExpenditure != null) {
            calculatedKpis.put("Q7.03", new Object[]{
                Math.round((researchExpenditure / totalRevenue * 100.0) * 100.0) / 100.0,
                "Chi cho NCKH", researchExpenditure,
                researchExpenditureBvh, researchExpenditureBvs
            });
        }
        if (nonBudgetRevenue != null) {
            calculatedKpis.put("Q7.07", new Object[]{
                Math.round((nonBudgetRevenue / totalRevenue * 100.0) * 100.0) / 100.0,
                "Thu ngoài ngân sách", nonBudgetRevenue,
                nonBudgetRevenueBvh, nonBudgetRevenueBvs
            });
        }

        for (Map.Entry<String, Object[]> entry : calculatedKpis.entrySet()) {
            String kpiCode = entry.getKey();
            Double actualValue = (Double) entry.getValue()[0];
            String numeratorName = (String) entry.getValue()[1];
            Double numeratorValue = (Double) entry.getValue()[2];
            Double numBvh = (Double) entry.getValue()[3];
            Double numBvs = (Double) entry.getValue()[4];

            try {
                // 1. Find kpi_id from kpi_definitions
                List<Map<String, Object>> kpiDefs = jdbcTemplate.queryForList(
                    "SELECT TOP 1 kpi_id FROM kpi_definitions WHERE kpi_code = ? AND (is_deleted = 0 OR is_deleted IS NULL)",
                    kpiCode
                );
                if (kpiDefs.isEmpty()) continue;
                int kpiId = (int) kpiDefs.get(0).get("kpi_id");

                StringBuilder notesBuilder = new StringBuilder();
                notesBuilder.append(String.format("[P. TCKT Toàn Học viện]: Tổng doanh thu = %,.2f, %s = %,.2f (Tỷ lệ = %.2f%%).",
                    totalRevenue, numeratorName, numeratorValue, actualValue));
                if (numBvh != null && numBvs != null) {
                    notesBuilder.append(String.format(" [Cơ sở: BVH = %,.2f; BVS = %,.2f].", numBvh, numBvs));
                }
                String notes = notesBuilder.toString();

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
