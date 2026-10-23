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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KpiT106Controller - REST endpoints for KPI T1.06
 * "Tỷ lệ học phần được đánh giá CLO đầy đủ"
 * Chu kỳ: Học kỳ (Semester, e.g. SEMESTER_1_2025-2026, SEMESTER_2_2024-2025)
 */
@RestController
@RequestMapping({"/api/kpi/t106", "/kpi/t106"})
public class KpiT106Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiT106Controller.class);

    private static final String[] VALID_ASSESSMENT_STATUSES = {
        "Day du", "Chua day du"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTable() {
        try {
            jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_t106_clo_assessment_data') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_t106_clo_assessment_data ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  course_id BIGINT NOT NULL, " +
                "  is_fully_assessed BIT NOT NULL DEFAULT 1, " +
                "  total_clos INT NULL, " +
                "  assessed_clos INT NULL, " +
                "  assessment_method NVARCHAR(255) NULL, " +
                "  evaluated_by NVARCHAR(255) NULL, " +
                "  notes NVARCHAR(1000) NULL, " +
                "  uploaded_by INT NULL, " +
                "  uploaded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "  batch_id NVARCHAR(50) NOT NULL, " +
                "  CONSTRAINT FK_kpi_t106_courses FOREIGN KEY (course_id) REFERENCES dbo.courses(id) ON DELETE CASCADE " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t106_period_code ON dbo.kpi_t106_clo_assessment_data (period_code); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t106_course_id ON dbo.kpi_t106_clo_assessment_data (course_id); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t106_batch_id ON dbo.kpi_t106_clo_assessment_data (batch_id); " +
                "END; " +
                // Auto-sync course_code in dbo.courses from course_test_results and fallback
                "IF EXISTS (SELECT 1 FROM dbo.courses WHERE course_code IS NULL OR RTRIM(LTRIM(course_code)) = '') " +
                "BEGIN " +
                "  UPDATE c SET c.course_code = tr.course_code " +
                "  FROM dbo.courses c " +
                "  CROSS APPLY ( " +
                "    SELECT TOP 1 tr.course_code FROM dbo.course_test_results tr " +
                "    WHERE LOWER(RTRIM(LTRIM(tr.course_name))) = LOWER(RTRIM(LTRIM(c.course_name))) " +
                "      AND tr.course_code IS NOT NULL AND tr.course_code <> '' " +
                "  ) tr " +
                "  WHERE c.course_code IS NULL OR RTRIM(LTRIM(c.course_code)) = ''; " +
                "  UPDATE dbo.courses SET course_code = CONCAT('HP', RIGHT(CONCAT('0000', id), 5)) " +
                "  WHERE course_code IS NULL OR RTRIM(LTRIM(course_code)) = ''; " +
                "END;"
            );
            log.info("Initialized kpi_t106_clo_assessment_data table and verified course codes successfully.");
        } catch (Exception e) {
            log.warn("Table kpi_t106_clo_assessment_data init notice: {}", e.getMessage());
        }
    }

    /**
     * Standardize period string to SEMESTER_X_YYYY-YYYY or similar
     */
    private String normalizePeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            return "SEMESTER_1_2025-2026";
        }
        String clean = period.trim();

        // 1. Check format: TERM_2025-2026_HK3 or TERM_2025-2026_HK1
        Matcher mTermHk = Pattern.compile("(?i)TERM[\\s_]*(20\\d\\d)[-_](20\\d\\d)[\\s_]*HK([123]|he|summer)").matcher(clean);
        if (mTermHk.find()) {
            return "SEMESTER_" + mTermHk.group(3).toUpperCase() + "_" + mTermHk.group(1) + "-" + mTermHk.group(2);
        }

        // 2. Check format: SEMESTER_1_2024-2025 or HK1_2024-2025 or HocKy_1_2024-2025
        Matcher mSem = Pattern.compile("(?i)(?:SEMESTER|HK|Hoc[\\s_]*Ky)[\\s_]*([123]|he|summer)[\\s_]*[-_]?\\s*(20\\d\\d)[-_](20\\d\\d)").matcher(clean);
        if (mSem.find()) {
            String semNum = mSem.group(1).toUpperCase();
            return "SEMESTER_" + semNum + "_" + mSem.group(2) + "-" + mSem.group(3);
        }

        // 3. Vietnamese: Hoc ky 3 nam 2025-2026
        Matcher mVn = Pattern.compile("(?i)H[ọo]c\\s*k[ỳy]\\s*([123]|h[èe])\\s*(?:n[ăa]m\\s*)?(20\\d\\d)(?:[-_](20\\d\\d))?").matcher(clean);
        if (mVn.find()) {
            String semNum = mVn.group(1).toUpperCase();
            String y1 = mVn.group(2);
            String y2 = mVn.group(3) != null ? mVn.group(3) : String.valueOf(Integer.parseInt(y1) + 1);
            return "SEMESTER_" + semNum + "_" + y1 + "-" + y2;
        }

        // Check format with school year only: 2024-2025
        Matcher mSy = Pattern.compile("(20\\d\\d)[-_](20\\d\\d)").matcher(clean);
        if (mSy.find()) {
            return "SEMESTER_1_" + mSy.group(1) + "-" + mSy.group(2);
        }

        // Single year format: 2025
        Matcher mYr = Pattern.compile("(20\\d\\d)").matcher(clean);
        if (mYr.find()) {
            int y = Integer.parseInt(mYr.group(1));
            return "SEMESTER_1_" + y + "-" + (y + 1);
        }

        return clean;
    }

    // -------------------------------------------------------------------------
    // 1. GET /kpi/t106/template
    //    Build and return an Excel file pre-filled with all active courses from training_programs.
    // -------------------------------------------------------------------------
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            // Get latest list of courses joined with training_programs, ensuring course_code is never null
            List<Map<String, Object>> courses = jdbcTemplate.queryForList(
                "SELECT c.id AS course_id, " +
                "       COALESCE(NULLIF(RTRIM(LTRIM(c.course_code)), ''), CONCAT('HP', RIGHT(CONCAT('0000', c.id), 5))) AS course_code, " +
                "       c.course_name, c.credits, " +
                "       p.code AS program_code, p.name AS program_name " +
                "FROM dbo.courses c " +
                "LEFT JOIN dbo.training_programs p ON c.program_id = p.id " +
                "ORDER BY p.code, c.course_name"
            );

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {

                // Instructions sheet
                XSSFSheet infoSheet = workbook.createSheet("Huong dan");
                infoSheet.setColumnWidth(0, 16000);
                String[] instructions = {
                    "HUONG DAN SU DUNG FILE MAU T1.06 - TY LE HOC PHAN DUOC DANH GIA CLO DAY DU",
                    "",
                    "1. Sheet 'Danh sach CLO Hoc phan' chua danh sach tat ca hoc phan duoc trich xuat tu he thong.",
                    "2. Cac cot A, B, C, D, E (ID, Ma HP, Ten HP, Tin chi, CTDT): DONG BO TU HE THONG, KHONG THAY DOI.",
                    "3. Cot F (Danh gia CLO day du?): Chon hoac nhap 'Day du' hoac 'Chua day du'.",
                    "   - 'Day du': Hoc phan da to chuc danh gia tat ca cac CLO theo de cuong chi tiet.",
                    "   - 'Chua day du': Chua danh gia hoac chi moi danh gia mot phan CLO.",
                    "4. Cot G & H (Tong so CLO & So CLO da danh gia): Dien so nguyen (vi du: 4 va 4 neu day du 100%).",
                    "5. Cot I (Hinh thuc danh gia): Dien hinh thuc (vi du: Do an, Trac nghiem LMS, Van dap, Tu luan, ...).",
                    "6. Cot J (Giang vien / Bo mon): Ten giang vien hoac bo mon phu trach danh gia.",
                    "7. Cot K (Ghi chu / Minh chung): Ghi chu them hoac dan link bien ban / bao cao nghiem thu CLO.",
                    "8. Luu file va tai len he thong qua nut 'Nhap tu Excel' tren man hinh KPI T1.06.",
                    "",
                    "CONG THUC KPI T1.06 = (So hoc phan danh gia CLO day du) / (Tong so hoc phan) x 100%"
                };
                for (int i = 0; i < instructions.length; i++) {
                    Row r = infoSheet.createRow(i);
                    Cell c = r.createCell(0);
                    c.setCellValue(instructions[i]);
                }

                // Data sheet
                XSSFSheet dataSheet = workbook.createSheet("Danh sach CLO Hoc phan");
                dataSheet.setColumnWidth(0, 3500);  // ID
                dataSheet.setColumnWidth(1, 4500);  // Ma HP
                dataSheet.setColumnWidth(2, 14000); // Ten HP
                dataSheet.setColumnWidth(3, 3000);  // Tin chi
                dataSheet.setColumnWidth(4, 12000); // CTDT
                dataSheet.setColumnWidth(5, 7000);  // Danh gia day du?
                dataSheet.setColumnWidth(6, 4000);  // Tong so CLO
                dataSheet.setColumnWidth(7, 5000);  // So CLO da danh gia
                dataSheet.setColumnWidth(8, 7000);  // Hinh thuc
                dataSheet.setColumnWidth(9, 8000);  // Giang vien / Bo mon
                dataSheet.setColumnWidth(10, 10000); // Ghi chu / Minh chung

                // Header styles
                XSSFCellStyle headerStyle = workbook.createCellStyle();
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 11);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 16, (byte) 100, (byte) 190}, null));
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // Locked column style (read-only indicator)
                XSSFCellStyle lockedStyle = workbook.createCellStyle();
                lockedStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 245, (byte) 247, (byte) 250}, null));
                lockedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                lockedStyle.setBorderBottom(BorderStyle.THIN);
                lockedStyle.setBorderRight(BorderStyle.THIN);

                // Center locked style
                XSSFCellStyle lockedCenterStyle = workbook.createCellStyle();
                lockedCenterStyle.cloneStyleFrom(lockedStyle);
                lockedCenterStyle.setAlignment(HorizontalAlignment.CENTER);

                // Editable column style
                XSSFCellStyle editStyle = workbook.createCellStyle();
                editStyle.setBorderBottom(BorderStyle.THIN);
                editStyle.setBorderRight(BorderStyle.THIN);

                XSSFCellStyle editCenterStyle = workbook.createCellStyle();
                editCenterStyle.cloneStyleFrom(editStyle);
                editCenterStyle.setAlignment(HorizontalAlignment.CENTER);

                // Header row
                Row headerRow = dataSheet.createRow(0);
                headerRow.setHeightInPoints(26);
                String[] headers = {
                    "ID HP", "Ma hoc phan", "Ten hoc phan", "So TC", "Chuong trinh dao tao",
                    "Danh gia CLO day du?", "Tong so CLO", "So CLO da danh gia", "Hinh thuc danh gia",
                    "Giang vien / Bo mon", "Ghi chu / Minh chung"
                };
                for (int col = 0; col < headers.length; col++) {
                    Cell cell = headerRow.createCell(col);
                    cell.setCellValue(headers[col]);
                    cell.setCellStyle(headerStyle);
                }

                // Data rows
                for (int i = 0; i < courses.size(); i++) {
                    Map<String, Object> crs = courses.get(i);
                    Row row = dataSheet.createRow(i + 1);
                    row.setHeightInPoints(20);

                    // ID
                    Cell idCell = row.createCell(0);
                    idCell.setCellValue(crs.get("course_id") != null ? Long.parseLong(crs.get("course_id").toString()) : 0L);
                    idCell.setCellStyle(lockedCenterStyle);

                    // Course code
                    Cell codeCell = row.createCell(1);
                    codeCell.setCellValue(crs.get("course_code") != null ? crs.get("course_code").toString() : "");
                    codeCell.setCellStyle(lockedCenterStyle);

                    // Course name
                    Cell nameCell = row.createCell(2);
                    nameCell.setCellValue(crs.get("course_name") != null ? crs.get("course_name").toString() : "");
                    nameCell.setCellStyle(lockedStyle);

                    // Credits
                    Cell credCell = row.createCell(3);
                    credCell.setCellValue(crs.get("credits") != null ? crs.get("credits").toString() : "");
                    credCell.setCellStyle(lockedCenterStyle);

                    // Program name
                    Cell progCell = row.createCell(4);
                    String progName = crs.get("program_name") != null ? crs.get("program_name").toString() : "";
                    String progCode = crs.get("program_code") != null ? "[" + crs.get("program_code") + "] " : "";
                    progCell.setCellValue(progCode + progName);
                    progCell.setCellStyle(lockedStyle);

                    // Is fully assessed (default empty or "Chua day du")
                    Cell assessCell = row.createCell(5);
                    assessCell.setCellValue("");
                    assessCell.setCellStyle(editCenterStyle);

                    // Total CLOs
                    Cell totalCloCell = row.createCell(6);
                    totalCloCell.setCellValue("");
                    totalCloCell.setCellStyle(editCenterStyle);

                    // Assessed CLOs
                    Cell assessedCloCell = row.createCell(7);
                    assessedCloCell.setCellValue("");
                    assessedCloCell.setCellStyle(editCenterStyle);

                    // Method
                    Cell methodCell = row.createCell(8);
                    methodCell.setCellValue("");
                    methodCell.setCellStyle(editStyle);

                    // Evaluated by
                    Cell evalCell = row.createCell(9);
                    evalCell.setCellValue("");
                    evalCell.setCellStyle(editStyle);

                    // Notes
                    Cell noteCell = row.createCell(10);
                    noteCell.setCellValue("");
                    noteCell.setCellStyle(editStyle);
                }

                // Dropdown validation on column F (Danh gia CLO day du?)
                if (!courses.isEmpty()) {
                    XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(dataSheet);
                    XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint)
                        dvHelper.createExplicitListConstraint(VALID_ASSESSMENT_STATUSES);
                    org.apache.poi.ss.util.CellRangeAddressList addressList =
                        new org.apache.poi.ss.util.CellRangeAddressList(1, courses.size(), 5, 5);
                    XSSFDataValidation validation = (XSSFDataValidation)
                        dvHelper.createValidation(dvConstraint, addressList);
                    validation.setShowErrorBox(false);
                    dataSheet.addValidationData(validation);
                }

                dataSheet.createFreezePane(0, 1);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                byte[] bytes = baos.toByteArray();

                String fileName = "mau_t106_danh_gia_clo.xlsx";
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                responseHeaders.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                responseHeaders.setContentLength(bytes.length);

                return ResponseEntity.ok().headers(responseHeaders).body(bytes);
            }

        } catch (Exception e) {
            log.error("Error generating T1.06 template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // 2. GET /kpi/t106/courses
    //    Get latest list of courses with training_programs info for UI picker / autocomplete
    // -------------------------------------------------------------------------
    @GetMapping("/courses")
    public ResponseEntity<String> getCatalogCourses() {
        JSONObject result = new JSONObject();
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT c.id AS course_id, " +
                "       COALESCE(NULLIF(RTRIM(LTRIM(c.course_code)), ''), CONCAT('HP', RIGHT(CONCAT('0000', c.id), 5))) AS course_code, " +
                "       c.course_name, c.credits, " +
                "       p.id AS program_id, p.code AS program_code, p.name AS program_name " +
                "FROM dbo.courses c " +
                "LEFT JOIN dbo.training_programs p ON c.program_id = p.id " +
                "ORDER BY p.code, c.course_name"
            );

            JSONArray arr = new JSONArray();
            for (Map<String, Object> m : list) {
                JSONObject o = new JSONObject();
                o.put("courseId", m.get("course_id"));
                o.put("courseCode", m.get("course_code") != null ? m.get("course_code").toString() : "");
                o.put("courseName", m.get("course_name") != null ? m.get("course_name").toString() : "");
                o.put("credits", m.get("credits") != null ? m.get("credits") : 0);
                o.put("programId", m.get("program_id"));
                o.put("programCode", m.get("program_code") != null ? m.get("program_code").toString() : "");
                o.put("programName", m.get("program_name") != null ? m.get("program_name").toString() : "");
                arr.put(o);
            }

            result.put("status", "SUCCESS");
            result.put("total", arr.length());
            result.put("data", arr);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error fetching catalog courses", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 3. POST /kpi/t106/upload
    //    Parse uploaded Excel, stores into dbo.kpi_t106_clo_assessment_data
    // -------------------------------------------------------------------------
    @PostMapping("/upload")
    public ResponseEntity<String> uploadCloAssessmentData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "period_code", defaultValue = "SEMESTER_1_2025-2026") String periodCode,
            @RequestParam(value = "uploaded_by", required = false) Integer uploadedBy) {

        JSONObject result = new JSONObject();

        if (file == null || file.isEmpty()) {
            result.put("status", "ERROR");
            result.put("message", "File khong hop le hoac trong.");
            return ResponseEntity.badRequest().body(result.toString());
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheet("Danh sach CLO Hoc phan");
            if (sheet == null) sheet = workbook.getSheetAt(0);

            DataFormatter formatter = new DataFormatter();
            UUID batchId = UUID.randomUUID();
            String normalizedPeriod = normalizePeriod(periodCode);

            // Fetch course map (by id and by course_code) to ensure fast and accurate FK resolution
            Map<Long, Long> existingCourseIdMap = new HashMap<>();
            Map<String, Long> codeToCourseIdMap = new HashMap<>();
            List<Map<String, Object>> dbCourses = jdbcTemplate.queryForList("SELECT id, course_code FROM dbo.courses");
            for (Map<String, Object> c : dbCourses) {
                Long cid = Long.parseLong(c.get("id").toString());
                existingCourseIdMap.put(cid, cid);
                if (c.get("course_code") != null) {
                    codeToCourseIdMap.put(c.get("course_code").toString().trim().toUpperCase(), cid);
                }
            }

            // Total courses in curriculum as standard baseline denominator
            int totalCurriculumCourses = dbCourses.size();

            // Clear previous records for this period
            jdbcTemplate.update(
                "DELETE FROM dbo.kpi_t106_clo_assessment_data WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, periodCode
            );

            List<Map<String, Object>> rowsToInsert = new ArrayList<>();
            int fullyAssessedCount = 0;
            int partiallyAssessedCount = 0;
            int unassessedCount = 0;
            JSONArray previewRows = new JSONArray();

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String rawId          = formatter.formatCellValue(row.getCell(0)).trim();
                String courseCode     = formatter.formatCellValue(row.getCell(1)).trim();
                String courseName     = formatter.formatCellValue(row.getCell(2)).trim();
                String rawStatus      = formatter.formatCellValue(row.getCell(5)).trim();
                String rawTotalClos   = formatter.formatCellValue(row.getCell(6)).trim();
                String rawAssessedClos= formatter.formatCellValue(row.getCell(7)).trim();
                String method         = formatter.formatCellValue(row.getCell(8)).trim();
                String evaluatedBy    = formatter.formatCellValue(row.getCell(9)).trim();
                String notes          = formatter.formatCellValue(row.getCell(10)).trim();

                if (rawId.isEmpty() && courseCode.isEmpty() && courseName.isEmpty()) continue;

                Long resolvedCourseId = null;
                if (!rawId.isEmpty()) {
                    try {
                        long parsedId = Long.parseLong(rawId);
                        if (existingCourseIdMap.containsKey(parsedId)) {
                            resolvedCourseId = parsedId;
                        }
                    } catch (NumberFormatException ignored) {}
                }

                if (resolvedCourseId == null && !courseCode.isEmpty()) {
                    resolvedCourseId = codeToCourseIdMap.get(courseCode.toUpperCase());
                }

                // If still not resolved, try lookup by name
                if (resolvedCourseId == null && !courseName.isEmpty()) {
                    List<Long> matched = jdbcTemplate.queryForList(
                        "SELECT TOP 1 id FROM dbo.courses WHERE course_name = ?", Long.class, courseName
                    );
                    if (!matched.isEmpty()) resolvedCourseId = matched.get(0);
                }

                if (resolvedCourseId == null) {
                    continue; // Skip unrecognized course rows
                }

                Integer totalClos = null;
                if (!rawTotalClos.isEmpty()) {
                    try { totalClos = Integer.parseInt(rawTotalClos); } catch (NumberFormatException ignored) {}
                }

                Integer assessedClos = null;
                if (!rawAssessedClos.isEmpty()) {
                    try { assessedClos = Integer.parseInt(rawAssessedClos); } catch (NumberFormatException ignored) {}
                }

                boolean isFullyAssessed = false;
                String stUpper = rawStatus.toUpperCase();
                if (stUpper.contains("DAY DU") || stUpper.contains("ĐẦY ĐỦ") || stUpper.contains("YES") ||
                    stUpper.contains("DAT") || stUpper.contains("ĐẠT") || stUpper.equals("1") || stUpper.equals("TRUE")) {
                    isFullyAssessed = true;
                } else if (totalClos != null && assessedClos != null && totalClos > 0 && assessedClos >= totalClos) {
                    isFullyAssessed = true;
                }

                if (isFullyAssessed) {
                    fullyAssessedCount++;
                } else if (assessedClos != null && assessedClos > 0) {
                    partiallyAssessedCount++;
                } else {
                    unassessedCount++;
                }

                Map<String, Object> rowMap = new HashMap<>();
                rowMap.put("courseId", resolvedCourseId);
                rowMap.put("isFullyAssessed", isFullyAssessed);
                rowMap.put("totalClos", totalClos);
                rowMap.put("assessedClos", assessedClos);
                rowMap.put("method", method.isEmpty() ? null : method);
                rowMap.put("evaluatedBy", evaluatedBy.isEmpty() ? null : evaluatedBy);
                rowMap.put("notes", notes.isEmpty() ? null : notes);
                rowsToInsert.add(rowMap);

                if (previewRows.length() < 200) {
                    JSONObject pr = new JSONObject();
                    pr.put("courseId", resolvedCourseId);
                    pr.put("courseCode", courseCode);
                    pr.put("courseName", courseName);
                    pr.put("isFullyAssessed", isFullyAssessed);
                    pr.put("totalClos", totalClos != null ? totalClos : 0);
                    pr.put("assessedClos", assessedClos != null ? assessedClos : 0);
                    pr.put("assessmentMethod", method);
                    pr.put("evaluatedBy", evaluatedBy);
                    pr.put("notes", notes);
                    previewRows.put(pr);
                }
            }

            // Batch insert into dbo.kpi_t106_clo_assessment_data
            if (!rowsToInsert.isEmpty()) {
                String insertSql = "INSERT INTO dbo.kpi_t106_clo_assessment_data " +
                    "(period_code, course_id, is_fully_assessed, total_clos, assessed_clos, assessment_method, evaluated_by, notes, uploaded_by, batch_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                jdbcTemplate.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Map<String, Object> r = rowsToInsert.get(i);
                        ps.setString(1, normalizedPeriod);
                        ps.setLong(2, (Long) r.get("courseId"));
                        ps.setBoolean(3, (Boolean) r.get("isFullyAssessed"));

                        if (r.get("totalClos") != null) ps.setInt(4, (Integer) r.get("totalClos"));
                        else ps.setNull(4, Types.INTEGER);

                        if (r.get("assessedClos") != null) ps.setInt(5, (Integer) r.get("assessedClos"));
                        else ps.setNull(5, Types.INTEGER);

                        if (r.get("method") != null) ps.setNString(6, r.get("method").toString());
                        else ps.setNull(6, Types.NVARCHAR);

                        if (r.get("evaluatedBy") != null) ps.setNString(7, r.get("evaluatedBy").toString());
                        else ps.setNull(7, Types.NVARCHAR);

                        if (r.get("notes") != null) ps.setNString(8, r.get("notes").toString());
                        else ps.setNull(8, Types.NVARCHAR);

                        if (uploadedBy != null) ps.setInt(9, uploadedBy);
                        else ps.setNull(9, Types.INTEGER);

                        ps.setString(10, batchId.toString());
                    }

                    @Override
                    public int getBatchSize() {
                        return rowsToInsert.size();
                    }
                });
            }

            // Denominator: use total curriculum courses if greater than rows uploaded, or rowsToInsert.size()
            int denominator = Math.max(totalCurriculumCourses, rowsToInsert.size());
            double ratio = denominator > 0
                ? Math.round((fullyAssessedCount * 100.0 / denominator) * 100.0) / 100.0
                : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("batch_id", batchId.toString());
            result.put("total_courses", denominator);
            result.put("total_evaluated", rowsToInsert.size());
            result.put("fully_assessed", fullyAssessedCount);
            result.put("partially_assessed", partiallyAssessedCount);
            result.put("unassessed", denominator - fullyAssessedCount);
            result.put("ratio", ratio);
            result.put("rows", previewRows);
            result.put("message", "Da xu ly danh gia CLO cho " + rowsToInsert.size() + " hoc phan. Day du: " +
                fullyAssessedCount + "/" + denominator + " (" + ratio + "%).");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error processing T1.06 Excel upload", e);
            result.put("status", "ERROR");
            result.put("message", "Loi xu ly file Excel: " + e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 4. POST /kpi/t106/save
    //    Save or update CLO assessment list directly via JSON (from web UI form / table)
    // -------------------------------------------------------------------------
    @PostMapping("/save")
    public ResponseEntity<String> saveCloAssessmentData(@RequestBody String payload) {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(payload);
            String rawPeriod = json.optString("period_code", "SEMESTER_1_2025-2026");
            String normalizedPeriod = normalizePeriod(rawPeriod);
            Integer uploadedBy = json.has("uploaded_by") ? json.getInt("uploaded_by") : null;
            JSONArray items = json.optJSONArray("items");

            if (items == null || items.length() == 0) {
                result.put("status", "ERROR");
                result.put("message", "Danh sach hoc phan danh gia khong duoc trong.");
                return ResponseEntity.badRequest().body(result.toString());
            }

            UUID batchId = UUID.randomUUID();

            // Clear previous records for this period
            jdbcTemplate.update(
                "DELETE FROM dbo.kpi_t106_clo_assessment_data WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, rawPeriod
            );

            int fullyAssessed = 0;
            String insertSql = "INSERT INTO dbo.kpi_t106_clo_assessment_data " +
                "(period_code, course_id, is_fully_assessed, total_clos, assessed_clos, assessment_method, evaluated_by, notes, uploaded_by, batch_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                long courseId = it.getLong("courseId");
                boolean isFull = it.optBoolean("isFullyAssessed", true);
                if (isFull) fullyAssessed++;

                Integer totalClos = it.has("totalClos") && !it.isNull("totalClos") ? it.getInt("totalClos") : null;
                Integer assessedClos = it.has("assessedClos") && !it.isNull("assessedClos") ? it.getInt("assessedClos") : null;
                String method = it.optString("assessmentMethod", null);
                String evaluatedBy = it.optString("evaluatedBy", null);
                String notes = it.optString("notes", null);

                jdbcTemplate.update(insertSql, ps -> {
                    ps.setString(1, normalizedPeriod);
                    ps.setLong(2, courseId);
                    ps.setBoolean(3, isFull);

                    if (totalClos != null) ps.setInt(4, totalClos);
                    else ps.setNull(4, Types.INTEGER);

                    if (assessedClos != null) ps.setInt(5, assessedClos);
                    else ps.setNull(5, Types.INTEGER);

                    if (method != null && !method.isEmpty()) ps.setNString(6, method);
                    else ps.setNull(6, Types.NVARCHAR);

                    if (evaluatedBy != null && !evaluatedBy.isEmpty()) ps.setNString(7, evaluatedBy);
                    else ps.setNull(7, Types.NVARCHAR);

                    if (notes != null && !notes.isEmpty()) ps.setNString(8, notes);
                    else ps.setNull(8, Types.NVARCHAR);

                    if (uploadedBy != null) ps.setInt(9, uploadedBy);
                    else ps.setNull(9, Types.INTEGER);

                    ps.setString(10, batchId.toString());
                });
            }

            Integer totalCurriculumCourses = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.courses", Integer.class);
            if (totalCurriculumCourses == null) totalCurriculumCourses = items.length();

            int denominator = Math.max(totalCurriculumCourses, items.length());
            double ratio = denominator > 0
                ? Math.round((fullyAssessed * 100.0 / denominator) * 100.0) / 100.0
                : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("batch_id", batchId.toString());
            result.put("total_courses", denominator);
            result.put("fully_assessed", fullyAssessed);
            result.put("ratio", ratio);
            result.put("message", "Da luu thong tin danh gia CLO cho " + items.length() + " hoc phan thanh cong.");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error saving T1.06 CLO assessment data", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 5. GET /kpi/t106/data?period=SEMESTER_1_2025-2026
    //    Get list of assessed courses with joined details from courses & training_programs
    // -------------------------------------------------------------------------
    @GetMapping("/data")
    public ResponseEntity<String> getCloAssessmentData(
            @RequestParam(value = "period", defaultValue = "SEMESTER_1_2025-2026") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);

            String sql =
                "SELECT a.id, a.period_code, a.course_id, " +
                "       COALESCE(NULLIF(RTRIM(LTRIM(c.course_code)), ''), CONCAT('HP', RIGHT(CONCAT('0000', c.id), 5))) AS course_code, " +
                "       c.course_name, c.credits, " +
                "       p.id AS program_id, p.code AS program_code, p.name AS program_name, " +
                "       a.is_fully_assessed, a.total_clos, a.assessed_clos, a.assessment_method, " +
                "       a.evaluated_by, a.notes, a.uploaded_by, a.uploaded_at, a.batch_id " +
                "FROM dbo.kpi_t106_clo_assessment_data a " +
                "INNER JOIN dbo.courses c ON a.course_id = c.id " +
                "LEFT JOIN dbo.training_programs p ON c.program_id = p.id " +
                "WHERE a.period_code = ? OR a.period_code = ? " +
                "ORDER BY p.code, c.course_name";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, normalizedPeriod, period);

            // Fallback 1: match school year (e.g. 2025-2026)
            if (rows.isEmpty()) {
                Matcher syM = Pattern.compile("(20\\d\\d)[-_](20\\d\\d)").matcher(period);
                if (syM.find()) {
                    String syPattern = "%" + syM.group(1) + "-" + syM.group(2) + "%";
                    String sySql = sql.replace("WHERE a.period_code = ? OR a.period_code = ?", "WHERE a.period_code LIKE ?");
                    rows = jdbcTemplate.queryForList(sySql, syPattern);
                }
            }

            // Fallback 2: if still empty, fetch the latest uploaded batch
            if (rows.isEmpty()) {
                String latestSql = "SELECT TOP 1 period_code FROM dbo.kpi_t106_clo_assessment_data ORDER BY id DESC";
                List<String> latestPeriods = jdbcTemplate.queryForList(latestSql, String.class);
                if (!latestPeriods.isEmpty()) {
                    rows = jdbcTemplate.queryForList(sql, latestPeriods.get(0), latestPeriods.get(0));
                }
            }

            Integer totalCurriculumCourses = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.courses", Integer.class);
            if (totalCurriculumCourses == null) totalCurriculumCourses = rows.size();

            int fullyAssessed = 0;
            int partiallyAssessed = 0;
            int unassessedInRows = 0;

            JSONArray arr = new JSONArray();
            for (Map<String, Object> r : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", r.get("id"));
                obj.put("periodCode", r.get("period_code"));
                obj.put("courseId", r.get("course_id"));
                obj.put("courseCode", r.get("course_code") != null ? r.get("course_code").toString() : "");
                obj.put("courseName", r.get("course_name") != null ? r.get("course_name").toString() : "");
                obj.put("credits", r.get("credits") != null ? r.get("credits") : 0);
                obj.put("programId", r.get("program_id"));
                obj.put("programCode", r.get("program_code") != null ? r.get("program_code").toString() : "");
                obj.put("programName", r.get("program_name") != null ? r.get("program_name").toString() : "");

                boolean isFull = Boolean.TRUE.equals(r.get("is_fully_assessed"));
                obj.put("isFullyAssessed", isFull);

                Integer tClos = r.get("total_clos") != null ? (Integer) r.get("total_clos") : null;
                Integer aClos = r.get("assessed_clos") != null ? (Integer) r.get("assessed_clos") : null;
                obj.put("totalClos", tClos != null ? tClos : 0);
                obj.put("assessedClos", aClos != null ? aClos : 0);
                obj.put("assessmentMethod", r.get("assessment_method") != null ? r.get("assessment_method").toString() : "");
                obj.put("evaluatedBy", r.get("evaluated_by") != null ? r.get("evaluated_by").toString() : "");
                obj.put("notes", r.get("notes") != null ? r.get("notes").toString() : "");
                obj.put("uploadedAt", r.get("uploaded_at") != null ? r.get("uploaded_at").toString() : "");
                obj.put("batchId", r.get("batch_id") != null ? r.get("batch_id").toString() : "");

                if (isFull) fullyAssessed++;
                else if (aClos != null && aClos > 0) partiallyAssessed++;
                else unassessedInRows++;

                arr.put(obj);
            }

            int denominator = Math.max(totalCurriculumCourses, rows.size());
            double ratio = denominator > 0
                ? Math.round((fullyAssessed * 100.0 / denominator) * 100.0) / 100.0
                : 0.0;

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("total_curriculum_courses", denominator);
            result.put("total_evaluated", rows.size());
            result.put("fully_assessed", fullyAssessed);
            result.put("partially_assessed", partiallyAssessed);
            result.put("unassessed", denominator - fullyAssessed);
            result.put("ratio", ratio);
            result.put("rows", arr);
            result.put("data", arr);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error fetching T1.06 data for period {}", period, e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 6. GET /kpi/t106/ratio?period=SEMESTER_1_2025-2026
    // -------------------------------------------------------------------------
    @GetMapping("/ratio")
    public ResponseEntity<String> getCloAssessmentRatio(
            @RequestParam(value = "period", defaultValue = "SEMESTER_1_2025-2026") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);

            Integer fullyAssessed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.kpi_t106_clo_assessment_data " +
                "WHERE (period_code = ? OR period_code = ?) AND is_fully_assessed = 1",
                Integer.class, normalizedPeriod, period
            );
            if (fullyAssessed == null) fullyAssessed = 0;

            Integer evaluatedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.kpi_t106_clo_assessment_data " +
                "WHERE period_code = ? OR period_code = ?",
                Integer.class, normalizedPeriod, period
            );
            if (evaluatedCount == null) evaluatedCount = 0;

            if (evaluatedCount == 0) {
                // Fallback to year matching
                Matcher syM = Pattern.compile("(20\\d\\d)[-_](20\\d\\d)").matcher(period);
                if (syM.find()) {
                    String syPattern = "%" + syM.group(1) + "-" + syM.group(2) + "%";
                    fullyAssessed = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM dbo.kpi_t106_clo_assessment_data WHERE period_code LIKE ? AND is_fully_assessed = 1",
                        Integer.class, syPattern
                    );
                    if (fullyAssessed == null) fullyAssessed = 0;

                    evaluatedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM dbo.kpi_t106_clo_assessment_data WHERE period_code LIKE ?",
                        Integer.class, syPattern
                    );
                    if (evaluatedCount == null) evaluatedCount = 0;
                }
            }

            if (evaluatedCount == 0) {
                // Fallback to latest batch
                String latestSql = "SELECT TOP 1 period_code FROM dbo.kpi_t106_clo_assessment_data ORDER BY id DESC";
                List<String> latestPeriods = jdbcTemplate.queryForList(latestSql, String.class);
                if (!latestPeriods.isEmpty()) {
                    String lp = latestPeriods.get(0);
                    fullyAssessed = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM dbo.kpi_t106_clo_assessment_data WHERE period_code = ? AND is_fully_assessed = 1",
                        Integer.class, lp
                    );
                    if (fullyAssessed == null) fullyAssessed = 0;

                    evaluatedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM dbo.kpi_t106_clo_assessment_data WHERE period_code = ?",
                        Integer.class, lp
                    );
                    if (evaluatedCount == null) evaluatedCount = 0;
                }
            }

            Integer totalCurriculumCourses = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dbo.courses", Integer.class);
            if (totalCurriculumCourses == null) totalCurriculumCourses = evaluatedCount;

            int denominator = Math.max(totalCurriculumCourses, evaluatedCount);
            double ratio = denominator > 0
                ? Math.round((fullyAssessed * 100.0 / denominator) * 100.0) / 100.0
                : 0.0;

            result.put("status", evaluatedCount > 0 ? "SUCCESS" : "EMPTY");
            result.put("period_code", normalizedPeriod);
            result.put("total_courses", denominator);
            result.put("total_evaluated", evaluatedCount);
            result.put("fully_assessed", fullyAssessed);
            result.put("ratio", ratio);
            if (evaluatedCount == 0) {
                result.put("message", "Chua co du lieu danh gia CLO cho ky " + period);
            }

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error calculating T1.06 ratio for period {}", period, e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 7. DELETE /kpi/t106/data?period=SEMESTER_1_2025-2026
    // -------------------------------------------------------------------------
    @DeleteMapping("/data")
    public ResponseEntity<String> deleteCloAssessmentData(
            @RequestParam(value = "period", defaultValue = "SEMESTER_1_2025-2026") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);
            int rowsDeleted = jdbcTemplate.update(
                "DELETE FROM dbo.kpi_t106_clo_assessment_data WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, period
            );

            result.put("status", "SUCCESS");
            result.put("rows_deleted", rowsDeleted);
            result.put("message", "Da xoa " + rowsDeleted + " ban ghi danh gia CLO cho ky " + period);
            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error deleting T1.06 data for period {}", period, e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }
}
