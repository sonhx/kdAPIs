package com.kpi;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KpiK601Controller - REST endpoints for KPI K6.01
 * "Ty le CTDT duoc kiem dinh AUN-QA / ASIIN" (and other standards, multiple standards per program)
 */
@RestController
@RequestMapping({"/api/kpi/k601", "/kpi/k601"})
public class KpiK601Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiK601Controller.class);

    private static final String[] VALID_ACCREDITATION_TYPES = {
        "AUN-QA", "ASIIN", "AUN-QA, ASIIN", "ABET", "FIBAA", "CTI", "HCERES"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Helper to normalize period string to ANNUAL_YYYY
    
    private static boolean hasOtherStandard(String accType) {
        if (accType == null || accType.trim().isEmpty()) return false;
        String[] tokens = accType.split("[,;+/]+");
        for (String t : tokens) {
            String up = t.trim().toUpperCase();
            if (!up.isEmpty() && !up.contains("AUN") && !up.contains("ASIIN")) {
                return true;
            }
        }
        return false;
    }

    private String normalizePeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            return "ANNUAL_2025";
        }
        String clean = period.trim();
        Matcher m = Pattern.compile("(20\\d\\d)").matcher(clean);
        if (m.find()) {
            return "ANNUAL_" + m.group(1);
        }
        return clean;
    }

    // -------------------------------------------------------------------------
    // 1. GET /kpi/k601/template
    //    Build and return an Excel file pre-filled with all active training programs.
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
                infoSheet.setColumnWidth(0, 14000);
                String[] instructions = {
                    "HUONG DAN SU DUNG FILE MAU K6.01 - TY LE CTDT DUOC KIEM DINH AUN-QA / ASIIN",
                    "",
                    "1. Sheet 'Danh sach CTDT' chua danh sach chuong trinh dao tao lay tu he thong.",
                    "2. Cot A (Ma CT) va Cot B (Ten CTDT): KHONG CHINH SUA.",
                    "3. Cot C (Loai kiem dinh): Nhap tieu chuan kiem dinh da dat duoc:",
                    "   - Cac tieu chuan: AUN-QA, ASIIN, ABET, FIBAA, CTI, HCERES...",
                    "   - MOT CTDT CO THE DAT NHIEU TIEU CHUAN: Dien cach nhau bang dau phay (vi du: 'AUN-QA, ASIIN').",
                    "   - De trong neu chua duoc kiem dinh.",
                    "4. Luu file va tai len he thong qua chuc nang Nhap tu Excel tren trang KPI.",
                    "",
                    "KPI K6.01 = (So CTDT da kiem dinh) / (Tong so CTDT) x 100%"
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
                dataSheet.setColumnWidth(2, 7000);

                // Header style
                XSSFCellStyle headerStyle = workbook.createCellStyle();
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 11);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)67, (byte)56, (byte)202}, null));
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // Locked column style (read-only indicator)
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
                String[] headers = {"Ma CT", "Ten chuong trinh dao tao", "Loai kiem dinh (AUN-QA, ASIIN, hoac ket hop)"};
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

                    Cell accCell = row.createCell(2);
                    accCell.setCellValue("");
                    accCell.setCellStyle(editStyle);
                }

                // Dropdown validation on column C (permits entering custom/multiple values)
                if (!programs.isEmpty()) {
                    XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(dataSheet);
                    XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint)
                        dvHelper.createExplicitListConstraint(VALID_ACCREDITATION_TYPES);
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

                String fileName = "mau_k601_kiem_dinh_ctdt.xlsx";
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                responseHeaders.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                responseHeaders.setContentLength(bytes.length);

                return ResponseEntity.ok().headers(responseHeaders).body(bytes);
            }

        } catch (Exception e) {
            log.error("Error generating K6.01 template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // 2. POST /kpi/k601/upload
    //    Parse uploaded Excel, handles multiple and custom accreditation standards
    // -------------------------------------------------------------------------
    @PostMapping("/upload")
    public ResponseEntity<String> uploadAccreditationData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "period_code", defaultValue = "ANNUAL_2025") String periodCode,
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
                "DELETE FROM kpi_k601_accreditation_data WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, periodCode
            );

            int totalRows = 0;
            int accreditedCount = 0;
            int aunQaCount = 0;
            int asiinCount = 0;
            int otherCount = 0;
            JSONArray previewRows = new JSONArray();

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String programCode = formatter.formatCellValue(row.getCell(0)).trim();
                String programName = formatter.formatCellValue(row.getCell(1)).trim();
                String rawAcc      = formatter.formatCellValue(row.getCell(2)).trim();

                if (programCode.isEmpty() && programName.isEmpty()) continue;

                String accType = null;
                if (!rawAcc.isEmpty() && !rawAcc.equalsIgnoreCase("CHÆ¯A") && !rawAcc.equalsIgnoreCase("KHÃ”NG") && !rawAcc.equals("-")) {
                    String[] tokens = rawAcc.split("[,;+/]+");
                    LinkedHashSet<String> standards = new LinkedHashSet<>();
                    for (String token : tokens) {
                        String t = token.trim();
                        if (t.isEmpty()) continue;
                        String up = t.toUpperCase();
                        if (up.contains("AUN")) standards.add("AUN-QA");
                        else if (up.contains("ASIIN")) standards.add("ASIIN");
                        else if (up.contains("ABET")) standards.add("ABET");
                        else if (up.contains("FIBAA")) standards.add("FIBAA");
                        else if (up.contains("CTI")) standards.add("CTI");
                        else if (up.contains("HCERES")) standards.add("HCERES");
                        else standards.add(t);
                    }
                    if (!standards.isEmpty()) {
                        accType = String.join(", ", standards);
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
                    "INSERT INTO kpi_k601_accreditation_data " +
                    "(period_code, program_id, program_name, program_code, accreditation_type, uploaded_by, batch_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    normalizedPeriod,
                    programId,
                    programName.isEmpty() ? programCode : programName,
                    programCode.isEmpty() ? null : programCode,
                    accType,
                    uploadedBy,
                    batchId.toString()
                );

                totalRows++;
                if (accType != null) {
                    accreditedCount++;
                    String upper = accType.toUpperCase();
                    if (upper.contains("AUN")) aunQaCount++;
                    if (upper.contains("ASIIN")) asiinCount++;
                    if (hasOtherStandard(accType)) otherCount++;
                }

                if (previewRows.length() < 200) {
                    JSONObject pr = new JSONObject();
                    pr.put("programCode", programCode);
                    pr.put("programName", programName);
                    pr.put("accreditationType", accType != null ? accType : "");
                    previewRows.put(pr);
                }
            }

            double ratio = totalRows > 0
                ? Math.round((accreditedCount * 100.0 / totalRows) * 100.0) / 100.0
                : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("batch_id", batchId.toString());
            result.put("total", totalRows);
            result.put("accredited", accreditedCount);
            result.put("not_accredited", totalRows - accreditedCount);
            result.put("aun_qa_count", aunQaCount);
            result.put("asiin_count", asiinCount);
            result.put("other_count", otherCount);
            result.put("ratio", ratio);
            result.put("rows", previewRows);
            result.put("message", "Da xu ly " + totalRows + " chuong trinh, kiem dinh: " + accreditedCount + " CTDT (" + ratio + "%).");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error processing K6.01 Excel upload", e);
            result.put("status", "ERROR");
            result.put("message", "Loi xu ly file Excel: " + e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 3. GET /kpi/k601/data?period=ANNUAL_2025
    // -------------------------------------------------------------------------
    @GetMapping("/data")
    public ResponseEntity<String> getAccreditationData(
            @RequestParam(value = "period", defaultValue = "ANNUAL_2025") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, program_id, program_name, program_code, accreditation_type, uploaded_at " +
                "FROM kpi_k601_accreditation_data WHERE period_code = ? OR period_code = ? " +
                "ORDER BY program_code, program_name",
                normalizedPeriod, period
            );

            int total = rows.size();
            int accredited = 0;
            int aunQa = 0;
            int asiin = 0;
            int other = 0;

            JSONArray arr = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", row.get("id"));
                obj.put("programId", row.get("program_id"));
                obj.put("programName", row.get("program_name") != null ? row.get("program_name").toString() : "");
                obj.put("programCode", row.get("program_code") != null ? row.get("program_code").toString() : "");
                String accType = row.get("accreditation_type") != null ? row.get("accreditation_type").toString().trim() : "";
                obj.put("accreditationType", accType);
                if (!accType.isEmpty()) {
                    accredited++;
                    String upper = accType.toUpperCase();
                    if (upper.contains("AUN")) aunQa++;
                    if (upper.contains("ASIIN")) asiin++;
                    if (hasOtherStandard(accType)) other++;
                }
                obj.put("uploadedAt", row.get("uploaded_at") != null ? row.get("uploaded_at").toString() : "");
                arr.put(obj);
            }

            double ratio = total > 0 ? Math.round((accredited * 100.0 / total) * 100.0) / 100.0 : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("total", total);
            result.put("accredited", accredited);
            result.put("not_accredited", total - accredited);
            result.put("aun_qa_count", aunQa);
            result.put("asiin_count", asiin);
            result.put("other_count", other);
            result.put("ratio", ratio);
            result.put("count", total);
            result.put("rows", arr);
            result.put("data", arr);
            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error fetching K6.01 data for period {}", period, e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 4. GET /kpi/k601/ratio?period=ANNUAL_2025
    // -------------------------------------------------------------------------
    @GetMapping("/ratio")
    public ResponseEntity<String> getAccreditationRatio(
            @RequestParam(value = "period", defaultValue = "ANNUAL_2025") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT accreditation_type FROM kpi_k601_accreditation_data WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, period
            );

            if (rows.isEmpty()) {
                result.put("status", "EMPTY");
                result.put("message", "Chua co du lieu cho ky " + period);
                result.put("ratio", 0.0);
                return ResponseEntity.ok(result.toString());
            }

            int total = rows.size();
            int accredited = 0;
            int aunQa = 0;
            int asiin = 0;
            int otherCount = 0;

            for (Map<String, Object> r : rows) {
                String accType = r.get("accreditation_type") != null ? r.get("accreditation_type").toString().trim() : "";
                if (!accType.isEmpty()) {
                    accredited++;
                    String upper = accType.toUpperCase();
                    if (upper.contains("AUN")) aunQa++;
                    if (upper.contains("ASIIN")) asiin++;
                    if (hasOtherStandard(accType)) otherCount++;
                }
            }

            double ratio = total > 0 ? Math.round((accredited * 100.0 / total) * 100.0) / 100.0 : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("total", total);
            result.put("accredited", accredited);
            result.put("not_accredited", total - accredited);
            result.put("aun_qa_count", aunQa);
            result.put("asiin_count", asiin);
            result.put("other_count", otherCount);
            result.put("ratio", ratio);
            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error calculating K6.01 ratio for period {}", period, e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }
}