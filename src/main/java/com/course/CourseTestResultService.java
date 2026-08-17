package com.course;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class CourseTestResultService {

    private static final Logger log = LoggerFactory.getLogger(CourseTestResultService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

	/*@PostConstruct
	public void initDatabaseTable() {
	    try {
	        log.info("Initializing course_test_results database table...");
	        jdbcTemplate.execute(
	            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'course_test_results') " +
	            "BEGIN " +
	            "CREATE TABLE course_test_results (" +
	            "    id INT IDENTITY(1,1) PRIMARY KEY," +
	            "    semester NVARCHAR(100) NOT NULL," +
	            "    tt INT NULL," +
	            "    course_code VARCHAR(50) NOT NULL," +
	            "    course_name NVARCHAR(255) NOT NULL," +
	            "    credits INT NULL," +
	            "    total_students INT NULL," +
	            "    graded_students INT NULL," +
	            "    count_0_1 INT DEFAULT 0," +
	            "    count_1_2 INT DEFAULT 0," +
	            "    count_2_3 INT DEFAULT 0," +
	            "    count_3_4 INT DEFAULT 0," +
	            "    count_4_5 INT DEFAULT 0," +
	            "    count_5_6 INT DEFAULT 0," +
	            "    count_6_7 INT DEFAULT 0," +
	            "    count_7_8 INT DEFAULT 0," +
	            "    count_8_9 INT DEFAULT 0," +
	            "    count_9_10 INT DEFAULT 0," +
	            "    count_c INT DEFAULT 0," +
	            "    count_v INT DEFAULT 0," +
	            "    count_h INT DEFAULT 0," +
	            "    count_dc INT DEFAULT 0," +
	            "    count_m INT DEFAULT 0," +
	            "    avg_score FLOAT NULL," +
	            "    total_hp_students INT NULL," +
	            "    fail_count INT NULL," +
	            "    other_count INT NULL," +
	            "    fail_rate FLOAT NULL," +
	            "    created_at DATETIME2 DEFAULT GETDATE()" +
	            "); " +
	            "CREATE INDEX IX_course_test_code ON course_test_results(course_code);" +
	            "CREATE INDEX IX_course_test_sem ON course_test_results(semester);" +
	            "END"
	        );
	        log.info("course_test_results table checked/created.");
	
	        // Clear table if previous records have corrupted '?' Unicode characters
	        try {
	            Integer corruptCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_test_results WHERE course_name LIKE '%?%'", Integer.class);
	            if (corruptCount != null && corruptCount > 0) {
	                log.info("Found {} corrupted records with '?' in course_test_results. Clearing for fresh Unicode seed...", corruptCount);
	                jdbcTemplate.execute("TRUNCATE TABLE course_test_results");
	            }
	        } catch (Exception ignored) {}
	
	        seedInitialDataIfEmpty();
	    } catch (Exception e) {
	        log.error("Error initializing course_test_results table: {}", e.getMessage(), e);
	    }
	}*/

    private void seedInitialDataIfEmpty() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_test_results", Integer.class);
            if (count == null || count == 0) {
                log.info("course_test_results is empty. Looking for seed CSV file...");
                Path path = Paths.get("TK SV theo phổ điểm thi Khóa 2022-2025.csv");
                if (!Files.exists(path)) {
                    path = Paths.get("E:/eclipse-workspace/kdAPIs/TK SV theo ph? di?m thi Kha 2022-2025.csv");
                }
                if (!Files.exists(path)) {
                    // Search in working dir for any matching csv
                    try (var stream = Files.list(Paths.get("."))) {
                        Optional<Path> found = stream.filter(p -> p.toString().toLowerCase().contains("phổ điểm") || p.toString().toLowerCase().contains("di?m")).findFirst();
                        if (found.isPresent()) path = found.get();
                    }
                }
                if (Files.exists(path)) {
                    log.info("Seeding data from file: {}", path.toAbsolutePath());
                    try (InputStream is = Files.newInputStream(path)) {
                        saveUploadedCsv(is, "HK2 2025-2026");
                    }
                } else {
                    log.warn("Seed CSV file not found on disk. Skipping seed.");
                }
            }
        } catch (Exception e) {
            log.warn("Could not seed initial course test results: {}", e.getMessage());
        }
    }

    public synchronized int saveUploadedFile(InputStream inputStream, String filename, String overrideSemester) throws Exception {
        if (filename != null && (filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls"))) {
            return saveUploadedExcel(inputStream, overrideSemester);
        } else {
            return saveUploadedCsv(inputStream, overrideSemester);
        }
    }

    public synchronized int saveUploadedExcel(InputStream inputStream, String overrideSemester) throws Exception {
        List<CourseTestResult> list = new ArrayList<>();
        String extractedSemester = null;

        try (org.apache.poi.ss.usermodel.Workbook workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(inputStream)) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            int rowIndex = 0;

            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                rowIndex++;
                List<String> columns = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                    columns.add(getCellValueAsString(cell));
                }

                if (columns.isEmpty()) continue;

                if (rowIndex <= 3) {
                    String fullLine = String.join(" ", columns);
                    if (fullLine.toLowerCase().contains("học kỳ")) {
                        extractedSemester = extractSemesterFromHeader(fullLine);
                    }
                }

                String firstCol = columns.get(0).trim();
                if (firstCol.startsWith("\uFEFF")) {
                    firstCol = firstCol.substring(1).trim();
                }
                if (!isInteger(firstCol)) continue;

                CourseTestResult item = new CourseTestResult();
                item.setTt(parseInt(firstCol));
                item.setCourseCode(columns.size() > 1 ? columns.get(1).trim() : "");
                item.setCourseName(columns.size() > 2 ? columns.get(2).trim() : "");
                item.setCredits(columns.size() > 3 ? parseInt(columns.get(3)) : 0);
                item.setTotalStudents(columns.size() > 4 ? parseInt(columns.get(4)) : 0);
                item.setGradedStudents(columns.size() > 5 ? parseInt(columns.get(5)) : 0);

                item.setCount01(columns.size() > 6 ? parseInt(columns.get(6)) : 0);
                item.setCount12(columns.size() > 7 ? parseInt(columns.get(7)) : 0);
                item.setCount23(columns.size() > 8 ? parseInt(columns.get(8)) : 0);
                item.setCount34(columns.size() > 9 ? parseInt(columns.get(9)) : 0);
                item.setCount45(columns.size() > 10 ? parseInt(columns.get(10)) : 0);
                item.setCount56(columns.size() > 11 ? parseInt(columns.get(11)) : 0);
                item.setCount67(columns.size() > 12 ? parseInt(columns.get(12)) : 0);
                item.setCount78(columns.size() > 13 ? parseInt(columns.get(13)) : 0);
                item.setCount89(columns.size() > 14 ? parseInt(columns.get(14)) : 0);
                item.setCount910(columns.size() > 15 ? parseInt(columns.get(15)) : 0);

                item.setCountC(columns.size() > 16 ? parseInt(columns.get(16)) : 0);
                item.setCountV(columns.size() > 17 ? parseInt(columns.get(17)) : 0);
                item.setCountH(columns.size() > 18 ? parseInt(columns.get(18)) : 0);
                item.setCountDc(columns.size() > 19 ? parseInt(columns.get(19)) : 0);
                item.setCountM(columns.size() > 20 ? parseInt(columns.get(20)) : 0);

                item.setAvgScore(columns.size() > 21 ? parseDouble(columns.get(21)) : 0.0);
                item.setTotalHpStudents(columns.size() > 22 ? parseInt(columns.get(22)) : 0);
                item.setFailCount(columns.size() > 23 ? parseInt(columns.get(23)) : 0);
                item.setOtherCount(columns.size() > 24 ? parseInt(columns.get(24)) : 0);
                item.setFailRate(columns.size() > 25 ? parseDouble(columns.get(25)) : 0.0);

                list.add(item);
            }
        }

        String finalSemester = (overrideSemester != null && !overrideSemester.trim().isEmpty())
                ? overrideSemester.trim()
                : (extractedSemester != null ? extractedSemester : "HK2 2025-2026");

        if (list.isEmpty()) {
            return 0;
        }

        jdbcTemplate.update("DELETE FROM course_test_results WHERE semester = ?", finalSemester);
        return saveCourseTestResultList(list, finalSemester);
    }

    private String getCellValueAsString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num)) {
                        return String.valueOf((long) num);
                    }
                    return String.valueOf(num);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        return cell.getStringCellValue();
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    public synchronized int saveUploadedCsv(InputStream inputStream, String overrideSemester) throws Exception {
        List<CourseTestResult> list = new ArrayList<>();
        String extractedSemester = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;

                // Strip BOM header if present
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                // Check line 2 for semester header info if extractedSemester not set
                if (lineNumber == 2 && line.toLowerCase().contains("học kỳ")) {
                    extractedSemester = extractSemesterFromHeader(line);
                }

                // Split CSV line respecting quotes
                List<String> columns = parseCsvLine(line);
                if (columns.size() < 5) continue;

                String firstCol = columns.get(0).trim();
                if (firstCol.startsWith("\uFEFF")) {
                    firstCol = firstCol.substring(1).trim();
                }
                // Data rows start with an integer in column 0 (TT)
                if (!isInteger(firstCol)) continue;

                CourseTestResult item = new CourseTestResult();
                item.setTt(parseInt(firstCol));
                item.setCourseCode(columns.size() > 1 ? columns.get(1).trim() : "");
                item.setCourseName(columns.size() > 2 ? columns.get(2).trim() : "");
                item.setCredits(columns.size() > 3 ? parseInt(columns.get(3)) : 0);
                item.setTotalStudents(columns.size() > 4 ? parseInt(columns.get(4)) : 0);
                item.setGradedStudents(columns.size() > 5 ? parseInt(columns.get(5)) : 0);

                item.setCount01(columns.size() > 6 ? parseInt(columns.get(6)) : 0);
                item.setCount12(columns.size() > 7 ? parseInt(columns.get(7)) : 0);
                item.setCount23(columns.size() > 8 ? parseInt(columns.get(8)) : 0);
                item.setCount34(columns.size() > 9 ? parseInt(columns.get(9)) : 0);
                item.setCount45(columns.size() > 10 ? parseInt(columns.get(10)) : 0);
                item.setCount56(columns.size() > 11 ? parseInt(columns.get(11)) : 0);
                item.setCount67(columns.size() > 12 ? parseInt(columns.get(12)) : 0);
                item.setCount78(columns.size() > 13 ? parseInt(columns.get(13)) : 0);
                item.setCount89(columns.size() > 14 ? parseInt(columns.get(14)) : 0);
                item.setCount910(columns.size() > 15 ? parseInt(columns.get(15)) : 0);

                item.setCountC(columns.size() > 16 ? parseInt(columns.get(16)) : 0);
                item.setCountV(columns.size() > 17 ? parseInt(columns.get(17)) : 0);
                item.setCountH(columns.size() > 18 ? parseInt(columns.get(18)) : 0);
                item.setCountDc(columns.size() > 19 ? parseInt(columns.get(19)) : 0);
                item.setCountM(columns.size() > 20 ? parseInt(columns.get(20)) : 0);

                item.setAvgScore(columns.size() > 21 ? parseDouble(columns.get(21)) : 0.0);
                item.setTotalHpStudents(columns.size() > 22 ? parseInt(columns.get(22)) : 0);
                item.setFailCount(columns.size() > 23 ? parseInt(columns.get(23)) : 0);
                item.setOtherCount(columns.size() > 24 ? parseInt(columns.get(24)) : 0);
                item.setFailRate(columns.size() > 25 ? parseDouble(columns.get(25)) : 0.0);

                list.add(item);
            }
        }

        String finalSemester = (overrideSemester != null && !overrideSemester.trim().isEmpty())
                ? overrideSemester.trim()
                : (extractedSemester != null ? extractedSemester : "HK2 2025-2026");

        if (list.isEmpty()) {
            return 0;
        }

        // Delete existing records for this target semester to prevent duplication
        jdbcTemplate.update("DELETE FROM course_test_results WHERE semester = ?", finalSemester);
        return saveCourseTestResultList(list, finalSemester);
    }

    private int saveCourseTestResultList(List<CourseTestResult> list, String finalSemester) {
        String insertSql = "INSERT INTO course_test_results (" +
                "semester, tt, course_code, course_name, credits, total_students, graded_students, " +
                "count_0_1, count_1_2, count_2_3, count_3_4, count_4_5, count_5_6, count_6_7, count_7_8, count_8_9, count_9_10, " +
                "count_c, count_v, count_h, count_dc, count_m, avg_score, total_hp_students, fail_count, other_count, fail_rate" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int insertedCount = 0;
        for (CourseTestResult item : list) {
            jdbcTemplate.update(insertSql, ps -> {
                ps.setNString(1, finalSemester);
                ps.setObject(2, item.getTt(), java.sql.Types.INTEGER);
                ps.setString(3, item.getCourseCode());
                ps.setNString(4, item.getCourseName()); // Explicit setNString for Unicode NVARCHAR!
                ps.setObject(5, item.getCredits(), java.sql.Types.INTEGER);
                ps.setObject(6, item.getTotalStudents(), java.sql.Types.INTEGER);
                ps.setObject(7, item.getGradedStudents(), java.sql.Types.INTEGER);
                ps.setObject(8, item.getCount01(), java.sql.Types.INTEGER);
                ps.setObject(9, item.getCount12(), java.sql.Types.INTEGER);
                ps.setObject(10, item.getCount23(), java.sql.Types.INTEGER);
                ps.setObject(11, item.getCount34(), java.sql.Types.INTEGER);
                ps.setObject(12, item.getCount45(), java.sql.Types.INTEGER);
                ps.setObject(13, item.getCount56(), java.sql.Types.INTEGER);
                ps.setObject(14, item.getCount67(), java.sql.Types.INTEGER);
                ps.setObject(15, item.getCount78(), java.sql.Types.INTEGER);
                ps.setObject(16, item.getCount89(), java.sql.Types.INTEGER);
                ps.setObject(17, item.getCount910(), java.sql.Types.INTEGER);
                ps.setObject(18, item.getCountC(), java.sql.Types.INTEGER);
                ps.setObject(19, item.getCountV(), java.sql.Types.INTEGER);
                ps.setObject(20, item.getCountH(), java.sql.Types.INTEGER);
                ps.setObject(21, item.getCountDc(), java.sql.Types.INTEGER);
                ps.setObject(22, item.getCountM(), java.sql.Types.INTEGER);
                ps.setObject(23, item.getAvgScore(), java.sql.Types.DOUBLE);
                ps.setObject(24, item.getTotalHpStudents(), java.sql.Types.INTEGER);
                ps.setObject(25, item.getFailCount(), java.sql.Types.INTEGER);
                ps.setObject(26, item.getOtherCount(), java.sql.Types.INTEGER);
                ps.setObject(27, item.getFailRate(), java.sql.Types.DOUBLE);
            });
            insertedCount++;
        }

        invalidateSemestersCache();
        log.info("Inserted {} records into course_test_results for semester '{}'", insertedCount, finalSemester);
        return insertedCount;
    }

    private static final Map<String, List<Map<String, Object>>> cachedRiskStatsBySemester = new java.util.concurrent.ConcurrentHashMap<>();

    public synchronized void invalidateRiskStatsCache() {
        cachedRiskStatsBySemester.clear();
        cachedSemesters = null;
    }

    public List<Map<String, Object>> getCourseRiskStats(String semester) {
        String key = (semester != null && !semester.trim().isEmpty()) ? semester.trim() : "ALL";
        List<Map<String, Object>> cached = cachedRiskStatsBySemester.get(key);
        if (cached != null) {
            return cached;
        }

        String sql = "SELECT * FROM course_test_results WITH (NOLOCK)";
        List<Object> params = new ArrayList<>();

        if (!"ALL".equalsIgnoreCase(key)) {
            sql += " WHERE semester = ?";
            params.add(key);
        }

        sql += " ORDER BY fail_rate DESC, total_students DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> map = new HashMap<>();
            String code = (String) row.get("course_code");
            Double failRate = row.get("fail_rate") != null ? ((Number) row.get("fail_rate")).doubleValue() : 0.0;
            Double avgScore = row.get("avg_score") != null ? ((Number) row.get("avg_score")).doubleValue() : 0.0;
            Integer enrollments = row.get("total_students") != null ? ((Number) row.get("total_students")).intValue() : 0;

            String department = deriveDepartment(code);
            String severity = failRate >= 25.0 ? "high" : (failRate >= 15.0 ? "medium" : "low");
            String trend = failRate >= 25.0 ? "down" : (failRate >= 15.0 ? "stable" : "up");

            // Calculate estimated satisfaction rating out of 100%
            int satisfaction = Math.max(40, Math.min(95, (int) Math.round(100.0 - (failRate * 1.2) + (avgScore * 2.0))));

            map.put("id", row.get("id"));
            map.put("code", code);
            map.put("name", row.get("course_name"));
            map.put("department", department);
            map.put("semester", row.get("semester"));
            map.put("credits", row.get("credits") != null ? ((Number) row.get("credits")).intValue() : 0);
            map.put("enrollments", enrollments);
            map.put("gradedStudents", row.get("graded_students") != null ? ((Number) row.get("graded_students")).intValue() : 0);
            map.put("failRate", Math.round(failRate * 100.0) / 100.0);
            map.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
            map.put("satisfaction", satisfaction);
            map.put("severity", severity);
            map.put("trend", trend);

            // Grade distribution buckets
            map.put("count01", row.get("count_0_1") != null ? ((Number) row.get("count_0_1")).intValue() : 0);
            map.put("count12", row.get("count_1_2") != null ? ((Number) row.get("count_1_2")).intValue() : 0);
            map.put("count23", row.get("count_2_3") != null ? ((Number) row.get("count_2_3")).intValue() : 0);
            map.put("count34", row.get("count_3_4") != null ? ((Number) row.get("count_3_4")).intValue() : 0);
            map.put("count45", row.get("count_4_5") != null ? ((Number) row.get("count_4_5")).intValue() : 0);
            map.put("count56", row.get("count_5_6") != null ? ((Number) row.get("count_5_6")).intValue() : 0);
            map.put("count67", row.get("count_6_7") != null ? ((Number) row.get("count_6_7")).intValue() : 0);
            map.put("count78", row.get("count_7_8") != null ? ((Number) row.get("count_7_8")).intValue() : 0);
            map.put("count89", row.get("count_8_9") != null ? ((Number) row.get("count_8_9")).intValue() : 0);
            map.put("count910", row.get("count_9_10") != null ? ((Number) row.get("count_9_10")).intValue() : 0);

            // Exam status breakdown
            map.put("countC", row.get("count_c") != null ? ((Number) row.get("count_c")).intValue() : 0);
            map.put("countV", row.get("count_v") != null ? ((Number) row.get("count_v")).intValue() : 0);
            map.put("countH", row.get("count_h") != null ? ((Number) row.get("count_h")).intValue() : 0);
            map.put("countDc", row.get("count_dc") != null ? ((Number) row.get("count_dc")).intValue() : 0);
            map.put("countM", row.get("count_m") != null ? ((Number) row.get("count_m")).intValue() : 0);
            map.put("failCount", row.get("fail_count") != null ? ((Number) row.get("fail_count")).intValue() : 0);

            result.add(map);
        }

        cachedRiskStatsBySemester.put(key, result);
        return result;
    }

    @PostConstruct
    public void initIndexes() {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String sql = 
                    "IF EXISTS (SELECT * FROM sys.tables WHERE name = 'course_test_results') BEGIN " +
                    "  IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_course_test_sem' AND object_id = OBJECT_ID('course_test_results')) " +
                    "    CREATE INDEX IX_course_test_sem ON course_test_results(semester); " +
                    "  IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_course_test_sem_fail' AND object_id = OBJECT_ID('course_test_results')) " +
                    "    CREATE INDEX IX_course_test_sem_fail ON course_test_results(semester, fail_rate DESC, total_students DESC); " +
                    "  IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_course_test_code' AND object_id = OBJECT_ID('course_test_results')) " +
                    "    CREATE INDEX IX_course_test_code ON course_test_results(course_code); " +
                    "END";
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.debug("Notice course_test_results index init: {}", e.getMessage());
            }
        });
    }

    private static List<String> cachedSemesters = null;

    public synchronized void invalidateSemestersCache() {
        cachedRiskStatsBySemester.clear();
        cachedSemesters = null;
    }

    public List<String> getAvailableSemesters() {
        if (cachedSemesters != null) {
            return cachedSemesters;
        }
        try {
            String sql = "SELECT DISTINCT semester FROM course_test_results WITH (NOLOCK) ORDER BY semester DESC";
            cachedSemesters = jdbcTemplate.queryForList(sql, String.class);
            return cachedSemesters;
        } catch (Exception e) {
            log.error("Error getting available semesters: {}", e.getMessage());
            return cachedSemesters != null ? cachedSemesters : Collections.emptyList();
        }
    }

    private String deriveDepartment(String courseCode) {
        if (courseCode == null) return "Khoa CNTT";
        String upper = courseCode.toUpperCase();
        if (upper.startsWith("INT") || upper.startsWith("AI")) return "Khoa CNTT";
        if (upper.startsWith("BAS") || upper.startsWith("MAT")) return "Khoa Cơ bản";
        if (upper.startsWith("ELE")) return "Khoa Điện tử";
        if (upper.startsWith("TEL")) return "Khoa Viễn thông";
        if (upper.startsWith("BSA") || upper.startsWith("BUS")) return "Khoa QTKD";
        if (upper.startsWith("MAR")) return "Khoa Marketing";
        return "Khoa CNTT";
    }

    private String extractSemesterFromHeader(String line) {
        String s = line.replaceAll("(?i)Điểm thi học kỳ", "").replace(",", "").trim();
        if (s.isEmpty()) return "HK2 2025-2026";
        return s;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString());
        return result;
    }

    private boolean isInteger(String s) {
        if (s == null) return false;
        String clean = s.replaceAll("\"", "").trim();
        try {
            Integer.parseInt(clean);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int parseInt(String s) {
        if (s == null) return 0;
        String clean = s.replaceAll("[^0-9-]", "").trim();
        if (clean.isEmpty()) return 0;
        try {
            return Integer.parseInt(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDouble(String s) {
        if (s == null) return 0.0;
        String clean = s.replaceAll("[^0-9.-]", "").trim();
        if (clean.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
