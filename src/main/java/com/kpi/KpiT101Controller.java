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
 * KpiT101Controller - REST endpoints for KPI T1.01
 * "Tỷ lệ sinh viên nhập học / tuyển sinh theo kế hoạch"
 * Chu kỳ: Năm (Year, e.g. YEAR_2026, 2026)
 * Đối soát tự động:
 *   - Chỉ tiêu (Mẫu số): Lấy từ bảng dbo.admissions (đã crawl từ Đề án tuyển sinh PTIT)
 *   - Số thực nhập (Tử số): Nhập từ file Excel do Phòng Đào tạo upload (mức Ngành/CTĐT)
 * Tự động tính toán & tổng hợp:
 *   1. Mức Ngành / Chương trình đào tạo (Granular level)
 *   2. Mức Khoa chủ quản (Department roll-up)
 *   3. Mức Cơ sở BVH & BVS (Campus roll-up)
 *   4. Mức Toàn Học viện (Institutional level -> lưu kpi_data_points cho T1.01)
 */
@RestController
@RequestMapping({"/api/kpi/t101", "/kpi/t101"})
public class KpiT101Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiT101Controller.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private kpiExtend kpiExtendService;

    @PostConstruct
    public void initTable() {
        try {
            jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_t101_admission_data') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_t101_admission_data ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  admission_year INT NOT NULL, " +
                "  campus NVARCHAR(20) NOT NULL, " +
                "  unit_type NVARCHAR(50) NOT NULL, " +
                "  unit_code NVARCHAR(50) NOT NULL, " +
                "  unit_name NVARCHAR(255) NOT NULL, " +
                "  target_quota INT NOT NULL DEFAULT 0, " +
                "  enrolled_count INT NOT NULL DEFAULT 0, " +
                "  enrollment_rate DECIMAL(6,2) NULL, " +
                "  notes NVARCHAR(1000) NULL, " +
                "  uploaded_by INT NULL, " +
                "  uploaded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(), " +
                "  batch_id NVARCHAR(50) NOT NULL " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t101_period_code ON dbo.kpi_t101_admission_data (period_code); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t101_campus_unit ON dbo.kpi_t101_admission_data (campus, unit_type, unit_code); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t101_batch_id ON dbo.kpi_t101_admission_data (batch_id); " +
                "END; " +
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.kpi_t101_major_enrollment') AND type = 'U') " +
                "BEGIN " +
                "CREATE TABLE dbo.kpi_t101_major_enrollment ( " +
                "  id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY, " +
                "  period_code NVARCHAR(50) NOT NULL, " +
                "  admission_year INT NOT NULL, " +
                "  campus_code NVARCHAR(20) NOT NULL, " +
                "  major_code NVARCHAR(50) NOT NULL, " +
                "  major_name NVARCHAR(255) NOT NULL, " +
                "  program_type NVARCHAR(100) NOT NULL, " +
                "  department_code NVARCHAR(50) NULL, " +
                "  department_name NVARCHAR(255) NULL, " +
                "  target_quota INT NOT NULL DEFAULT 0, " +
                "  enrolled_count INT NOT NULL DEFAULT 0, " +
                "  enrollment_rate DECIMAL(6,2) NULL, " +
                "  notes NVARCHAR(1000) NULL, " +
                "  batch_id NVARCHAR(50) NOT NULL, " +
                "  uploaded_at DATETIME2 NOT NULL DEFAULT SYSDATETIME() " +
                "); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t101_major_period ON dbo.kpi_t101_major_enrollment (period_code, campus_code, major_code); " +
                "CREATE NONCLUSTERED INDEX IX_kpi_t101_major_batch ON dbo.kpi_t101_major_enrollment (batch_id); " +
                "END;"
            );
            log.info("Initialized kpi_t101_admission_data and kpi_t101_major_enrollment tables successfully.");
        } catch (Exception e) {
            log.warn("Table kpi_t101 tables init notice: {}", e.getMessage());
        }
    }

    private String normalizePeriod(String period, int year) {
        if (period == null || period.trim().isEmpty()) {
            return "YEAR_" + year;
        }
        String clean = period.trim().toUpperCase();
        if (clean.startsWith("YEAR_")) {
            return clean;
        }
        Matcher m = Pattern.compile("(\\d{4})").matcher(clean);
        if (m.find()) {
            return "YEAR_" + m.group(1);
        }
        return "YEAR_" + year;
    }

    // -------------------------------------------------------------------------
    // 1. GET /api/kpi/t101/template - Download pre-configured Excel template
    // -------------------------------------------------------------------------
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            String[] possiblePaths = {
                "e:/eclipse-workspace/qa-ims/apps/qa-dashboard/public/templates/mau_t101_nhap_hoc_tuyen_sinh.xlsx",
                "e:/eclipse-workspace/qa-ims/scratch/mau_t101_nhap_hoc_tuyen_sinh.xlsx",
                "public/templates/mau_t101_nhap_hoc_tuyen_sinh.xlsx"
            };

            for (String p : possiblePaths) {
                File f = new File(p);
                if (f.exists() && f.canRead()) {
                    byte[] data = Files.readAllBytes(Paths.get(p));
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                    headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau_t101_nhap_hoc_tuyen_sinh.xlsx\"");
                    headers.setContentLength(data.length);
                    return ResponseEntity.ok().headers(headers).body(data);
                }
            }

            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error downloading T1.01 template", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // 2. POST /api/kpi/t101/upload - Upload filled Excel file, parse, join & persist
    // -------------------------------------------------------------------------
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadAdmissionData(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "year", defaultValue = "2026") int year,
            @RequestParam(value = "period", required = false) String period,
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
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;

            if (sheet == null) {
                result.put("status", "ERROR");
                result.put("message", "Tệp Excel không chứa sheet dữ liệu hợp lệ.");
                return ResponseEntity.badRequest().body(result.toString());
            }

            // 1. Fetch official admissions quota from dbo.admissions for target year
            String quotaSql = "SELECT campus_code, major_code, major_name, program_type, target_quota " +
                              "FROM dbo.admissions WHERE admission_year = ?";
            List<Map<String, Object>> quotaList = jdbcTemplate.queryForList(quotaSql, year);

            // Lookup maps for quota
            Map<String, Integer> quotaMapExact = new HashMap<>();
            Map<String, Integer> quotaMapMajorOnly = new HashMap<>();

            for (Map<String, Object> q : quotaList) {
                String cCode = q.get("campus_code") != null ? q.get("campus_code").toString().trim().toUpperCase() : "";
                String mCode = q.get("major_code") != null ? q.get("major_code").toString().trim().toUpperCase() : "";
                String pType = q.get("program_type") != null ? q.get("program_type").toString().trim().toLowerCase() : "";
                int quota = q.get("target_quota") != null ? ((Number) q.get("target_quota")).intValue() : 0;

                quotaMapExact.put(cCode + "|" + mCode + "|" + pType, quota);
                quotaMapMajorOnly.put(cCode + "|" + mCode, quota);
            }

            log.info("Loaded {} admission quota records from dbo.admissions for year {}", quotaList.size(), year);

            // 2. Identify header row and column indices
            int headerRowIdx = -1;
            int colMajorCode = -1;
            int colMajorName = -1;
            int colCampus = -1;
            int colProgType = -1;
            int colDept = -1;
            int colEnrolled = -1;
            int colNotes = -1;

            for (int r = 0; r <= Math.min(15, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String val = getCellString(row, c).trim().toLowerCase();
                    if (val.contains("mã xét tuyển") || val.contains("mã ngành")) {
                        colMajorCode = c;
                        headerRowIdx = r;
                    } else if (val.contains("tên ngành") || val.contains("chương trình")) {
                        colMajorName = c;
                    } else if (val.contains("cơ sở")) {
                        colCampus = c;
                    } else if (val.contains("loại hình") || val.contains("hệ đào tạo")) {
                        colProgType = c;
                    } else if (val.contains("khoa") || val.contains("đơn vị")) {
                        colDept = c;
                    } else if (val.contains("nhập học") || val.contains("thực tế")) {
                        colEnrolled = c;
                    } else if (val.contains("ghi chú") || val.contains("quyết định")) {
                        colNotes = c;
                    }
                }
                if (colMajorCode != -1 && colEnrolled != -1) {
                    break;
                }
            }

            // Fallback column positions if not found by header text
            if (headerRowIdx == -1) headerRowIdx = 5; // row 6 is standard (0-indexed: 5)
            if (colCampus == -1) colCampus = 1;      // Col B
            if (colMajorCode == -1) colMajorCode = 2; // Col C
            if (colMajorName == -1) colMajorName = 3; // Col D
            if (colProgType == -1) colProgType = 4;   // Col E
            if (colDept == -1) colDept = 5;           // Col F
            if (colEnrolled == -1) colEnrolled = 6;   // Col G
            if (colNotes == -1) colNotes = 7;         // Col H

            // 3. Parse data rows
            String normalizedPeriod = normalizePeriod(period, year);
            String batchId = UUID.randomUUID().toString();

            List<MajorEnrollmentRow> majorRows = new ArrayList<>();
            Map<String, UnitStats> deptStatsMap = new LinkedHashMap<>();
            UnitStats bvhTotal = new UnitStats("BVH", "CAMPUS", "Cơ sở Hà Nội (BVH)");
            UnitStats bvsTotal = new UnitStats("BVS", "CAMPUS", "Cơ sở TP.HCM (BVS)");
            UnitStats allTotal = new UnitStats("ALL", "INSTITUTION", "Toàn Học viện");

            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String majorCode = getCellString(row, colMajorCode).trim().toUpperCase();
                // Skip empty rows or summary rows
                if (majorCode.isEmpty() || majorCode.contains("TỔNG") || majorCode.equalsIgnoreCase("STT")) {
                    continue;
                }

                String campusRaw = getCellString(row, colCampus).trim();
                String campusCode = "BVH";
                if (campusRaw.contains("BVS") || campusRaw.toUpperCase().contains("TP.HCM") || campusRaw.toUpperCase().contains("HỒ CHÍ MINH")) {
                    campusCode = "BVS";
                }

                String majorName = getCellString(row, colMajorName).trim();
                String progType = getCellString(row, colProgType).trim();
                if (progType.isEmpty()) progType = "Đại trà";

                String deptName = getCellString(row, colDept).trim();
                if (deptName.isEmpty()) {
                    deptName = resolveDepartment(campusCode, majorCode, majorName, progType);
                }
                String deptCode = buildDeptCode(deptName);

                int enrolled = getCellInt(row, colEnrolled, evaluator);
                String notes = getCellString(row, colNotes).trim();

                // Join with target quota from dbo.admissions
                int targetQuota = quotaMapExact.getOrDefault(
                    campusCode + "|" + majorCode + "|" + progType.toLowerCase(),
                    quotaMapMajorOnly.getOrDefault(campusCode + "|" + majorCode, 0)
                );

                double rate = targetQuota > 0
                    ? Math.round((enrolled * 100.0 / targetQuota) * 100.0) / 100.0
                    : 0.0;

                MajorEnrollmentRow mr = new MajorEnrollmentRow();
                mr.periodCode = normalizedPeriod;
                mr.admissionYear = year;
                mr.campusCode = campusCode;
                mr.majorCode = majorCode;
                mr.majorName = majorName;
                mr.programType = progType;
                mr.departmentCode = deptCode;
                mr.departmentName = deptName;
                mr.targetQuota = targetQuota;
                mr.enrolledCount = enrolled;
                mr.enrollmentRate = rate;
                mr.notes = notes;
                mr.batchId = batchId;
                majorRows.add(mr);

                // Accumulate stats by department
                UnitStats ds = deptStatsMap.computeIfAbsent(deptCode, k -> new UnitStats(k, "DEPARTMENT", mr.departmentName));
                ds.campus = campusCode;
                ds.targetQuota += targetQuota;
                ds.enrolledCount += enrolled;

                // Accumulate stats by campus
                if ("BVH".equals(campusCode)) {
                    bvhTotal.targetQuota += targetQuota;
                    bvhTotal.enrolledCount += enrolled;
                } else {
                    bvsTotal.targetQuota += targetQuota;
                    bvsTotal.enrolledCount += enrolled;
                }

                // Accumulate whole institution
                allTotal.targetQuota += targetQuota;
                allTotal.enrolledCount += enrolled;
            }

            if (majorRows.isEmpty()) {
                result.put("status", "ERROR");
                result.put("message", "Không tìm thấy dòng dữ liệu tuyển sinh hợp lệ nào trong tệp.");
                return ResponseEntity.badRequest().body(result.toString());
            }

            // Calculate aggregated percentage rates
            allTotal.calculateRate();
            bvhTotal.calculateRate();
            bvsTotal.calculateRate();
            for (UnitStats ds : deptStatsMap.values()) {
                ds.calculateRate();
            }

            // 4. Persistence into Database
            // Clean previous records for this period
            jdbcTemplate.update("DELETE FROM dbo.kpi_t101_admission_data WHERE period_code = ?", normalizedPeriod);
            jdbcTemplate.update("DELETE FROM dbo.kpi_t101_major_enrollment WHERE period_code = ?", normalizedPeriod);

            // Batch insert major rows
            String insertMajorSql =
                "INSERT INTO dbo.kpi_t101_major_enrollment " +
                "(period_code, admission_year, campus_code, major_code, major_name, program_type, department_code, department_name, target_quota, enrolled_count, enrollment_rate, notes, batch_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.batchUpdate(insertMajorSql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    MajorEnrollmentRow m = majorRows.get(i);
                    ps.setString(1, m.periodCode);
                    ps.setInt(2, m.admissionYear);
                    ps.setString(3, m.campusCode);
                    ps.setString(4, m.majorCode);
                    ps.setString(5, m.majorName);
                    ps.setString(6, m.programType);
                    ps.setString(7, m.departmentCode);
                    ps.setString(8, m.departmentName);
                    ps.setInt(9, m.targetQuota);
                    ps.setInt(10, m.enrolledCount);
                    ps.setBigDecimal(11, java.math.BigDecimal.valueOf(m.enrollmentRate));
                    ps.setString(12, m.notes);
                    ps.setString(13, m.batchId);
                }

                @Override
                public int getBatchSize() {
                    return majorRows.size();
                }
            });

            // Prepare summary rows (ALL, BVH, BVS, and each Department)
            List<UnitStats> summaryRows = new ArrayList<>();
            summaryRows.add(allTotal);
            summaryRows.add(bvhTotal);
            summaryRows.add(bvsTotal);
            summaryRows.addAll(deptStatsMap.values());

            String insertSummarySql =
                "INSERT INTO dbo.kpi_t101_admission_data " +
                "(period_code, admission_year, campus, unit_type, unit_code, unit_name, target_quota, enrolled_count, enrollment_rate, notes, uploaded_by, batch_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.batchUpdate(insertSummarySql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    UnitStats u = summaryRows.get(i);
                    ps.setString(1, normalizedPeriod);
                    ps.setInt(2, year);
                    ps.setString(3, u.campus);
                    ps.setString(4, u.unitType);
                    ps.setString(5, u.unitCode);
                    ps.setString(6, u.unitName);
                    ps.setInt(7, u.targetQuota);
                    ps.setInt(8, u.enrolledCount);
                    ps.setBigDecimal(9, java.math.BigDecimal.valueOf(u.enrollmentRate));
                    ps.setString(10, u.notes);
                    if (uploadedBy != null) ps.setInt(11, uploadedBy);
                    else ps.setNull(11, Types.INTEGER);
                    ps.setString(12, batchId);
                }

                @Override
                public int getBatchSize() {
                    return summaryRows.size();
                }
            });

            // 5. Update KPI T1.01 in kpi_data_points (Institutional actual_value)
            String calcNote = String.format(
                "[Tự động từ tệp kết quả nhập học \"%s\"]: Toàn học viện nhập học %,d / %,d chỉ tiêu (Cơ sở Hà Nội: %,d/%,d = %.2f%%, Cơ sở TP.HCM: %,d/%,d = %.2f%%) → Tỷ lệ T1.01: %.2f%%.",
                file.getOriginalFilename(),
                allTotal.enrolledCount, allTotal.targetQuota,
                bvhTotal.enrolledCount, bvhTotal.targetQuota, bvhTotal.enrollmentRate,
                bvsTotal.enrolledCount, bvsTotal.targetQuota, bvsTotal.enrollmentRate,
                allTotal.enrollmentRate
            );

            syncToKpiDataPoints(year, normalizedPeriod, allTotal.enrollmentRate, calcNote);

            // Build JSON Response
            result.put("status", "SUCCESS");
            result.put("message", "Tải lên và tính toán chỉ số KPI T1.01 thành công.");
            result.put("period_code", normalizedPeriod);
            result.put("admission_year", year);
            result.put("batch_id", batchId);
            result.put("kpi_code", "T1.01");
            result.put("institutional_rate", allTotal.enrollmentRate);
            result.put("total_enrolled", allTotal.enrolledCount);
            result.put("total_quota", allTotal.targetQuota);
            result.put("calc_note", calcNote);

            // Campus breakdown
            JSONObject campusJson = new JSONObject();
            campusJson.put("bvh", unitStatsToJson(bvhTotal));
            campusJson.put("bvs", unitStatsToJson(bvsTotal));
            campusJson.put("all", unitStatsToJson(allTotal));
            result.put("campus_summary", campusJson);

            // Department breakdown
            JSONArray deptArr = new JSONArray();
            for (UnitStats ds : deptStatsMap.values()) {
                deptArr.put(unitStatsToJson(ds));
            }
            result.put("departments", deptArr);

            // Majors preview
            JSONArray majorsArr = new JSONArray();
            for (MajorEnrollmentRow m : majorRows) {
                JSONObject mo = new JSONObject();
                mo.put("campus", m.campusCode);
                mo.put("majorCode", m.majorCode);
                mo.put("majorName", m.majorName);
                mo.put("programType", m.programType);
                mo.put("department", m.departmentName);
                mo.put("targetQuota", m.targetQuota);
                mo.put("enrolledCount", m.enrolledCount);
                mo.put("enrollmentRate", m.enrollmentRate);
                mo.put("notes", m.notes);
                majorsArr.put(mo);
            }
            result.put("majors", majorsArr);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error processing T1.01 upload", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi xử lý tệp: " + e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 3. GET /api/kpi/t101/data - Retrieve parsed admissions KPI data
    // -------------------------------------------------------------------------
    @GetMapping("/data")
    public ResponseEntity<String> getAdmissionData(
            @RequestParam(value = "year", defaultValue = "2026") int year,
            @RequestParam(value = "period", required = false) String period) {

        JSONObject result = new JSONObject();
        String normalizedPeriod = normalizePeriod(period, year);

        try {
            // Summary rows
            String summarySql =
                "SELECT period_code, admission_year, campus, unit_type, unit_code, unit_name, " +
                "       target_quota, enrolled_count, enrollment_rate, notes, uploaded_at, batch_id " +
                "FROM dbo.kpi_t101_admission_data " +
                "WHERE period_code = ? OR admission_year = ? " +
                "ORDER BY CASE unit_type WHEN 'INSTITUTION' THEN 1 WHEN 'CAMPUS' THEN 2 ELSE 3 END, campus, unit_name";

            List<Map<String, Object>> summaryList = jdbcTemplate.queryForList(summarySql, normalizedPeriod, year);

            // Major rows
            String majorSql =
                "SELECT id, period_code, admission_year, campus_code, major_code, major_name, " +
                "       program_type, department_code, department_name, target_quota, enrolled_count, " +
                "       enrollment_rate, notes, batch_id " +
                "FROM dbo.kpi_t101_major_enrollment " +
                "WHERE period_code = ? OR admission_year = ? " +
                "ORDER BY campus_code, program_type, major_code";

            List<Map<String, Object>> majorList = jdbcTemplate.queryForList(majorSql, normalizedPeriod, year);

            JSONObject campusJson = new JSONObject();
            JSONArray deptArr = new JSONArray();
            double instRate = 0.0;
            int totalEnrolled = 0;
            int totalQuota = 0;

            for (Map<String, Object> r : summaryList) {
                String uType = (String) r.get("unit_type");
                String uCode = (String) r.get("unit_code");
                JSONObject uObj = mapToUnitJson(r);

                if ("INSTITUTION".equalsIgnoreCase(uType)) {
                    campusJson.put("all", uObj);
                    Number rateNum = (Number) r.get("enrollment_rate");
                    if (rateNum != null) instRate = rateNum.doubleValue();
                    Number eNum = (Number) r.get("enrolled_count");
                    if (eNum != null) totalEnrolled = eNum.intValue();
                    Number qNum = (Number) r.get("target_quota");
                    if (qNum != null) totalQuota = qNum.intValue();
                } else if ("CAMPUS".equalsIgnoreCase(uType)) {
                    if ("BVH".equalsIgnoreCase(uCode)) campusJson.put("bvh", uObj);
                    else if ("BVS".equalsIgnoreCase(uCode)) campusJson.put("bvs", uObj);
                } else if ("DEPARTMENT".equalsIgnoreCase(uType)) {
                    deptArr.put(uObj);
                }
            }

            JSONArray majorsArr = new JSONArray();
            for (Map<String, Object> m : majorList) {
                JSONObject mo = new JSONObject();
                mo.put("id", m.get("id"));
                mo.put("campus", m.get("campus_code"));
                mo.put("majorCode", m.get("major_code"));
                mo.put("majorName", m.get("major_name"));
                mo.put("programType", m.get("program_type"));
                mo.put("department", m.get("department_name"));
                mo.put("targetQuota", m.get("target_quota"));
                mo.put("enrolledCount", m.get("enrolled_count"));
                mo.put("enrollmentRate", m.get("enrollment_rate"));
                mo.put("notes", m.get("notes"));
                majorsArr.put(mo);
            }

            result.put("status", "SUCCESS");
            result.put("period_code", normalizedPeriod);
            result.put("admission_year", year);
            result.put("kpi_code", "T1.01");
            result.put("institutional_rate", instRate);
            result.put("total_enrolled", totalEnrolled);
            result.put("total_quota", totalQuota);
            result.put("campus_summary", campusJson);
            result.put("departments", deptArr);
            result.put("majors", majorsArr);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error retrieving T1.01 admission data", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Helper: Sync to kpi_data_points
    // -------------------------------------------------------------------------
    private void syncToKpiDataPoints(int year, String periodCode, double actualValue, String notes) {
        try {
            // Find kpi_id for T1.01
            Integer kpiId = null;
            try {
                kpiId = jdbcTemplate.queryForObject(
                    "SELECT TOP 1 kpi_id FROM dbo.kpi_definitions WHERE kpi_code = 'T1.01' AND (is_deleted = 0 OR is_deleted IS NULL)",
                    Integer.class
                );
            } catch (Exception ignore) {}

            if (kpiId == null) {
                kpiId = 1; // Fallback standard default kpi_id for T1.01
            }

            // Determine period_id
            Integer periodId = null;
            try {
                periodId = jdbcTemplate.queryForObject(
                    "SELECT TOP 1 period_id FROM dbo.timeline WHERE period = ? OR period LIKE ?",
                    Integer.class, periodCode, "%" + year + "%"
                );
            } catch (Exception ignore) {}

            if (periodId == null) {
                try {
                    periodId = jdbcTemplate.queryForObject("SELECT TOP 1 period_id FROM dbo.timeline ORDER BY period_id DESC", Integer.class);
                } catch (Exception ignore) {}
            }
            if (periodId == null) periodId = 1;

            // Check if record exists in kpi_data_points
            String checkSql = "SELECT TOP 1 data_id FROM dbo.kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
            List<Integer> existingIds = jdbcTemplate.query(checkSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

            if (!existingIds.isEmpty()) {
                int dataId = existingIds.get(0);
                String updateSql = "UPDATE dbo.kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ?, entry_source = 'AUTOMATED_SYNC', updated_by_system = 'KpiT101Controller' WHERE data_id = ?";
                jdbcTemplate.update(updateSql, actualValue, notes, dataId);
                log.info("Updated kpi_data_points (data_id: {}) for KPI T1.01: {}%", dataId, actualValue);
            } else {
                String insertSql = "INSERT INTO dbo.kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes, entry_source, updated_by_system) VALUES (?, ?, ?, GETDATE(), 5, ?, 'AUTOMATED_SYNC', 'KpiT101Controller')";
                jdbcTemplate.update(insertSql, kpiId, periodId, actualValue, notes);
                log.info("Inserted new kpi_data_points for KPI T1.01: {}%", actualValue);
            }

            // Invalidate cache if service is present
            if (kpiExtendService != null) {
                try {
                    kpiExtendService.invalidateKpisWithAssignmentsCache();
                } catch (Exception ignore) {}
            }

        } catch (Exception e) {
            log.warn("Could not sync KPI T1.01 to kpi_data_points: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helper Classes & Parsing Methods
    // -------------------------------------------------------------------------
    private static class MajorEnrollmentRow {
        String periodCode;
        int admissionYear;
        String campusCode;
        String majorCode;
        String majorName;
        String programType;
        String departmentCode;
        String departmentName;
        int targetQuota = 0;
        int enrolledCount = 0;
        double enrollmentRate = 0.0;
        String notes;
        String batchId;
    }

    private static class UnitStats {
        String unitCode;
        String unitType;
        String unitName;
        String campus = "ALL";
        int targetQuota = 0;
        int enrolledCount = 0;
        double enrollmentRate = 0.0;
        String notes;

        UnitStats(String unitCode, String unitType, String unitName) {
            this.unitCode = unitCode;
            this.unitType = unitType;
            this.unitName = unitName;
        }

        void calculateRate() {
            this.enrollmentRate = this.targetQuota > 0
                ? Math.round((this.enrolledCount * 100.0 / this.targetQuota) * 100.0) / 100.0
                : 0.0;
        }
    }

    private String getCellString(Row row, int col) {
        if (row == null || col < 0) return "";
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue().trim();
                case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
                case FORMULA: return cell.getCellFormula();
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private int getCellInt(Row row, int col, FormulaEvaluator evaluator) {
        if (row == null || col < 0) return 0;
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

    private String buildDeptCode(String deptName) {
        if (deptName == null || deptName.isEmpty()) return "KHOA_CHUNG";
        String n = deptName.toLowerCase();
        if (n.contains("quốc tế") || n.contains("cie")) return "CIE";
        if (n.contains("an toàn thông tin")) return "KHOA_ATTT";
        if (n.contains("đa phương tiện")) return "KHOA_DPT";
        if (n.contains("marketing")) return "KHOA_MKT";
        if (n.contains("công nghệ thông tin 1") || (n.contains("cntt") && n.contains("1"))) return "KHOA_CNTT_1";
        if (n.contains("công nghệ thông tin 2") || (n.contains("cntt") && n.contains("2"))) return "KHOA_CNTT_2";
        if (n.contains("viễn thông 1")) return "KHOA_VT_1";
        if (n.contains("viễn thông 2")) return "KHOA_VT_2";
        if (n.contains("điện tử 1")) return "KHOA_DT_1";
        if (n.contains("điện tử 2")) return "KHOA_DT_2";
        if (n.contains("quản trị kinh doanh 1") || (n.contains("qtkd") && n.contains("1"))) return "KHOA_QTKD_1";
        if (n.contains("quản trị kinh doanh 2") || (n.contains("qtkd") && n.contains("2"))) return "KHOA_QTKD_2";
        if (n.contains("tài chính kế toán 1") || (n.contains("kế toán") && n.contains("1"))) return "KHOA_TCKT_1";
        if (n.contains("tài chính kế toán 2") || (n.contains("kế toán") && n.contains("2"))) return "KHOA_TCKT_2";
        return "KHOA_CHUNG";
    }

    private String resolveDepartment(String campus, String majorCode, String majorName, String progType) {
        String c = majorCode != null ? majorCode.toUpperCase() : "";
        String n = majorName != null ? majorName.toLowerCase() : "";

        if (c.contains("LK") || n.contains("liên kết")) {
            return "Trung tâm Đào tạo Quốc tế (CIE)";
        }
        if (c.contains("7480202") || c.contains("8480202") || n.contains("an toàn thông tin")) {
            return "BVH".equals(campus) ? "Khoa An toàn Thông tin" : "Khoa Công nghệ Thông tin 2";
        }
        if (c.contains("7480201") || c.contains("7480101") || c.contains("7480102") || c.contains("7480107") ||
            c.contains("8480101") || c.contains("8480104") || n.contains("công nghệ thông tin") ||
            n.contains("trí tuệ nhân tạo") || n.contains("khoa học dữ liệu") || n.contains("kỹ thuật dữ liệu")) {
            return "BVH".equals(campus) ? "Khoa Công nghệ Thông tin 1" : "Khoa Công nghệ Thông tin 2";
        }
        if (c.contains("7520207") || c.contains("7520208") || c.contains("8520208") ||
            n.contains("viễn thông") || n.contains("iot") || n.contains("hàng không")) {
            return "BVH".equals(campus) ? "Khoa Viễn thông 1" : "Khoa Viễn thông 2";
        }
        if (c.contains("7510301") || c.contains("7520216") || c.contains("8520203") ||
            n.contains("điện tử") || n.contains("vi mạch") || n.contains("tự động hóa") || n.contains("robot")) {
            return "BVH".equals(campus) ? "Khoa Kỹ thuật Điện tử 1" : "Khoa Kỹ thuật Điện tử 2";
        }
        if (c.contains("7320101") || c.contains("7320104") || c.contains("7329001") ||
            n.contains("báo chí") || n.contains("truyền thông") || n.contains("game") || n.contains("văn hóa")) {
            return "Khoa Đa phương tiện";
        }
        if (c.contains("7340115") || n.contains("marketing") || n.contains("quan hệ công chúng")) {
            return "BVH".equals(campus) ? "Khoa Marketing" : "Khoa Quản trị Kinh doanh 2";
        }
        if (c.contains("7340101") || c.contains("7340122") || n.contains("quản trị kinh doanh") ||
            n.contains("thương mại điện tử") || n.contains("logistics")) {
            return "BVH".equals(campus) ? "Khoa Quản trị Kinh doanh 1" : "Khoa Quản trị Kinh doanh 2";
        }
        if (c.contains("7340301") || c.contains("7340201") || c.contains("7340205") ||
            n.contains("kế toán") || n.contains("tài chính") || n.contains("fintech")) {
            return "BVH".equals(campus) ? "Khoa Tài chính Kế toán 1" : "Khoa Tài chính Kế toán 2";
        }
        return "BVH".equals(campus) ? "Khoa Quản trị Kinh doanh 1" : "Khoa Quản trị Kinh doanh 2";
    }

    private JSONObject unitStatsToJson(UnitStats u) {
        JSONObject obj = new JSONObject();
        obj.put("unitCode", u.unitCode);
        obj.put("unitType", u.unitType);
        obj.put("unitName", u.unitName);
        obj.put("campus", u.campus);
        obj.put("targetQuota", u.targetQuota);
        obj.put("enrolledCount", u.enrolledCount);
        obj.put("enrollmentRate", u.enrollmentRate);
        return obj;
    }

    private JSONObject mapToUnitJson(Map<String, Object> r) {
        JSONObject obj = new JSONObject();
        obj.put("unitCode", r.get("unit_code"));
        obj.put("unitType", r.get("unit_type"));
        obj.put("unitName", r.get("unit_name"));
        obj.put("campus", r.get("campus"));
        obj.put("targetQuota", r.get("target_quota"));
        obj.put("enrolledCount", r.get("enrolled_count"));
        obj.put("enrollmentRate", r.get("enrollment_rate"));
        obj.put("notes", r.get("notes"));
        return obj;
    }
}
