package com.kpi;

import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KpiT103Controller - REST endpoints for KPI T1.03
 * "Tỷ lệ sinh viên tốt nghiệp đúng hạn"
 * Chu kỳ: Năm học (School Year, e.g. SCHOOLYEAR_2025-2026, 2025-2026)
 * Khối ngành: Khối Kỹ thuật & Khối Kinh tế - Truyền thông (Cơ sở HN & Cơ sở HCM)
 * Công thức KPI: Tổng SV tốt nghiệp đúng hạn Toàn học viện / Tổng SV cuối khóa Toàn học viện x 100%
 */
@RestController
@RequestMapping({"/api/kpi/t103", "/kpi/t103"})
public class KpiT103Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiT103Controller.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTable() {
        try {
            jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_t103_graduation_data') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_t103_graduation_data ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  cohort NVARCHAR(50) NULL, " +
                "  graduation_milestone NVARCHAR(50) NULL, " +
                "  campus NVARCHAR(20) NOT NULL, " +
                "  faculty_group NVARCHAR(50) NOT NULL, " +
                "  initial_students INT NOT NULL DEFAULT 0, " +
                "  final_students INT NOT NULL DEFAULT 0, " +
                "  eligible_students INT NOT NULL DEFAULT 0, " +
                "  graduated_students INT NOT NULL DEFAULT 0, " +
                "  graduation_rate_by_final DECIMAL(6,2) NULL, " +
                "  graduation_rate_by_initial DECIMAL(6,2) NULL, " +
                "  notes NVARCHAR(1000) NULL, " +
                "  uploaded_by INT NULL, " +
                "  uploaded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "  batch_id NVARCHAR(50) NOT NULL " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t103_period_code ON dbo.kpi_t103_graduation_data (period_code); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t103_campus_faculty ON dbo.kpi_t103_graduation_data (campus, faculty_group); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t103_batch_id ON dbo.kpi_t103_graduation_data (batch_id); " +
                "END; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_t103_major_breakdown') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_t103_major_breakdown ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  cohort NVARCHAR(50) NULL, " +
                "  campus NVARCHAR(20) NOT NULL, " +
                "  faculty_group NVARCHAR(50) NOT NULL, " +
                "  major_code NVARCHAR(50) NOT NULL, " +
                "  major_name NVARCHAR(255) NULL, " +
                "  initial_students INT NOT NULL DEFAULT 0, " +
                "  final_students INT NOT NULL DEFAULT 0, " +
                "  eligible_students INT NOT NULL DEFAULT 0, " +
                "  graduated_students INT NOT NULL DEFAULT 0, " +
                "  graduation_rate_by_final DECIMAL(6,2) NULL, " +
                "  graduation_rate_by_initial DECIMAL(6,2) NULL, " +
                "  batch_id NVARCHAR(50) NOT NULL, " +
                "  uploaded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME() " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t103_major_period ON dbo.kpi_t103_major_breakdown (period_code, campus); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t103_major_batch ON dbo.kpi_t103_major_breakdown (batch_id); " +
                "END;"
            );
            log.info("Initialized kpi_t103_graduation_data and kpi_t103_major_breakdown tables successfully.");
        } catch (Exception e) {
            log.warn("Table kpi_t103 tables init notice: {}", e.getMessage());
        }
    }

    /**
     * Standardize period string to SCHOOLYEAR_YYYY-YYYY or similar
     */
    private String normalizePeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            return "SCHOOLYEAR_2025-2026";
        }
        String clean = period.trim();
        if (clean.matches("(?i)SCHOOLYEAR_\\d{4}-\\d{4}")) {
            return clean.toUpperCase();
        }
        Matcher m = Pattern.compile("(\\d{4})[-_](\\d{4})").matcher(clean);
        if (m.find()) {
            return "SCHOOLYEAR_" + m.group(1) + "-" + m.group(2);
        }
        return clean;
    }

    // -------------------------------------------------------------------------
    // 1. GET /kpi/t103/template - Download pre-configured Excel template
    // -------------------------------------------------------------------------
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            String[] possiblePaths = {
                "e:/eclipse-workspace/qa-ims/apps/qa-dashboard/public/templates/mau_t103_tot_nghiep_dung_han.xlsx",
                "E:/OneDrive/OneDrive - ptit.edu.vn/0001. E-IQA/Data/file from anh Sỹ/template_Totnghiep.xlsx",
                "C:/Users/sonhx/.gemini/antigravity-ide/brain/5b11dcff-3abd-41a7-b741-8a6878d3c2ff/scratch/template_Totnghiep.xlsx"
            };

            for (String p : possiblePaths) {
                File f = new File(p);
                if (f.exists() && f.canRead()) {
                    byte[] data = Files.readAllBytes(Paths.get(p));
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                    headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_t103_tot_nghiep_dung_han.xlsx\"");
                    headers.setContentLength(data.length);
                    return ResponseEntity.ok().headers(headers).body(data);
                }
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error downloading T1.03 template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // 2. POST /kpi/t103/upload - Upload filled Excel file, parse, persist & calculate
    // -------------------------------------------------------------------------
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadGraduationData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "cohort", required = false) String cohort,
            @RequestParam(value = "uploaded_by", required = false) Integer uploadedBy) {

        JSONObject result = new JSONObject();

        if (file == null || file.isEmpty()) {
            result.put("status", "ERROR");
            result.put("message", "Tệp Excel không được để trống.");
            return ResponseEntity.badRequest().body(result.toString());
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Sheet hnSheet = findSheet(workbook, "HN", "Ha Noi", "Hà Nội");
            Sheet hcmSheet = findSheet(workbook, "HCM", "Ho Chi Minh", "Hồ Chí Minh");

            if (hnSheet == null && hcmSheet == null) {
                result.put("status", "ERROR");
                result.put("message", "Tệp Excel không chứa sheet dữ liệu 'HN' hoặc 'HCM' hợp lệ.");
                return ResponseEntity.badRequest().body(result.toString());
            }

            // Extract cohort & milestone from Sheet title if not supplied
            String detectedCohort = cohort;
            String detectedMilestone = "T3.2026";
            if (hnSheet != null) {
                String t1 = getCellString(hnSheet.getRow(0), 0);
                Matcher cM = Pattern.compile("KHÓA\\s*(\\d{4})", Pattern.CASE_INSENSITIVE).matcher(t1);
                if (cM.find() && (detectedCohort == null || detectedCohort.isEmpty())) {
                    detectedCohort = "Khóa " + cM.group(1);
                }
                String r9Title = getCellString(hnSheet.getRow(8), 1);
                Matcher mM = Pattern.compile("\\((T\\d{1,2}\\.\\d{4})\\)", Pattern.CASE_INSENSITIVE).matcher(r9Title);
                if (mM.find()) {
                    detectedMilestone = mM.group(1);
                }
            }
            if (detectedCohort == null || detectedCohort.isEmpty()) {
                detectedCohort = "Khóa 2021";
            }

            String normalizedPeriod = normalizePeriod(period);
            String batchId = UUID.randomUUID().toString();

            // Parse HN Sheet
            SheetData hnData = (hnSheet != null) ? parseSheet(hnSheet, evaluator, "HN", detectedCohort) : new SheetData("HN");

            // Parse HCM Sheet
            SheetData hcmData = (hcmSheet != null) ? parseSheet(hcmSheet, evaluator, "HCM", detectedCohort) : new SheetData("HCM");

            // Compute Aggregation (Toàn Học Viện = HN + HCM)
            FacultyGroupStats aggTechnical = aggregateFaculty(hnData.technical, hcmData.technical);
            FacultyGroupStats aggEconomics = aggregateFaculty(hnData.economics, hcmData.economics);
            FacultyGroupStats aggTotal = aggregateFaculty(aggTechnical, aggEconomics);

            // Compute Campus Totals
            FacultyGroupStats hnTotal = aggregateFaculty(hnData.technical, hnData.economics);
            FacultyGroupStats hcmTotal = aggregateFaculty(hcmData.technical, hcmData.economics);

            // Denominator for KPI T1.03: Final students of Academy
            double primaryRatio = aggTotal.finalStudents > 0
                ? Math.round((aggTotal.graduatedStudents * 100.0 / aggTotal.finalStudents) * 100.0) / 100.0
                : 0.0;
            double ratioByInitial = aggTotal.initialStudents > 0
                ? Math.round((aggTotal.graduatedStudents * 100.0 / aggTotal.initialStudents) * 100.0) / 100.0
                : 0.0;

            // Prepare database records for dbo.kpi_t103_graduation_data
            List<SummaryRowToInsert> summaryRows = new ArrayList<>();
            // HN
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "HN", "KY_THUAT", hnData.technical));
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "HN", "KINH_TE", hnData.economics));
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "HN", "TONG_HOP", hnTotal));
            // HCM
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "HCM", "KY_THUAT", hcmData.technical));
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "HCM", "KINH_TE", hcmData.economics));
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "HCM", "TONG_HOP", hcmTotal));
            // ALL (Aggregated)
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "ALL", "KY_THUAT", aggTechnical));
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "ALL", "KINH_TE", aggEconomics));
            summaryRows.add(new SummaryRowToInsert(normalizedPeriod, detectedCohort, detectedMilestone, "ALL", "TONG_HOP", aggTotal));

            // Clean previous records for this period
            jdbcTemplate.update("DELETE FROM dbo.kpi_t103_graduation_data WHERE period_code = ?", normalizedPeriod);
            jdbcTemplate.update("DELETE FROM dbo.kpi_t103_major_breakdown WHERE period_code = ?", normalizedPeriod);

            // Batch insert summary rows
            String insertSummarySql =
                "INSERT INTO dbo.kpi_t103_graduation_data " +
                "(period_code, cohort, graduation_milestone, campus, faculty_group, initial_students, final_students, eligible_students, graduated_students, graduation_rate_by_final, graduation_rate_by_initial, uploaded_by, batch_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.batchUpdate(insertSummarySql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    SummaryRowToInsert r = summaryRows.get(i);
                    ps.setString(1, r.periodCode);
                    ps.setString(2, r.cohort);
                    ps.setString(3, r.milestone);
                    ps.setString(4, r.campus);
                    ps.setString(5, r.facultyGroup);
                    ps.setInt(6, r.stats.initialStudents);
                    ps.setInt(7, r.stats.finalStudents);
                    ps.setInt(8, r.stats.eligibleStudents);
                    ps.setInt(9, r.stats.graduatedStudents);
                    ps.setBigDecimal(10, java.math.BigDecimal.valueOf(r.stats.rateByFinal));
                    ps.setBigDecimal(11, java.math.BigDecimal.valueOf(r.stats.rateByInitial));
                    if (uploadedBy != null) ps.setInt(12, uploadedBy);
                    else ps.setNull(12, Types.INTEGER);
                    ps.setString(13, batchId);
                }

                @Override
                public int getBatchSize() {
                    return summaryRows.size();
                }
            });

            // Combine and insert Major breakdowns
            List<MajorRow> allMajors = new ArrayList<>();
            allMajors.addAll(hnData.majors);
            allMajors.addAll(hcmData.majors);

            final String finalCohort = detectedCohort;
            if (!allMajors.isEmpty()) {
                String insertMajorSql =
                    "INSERT INTO dbo.kpi_t103_major_breakdown " +
                    "(period_code, cohort, campus, faculty_group, major_code, major_name, initial_students, final_students, eligible_students, graduated_students, graduation_rate_by_final, graduation_rate_by_initial, batch_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                jdbcTemplate.batchUpdate(insertMajorSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        MajorRow m = allMajors.get(i);
                        ps.setString(1, normalizedPeriod);
                        ps.setString(2, finalCohort);
                        ps.setString(3, m.campus);
                        ps.setString(4, m.facultyGroup);
                        ps.setString(5, m.majorCode);
                        ps.setString(6, m.majorName);
                        ps.setInt(7, m.initialStudents);
                        ps.setInt(8, m.finalStudents);
                        ps.setInt(9, m.eligibleStudents);
                        ps.setInt(10, m.graduatedStudents);
                        ps.setBigDecimal(11, java.math.BigDecimal.valueOf(m.rateByFinal));
                        ps.setBigDecimal(12, java.math.BigDecimal.valueOf(m.rateByInitial));
                        ps.setString(13, batchId);
                    }

                    @Override
                    public int getBatchSize() {
                        return allMajors.size();
                    }
                });
            }

            // Build detailed explanation note
            String calcNote = String.format(
                "[Tự động từ tệp kết quả tốt nghiệp \"%s\"]: Toàn học viện tốt nghiệp: %,d / %,d SV cuối khóa (Khối Kỹ thuật: %,d/%,d = %.2f%%, Khối Kinh tế: %,d/%,d = %.2f%%) → Tỷ lệ tốt nghiệp đúng hạn T1.03: %.2f%%.",
                file.getOriginalFilename(),
                aggTotal.graduatedStudents, aggTotal.finalStudents,
                aggTechnical.graduatedStudents, aggTechnical.finalStudents, aggTechnical.rateByFinal,
                aggEconomics.graduatedStudents, aggEconomics.finalStudents, aggEconomics.rateByFinal,
                primaryRatio
            );

            // Build JSON response
            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("cohort", detectedCohort);
            result.put("milestone", detectedMilestone);
            result.put("batch_id", batchId);
            result.put("kpi_code", "T1.03");
            result.put("ratio", primaryRatio);
            result.put("ratio_by_initial", ratioByInitial);
            result.put("calc_note", calcNote);

            // Summary stats object
            JSONObject summaryJson = new JSONObject();
            summaryJson.put("all", facultyToJson(aggTotal));
            summaryJson.put("technical", facultyToJson(aggTechnical));
            summaryJson.put("economics", facultyToJson(aggEconomics));
            result.put("summary", summaryJson);

            // Campus breakdown
            JSONObject campusJson = new JSONObject();
            JSONObject hnJson = new JSONObject();
            hnJson.put("technical", facultyToJson(hnData.technical));
            hnJson.put("economics", facultyToJson(hnData.economics));
            hnJson.put("total", facultyToJson(hnTotal));
            campusJson.put("hn", hnJson);

            JSONObject hcmJson = new JSONObject();
            hcmJson.put("technical", facultyToJson(hcmData.technical));
            hcmJson.put("economics", facultyToJson(hcmData.economics));
            hcmJson.put("total", facultyToJson(hcmTotal));
            campusJson.put("hcm", hcmJson);
            result.put("campus_breakdown", campusJson);

            // Majors preview array
            JSONArray majorsArr = new JSONArray();
            for (MajorRow m : allMajors) {
                JSONObject mo = new JSONObject();
                mo.put("campus", m.campus);
                mo.put("facultyGroup", m.facultyGroup);
                mo.put("majorCode", m.majorCode);
                mo.put("majorName", m.majorName);
                mo.put("initialStudents", m.initialStudents);
                mo.put("finalStudents", m.finalStudents);
                mo.put("eligibleStudents", m.eligibleStudents);
                mo.put("graduatedStudents", m.graduatedStudents);
                mo.put("rateByFinal", m.rateByFinal);
                mo.put("rateByInitial", m.rateByInitial);
                majorsArr.put(mo);
            }
            result.put("majors", majorsArr);

            result.put("message", "Đã xử lý và lưu kết quả tốt nghiệp " + detectedCohort + " thành công. Tỷ lệ KPI T1.03: " + primaryRatio + "%.");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error processing T1.03 Excel upload", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi xử lý file Excel T1.03: " + e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 3. GET /kpi/t103/data?period=SCHOOLYEAR_2025-2026 - Retrieve stored records
    // -------------------------------------------------------------------------
    @GetMapping("/data")
    public ResponseEntity<String> getGraduationData(
            @RequestParam(value = "period", defaultValue = "SCHOOLYEAR_2025-2026") String period) {

        JSONObject result = new JSONObject();
        try {
            String normalizedPeriod = normalizePeriod(period);

            String summarySql =
                "SELECT campus, faculty_group, cohort, graduation_milestone, initial_students, final_students, " +
                "       eligible_students, graduated_students, graduation_rate_by_final, graduation_rate_by_initial " +
                "FROM dbo.kpi_t103_graduation_data " +
                "WHERE period_code = ? OR period_code = ? " +
                "ORDER BY campus, faculty_group";

            List<Map<String, Object>> summaryRows = jdbcTemplate.queryForList(summarySql, normalizedPeriod, period);

            // Fallback: match by year
            if (summaryRows.isEmpty()) {
                Matcher syM = Pattern.compile("(20\\d\\d)[-_](20\\d\\d)").matcher(period);
                if (syM.find()) {
                    String pattern = "%" + syM.group(1) + "-" + syM.group(2) + "%";
                    summaryRows = jdbcTemplate.queryForList(
                        summarySql.replace("WHERE period_code = ? OR period_code = ?", "WHERE period_code LIKE ?"),
                        pattern
                    );
                }
            }

            // Fallback 2: latest record
            if (summaryRows.isEmpty()) {
                String latestSql = "SELECT TOP 1 period_code FROM dbo.kpi_t103_graduation_data ORDER BY id DESC";
                List<String> latestPeriods = jdbcTemplate.queryForList(latestSql, String.class);
                if (!latestPeriods.isEmpty()) {
                    summaryRows = jdbcTemplate.queryForList(summarySql, latestPeriods.get(0), latestPeriods.get(0));
                }
            }

            if (summaryRows.isEmpty()) {
                result.put("status", "EMPTY");
                result.put("message", "Chưa có dữ liệu tốt nghiệp cho chu kỳ " + period);
                return ResponseEntity.ok(result.toString());
            }

            // Fetch major breakdown
            String majorSql =
                "SELECT campus, faculty_group, major_code, major_name, initial_students, final_students, " +
                "       eligible_students, graduated_students, graduation_rate_by_final, graduation_rate_by_initial " +
                "FROM dbo.kpi_t103_major_breakdown " +
                "WHERE period_code = ? OR period_code = ? " +
                "ORDER BY campus, major_code";
            List<Map<String, Object>> majorRows = jdbcTemplate.queryForList(majorSql, normalizedPeriod, period);

            // Populate JSON
            JSONObject campusJson = new JSONObject();
            JSONObject hnJson = new JSONObject();
            JSONObject hcmJson = new JSONObject();
            JSONObject summaryJson = new JSONObject();

            double ratio = 0.0;
            double ratioByInitial = 0.0;
            String cohort = "Khóa 2021";
            String milestone = "T3.2026";

            for (Map<String, Object> r : summaryRows) {
                String c = (String) r.get("campus");
                String f = (String) r.get("faculty_group");
                cohort = (String) r.get("cohort");
                milestone = (String) r.get("graduation_milestone");

                JSONObject rowObj = mapToFacultyJson(r);

                if ("ALL".equalsIgnoreCase(c)) {
                    if ("KY_THUAT".equalsIgnoreCase(f)) summaryJson.put("technical", rowObj);
                    else if ("KINH_TE".equalsIgnoreCase(f)) summaryJson.put("economics", rowObj);
                    else if ("TONG_HOP".equalsIgnoreCase(f)) {
                        summaryJson.put("all", rowObj);
                        Number rateNum = (Number) r.get("graduation_rate_by_final");
                        if (rateNum != null) ratio = rateNum.doubleValue();
                        Number initRateNum = (Number) r.get("graduation_rate_by_initial");
                        if (initRateNum != null) ratioByInitial = initRateNum.doubleValue();
                    }
                } else if ("HN".equalsIgnoreCase(c)) {
                    if ("KY_THUAT".equalsIgnoreCase(f)) hnJson.put("technical", rowObj);
                    else if ("KINH_TE".equalsIgnoreCase(f)) hnJson.put("economics", rowObj);
                    else if ("TONG_HOP".equalsIgnoreCase(f)) hnJson.put("total", rowObj);
                } else if ("HCM".equalsIgnoreCase(c)) {
                    if ("KY_THUAT".equalsIgnoreCase(f)) hcmJson.put("technical", rowObj);
                    else if ("KINH_TE".equalsIgnoreCase(f)) hcmJson.put("economics", rowObj);
                    else if ("TONG_HOP".equalsIgnoreCase(f)) hcmJson.put("total", rowObj);
                }
            }

            campusJson.put("hn", hnJson);
            campusJson.put("hcm", hcmJson);

            JSONArray majorsArr = new JSONArray();
            for (Map<String, Object> m : majorRows) {
                JSONObject mo = new JSONObject();
                mo.put("campus", m.get("campus"));
                mo.put("facultyGroup", m.get("faculty_group"));
                mo.put("majorCode", m.get("major_code"));
                mo.put("majorName", m.get("major_name"));
                mo.put("initialStudents", m.get("initial_students"));
                mo.put("finalStudents", m.get("final_students"));
                mo.put("eligibleStudents", m.get("eligible_students"));
                mo.put("graduatedStudents", m.get("graduated_students"));
                mo.put("rateByFinal", m.get("graduation_rate_by_final"));
                mo.put("rateByInitial", m.get("graduation_rate_by_initial"));
                majorsArr.put(mo);
            }

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("cohort", cohort);
            result.put("milestone", milestone);
            result.put("kpi_code", "T1.03");
            result.put("ratio", ratio);
            result.put("ratio_by_initial", ratioByInitial);
            result.put("summary", summaryJson);
            result.put("campus_breakdown", campusJson);
            result.put("majors", majorsArr);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error retrieving T1.03 graduation data", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Helper Classes and Parsing Methods
    // -------------------------------------------------------------------------
    private static class FacultyGroupStats {
        int initialStudents = 0;
        int finalStudents = 0;
        int eligibleStudents = 0;
        int graduatedStudents = 0;
        double rateByFinal = 0.0;
        double rateByInitial = 0.0;
    }

    private static class SheetData {
        String campus;
        FacultyGroupStats technical = new FacultyGroupStats();
        FacultyGroupStats economics = new FacultyGroupStats();
        List<MajorRow> majors = new ArrayList<>();

        SheetData(String campus) {
            this.campus = campus;
        }
    }

    private static class MajorRow {
        String campus;
        String facultyGroup;
        String majorCode;
        String majorName;
        int initialStudents = 0;
        int finalStudents = 0;
        int eligibleStudents = 0;
        int graduatedStudents = 0;
        double rateByFinal = 0.0;
        double rateByInitial = 0.0;
    }

    private static class SummaryRowToInsert {
        String periodCode;
        String cohort;
        String milestone;
        String campus;
        String facultyGroup;
        FacultyGroupStats stats;

        SummaryRowToInsert(String periodCode, String cohort, String milestone, String campus, String facultyGroup, FacultyGroupStats stats) {
            this.periodCode = periodCode;
            this.cohort = cohort;
            this.milestone = milestone;
            this.campus = campus;
            this.facultyGroup = facultyGroup;
            this.stats = stats;
        }
    }

    private Sheet findSheet(Workbook wb, String... names) {
        for (String n : names) {
            Sheet s = wb.getSheet(n);
            if (s != null) return s;
        }
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String name = wb.getSheetName(i);
            for (String n : names) {
                if (name.equalsIgnoreCase(n) || name.contains(n)) {
                    return wb.getSheetAt(i);
                }
            }
        }
        return null;
    }

    private SheetData parseSheet(Sheet sheet, FormulaEvaluator evaluator, String campus, String cohort) {
        SheetData sd = new SheetData(campus);

        // Find columns for Khối Kỹ thuật & Khối Kinh tế
        int techCol = -1;
        int econCol = -1;

        // Search row 2, 3, 4 for headers
        for (int r = 1; r <= 4; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String val = getCellString(row, c).trim().toLowerCase();
                if (techCol == -1 && (val.contains("khối kỹ thuật") || (val.contains("kỹ thuật") && val.contains("tổng")))) {
                    techCol = c;
                }
                if (econCol == -1 && (val.contains("kinh tế") || val.contains("kinh tế và truyền thông"))) {
                    econCol = c;
                }
            }
        }

        // Fallbacks if not detected by header text
        if (techCol == -1) {
            techCol = "HN".equalsIgnoreCase(campus) ? 14 : 16; // O=14 (HN), Q=16 (HCM)
        }
        if (econCol == -1) {
            econCol = "HN".equalsIgnoreCase(campus) ? 16 : 18; // Q=16 (HN), S=18 (HCM)
        }

        // Extract numbers for row 6 (đầu khóa), 7 (cuối khóa), 8 (đủ ĐK), 9 (tốt nghiệp)
        // 0-indexed: row 5 is Row 6; row 6 is Row 7; row 7 is Row 8; row 8 is Row 9
        Row r6 = sheet.getRow(5);
        Row r7 = sheet.getRow(6);
        Row r8 = sheet.getRow(7);
        Row r9 = sheet.getRow(8);

        // Technical Faculty
        sd.technical.initialStudents = getCellInt(r6, techCol, evaluator);
        sd.technical.finalStudents = getCellInt(r7, techCol, evaluator);
        sd.technical.eligibleStudents = getCellInt(r8, techCol, evaluator);
        sd.technical.graduatedStudents = getCellInt(r9, techCol, evaluator);
        if (sd.technical.finalStudents > 0) {
            sd.technical.rateByFinal = Math.round((sd.technical.graduatedStudents * 100.0 / sd.technical.finalStudents) * 100.0) / 100.0;
        }
        if (sd.technical.initialStudents > 0) {
            sd.technical.rateByInitial = Math.round((sd.technical.graduatedStudents * 100.0 / sd.technical.initialStudents) * 100.0) / 100.0;
        }

        // Economics Faculty
        sd.economics.initialStudents = getCellInt(r6, econCol, evaluator);
        sd.economics.finalStudents = getCellInt(r7, econCol, evaluator);
        sd.economics.eligibleStudents = getCellInt(r8, econCol, evaluator);
        sd.economics.graduatedStudents = getCellInt(r9, econCol, evaluator);
        if (sd.economics.finalStudents > 0) {
            sd.economics.rateByFinal = Math.round((sd.economics.graduatedStudents * 100.0 / sd.economics.finalStudents) * 100.0) / 100.0;
        }
        if (sd.economics.initialStudents > 0) {
            sd.economics.rateByInitial = Math.round((sd.economics.graduatedStudents * 100.0 / sd.economics.initialStudents) * 100.0) / 100.0;
        }

        // Parse individual majors from columns C (col 2) up to techCol
        Row majorHeaderRow = sheet.getRow(3);
        if (majorHeaderRow != null) {
            for (int c = 2; c < techCol; c += 2) {
                String majorCode = getCellString(majorHeaderRow, c).trim();
                if (majorCode.isEmpty() || majorCode.equalsIgnoreCase("sl") || majorCode.equalsIgnoreCase("tt")) {
                    continue;
                }
                MajorRow mr = new MajorRow();
                mr.campus = campus;
                mr.facultyGroup = "KY_THUAT";
                mr.majorCode = majorCode;
                mr.majorName = resolveMajorName(majorCode);
                mr.initialStudents = getCellInt(r6, c, evaluator);
                mr.finalStudents = getCellInt(r7, c, evaluator);
                mr.eligibleStudents = getCellInt(r8, c, evaluator);
                mr.graduatedStudents = getCellInt(r9, c, evaluator);
                if (mr.finalStudents > 0) {
                    mr.rateByFinal = Math.round((mr.graduatedStudents * 100.0 / mr.finalStudents) * 100.0) / 100.0;
                }
                if (mr.initialStudents > 0) {
                    mr.rateByInitial = Math.round((mr.graduatedStudents * 100.0 / mr.initialStudents) * 100.0) / 100.0;
                }
                sd.majors.add(mr);
            }
        }

        return sd;
    }

    private FacultyGroupStats aggregateFaculty(FacultyGroupStats a, FacultyGroupStats b) {
        FacultyGroupStats agg = new FacultyGroupStats();
        agg.initialStudents = (a != null ? a.initialStudents : 0) + (b != null ? b.initialStudents : 0);
        agg.finalStudents = (a != null ? a.finalStudents : 0) + (b != null ? b.finalStudents : 0);
        agg.eligibleStudents = (a != null ? a.eligibleStudents : 0) + (b != null ? b.eligibleStudents : 0);
        agg.graduatedStudents = (a != null ? a.graduatedStudents : 0) + (b != null ? b.graduatedStudents : 0);
        if (agg.finalStudents > 0) {
            agg.rateByFinal = Math.round((agg.graduatedStudents * 100.0 / agg.finalStudents) * 100.0) / 100.0;
        }
        if (agg.initialStudents > 0) {
            agg.rateByInitial = Math.round((agg.graduatedStudents * 100.0 / agg.initialStudents) * 100.0) / 100.0;
        }
        return agg;
    }

    private String getCellString(Row row, int col) {
        if (row == null) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue();
                case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
                case FORMULA: return cell.getCellFormula();
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private int getCellInt(Row row, int col, FormulaEvaluator evaluator) {
        if (row == null) return 0;
        Cell cell = row.getCell(col);
        if (cell == null) return 0;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA && evaluator != null) {
                CellValue cv = evaluator.evaluate(cell);
                if (cv != null) {
                    if (cv.getCellType() == CellType.NUMERIC) return (int) Math.round(cv.getNumberValue());
                    if (cv.getCellType() == CellType.STRING) return parseSafeInt(cv.getStringValue());
                }
            } else if (type == CellType.NUMERIC) {
                return (int) Math.round(cell.getNumericCellValue());
            } else if (type == CellType.STRING) {
                return parseSafeInt(cell.getStringCellValue());
            }
        } catch (Exception e) {
            try {
                return (int) Math.round(cell.getNumericCellValue());
            } catch (Exception ignore) {}
        }
        return 0;
    }

    private int parseSafeInt(String s) {
        if (s == null) return 0;
        String clean = s.replaceAll("[^0-9]", "");
        if (clean.isEmpty()) return 0;
        try {
            return Integer.parseInt(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    private String resolveMajorName(String code) {
        if (code == null) return "";
        String c = code.trim().toUpperCase();
        if (c.equals("ĐTVT") || c.equals("DTVT")) return "Kỹ thuật Điện tử Viễn thông";
        if (c.equals("Đ-ĐT") || c.equals("D-DT") || c.equals("DDT")) return "Kỹ thuật Điện, Điện tử";
        if (c.equals("CNTT")) return "Công nghệ Thông tin";
        if (c.equals("CN ĐPT") || c.equals("CNDPT")) return "Công nghệ Đa phương tiện";
        if (c.equals("ATTT")) return "An toàn Thông tin";
        if (c.equals("CNTT-CLC")) return "Công nghệ Thông tin (Chất lượng cao)";
        if (c.equals("KT ĐKTĐH") || c.equals("KTDKTDH")) return "Kỹ thuật Điều khiển & Tự động hóa";
        return code;
    }

    private JSONObject facultyToJson(FacultyGroupStats f) {
        JSONObject obj = new JSONObject();
        obj.put("initialStudents", f != null ? f.initialStudents : 0);
        obj.put("finalStudents", f != null ? f.finalStudents : 0);
        obj.put("eligibleStudents", f != null ? f.eligibleStudents : 0);
        obj.put("graduatedStudents", f != null ? f.graduatedStudents : 0);
        obj.put("rateByFinal", f != null ? f.rateByFinal : 0.0);
        obj.put("rateByInitial", f != null ? f.rateByInitial : 0.0);
        return obj;
    }

    private JSONObject mapToFacultyJson(Map<String, Object> r) {
        JSONObject obj = new JSONObject();
        obj.put("initialStudents", r.get("initial_students") != null ? r.get("initial_students") : 0);
        obj.put("finalStudents", r.get("final_students") != null ? r.get("final_students") : 0);
        obj.put("eligibleStudents", r.get("eligible_students") != null ? r.get("eligible_students") : 0);
        obj.put("graduatedStudents", r.get("graduated_students") != null ? r.get("graduated_students") : 0);
        obj.put("rateByFinal", r.get("graduation_rate_by_final") != null ? r.get("graduation_rate_by_final") : 0.0);
        obj.put("rateByInitial", r.get("graduation_rate_by_initial") != null ? r.get("graduation_rate_by_initial") : 0.0);
        return obj;
    }
}
