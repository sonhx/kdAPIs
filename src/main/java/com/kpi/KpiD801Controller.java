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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KpiD801Controller - REST endpoints for KPI D8.01
 * "Tỷ lệ học phần được quản lý trên LMS"
 * Công thức: (Số học phần có LMS / Tổng số học phần trong CSDL) × 100%
 * Chu kỳ: Học kỳ (Semester, e.g. TERM_2025-2026_HK3, SEMESTER_1_2025-2026)
 */
@RestController
@RequestMapping({"/api/kpi/d801", "/kpi/d801"})
@CrossOrigin(origins = "*")
public class KpiD801Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiD801Controller.class);

    private static final String[] VALID_LMS_STATUSES = {
        "Co", "Khong"
    };

    private static final String[] VALID_LMS_PLATFORMS = {
        "Moodle", "D-Space", "MS Teams", "Khac"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTable() {
        try {
            // 1. Detail table for courses managed on LMS
            jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_d801_lms_courses') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_d801_lms_courses ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  course_id BIGINT NULL, " +
                "  course_code VARCHAR(50) NOT NULL, " +
                "  course_name NVARCHAR(500) NOT NULL, " +
                "  credits INT NULL, " +
                "  program_name NVARCHAR(500) NULL, " +
                "  is_lms_managed BIT NOT NULL DEFAULT 1, " +
                "  lms_platform NVARCHAR(100) NULL DEFAULT 'Moodle', " +
                "  lms_course_code NVARCHAR(100) NULL, " +
                "  lms_url NVARCHAR(500) NULL, " +
                "  notes NVARCHAR(1000) NULL, " +
                "  uploaded_by INT NULL, " +
                "  uploaded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "  batch_id NVARCHAR(50) NOT NULL, " +
                "  CONSTRAINT FK_kpi_d801_courses FOREIGN KEY (course_id) REFERENCES dbo.courses(id) ON DELETE SET NULL " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_d801_period_code ON dbo.kpi_d801_lms_courses (period_code); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_d801_course_id ON dbo.kpi_d801_lms_courses (course_id); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_d801_course_code ON dbo.kpi_d801_lms_courses (course_code); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_d801_batch_id ON dbo.kpi_d801_lms_courses (batch_id); " +
                "END;"
            );

            // 2. Summary table for fast caching & manual count persistence
            jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_d801_lms_summary') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_d801_lms_summary ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  total_courses INT NOT NULL DEFAULT 2854, " +
                "  lms_managed_courses INT NOT NULL DEFAULT 0, " +
                "  ratio DECIMAL(6,2) NOT NULL DEFAULT 0.00, " +
                "  input_method NVARCHAR(50) NULL, " +
                "  notes NVARCHAR(1000) NULL, " +
                "  updated_by INT NULL, " +
                "  updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "  CONSTRAINT UQ_kpi_d801_lms_summary_period UNIQUE (period_code) " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_d801_summary_period ON dbo.kpi_d801_lms_summary (period_code); " +
                "END;"
            );

            log.info("Initialized kpi_d801_lms_courses and kpi_d801_lms_summary tables successfully.");
        } catch (Exception e) {
            log.warn("Table kpi_d801 tables init notice: {}", e.getMessage());
        }
    }

    /**
     * Get total courses count directly from dbo.courses table.
     * Defaults to 2,854 if the table is empty or inaccessible.
     */
    private int getTotalCoursesInDatabase() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM dbo.courses", Integer.class);
            if (count != null && count > 0) {
                return count;
            }
        } catch (Exception e) {
            log.warn("Could not query dbo.courses count: {}", e.getMessage());
        }
        return 2854;
    }

    /**
     * Standardize period string to TERM_YYYY-YYYY_HKX or SEMESTER_X_YYYY-YYYY
     */
    private String normalizePeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            return "TERM_2025-2026_HK3";
        }
        String clean = period.trim();

        // 1. Check format: TERM_2025-2026_HK3 or TERM_2025-2026_HK1
        Matcher mTermHk = Pattern.compile("(?i)TERM[\\s_]*(20\\d\\d)[-_](20\\d\\d)[\\s_]*HK([123]|he|summer)").matcher(clean);
        if (mTermHk.find()) {
            return "TERM_" + mTermHk.group(1) + "-" + mTermHk.group(2) + "_HK" + mTermHk.group(3).toUpperCase();
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
            return "TERM_" + mSy.group(1) + "-" + mSy.group(2) + "_HK1";
        }

        return clean;
    }

    // -------------------------------------------------------------------------
    // 1. GET /kpi/d801/template
    //    Build and return an Excel file pre-filled with all active courses from dbo.courses.
    // -------------------------------------------------------------------------
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
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
                    "HUONG DAN SU DUNG FILE MAU D8.01 - TY LE HOC PHAN DUOC QUAN LY TREN LMS",
                    "",
                    "1. Sheet 'Danh sach hoc phan LMS' chua toan bo danh muc 2.854 hoc phan cua Hoc vien tu CSDL.",
                    "2. Cot A (STT), Cot B (Ma HP), Cot C (Ten hoc phan), Cot D (So tin chi), Cot E (Chuong trinh dao tao):",
                    "   - Thong tin goc tu CSDL he thong, KHONG CHINH SUA cac cot nay.",
                    "3. Cot F (Quan ly tren LMS):",
                    "   - Chon 'Co' neu hoc phan da duoc tao khoa hoc va quan ly giang day/hoc tap tren LMS.",
                    "   - Chon 'Khong' hoac de trong neu hoc phan chua dua len LMS.",
                    "4. Cot G (Nen tang LMS):",
                    "   - Chon 'Moodle', 'D-Space', 'MS Teams', hoac 'Khac'.",
                    "5. Cot H (Ma / Link khoa hoc tren LMS):",
                    "   - Dien duong dan link khoa hoc LMS (URL) hoac ma dinh danh khoa hoc tren LMS.",
                    "6. Cot I (Ghi chu):",
                    "   - Ghi chu bo sung (neu co).",
                    "7. Luu file va tai len he thong tai o 'Tai len tep da dien' de he thong tu dong tinh toan ty le D8.01.",
                    "",
                    "CONG THUC TINH:",
                    "KPI D8.01 = (So hoc phan quan ly tren LMS / Tong so hoc phan trong CSDL [2.854]) x 100%"
                };
                for (int i = 0; i < instructions.length; i++) {
                    Row r = infoSheet.createRow(i);
                    Cell c = r.createCell(0);
                    c.setCellValue(instructions[i]);
                }

                // Data sheet
                XSSFSheet dataSheet = workbook.createSheet("Danh sach hoc phan LMS");

                // Headers
                String[] headers = {
                    "STT", "Ma hoc phan (*)", "Ten hoc phan (*)", "So tin chi",
                    "Chuong trinh dao tao", "Quan ly tren LMS (*) (Co/Khong)",
                    "Nen tang LMS (Moodle/D-Space/MS Teams)", "Ma / Link khoa hoc tren LMS", "Ghi chu"
                };

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

                CellStyle centerStyle = workbook.createCellStyle();
                centerStyle.setAlignment(HorizontalAlignment.CENTER);

                Row headerRow = dataSheet.createRow(0);
                headerRow.setHeightInPoints(28);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int rowIdx = 1;
                for (Map<String, Object> c : courses) {
                    Row row = dataSheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(rowIdx - 1);
                    row.getCell(0).setCellStyle(centerStyle);

                    row.createCell(1).setCellValue(c.get("course_code") != null ? c.get("course_code").toString() : "");
                    row.createCell(2).setCellValue(c.get("course_name") != null ? c.get("course_name").toString() : "");

                    Cell credCell = row.createCell(3);
                    if (c.get("credits") != null) {
                        try {
                            credCell.setCellValue(Double.parseDouble(c.get("credits").toString()));
                            credCell.setCellStyle(centerStyle);
                        } catch (Exception ex) {
                            credCell.setCellValue(c.get("credits").toString());
                        }
                    }

                    String progName = c.get("program_name") != null ? c.get("program_name").toString() : "";
                    String progCode = c.get("program_code") != null ? c.get("program_code").toString() : "";
                    row.createCell(4).setCellValue(progCode.isEmpty() ? progName : ("[" + progCode + "] " + progName));

                    // Default LMS status to "Khong"
                    Cell lmsStatusCell = row.createCell(5);
                    lmsStatusCell.setCellValue("Khong");
                    lmsStatusCell.setCellStyle(centerStyle);

                    // LMS platform default
                    row.createCell(6).setCellValue("Moodle");
                    row.createCell(7).setCellValue("");
                    row.createCell(8).setCellValue("");
                }

                dataSheet.setColumnWidth(0, 2000);   // STT
                dataSheet.setColumnWidth(1, 4500);   // Ma HP
                dataSheet.setColumnWidth(2, 12000);  // Ten HP
                dataSheet.setColumnWidth(3, 3000);   // So tin chi
                dataSheet.setColumnWidth(4, 9500);   // Chuong trinh dao tao
                dataSheet.setColumnWidth(5, 7500);   // Quan ly tren LMS
                dataSheet.setColumnWidth(6, 6000);   // Nen tang LMS
                dataSheet.setColumnWidth(7, 9000);   // Ma / Link LMS
                dataSheet.setColumnWidth(8, 7000);   // Ghi chu

                // Add dropdown data validation for LMS Managed column (Col 5)
                if (courses.size() > 0) {
                    DataValidationHelper dvHelper = dataSheet.getDataValidationHelper();
                    DataValidationConstraint dvConstraint =
                        dvHelper.createExplicitListConstraint(VALID_LMS_STATUSES);
                    org.apache.poi.ss.util.CellRangeAddressList addressList =
                        new org.apache.poi.ss.util.CellRangeAddressList(1, courses.size(), 5, 5);
                    DataValidation validation = dvHelper.createValidation(dvConstraint, addressList);
                    validation.setShowErrorBox(false);
                    dataSheet.addValidationData(validation);
                }

                dataSheet.createFreezePane(0, 1);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                workbook.write(baos);
                byte[] bytes = baos.toByteArray();

                String fileName = "mau_d801_hoc_phan_lms.xlsx";
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                responseHeaders.set("Content-Disposition", "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
                responseHeaders.setContentLength(bytes.length);

                return ResponseEntity.ok().headers(responseHeaders).body(bytes);
            }
        } catch (Exception e) {
            log.error("Error generating D8.01 template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // 2. GET /kpi/d801/courses
    //    List active curriculum courses with LMS status for current period
    // -------------------------------------------------------------------------
    @GetMapping("/courses")
    public ResponseEntity<String> getCourses(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "limit", defaultValue = "5000") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        try {
            String normPeriod = normalizePeriod(period);
            String rawPeriod = period != null ? period.trim() : normPeriod;

            StringBuilder sql = new StringBuilder(
                "SELECT c.id AS course_id, " +
                "       COALESCE(NULLIF(RTRIM(LTRIM(c.course_code)), ''), CONCAT('HP', RIGHT(CONCAT('0000', c.id), 5))) AS course_code, " +
                "       c.course_name, c.credits, " +
                "       p.id AS program_id, p.code AS program_code, p.name AS program_name, " +
                "       CASE WHEN lms.id IS NOT NULL AND lms.is_lms_managed = 1 THEN 1 ELSE 0 END AS is_lms_managed, " +
                "       lms.lms_platform, lms.lms_course_code, lms.lms_url " +
                "FROM dbo.courses c " +
                "LEFT JOIN dbo.training_programs p ON c.program_id = p.id " +
                "LEFT JOIN dbo.kpi_d801_lms_courses lms ON (lms.course_id = c.id OR UPPER(RTRIM(LTRIM(lms.course_code))) = UPPER(RTRIM(LTRIM(c.course_code)))) " +
                "     AND (lms.period_code = ? OR lms.period_code = ?) " +
                "WHERE 1=1 "
            );

            List<Object> params = new ArrayList<>();
            params.add(normPeriod);
            params.add(rawPeriod);

            if (search != null && !search.trim().isEmpty()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                sql.append(" AND (LOWER(c.course_name) LIKE ? OR LOWER(c.course_code) LIKE ? OR LOWER(p.name) LIKE ? OR LOWER(p.code) LIKE ?) ");
                params.add(term);
                params.add(term);
                params.add(term);
                params.add(term);
            }

            sql.append(" ORDER BY c.id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(offset);
            params.add(limit);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

            int totalCourses = getTotalCoursesInDatabase();

            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("total", totalCourses);
            res.put("period_code", normPeriod);

            JSONArray arr = new JSONArray();
            for (Map<String, Object> r : rows) {
                JSONObject o = new JSONObject();
                o.put("courseId", r.get("course_id"));
                o.put("courseCode", r.get("course_code"));
                o.put("courseName", r.get("course_name"));
                o.put("credits", r.get("credits"));
                o.put("programId", r.get("program_id"));
                o.put("programCode", r.get("program_code"));
                o.put("programName", r.get("program_name"));
                o.put("isLmsManaged", ((Number) r.get("is_lms_managed")).intValue() == 1);
                o.put("lmsPlatform", r.get("lms_platform") != null ? r.get("lms_platform") : "Moodle");
                o.put("lmsCourseCode", r.get("lms_course_code") != null ? r.get("lms_course_code") : "");
                o.put("lmsUrl", r.get("lms_url") != null ? r.get("lms_url") : "");
                arr.put(o);
            }
            res.put("data", arr);

            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error retrieving D8.01 courses", e);
            return ResponseEntity.internalServerError().body("{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // 3. POST /kpi/d801/upload
    //    Parse uploaded Excel, stores into dbo.kpi_d801_lms_courses and updates summary
    // -------------------------------------------------------------------------
    @PostMapping("/upload")
    public ResponseEntity<String> uploadLmsCoursesData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "period_code", defaultValue = "TERM_2025-2026_HK3") String periodCode,
            @RequestParam(value = "uploaded_by", required = false) Integer uploadedBy) {

        JSONObject result = new JSONObject();

        if (file == null || file.isEmpty()) {
            result.put("status", "ERROR");
            result.put("message", "File khong hop le hoac trong.");
            return ResponseEntity.badRequest().body(result.toString());
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheet("Danh sach hoc phan LMS");
            if (sheet == null) sheet = workbook.getSheetAt(workbook.getNumberOfSheets() > 1 ? 1 : 0);

            DataFormatter formatter = new DataFormatter();
            UUID batchId = UUID.randomUUID();
            String normalizedPeriod = normalizePeriod(periodCode);
            String rawPeriod = periodCode.trim();

            int totalInDb = getTotalCoursesInDatabase();

            // Build lookup of existing courses from dbo.courses
            List<Map<String, Object>> existingCourses = jdbcTemplate.queryForList(
                "SELECT c.id, UPPER(RTRIM(LTRIM(c.course_code))) AS course_code, LOWER(RTRIM(LTRIM(c.course_name))) AS course_name, c.credits, p.name AS program_name " +
                "FROM dbo.courses c " +
                "LEFT JOIN dbo.training_programs p ON c.program_id = p.id"
            );
            Map<String, Map<String, Object>> codeLookup = new HashMap<>();
            Map<String, Map<String, Object>> nameLookup = new HashMap<>();
            for (Map<String, Object> ec : existingCourses) {
                if (ec.get("course_code") != null) codeLookup.put(ec.get("course_code").toString().toUpperCase(), ec);
                if (ec.get("course_name") != null) nameLookup.put(ec.get("course_name").toString().toLowerCase(), ec);
            }

            int totalRows = 0;
            int lmsManagedCount = 0;
            JSONArray previewRows = new JSONArray();

            List<Object[]> batchArgs = new ArrayList<>();

            // Find header columns dynamically
            Row headerRow = sheet.getRow(0);
            int colCode = 1, colName = 2, colCredits = 3, colProgram = 4, colLmsStatus = 5, colPlatform = 6, colUrl = 7, colNotes = 8;
            if (headerRow != null) {
                for (int ci = 0; ci < headerRow.getLastCellNum(); ci++) {
                    String hVal = formatter.formatCellValue(headerRow.getCell(ci)).toLowerCase().trim();
                    if (hVal.contains("ma hoc phan") || hVal.contains("mã học phần") || hVal.contains("course_code") || hVal.equals("ma hp") || hVal.equals("mã hp")) {
                        colCode = ci;
                    } else if (hVal.contains("ten hoc phan") || hVal.contains("tên học phần") || hVal.contains("course_name") || hVal.contains("tên môn") || hVal.contains("ten mon")) {
                        colName = ci;
                    } else if (hVal.contains("tin chi") || hVal.contains("tín chỉ") || hVal.contains("credits")) {
                        colCredits = ci;
                    } else if (hVal.contains("chuong trinh") || hVal.contains("chương trình") || hVal.contains("ctdt")) {
                        colProgram = ci;
                    } else if (hVal.contains("quan ly tren lms") || hVal.contains("quản lý trên lms") || hVal.contains("lms") || hVal.contains("co/khong") || hVal.contains("có/không")) {
                        colLmsStatus = ci;
                    } else if (hVal.contains("nen tang") || hVal.contains("nền tảng") || hVal.contains("platform")) {
                        colPlatform = ci;
                    } else if (hVal.contains("link") || hVal.contains("duong dan") || hVal.contains("đường dẫn") || hVal.contains("url")) {
                        colUrl = ci;
                    } else if (hVal.contains("ghi chu") || hVal.contains("ghi chú") || hVal.contains("notes")) {
                        colNotes = ci;
                    }
                }
            }

            Set<String> processedCodes = new HashSet<>();

            for (int rIdx = 1; rIdx <= sheet.getLastRowNum(); rIdx++) {
                Row row = sheet.getRow(rIdx);
                if (row == null) continue;

                String courseCode = formatter.formatCellValue(row.getCell(colCode)).trim();
                String courseName = formatter.formatCellValue(row.getCell(colName)).trim();
                String rawStatus  = colLmsStatus >= 0 ? formatter.formatCellValue(row.getCell(colLmsStatus)).trim() : "";
                String platform   = colPlatform >= 0 ? formatter.formatCellValue(row.getCell(colPlatform)).trim() : "Moodle";
                String lmsUrl     = colUrl >= 0 ? formatter.formatCellValue(row.getCell(colUrl)).trim() : "";
                String notes      = colNotes >= 0 ? formatter.formatCellValue(row.getCell(colNotes)).trim() : "";

                if (courseCode.isEmpty() && courseName.isEmpty()) continue;

                totalRows++;

                // Match with database course
                Long courseId = null;
                Integer credits = null;
                String programName = null;

                Map<String, Object> matchedCourse = null;
                if (!courseCode.isEmpty() && codeLookup.containsKey(courseCode.toUpperCase())) {
                    matchedCourse = codeLookup.get(courseCode.toUpperCase());
                } else if (!courseName.isEmpty() && nameLookup.containsKey(courseName.toLowerCase())) {
                    matchedCourse = nameLookup.get(courseName.toLowerCase());
                }

                if (matchedCourse != null) {
                    courseId = ((Number) matchedCourse.get("id")).longValue();
                    if (courseCode.isEmpty()) courseCode = (String) matchedCourse.get("course_code");
                    if (courseName.isEmpty()) courseName = (String) matchedCourse.get("course_name");
                    if (matchedCourse.get("credits") != null) credits = ((Number) matchedCourse.get("credits")).intValue();
                    programName = (String) matchedCourse.get("program_name");
                }

                if (courseCode.isEmpty()) {
                    courseCode = "HP" + String.format("%05d", courseId != null ? courseId : totalRows);
                }

                // Determine if managed on LMS:
                // Marked as 'Co', 'Yes', '1', 'True', 'x', or has non-empty LMS link/platform and status not explicitly 'Khong'
                boolean isManaged = false;
                String statusNorm = rawStatus.toLowerCase().replace(" ", "");
                if (statusNorm.contains("co") || statusNorm.contains("có") || statusNorm.equals("1") || statusNorm.equals("x") || statusNorm.equals("true") || statusNorm.equals("yes")) {
                    isManaged = true;
                } else if (rawStatus.isEmpty() && (!lmsUrl.isEmpty() || (!platform.isEmpty() && !platform.equalsIgnoreCase("Khong")))) {
                    isManaged = true;
                } else if (headerRow != null && headerRow.getLastCellNum() <= 3 && !courseCode.isEmpty()) {
                    // Simple file uploaded with just list of LMS courses
                    isManaged = true;
                }

                if (isManaged) {
                    lmsManagedCount++;
                }

                processedCodes.add(courseCode.toUpperCase());

                batchArgs.add(new Object[]{
                    normalizedPeriod,
                    courseId,
                    courseCode,
                    courseName,
                    credits,
                    programName,
                    isManaged ? 1 : 0,
                    platform.isEmpty() ? "Moodle" : platform,
                    courseCode,
                    lmsUrl,
                    notes,
                    uploadedBy,
                    batchId.toString()
                });

                if (previewRows.length() < 100) {
                    JSONObject pr = new JSONObject();
                    pr.put("course_code", courseCode);
                    pr.put("course_name", courseName);
                    pr.put("credits", credits != null ? credits : 0);
                    pr.put("program_name", programName != null ? programName : "");
                    pr.put("is_lms_managed", isManaged);
                    pr.put("lms_platform", platform.isEmpty() ? "Moodle" : platform);
                    pr.put("lms_url", lmsUrl);
                    previewRows.put(pr);
                }
            }

            // Delete previous records for this period
            jdbcTemplate.update(
                "DELETE FROM dbo.kpi_d801_lms_courses WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, rawPeriod
            );

            // Batch insert
            String insertSql = "INSERT INTO dbo.kpi_d801_lms_courses " +
                "(period_code, course_id, course_code, course_name, credits, program_name, is_lms_managed, lms_platform, lms_course_code, lms_url, notes, uploaded_by, batch_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.batchUpdate(insertSql, batchArgs);

            // Denominator is always total courses in database (e.g. 2,854)
            int effectiveTotalCourses = totalInDb > 0 ? totalInDb : Math.max(totalRows, 2854);
            double ratio = effectiveTotalCourses > 0
                ? Math.round(((double) lmsManagedCount / effectiveTotalCourses) * 10000.0) / 100.0
                : 0.0;

            String calcNote = String.format(
                "[Tự động từ tệp LMS \"%s\"]: %d / %d học phần được quản lý trên LMS (Tỷ lệ = %.2f%%).",
                file.getOriginalFilename(), lmsManagedCount, effectiveTotalCourses, ratio
            );

            // Upsert into dbo.kpi_d801_lms_summary
            jdbcTemplate.update(
                "MERGE dbo.kpi_d801_lms_summary AS target " +
                "USING (SELECT ? AS period_code) AS source ON target.period_code = source.period_code " +
                "WHEN MATCHED THEN UPDATE SET total_courses = ?, lms_managed_courses = ?, ratio = ?, input_method = 'EXCEL_UPLOAD', notes = ?, updated_by = ?, updated_at = SYSDATETIME() " +
                "WHEN NOT MATCHED THEN INSERT (period_code, total_courses, lms_managed_courses, ratio, input_method, notes, updated_by, updated_at) " +
                "VALUES (?, ?, ?, ?, 'EXCEL_UPLOAD', ?, ?, SYSDATETIME());",
                normalizedPeriod, effectiveTotalCourses, lmsManagedCount, ratio, calcNote, uploadedBy,
                normalizedPeriod, effectiveTotalCourses, lmsManagedCount, ratio, calcNote, uploadedBy
            );

            result.put("status", "SUCCESS");
            result.put("batch_id", batchId.toString());
            result.put("period_code", normalizedPeriod);
            result.put("total_courses_db", totalInDb);
            result.put("total_courses", effectiveTotalCourses);
            result.put("total_rows_parsed", totalRows);
            result.put("lms_managed_courses", lmsManagedCount);
            result.put("unmanaged_courses", Math.max(0, effectiveTotalCourses - lmsManagedCount));
            result.put("ratio", ratio);
            result.put("calc_note", calcNote);
            result.put("preview", previewRows);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error processing D8.01 upload", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi xử lý file Excel D8.01: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 4. POST /kpi/d801/manual-summary
    //    Directly save total count of LMS managed courses & compute ratio against DB total
    // -------------------------------------------------------------------------
    @PostMapping("/manual-summary")
    public ResponseEntity<String> saveManualSummary(@RequestBody String payload) {
        JSONObject res = new JSONObject();
        try {
            JSONObject body = new JSONObject(payload);
            String rawPeriod = body.optString("period_code", "TERM_2025-2026_HK3");
            String periodCode = normalizePeriod(rawPeriod);
            int lmsManaged = body.optInt("lms_managed_courses", -1);
            if (lmsManaged < 0) {
                lmsManaged = body.optInt("managed_courses", 0);
            }

            int totalInDb = getTotalCoursesInDatabase();
            int total = body.optInt("total_courses", totalInDb);
            if (total <= 0) total = totalInDb;

            double ratio = total > 0
                ? Math.round(((double) lmsManaged / total) * 10000.0) / 100.0
                : 0.0;

            String customNotes = body.optString("notes", "").trim();
            String calcNote = !customNotes.isEmpty() ? customNotes : String.format(
                "[Kê khai học phần LMS]: %d / %d học phần được quản lý trên LMS (Tỷ lệ = %.2f%%).",
                lmsManaged, total, ratio
            );

            Integer updatedBy = body.has("updated_by") ? body.optInt("updated_by") : null;

            // Upsert into dbo.kpi_d801_lms_summary
            jdbcTemplate.update(
                "MERGE dbo.kpi_d801_lms_summary AS target " +
                "USING (SELECT ? AS period_code) AS source ON target.period_code = source.period_code " +
                "WHEN MATCHED THEN UPDATE SET total_courses = ?, lms_managed_courses = ?, ratio = ?, input_method = 'MANUAL_COUNT', notes = ?, updated_by = ?, updated_at = SYSDATETIME() " +
                "WHEN NOT MATCHED THEN INSERT (period_code, total_courses, lms_managed_courses, ratio, input_method, notes, updated_by, updated_at) " +
                "VALUES (?, ?, ?, ?, 'MANUAL_COUNT', ?, ?, SYSDATETIME());",
                periodCode, total, lmsManaged, ratio, calcNote, updatedBy,
                periodCode, total, lmsManaged, ratio, calcNote, updatedBy
            );

            res.put("status", "SUCCESS");
            res.put("period_code", periodCode);
            res.put("total_courses", total);
            res.put("lms_managed_courses", lmsManaged);
            res.put("unmanaged_courses", Math.max(0, total - lmsManaged));
            res.put("ratio", ratio);
            res.put("calc_note", calcNote);

            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error saving D8.01 manual summary", e);
            res.put("status", "ERROR");
            res.put("message", "Lỗi lưu số liệu D8.01: " + e.getMessage());
            return ResponseEntity.badRequest().body(res.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 5. POST /kpi/d801/save-codes
    //    Accept a list/text of course codes managed on LMS, match with DB & compute ratio
    // -------------------------------------------------------------------------
    @PostMapping("/save-codes")
    public ResponseEntity<String> saveCourseCodesList(@RequestBody String payload) {
        JSONObject res = new JSONObject();
        try {
            JSONObject body = new JSONObject(payload);
            String rawPeriod = body.optString("period_code", "TERM_2025-2026_HK3");
            String periodCode = normalizePeriod(rawPeriod);
            String platform = body.optString("lms_platform", "Moodle");
            Integer uploadedBy = body.has("uploaded_by") ? body.optInt("uploaded_by") : null;

            // Extract codes from JSON array or raw string
            List<String> rawCodes = new ArrayList<>();
            if (body.has("course_codes")) {
                Object codesObj = body.get("course_codes");
                if (codesObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) codesObj;
                    for (int i = 0; i < arr.length(); i++) {
                        String s = arr.optString(i, "").trim();
                        if (!s.isEmpty()) rawCodes.add(s);
                    }
                } else if (codesObj instanceof String) {
                    // Split by comma, semicolon, space, newline
                    String[] tokens = ((String) codesObj).split("[,;\\s\\n\\r]+");
                    for (String t : tokens) {
                        String s = t.trim();
                        if (!s.isEmpty()) rawCodes.add(s);
                    }
                }
            }

            int totalInDb = getTotalCoursesInDatabase();

            // Query all courses for matching
            List<Map<String, Object>> existingCourses = jdbcTemplate.queryForList(
                "SELECT c.id, UPPER(RTRIM(LTRIM(c.course_code))) AS course_code, c.course_name, c.credits, p.name AS program_name " +
                "FROM dbo.courses c " +
                "LEFT JOIN dbo.training_programs p ON c.program_id = p.id"
            );
            Map<String, Map<String, Object>> codeMap = new HashMap<>();
            for (Map<String, Object> c : existingCourses) {
                if (c.get("course_code") != null) {
                    codeMap.put(c.get("course_code").toString().toUpperCase(), c);
                }
            }

            Set<String> matchedCodes = new LinkedHashSet<>();
            List<String> invalidCodes = new ArrayList<>();

            for (String code : rawCodes) {
                String up = code.toUpperCase();
                if (codeMap.containsKey(up)) {
                    matchedCodes.add(up);
                } else {
                    invalidCodes.add(code);
                }
            }

            UUID batchId = UUID.randomUUID();

            // Clear previous records for this period
            jdbcTemplate.update(
                "DELETE FROM dbo.kpi_d801_lms_courses WHERE period_code = ? OR period_code = ?",
                periodCode, rawPeriod
            );

            // Insert matched courses into dbo.kpi_d801_lms_courses
            List<Object[]> batchArgs = new ArrayList<>();
            for (String mc : matchedCodes) {
                Map<String, Object> c = codeMap.get(mc);
                Long courseId = ((Number) c.get("id")).longValue();
                String courseName = (String) c.get("course_name");
                Integer credits = c.get("credits") != null ? ((Number) c.get("credits")).intValue() : null;
                String progName = (String) c.get("program_name");

                batchArgs.add(new Object[]{
                    periodCode,
                    courseId,
                    mc,
                    courseName,
                    credits,
                    progName,
                    1,
                    platform,
                    mc,
                    "",
                    "Nhập từ danh sách mã học phần LMS",
                    uploadedBy,
                    batchId.toString()
                });
            }

            if (!batchArgs.isEmpty()) {
                String insertSql = "INSERT INTO dbo.kpi_d801_lms_courses " +
                    "(period_code, course_id, course_code, course_name, credits, program_name, is_lms_managed, lms_platform, lms_course_code, lms_url, notes, uploaded_by, batch_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                jdbcTemplate.batchUpdate(insertSql, batchArgs);
            }

            int managedCount = matchedCodes.size();
            double ratio = totalInDb > 0
                ? Math.round(((double) managedCount / totalInDb) * 10000.0) / 100.0
                : 0.0;

            String calcNote = String.format(
                "[Danh sách mã HP LMS]: %d / %d học phần được quản lý trên LMS (Tỷ lệ = %.2f%%).",
                managedCount, totalInDb, ratio
            );

            // Upsert into summary
            jdbcTemplate.update(
                "MERGE dbo.kpi_d801_lms_summary AS target " +
                "USING (SELECT ? AS period_code) AS source ON target.period_code = source.period_code " +
                "WHEN MATCHED THEN UPDATE SET total_courses = ?, lms_managed_courses = ?, ratio = ?, input_method = 'PASTE_CODES', notes = ?, updated_by = ?, updated_at = SYSDATETIME() " +
                "WHEN NOT MATCHED THEN INSERT (period_code, total_courses, lms_managed_courses, ratio, input_method, notes, updated_by, updated_at) " +
                "VALUES (?, ?, ?, ?, 'PASTE_CODES', ?, ?, SYSDATETIME());",
                periodCode, totalInDb, managedCount, ratio, calcNote, uploadedBy,
                periodCode, totalInDb, managedCount, ratio, calcNote, uploadedBy
            );

            res.put("status", "SUCCESS");
            res.put("period_code", periodCode);
            res.put("total_courses", totalInDb);
            res.put("lms_managed_courses", managedCount);
            res.put("unmanaged_courses", Math.max(0, totalInDb - managedCount));
            res.put("ratio", ratio);
            res.put("matched_count", managedCount);
            res.put("invalid_codes", new JSONArray(invalidCodes));
            res.put("calc_note", calcNote);

            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error saving D8.01 course codes", e);
            res.put("status", "ERROR");
            res.put("message", "Lỗi lưu danh sách mã học phần: " + e.getMessage());
            return ResponseEntity.badRequest().body(res.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 6. GET /kpi/d801/data
    //    Retrieve assessment records and summary for period
    // -------------------------------------------------------------------------
    @GetMapping("/data")
    public ResponseEntity<String> getData(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "raw_period", required = false) String rawPeriodParam) {
        try {
            int totalInDb = getTotalCoursesInDatabase();
            String normPeriod = normalizePeriod(period);
            String rawPeriod = (rawPeriodParam != null && !rawPeriodParam.trim().isEmpty())
                ? rawPeriodParam.trim()
                : (period != null ? period.trim() : normPeriod);

            // 1. Check summary table first
            List<Map<String, Object>> summaries = jdbcTemplate.queryForList(
                "SELECT total_courses, lms_managed_courses, ratio, notes, input_method FROM dbo.kpi_d801_lms_summary WHERE period_code = ? OR period_code = ?",
                normPeriod, rawPeriod
            );

            // 2. Count managed courses from detail table
            Integer managedCountFromDetails = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM dbo.kpi_d801_lms_courses WHERE (period_code = ? OR period_code = ?) AND is_lms_managed = 1",
                Integer.class, normPeriod, rawPeriod
            );

            int managedCount = 0;
            int totalCourses = totalInDb;
            double ratio = 0.0;
            String calcNote = "";

            if (!summaries.isEmpty()) {
                Map<String, Object> sm = summaries.get(0);
                totalCourses = sm.get("total_courses") != null ? ((Number) sm.get("total_courses")).intValue() : totalInDb;
                managedCount = sm.get("lms_managed_courses") != null ? ((Number) sm.get("lms_managed_courses")).intValue() : 0;
                ratio = sm.get("ratio") != null ? ((Number) sm.get("ratio")).doubleValue() : 0.0;
                calcNote = sm.get("notes") != null ? (String) sm.get("notes") : "";
            } else if (managedCountFromDetails != null && managedCountFromDetails > 0) {
                managedCount = managedCountFromDetails;
                ratio = totalCourses > 0 ? Math.round(((double) managedCount / totalCourses) * 10000.0) / 100.0 : 0.0;
                calcNote = String.format("[Học phần LMS]: %d / %d học phần được quản lý trên LMS (Tỷ lệ = %.2f%%).", managedCount, totalCourses, ratio);
            }

            // Fetch top managed courses for preview
            List<Map<String, Object>> detailRows = jdbcTemplate.queryForList(
                "SELECT TOP 200 id, course_id, course_code, course_name, credits, program_name, is_lms_managed, lms_platform, lms_url, notes " +
                "FROM dbo.kpi_d801_lms_courses " +
                "WHERE (period_code = ? OR period_code = ?) AND is_lms_managed = 1 " +
                "ORDER BY id ASC",
                normPeriod, rawPeriod
            );

            JSONArray rowsArr = new JSONArray();
            for (Map<String, Object> r : detailRows) {
                JSONObject o = new JSONObject();
                o.put("id", r.get("id"));
                o.put("course_id", r.get("course_id"));
                o.put("course_code", r.get("course_code"));
                o.put("course_name", r.get("course_name"));
                o.put("credits", r.get("credits"));
                o.put("program_name", r.get("program_name"));
                o.put("is_lms_managed", ((Boolean) r.get("is_lms_managed")));
                o.put("lms_platform", r.get("lms_platform"));
                o.put("lms_url", r.get("lms_url"));
                o.put("notes", r.get("notes"));
                rowsArr.put(o);
            }

            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("period_code", normPeriod);
            res.put("total_courses", totalCourses);
            res.put("lms_managed_courses", managedCount);
            res.put("unmanaged_courses", Math.max(0, totalCourses - managedCount));
            res.put("ratio", ratio);
            res.put("calc_note", calcNote);
            res.put("rows", rowsArr);

            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error retrieving D8.01 data", e);
            return ResponseEntity.internalServerError().body("{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // 7. GET /kpi/d801/ratio
    //    Get current ratio for period
    // -------------------------------------------------------------------------
    @GetMapping("/ratio")
    public ResponseEntity<String> getRatio(@RequestParam(value = "period", required = false) String period) {
        try {
            int totalInDb = getTotalCoursesInDatabase();
            String normPeriod = normalizePeriod(period);
            String rawPeriod = period != null ? period.trim() : normPeriod;

            List<Map<String, Object>> summaries = jdbcTemplate.queryForList(
                "SELECT total_courses, lms_managed_courses, ratio FROM dbo.kpi_d801_lms_summary WHERE period_code = ? OR period_code = ?",
                normPeriod, rawPeriod
            );

            int managedCount = 0;
            int totalCourses = totalInDb;
            double ratio = 0.0;

            if (!summaries.isEmpty()) {
                Map<String, Object> sm = summaries.get(0);
                totalCourses = sm.get("total_courses") != null ? ((Number) sm.get("total_courses")).intValue() : totalInDb;
                managedCount = sm.get("lms_managed_courses") != null ? ((Number) sm.get("lms_managed_courses")).intValue() : 0;
                ratio = sm.get("ratio") != null ? ((Number) sm.get("ratio")).doubleValue() : 0.0;
            } else {
                Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM dbo.kpi_d801_lms_courses WHERE (period_code = ? OR period_code = ?) AND is_lms_managed = 1",
                    Integer.class, normPeriod, rawPeriod
                );
                if (cnt != null && cnt > 0) {
                    managedCount = cnt;
                    ratio = totalCourses > 0 ? Math.round(((double) managedCount / totalCourses) * 10000.0) / 100.0 : 0.0;
                }
            }

            JSONObject res = new JSONObject();
            res.put("status", "SUCCESS");
            res.put("period_code", normPeriod);
            res.put("total_courses", totalCourses);
            res.put("lms_managed_courses", managedCount);
            res.put("ratio", ratio);

            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error retrieving D8.01 ratio", e);
            return ResponseEntity.internalServerError().body("{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // 8. DELETE /kpi/d801/data
    //    Clear data for period
    // -------------------------------------------------------------------------
    @DeleteMapping("/data")
    public ResponseEntity<String> deleteData(@RequestParam(value = "period", required = false) String period) {
        try {
            String normPeriod = normalizePeriod(period);
            String rawPeriod = period != null ? period.trim() : normPeriod;

            jdbcTemplate.update("DELETE FROM dbo.kpi_d801_lms_courses WHERE period_code = ? OR period_code = ?", normPeriod, rawPeriod);
            jdbcTemplate.update("DELETE FROM dbo.kpi_d801_lms_summary WHERE period_code = ? OR period_code = ?", normPeriod, rawPeriod);

            return ResponseEntity.ok("{\"status\":\"SUCCESS\",\"message\":\"Da xoa du lieu D8.01 cho ky " + normPeriod + "\"}");
        } catch (Exception e) {
            log.error("Error deleting D8.01 data", e);
            return ResponseEntity.internalServerError().body("{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}

