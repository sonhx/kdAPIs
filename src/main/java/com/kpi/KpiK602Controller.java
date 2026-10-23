package com.kpi;

import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KpiK602Controller - REST endpoints for KPI K6.02
 * "Ty le CTDT co doi sanh benchmarking" (Standard: QS Asia University Rankings, extensible)
 */
@RestController
@RequestMapping({"/api/kpi/k602", "/kpi/k602"})
public class KpiK602Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiK602Controller.class);

    private static final String[] VALID_BENCHMARK_TYPES = {
        "QS Asia University Rankings"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTable() {
        try {
            jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_k602_benchmarking_data') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_k602_benchmarking_data ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  program_id BIGINT NULL, " +
                "  program_name NVARCHAR(500) NOT NULL, " +
                "  program_code NVARCHAR(50) NULL, " +
                "  benchmark_standard NVARCHAR(255) NULL, " +
                "  uploaded_by INT NULL, " +
                "  uploaded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "  batch_id NVARCHAR(50) NOT NULL " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_k602_period_code ON dbo.kpi_k602_benchmarking_data (period_code); " +
                "CREATE NONCLUSTERED INDEX IX_k602_batch_id ON dbo.kpi_k602_benchmarking_data (batch_id); " +
                "END"
            );
            log.info("Initialized kpi_k602_benchmarking_data table successfully.");
        } catch (Exception e) {
            log.warn("Table kpi_k602_benchmarking_data init notice: {}", e.getMessage());
        }
    }

    // Helper to normalize period string
    private String normalizePeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            return "SCHOOLYEAR_2025-2026";
        }
        String clean = period.trim();
        Matcher mSy = Pattern.compile("(20\\d\\d)[-_](20\\d\\d)").matcher(clean);
        if (mSy.find()) {
            return "SCHOOLYEAR_" + mSy.group(1) + "-" + mSy.group(2);
        }
        Matcher mYr = Pattern.compile("(20\\d\\d)").matcher(clean);
        if (mYr.find()) {
            return "ANNUAL_" + mYr.group(1);
        }
        return clean;
    }

    private static boolean hasOtherStandard(String std) {
        if (std == null || std.trim().isEmpty()) return false;
        String[] tokens = std.split("[,;+/]+");
        for (String t : tokens) {
            String up = t.trim().toUpperCase();
            if (!up.isEmpty() && !up.contains("QS")) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // 1. GET /kpi/k602/template
    // -------------------------------------------------------------------------
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            List<Map<String, Object>> programs = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM training_programs ORDER BY code, name"
            );

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {

                // Instructions sheet
                XSSFSheet infoSheet = workbook.createSheet("Huong dan");
                infoSheet.setColumnWidth(0, 15000);
                String[] instructions = {
                    "HUONG DAN SU DUNG FILE MAU K6.02 - TY LE CTDT CO DOI SANH BENCHMARKING",
                    "",
                    "1. Sheet 'Danh sach CTDT' chua danh sach 51 chuong trinh dao tao lay tu he thong.",
                    "2. Cot A (Ma CT) va Cot B (Ten CTDT): KHONG CHINH SUA.",
                    "3. Cot C (Tieu chuan doi sanh): Nhap tieu chuan doi sanh da thuc hien:",
                    "   - Tieu chuan mac dinh: 'QS Asia University Rankings'.",
                    "   - De trong neu chuong trinh chua thuc hien doi sanh benchmarking.",
                    "   - Co the chon tu danh sach hop chon hoac nhap them tieu chuan khac (neu co).",
                    "4. Luu file va tai len he thong qua chuc nang Nhap tu Excel tren trang KPI.",
                    "",
                    "KPI K6.02 = (So CTDT co hoat dong doi sanh) / (Tong so CTDT) x 100%"
                };
                for (int i = 0; i < instructions.length; i++) {
                    Row r = infoSheet.createRow(i);
                    Cell c = r.createCell(0);
                    c.setCellValue(instructions[i]);
                }

                // Data sheet
                XSSFSheet dataSheet = workbook.createSheet("Danh sach CTDT");
                dataSheet.setColumnWidth(0, 4500);
                dataSheet.setColumnWidth(1, 16000);
                dataSheet.setColumnWidth(2, 9000);

                // Header style
                XSSFCellStyle headerStyle = workbook.createCellStyle();
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 11);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)30, (byte)64, (byte)175}, null));
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // Locked column style
                XSSFCellStyle lockedStyle = workbook.createCellStyle();
                lockedStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)241, (byte)245, (byte)249}, null));
                lockedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                lockedStyle.setBorderBottom(BorderStyle.THIN);
                lockedStyle.setBorderRight(BorderStyle.THIN);

                // Editable column style
                XSSFCellStyle editStyle = workbook.createCellStyle();
                editStyle.setBorderBottom(BorderStyle.THIN);
                editStyle.setBorderRight(BorderStyle.THIN);
                editStyle.setAlignment(HorizontalAlignment.CENTER);

                // Header row
                Row headerRow = dataSheet.createRow(0);
                headerRow.setHeightInPoints(24);
                String[] headers = {"Ma CT", "Ten chuong trinh dao tao", "Tieu chuan doi sanh (QS Asia University Rankings)"};
                for (int col = 0; col < headers.length; col++) {
                    Cell cell = headerRow.createCell(col);
                    cell.setCellValue(headers[col]);
                    cell.setCellStyle(headerStyle);
                }

                // Data rows
                for (int i = 0; i < programs.size(); i++) {
                    Map<String, Object> prog = programs.get(i);
                    Row row = dataSheet.createRow(i + 1);
                    row.setHeightInPoints(18);

                    Cell codeCell = row.createCell(0);
                    codeCell.setCellValue(prog.get("code") != null ? prog.get("code").toString() : "");
                    codeCell.setCellStyle(lockedStyle);

                    Cell nameCell = row.createCell(1);
                    nameCell.setCellValue(prog.get("name") != null ? prog.get("name").toString() : "");
                    nameCell.setCellStyle(lockedStyle);

                    Cell stdCell = row.createCell(2);
                    stdCell.setCellValue("");
                    stdCell.setCellStyle(editStyle);
                }

                // Dropdown validation
                if (!programs.isEmpty()) {
                    XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(dataSheet);
                    XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint)
                        dvHelper.createExplicitListConstraint(VALID_BENCHMARK_TYPES);
                    org.apache.poi.ss.util.CellRangeAddressList addressList =
                        new org.apache.poi.ss.util.CellRangeAddressList(1, programs.size(), 2, 2);
                    XSSFDataValidation validation = (XSSFDataValidation)
                        dvHelper.createValidation(dvConstraint, addressList);
                    validation.setShowErrorBox(false);
                    dataSheet.addValidationData(validation);
                }

                dataSheet.createFreezePane(0, 1);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                byte[] bytes = baos.toByteArray();

                String fileName = "mau_k602_doi_sanh_ctdt.xlsx";
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                responseHeaders.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                responseHeaders.setContentLength(bytes.length);

                return ResponseEntity.ok().headers(responseHeaders).body(bytes);
            }

        } catch (Exception e) {
            log.error("Error generating K6.02 template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // 2. POST /kpi/k602/upload
    // -------------------------------------------------------------------------
    @PostMapping("/upload")
    public ResponseEntity<String> uploadBenchmarkingData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "period_code", defaultValue = "SCHOOLYEAR_2025-2026") String periodCode,
            @RequestParam(value = "uploaded_by", required = false) Integer uploadedBy) {

        JSONObject result = new JSONObject();

        if (file == null || file.isEmpty()) {
            result.put("status", "ERROR");
            result.put("message", "File khong hop le hoac trong.");
            return ResponseEntity.badRequest().body(result.toString());
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheet("Danh sach CTDT");
            if (sheet == null) sheet = workbook.getSheetAt(0);

            DataFormatter formatter = new DataFormatter();
            UUID batchId = UUID.randomUUID();
            String normalizedPeriod = normalizePeriod(periodCode);

            // Delete previous batch for this period
            jdbcTemplate.update(
                "DELETE FROM kpi_k602_benchmarking_data WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, periodCode
            );

            int totalRows = 0;
            int benchmarkedCount = 0;
            int qsAsiaCount = 0;
            int otherCount = 0;
            JSONArray previewRows = new JSONArray();

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String programCode = formatter.formatCellValue(row.getCell(0)).trim();
                String programName = formatter.formatCellValue(row.getCell(1)).trim();
                String rawStd      = formatter.formatCellValue(row.getCell(2)).trim();

                if (programCode.isEmpty() && programName.isEmpty()) continue;

                String benchStd = null;
                if (!rawStd.isEmpty() && !rawStd.equalsIgnoreCase("CHƯA") && !rawStd.equalsIgnoreCase("KHÔNG") && !rawStd.equals("-")) {
                    String[] tokens = rawStd.split("[,;+/]+");
                    LinkedHashSet<String> standards = new LinkedHashSet<>();
                    for (String token : tokens) {
                        String t = token.trim();
                        if (t.isEmpty()) continue;
                        String up = t.toUpperCase();
                        if (up.contains("QS")) standards.add("QS Asia University Rankings");
                        else standards.add(t);
                    }
                    if (!standards.isEmpty()) {
                        benchStd = String.join(", ", standards);
                    }
                }

                Long programId = null;
                if (!programCode.isEmpty()) {
                    List<Map<String, Object>> idRows = jdbcTemplate.queryForList(
                        "SELECT TOP 1 id FROM training_programs WHERE code = ?", programCode
                    );
                    if (!idRows.isEmpty() && idRows.get(0).get("id") != null) {
                        programId = ((Number) idRows.get(0).get("id")).longValue();
                    }
                }

                jdbcTemplate.update(
                    "INSERT INTO kpi_k602_benchmarking_data " +
                    "(period_code, program_id, program_name, program_code, benchmark_standard, uploaded_by, batch_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    normalizedPeriod,
                    programId,
                    programName.isEmpty() ? programCode : programName,
                    programCode.isEmpty() ? null : programCode,
                    benchStd,
                    uploadedBy,
                    batchId.toString()
                );

                totalRows++;
                if (benchStd != null) {
                    benchmarkedCount++;
                    String upper = benchStd.toUpperCase();
                    if (upper.contains("QS")) qsAsiaCount++;
                    if (hasOtherStandard(benchStd)) otherCount++;
                }

                if (previewRows.length() < 200) {
                    JSONObject pr = new JSONObject();
                    pr.put("programCode", programCode);
                    pr.put("programName", programName);
                    pr.put("benchmarkStandard", benchStd != null ? benchStd : "");
                    previewRows.put(pr);
                }
            }

            double ratio = totalRows > 0
                ? Math.round((benchmarkedCount * 100.0 / totalRows) * 100.0) / 100.0
                : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("batch_id", batchId.toString());
            result.put("total", totalRows);
            result.put("benchmarked", benchmarkedCount);
            result.put("not_benchmarked", totalRows - benchmarkedCount);
            result.put("qs_asia_count", qsAsiaCount);
            result.put("other_count", otherCount);
            result.put("ratio", ratio);
            result.put("rows", previewRows);
            result.put("message", "Da xu ly " + totalRows + " chuong trinh, doi sanh: " + benchmarkedCount + " CTDT (" + ratio + "%).");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error processing K6.02 Excel upload", e);
            result.put("status", "ERROR");
            result.put("message", "Loi xu ly file Excel: " + e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 3. GET /kpi/k602/data?period=SCHOOLYEAR_2025-2026
    // -------------------------------------------------------------------------
    @GetMapping("/data")
    public ResponseEntity<String> getBenchmarkingData(
            @RequestParam(value = "period", defaultValue = "SCHOOLYEAR_2025-2026") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, program_id, program_name, program_code, benchmark_standard, uploaded_at " +
                "FROM kpi_k602_benchmarking_data WHERE period_code = ? OR period_code = ? " +
                "ORDER BY program_code, program_name",
                normalizedPeriod, period
            );

            int total = rows.size();
            int benchmarked = 0;
            int qsAsia = 0;
            int other = 0;

            JSONArray arr = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", row.get("id"));
                obj.put("programId", row.get("program_id"));
                obj.put("programName", row.get("program_name") != null ? row.get("program_name").toString() : "");
                obj.put("programCode", row.get("program_code") != null ? row.get("program_code").toString() : "");
                String benchStd = row.get("benchmark_standard") != null ? row.get("benchmark_standard").toString().trim() : "";
                obj.put("benchmarkStandard", benchStd);
                if (!benchStd.isEmpty()) {
                    benchmarked++;
                    String upper = benchStd.toUpperCase();
                    if (upper.contains("QS")) qsAsia++;
                    if (hasOtherStandard(benchStd)) other++;
                }
                obj.put("uploadedAt", row.get("uploaded_at") != null ? row.get("uploaded_at").toString() : "");
                arr.put(obj);
            }

            double ratio = total > 0 ? Math.round((benchmarked * 100.0 / total) * 100.0) / 100.0 : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("total", total);
            result.put("benchmarked", benchmarked);
            result.put("not_benchmarked", total - benchmarked);
            result.put("qs_asia_count", qsAsia);
            result.put("other_count", other);
            result.put("ratio", ratio);
            result.put("count", total);
            result.put("rows", arr);
            result.put("data", arr);
            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error fetching K6.02 data for period {}", period, e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 4. GET /kpi/k602/ratio?period=SCHOOLYEAR_2025-2026
    // -------------------------------------------------------------------------
    @GetMapping("/ratio")
    public ResponseEntity<String> getBenchmarkingRatio(
            @RequestParam(value = "period", defaultValue = "SCHOOLYEAR_2025-2026") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT benchmark_standard FROM kpi_k602_benchmarking_data WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, period
            );

            if (rows.isEmpty()) {
                result.put("status", "EMPTY");
                result.put("message", "Chua co du lieu cho ky " + period);
                result.put("ratio", 0.0);
                return ResponseEntity.ok(result.toString());
            }

            int total = rows.size();
            int benchmarked = 0;
            int qsAsia = 0;
            int otherCount = 0;

            for (Map<String, Object> r : rows) {
                String benchStd = r.get("benchmark_standard") != null ? r.get("benchmark_standard").toString().trim() : "";
                if (!benchStd.isEmpty()) {
                    benchmarked++;
                    String upper = benchStd.toUpperCase();
                    if (upper.contains("QS")) qsAsia++;
                    if (hasOtherStandard(benchStd)) otherCount++;
                }
            }

            double ratio = total > 0 ? Math.round((benchmarked * 100.0 / total) * 100.0) / 100.0 : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("total", total);
            result.put("benchmarked", benchmarked);
            result.put("not_benchmarked", total - benchmarked);
            result.put("qs_asia_count", qsAsia);
            result.put("other_count", otherCount);
            result.put("ratio", ratio);
            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error calculating K6.02 ratio for period {}", period, e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }
}
