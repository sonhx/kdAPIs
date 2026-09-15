package com.surveys.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyT107Service {

    private static final Logger log = LoggerFactory.getLogger(SurveyT107Service.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @PostConstruct
    public void initDatabaseTables() {
        try {
            String createSurveysTable = 
                "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'surveys') " +
                "BEGIN " +
                "    CREATE TABLE dbo.surveys ( " +
                "        id VARCHAR(100) PRIMARY KEY, " +
                "        parent_id VARCHAR(100) NULL, " +
                "        survey_type NVARCHAR(100) NULL, " +
                "        input_method VARCHAR(50) NULL, " +
                "        title NVARCHAR(500) NOT NULL, " +
                "        description NVARCHAR(MAX) NULL, " +
                "        is_active BIT DEFAULT 1, " +
                "        course_semester_code VARCHAR(100) NULL, " +
                "        max_responses INT DEFAULT 1, " +
                "        has_commitment BIT DEFAULT 0, " +
                "        created_by VARCHAR(100) NULL, " +
                "        created_at DATETIME DEFAULT GETDATE(), " +
                "        updated_at DATETIME DEFAULT GETDATE() " +
                "    ); " +
                "END;";
            jdbcTemplate.execute(createSurveysTable);

            String addInputMethodColumn = 
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.surveys') AND name = 'input_method') " +
                "BEGIN " +
                "    ALTER TABLE dbo.surveys ADD input_method VARCHAR(50) NULL; " +
                "END;";
            jdbcTemplate.execute(addInputMethodColumn);

            String createCampaignsTable = 
                "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'survey_campaigns') " +
                "BEGIN " +
                "    CREATE TABLE dbo.survey_campaigns ( " +
                "        id VARCHAR(100) PRIMARY KEY, " +
                "        survey_id VARCHAR(100) NOT NULL, " +
                "        title NVARCHAR(500) NOT NULL, " +
                "        semester_code VARCHAR(100) NULL, " +
                "        semester_name NVARCHAR(200) NULL, " +
                "        start_time DATETIME NULL, " +
                "        end_time DATETIME NULL, " +
                "        max_responses INT DEFAULT 1, " +
                "        is_active BIT DEFAULT 1, " +
                "        created_at DATETIME DEFAULT GETDATE(), " +
                "        updated_at DATETIME DEFAULT GETDATE() " +
                "    ); " +
                "END;";
            jdbcTemplate.execute(createCampaignsTable);

            String createResponsesTable = 
                "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'survey_responses') " +
                "BEGIN " +
                "    CREATE TABLE dbo.survey_responses ( " +
                "        id VARCHAR(100) PRIMARY KEY, " +
                "        survey_id VARCHAR(100) NOT NULL, " +
                "        campaign_id VARCHAR(100) NULL, " +
                "        user_code NVARCHAR(100) NULL, " +
                "        full_name NVARCHAR(250) NULL, " +
                "        role NVARCHAR(100) NULL, " +
                "        class_code NVARCHAR(150) NULL, " +
                "        is_answered BIT DEFAULT 1, " +
                "        started_at DATETIME NULL, " +
                "        created_at DATETIME DEFAULT GETDATE(), " +
                "        updated_at DATETIME DEFAULT GETDATE() " +
                "    ); " +
                "END;";
            jdbcTemplate.execute(createResponsesTable);

            String addCampaignIdColumn = 
                "IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.survey_responses') AND name = 'campaign_id') " +
                "BEGIN " +
                "    ALTER TABLE dbo.survey_responses ADD campaign_id VARCHAR(100) NULL; " +
                "END;";
            jdbcTemplate.execute(addCampaignIdColumn);

            String createAnswersTable = 
                "IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'survey_response_answers') " +
                "BEGIN " +
                "    CREATE TABLE dbo.survey_response_answers ( " +
                "        id VARCHAR(100) PRIMARY KEY, " +
                "        response_id VARCHAR(100) NOT NULL, " +
                "        question_id VARCHAR(100) NULL, " +
                "        choices NVARCHAR(MAX) NULL, " +
                "        other_answer NVARCHAR(MAX) NULL " +
                "    ); " +
                "END;";
            jdbcTemplate.execute(createAnswersTable);

            // Safely expand id column sizes in existing tables to VARCHAR(100) if created with smaller limits (e.g. VARCHAR(24))
            try { jdbcTemplate.execute("ALTER TABLE dbo.survey_campaigns ALTER COLUMN id VARCHAR(100) NOT NULL;"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE dbo.survey_campaigns ALTER COLUMN survey_id VARCHAR(100) NOT NULL;"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE dbo.surveys ALTER COLUMN id VARCHAR(100) NOT NULL;"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE dbo.survey_responses ALTER COLUMN id VARCHAR(100) NOT NULL;"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE dbo.survey_responses ALTER COLUMN survey_id VARCHAR(100) NOT NULL;"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE dbo.survey_responses ALTER COLUMN campaign_id VARCHAR(100) NULL;"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE dbo.survey_response_answers ALTER COLUMN id VARCHAR(100) NOT NULL;"); } catch (Exception ignored) {}
            try { jdbcTemplate.execute("ALTER TABLE dbo.survey_response_answers ALTER COLUMN response_id VARCHAR(100) NOT NULL;"); } catch (Exception ignored) {}
        } catch (Exception e) {
            log.warn("Notice checking core survey tables: {}", e.getMessage());
        }
    }

    public JSONObject processT107Excel(InputStream inputStream, String fileName, String periodId, String uploadedBy) {
        JSONObject result = new JSONObject();

        try {
            org.apache.poi.util.IOUtils.setByteArrayMaxOverride(1_000_000_000);
        } catch (Throwable t) {
            log.warn("Could not set IOUtils byteArrayMaxOverride: {}", t.getMessage());
        }

        java.io.File tempFile = null;
        double overallAvgVal = 0.0;
        int finalValidResponsesCount = 0;
        String finalSheetName = "data";
        List<Map<String, Object>> responseRows = new ArrayList<>();

        try {
            tempFile = java.io.File.createTempFile("survey_t107_", ".xlsx");
            java.nio.file.Files.copy(inputStream, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("Could not buffer stream to temp file: {}", e.getMessage());
        }

        boolean parsedBySAX = false;
        if (tempFile != null && tempFile.length() > 0) {
            SAXParseResult saxRes = parseT107ExcelSAX(tempFile, responseRows);
            if (saxRes.success) {
                overallAvgVal = saxRes.overallAvg;
                finalValidResponsesCount = saxRes.validResponsesCount;
                finalSheetName = saxRes.sheetName;
                parsedBySAX = true;
                log.info("Successfully parsed T1.07 Excel file using SAX streaming. Rows: {}, Avg: {}", finalValidResponsesCount, overallAvgVal);
            } else if (saxRes.errorMessage != null && saxRes.errorMessage.contains("Không tìm thấy trang tính")) {
                if (tempFile != null && tempFile.exists()) {
                    try { tempFile.delete(); } catch (Exception ignored) {}
                }
                result.put("code", 400);
                result.put("description", saxRes.errorMessage);
                return result;
            }
        }

        if (!parsedBySAX) {
            Workbook workbook = null;
            try {
                if (tempFile != null && tempFile.length() > 0) {
                    workbook = WorkbookFactory.create(tempFile);
                } else {
                    workbook = WorkbookFactory.create(inputStream);
                }

                Sheet sheet = null;
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    String name = workbook.getSheetName(i);
                    if (name != null && name.trim().equalsIgnoreCase("data")) {
                        sheet = workbook.getSheetAt(i);
                        finalSheetName = name;
                        break;
                    }
                }

                if (sheet == null) {
                    for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                        String name = workbook.getSheetName(i);
                        if (name != null) {
                            String clean = name.trim().toLowerCase();
                            if (clean.contains("data") || clean.contains("sinh") || clean.contains("sheet")) {
                                sheet = workbook.getSheetAt(i);
                                finalSheetName = name;
                                break;
                            }
                        }
                    }
                }

                if (sheet == null && workbook.getNumberOfSheets() > 0) {
                    sheet = workbook.getSheetAt(0);
                    finalSheetName = sheet.getSheetName();
                }

                if (sheet == null) {
                    result.put("code", 400);
                    result.put("description", "Không tìm thấy trang tính (worksheet) 'data' trong tệp Excel.");
                    return result;
                }

                double totalRatingSum = 0.0;
                int firstRowNum = sheet.getFirstRowNum();
                int lastRowNum = sheet.getLastRowNum();

                for (int r = firstRowNum; r <= lastRowNum; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    List<Double> rowRatings = new ArrayList<>();
                    String studentCode = null;
                    String studentName = null;
                    String programName = null;

                    int lastCellNum = row.getLastCellNum();
                    for (int c = 0; c < lastCellNum; c++) {
                        Cell cell = row.getCell(c);
                        if (cell == null) continue;

                        Double numVal = parseRatingValue(cell, null);
                        if (numVal != null && numVal >= 1.0 && numVal <= 5.0) {
                            rowRatings.add(numVal);
                        } else if (cell.getCellType() == CellType.STRING) {
                            String cellStr = cell.getStringCellValue().trim();
                            if (cellStr.length() > 0) {
                                if (studentCode == null && cellStr.matches("(?i)^[A-Z0-9]{5,15}$")) {
                                    studentCode = cellStr;
                                } else if (studentName == null && cellStr.matches("(?i)^[\\p{L}\\s]{4,50}$") && !cellStr.equalsIgnoreCase("Sinh viên") && !cellStr.equalsIgnoreCase("Mức độ hài lòng")) {
                                    studentName = cellStr;
                                }
                            }
                        }
                    }

                    if (!rowRatings.isEmpty()) {
                        double rowAvg = rowRatings.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                        if (rowAvg >= 1.0 && rowAvg <= 5.0) {
                            finalValidResponsesCount++;
                            totalRatingSum += rowAvg;

                            Map<String, Object> respData = new HashMap<>();
                            respData.put("rowIndex", r + 1);
                            respData.put("studentCode", studentCode != null ? studentCode : "");
                            respData.put("studentName", studentName != null ? studentName : "");
                            respData.put("programName", programName != null ? programName : "");
                            respData.put("ratingScore", BigDecimal.valueOf(rowAvg).setScale(2, RoundingMode.HALF_UP).doubleValue());
                            responseRows.add(respData);
                        }
                    }
                }

                if (finalValidResponsesCount == 0) {
                    result.put("code", 400);
                    result.put("description", "Không tìm thấy dữ liệu điểm khảo sát (thang điểm 1–5) hợp lệ trong trang tính '" + finalSheetName + "'.");
                    return result;
                }

                overallAvgVal = totalRatingSum / finalValidResponsesCount;

            } catch (Exception e) {
                log.error("Error parsing T1.07 Excel file via DOM fallback: ", e);
                result.put("code", 500);
                result.put("description", "Lỗi đọc tệp Excel: " + e.getMessage());
                return result;
            } finally {
                if (workbook != null) {
                    try { workbook.close(); } catch (Exception ignored) {}
                }
            }
        }

        if (tempFile != null && tempFile.exists()) {
            try { tempFile.delete(); } catch (Exception ignored) {}
        }

        // ── NOW acquire DB Connection ONLY for the quick batch save (< 1s) ──
        final double finalAverageScore = overallAvgVal;
        final int finalResponses = finalValidResponsesCount;
        final String targetSheet = finalSheetName;

        try {
            initDatabaseTables();
            return transactionTemplate.execute(status -> {
                BigDecimal roundedAverage = BigDecimal.valueOf(finalAverageScore).setScale(2, RoundingMode.HALF_UP);

                String safePeriodId = (periodId != null && !periodId.isEmpty() && !"null".equalsIgnoreCase(periodId)) ? periodId : "2025-2026";
                String safeUploadedBy = (uploadedBy != null && !uploadedBy.isEmpty()) ? uploadedBy : "Admin";

                String surveyId = "T1.07";
                String surveyTitle = "Khảo sát Mức độ hài lòng của SV về CTĐT (KPI T1.07)";
                String surveyType = "Đánh giá học phần";
                String inputMethod = "MANUAL_EXCEL";

                // 1. Upsert survey entry in surveys
                String upsertSurveySql = 
                    "IF NOT EXISTS (SELECT 1 FROM dbo.surveys WHERE id = ?) " +
                    "BEGIN " +
                    "    INSERT INTO dbo.surveys (id, survey_type, input_method, title, description, is_active, created_by, created_at, updated_at) " +
                    "    VALUES (?, ?, ?, ?, N'Tự động tạo khi tải lên tệp Excel khảo sát KPI T1.07', 1, ?, GETDATE(), GETDATE()); " +
                    "END " +
                    "ELSE " +
                    "BEGIN " +
                    "    UPDATE dbo.surveys SET survey_type = ?, input_method = ?, updated_at = GETDATE() WHERE id = ?; " +
                    "END;";
                jdbcTemplate.update(upsertSurveySql, surveyId, surveyId, surveyType, inputMethod, surveyTitle, safeUploadedBy, surveyType, inputMethod, surveyId);

                // 2. Insert or update campaign entry in survey_campaigns using C_T107_<period_id> (max 20 chars)
                String safePeriodIdStr = safePeriodId.replaceAll("[^a-zA-Z0-9_-]", "");
                if (safePeriodIdStr.length() > 13) {
                    safePeriodIdStr = safePeriodIdStr.substring(0, 13);
                }
                String campaignId = "C_T107_" + safePeriodIdStr;
                String campaignTitle = "Đợt tải lên Excel T1.07 (" + fileName + ")";

                String upsertCampaignSql = 
                    "IF NOT EXISTS (SELECT 1 FROM dbo.survey_campaigns WHERE id = ?) " +
                    "BEGIN " +
                    "    INSERT INTO dbo.survey_campaigns (id, survey_id, title, semester_code, max_responses, is_active, created_at, updated_at) " +
                    "    VALUES (?, ?, ?, ?, 1, 1, GETDATE(), GETDATE()); " +
                    "END " +
                    "ELSE " +
                    "BEGIN " +
                    "    UPDATE dbo.survey_campaigns SET title = ?, semester_code = ?, updated_at = GETDATE() WHERE id = ?; " +
                    "END;";
                jdbcTemplate.update(upsertCampaignSql, campaignId, campaignId, surveyId, campaignTitle, safePeriodId, campaignTitle, safePeriodId, campaignId);

                // 3. Ultra-fast bulk multi-row insert into survey_responses and survey_response_answers (< 1s execution)
                bulkInsertResponsesAndAnswers(surveyId, campaignId, responseRows);

                syncToKpiDataPoints(roundedAverage.doubleValue(), fileName, safePeriodId, finalResponses);

                JSONObject res = new JSONObject();
                res.put("code", 200);
                res.put("description", "Tải lên và lưu trữ thành công " + finalResponses + " lượt trả lời khảo sát T1.07 vào hệ thống!");
                res.put("survey_id", surveyId);
                res.put("campaign_id", campaignId);
                res.put("file_name", fileName);
                res.put("sheet_name", targetSheet);
                res.put("period_id", safePeriodId);
                res.put("total_responses", finalResponses);
                res.put("average_score", roundedAverage.doubleValue());
                res.put("kpi_code", "T1.07");
                return res;
            });
        } catch (Exception e) {
            log.error("Error persisting T1.07 survey batch data: ", e);
            result.put("code", 500);
            result.put("description", "Lỗi lưu dữ liệu cơ sở dữ liệu: " + e.getMessage());
            return result;
        }
    }

    private void bulkInsertResponsesAndAnswers(String surveyId, String campaignId, List<Map<String, Object>> responseRows) {
        if (responseRows == null || responseRows.isEmpty()) return;

        final int maxRowsPerStmt = 250;
        
        // 1. Bulk insert survey_responses (250 rows per INSERT statement)
        for (int i = 0; i < responseRows.size(); i += maxRowsPerStmt) {
            int end = Math.min(i + maxRowsPerStmt, responseRows.size());
            List<Map<String, Object>> subList = responseRows.subList(i, end);

            StringBuilder sql = new StringBuilder(
                "INSERT INTO dbo.survey_responses (id, survey_id, campaign_id, user_code, full_name, role, class_code, is_answered, created_at, updated_at) VALUES "
            );

            List<Object> params = new ArrayList<>();
            for (int j = 0; j < subList.size(); j++) {
                Map<String, Object> resp = subList.get(j);
                String respId = (String) resp.get("respId");
                if (respId == null || respId.length() > 24) {
                    respId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                    resp.put("respId", respId);
                }
                String studentCode = (String) resp.get("studentCode");
                if (studentCode != null && studentCode.length() > 50) {
                    studentCode = studentCode.substring(0, 50);
                }
                String studentName = (String) resp.get("studentName");
                if (studentName != null && studentName.length() > 150) {
                    studentName = studentName.substring(0, 150);
                }
                String programName = (String) resp.get("programName");
                if (programName != null && programName.length() > 100) {
                    programName = programName.substring(0, 100);
                }

                if (j > 0) sql.append(",");
                sql.append("(?, ?, ?, ?, ?, N'Sinh viên', ?, 1, GETDATE(), GETDATE())");

                params.add(respId);
                params.add(surveyId);
                params.add(campaignId);
                params.add(studentCode != null ? studentCode : "");
                params.add(studentName != null ? studentName : "");
                params.add(programName != null ? programName : "");
            }

            jdbcTemplate.update(sql.toString(), params.toArray());
        }

        // 2. Bulk insert survey_response_answers (500 rows per INSERT statement)
        final int maxAnswersPerStmt = 500;
        for (int i = 0; i < responseRows.size(); i += maxAnswersPerStmt) {
            int end = Math.min(i + maxAnswersPerStmt, responseRows.size());
            List<Map<String, Object>> subList = responseRows.subList(i, end);

            StringBuilder sql = new StringBuilder(
                "INSERT INTO dbo.survey_response_answers (id, response_id, question_id, choices) VALUES "
            );

            List<Object> params = new ArrayList<>();
            for (int j = 0; j < subList.size(); j++) {
                Map<String, Object> resp = subList.get(j);
                String respId = (String) resp.get("respId");
                Double score = (Double) resp.get("ratingScore");

                String answerId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);

                if (j > 0) sql.append(",");
                sql.append("(?, ?, 'Q_T1.07_RATING', ?)");

                params.add(answerId);
                params.add(respId);
                params.add(score != null ? String.valueOf(score) : "0");
            }

            jdbcTemplate.update(sql.toString(), params.toArray());
        }
    }

    private void syncToKpiDataPoints(double averageScore, String fileName, String periodId, int totalResponses) {
        try {
            // Find kpi_id for T1.07
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList("SELECT kpi_id FROM dbo.kpi_definitions WHERE kpi_code = 'T1.07'");
            if (kpiRows.isEmpty()) {
                log.warn("KPI code T1.07 not found in kpi_definitions table.");
                return;
            }

            Integer kpiId = (Integer) kpiRows.get(0).get("kpi_id");

            // Find or create period_id integer if needed, or query matching period
            Integer intPeriodId = null;
            try {
                intPeriodId = Integer.parseInt(periodId);
            } catch (Exception e) {
                List<Map<String, Object>> pRows = jdbcTemplate.queryForList("SELECT period_id FROM dbo.period_instances WHERE period_code = ?", periodId);
                if (!pRows.isEmpty()) {
                    intPeriodId = (Integer) pRows.get(0).get("period_id");
                }
            }

            if (intPeriodId == null) {
                // Default to standard period or latest period
                List<Map<String, Object>> pRows = jdbcTemplate.queryForList("SELECT TOP 1 period_id FROM dbo.period_instances ORDER BY period_id DESC");
                if (!pRows.isEmpty()) {
                    intPeriodId = (Integer) pRows.get(0).get("period_id");
                } else {
                    intPeriodId = 1;
                }
            }

            String notesText = "Trung bình điểm khảo sát SV về CTĐT (Tên tệp: " + fileName + ", Tổng số lượt: " + totalResponses + ")";

            // Check existing kpi_data_points row
            String checkSql = "SELECT COUNT(*) FROM dbo.kpi_data_points WHERE kpi_id = ? AND period_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, kpiId, intPeriodId);

            if (count != null && count > 0) {
                String updateSql = "UPDATE dbo.kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ? WHERE kpi_id = ? AND period_id = ?";
                jdbcTemplate.update(updateSql, averageScore, notesText, kpiId, intPeriodId);
            } else {
                String insertSql = "INSERT INTO dbo.kpi_data_points (kpi_id, period_id, actual_value, target, status_id, notes, updated_at) VALUES (?, ?, ?, 4.00, 1, ?, GETDATE())";
                jdbcTemplate.update(insertSql, kpiId, intPeriodId, averageScore, notesText);
            }

            log.info("Synced KPI T1.07 actual_value to {} for period_id {}", averageScore, intPeriodId);
        } catch (Exception e) {
            log.error("Error syncing T1.07 score to kpi_data_points: ", e);
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            if (d == (long) d) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        } else if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else if (cell.getCellType() == CellType.FORMULA) {
            try {
                return String.valueOf(cell.getNumericCellValue());
            } catch (Exception e) {
                try {
                    return cell.getStringCellValue();
                } catch (Exception ex) {
                    return "";
                }
            }
        }
        return "";
    }

    private Double parseRatingValue(Cell cell, String cellStr) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val >= 1.0 && val <= 5.0) return val;
        }

        String strToParse = cellStr;
        if ((strToParse == null || strToParse.isEmpty()) && cell != null && cell.getCellType() == CellType.STRING) {
            strToParse = cell.getStringCellValue().trim();
        }

        if (strToParse == null || strToParse.isEmpty()) return null;

        try {
            double val = Double.parseDouble(strToParse);
            if (val >= 1.0 && val <= 5.0) return val;
        } catch (Exception ignored) {}

        String lower = strToParse.toLowerCase();
        if (lower.contains("rất hài lòng") || lower.contains("rat hai long") || lower.contains("rất tốt") || lower.contains("rat tot") || lower.equals("5")) {
            return 5.0;
        } else if (lower.contains("hài lòng") || lower.contains("hai long") || lower.contains("tốt") || lower.contains("tot") || lower.equals("4")) {
            return 4.0;
        } else if (lower.contains("bình thường") || lower.contains("binh thuong") || lower.contains("trung bình") || lower.contains("trung binh") || lower.equals("3")) {
            return 3.0;
        } else if (lower.contains("không hài lòng") || lower.contains("khong hai long") || lower.contains("kém") || lower.contains("kem") || lower.equals("2")) {
            return 2.0;
        } else if (lower.contains("rất không hài lòng") || lower.contains("rat khong hai long") || lower.contains("rất kém") || lower.contains("rat kem") || lower.equals("1")) {
            return 1.0;
        }

        return null;
    }

    public byte[] generateT107ExcelTemplate() {
        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            // Template requirement: Only ONE worksheet named "data"
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("data");

            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "Mã sinh viên", "Họ và tên", "Chương trình đào tạo", "Mức độ hài lòng (1-5)", "Ghi chú / Ý kiến đóng góp"};
            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample Data Rows for user reference (1-5 rating scale)
            Object[][] sampleData = {
                {1, "B21DCCN001", "Nguyễn Văn An", "Công nghệ thông tin", 5, "Chương trình đào tạo tốt, sát với thực tế"},
                {2, "B21DCCN002", "Trần Thị Bình", "An toàn thông tin", 4, "Giảng viên nhiệt tình, tài liệu bài giảng phong phú"},
                {3, "B21DCCN003", "Lê Hoàng Cường", "Khoa học dữ liệu", 4, "Môn thực hành chất lượng, đáp ứng chuẩn đầu ra"},
                {4, "B21DCCN004", "Phạm Minh Đức", "Công nghệ thông tin", 5, "Rất hài lòng với nội dung học phần và dự án thực tế"},
                {5, "B21DCCN005", "Vũ Thị Mai", "Truyền thông đa phương tiện", 3, "Cần bổ sung thêm thời lượng thực hành phòng lab"}
            };

            for (int r = 0; r < sampleData.length; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
                Object[] rowData = sampleData[r];
                for (int c = 0; c < rowData.length; c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
                    if (rowData[c] instanceof Number) {
                        cell.setCellValue(((Number) rowData[c]).doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(rowData[c]));
                    }
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating T107 Excel template: ", e);
            return new byte[0];
        }
    }

    private static class SAXParseResult {
        double overallAvg = 0.0;
        int validResponsesCount = 0;
        String sheetName = "data";
        boolean success = false;
        String errorMessage = null;
    }

    private SAXParseResult parseT107ExcelSAX(java.io.File tempFile, List<Map<String, Object>> responseRows) {
        SAXParseResult res = new SAXParseResult();
        org.apache.poi.openxml4j.opc.OPCPackage pkg = null;
        try {
            pkg = org.apache.poi.openxml4j.opc.OPCPackage.open(tempFile, org.apache.poi.openxml4j.opc.PackageAccess.READ);
            org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable strings = new org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable(pkg);
            org.apache.poi.xssf.eventusermodel.XSSFReader xssfReader = new org.apache.poi.xssf.eventusermodel.XSSFReader(pkg);
            org.apache.poi.xssf.model.StylesTable styles = xssfReader.getStylesTable();

            org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator iter = (org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator) xssfReader.getSheetsData();
            
            java.io.InputStream sheetInputStream = null;
            String targetSheetName = "data";

            while (iter.hasNext()) {
                java.io.InputStream stream = iter.next();
                String name = iter.getSheetName();
                if (name != null && name.trim().equalsIgnoreCase("data")) {
                    sheetInputStream = stream;
                    targetSheetName = name;
                    break;
                }
            }

            if (sheetInputStream == null) {
                iter = (org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator) xssfReader.getSheetsData();
                while (iter.hasNext()) {
                    java.io.InputStream stream = iter.next();
                    String name = iter.getSheetName();
                    if (name != null) {
                        String clean = name.trim().toLowerCase();
                        if (clean.contains("data") || clean.contains("sinh") || clean.contains("sheet")) {
                            sheetInputStream = stream;
                            targetSheetName = name;
                            break;
                        }
                    }
                }
            }

            if (sheetInputStream == null) {
                iter = (org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator) xssfReader.getSheetsData();
                if (iter.hasNext()) {
                    sheetInputStream = iter.next();
                    targetSheetName = iter.getSheetName();
                }
            }

            if (sheetInputStream == null) {
                res.errorMessage = "Không tìm thấy trang tính (worksheet) 'data' trong tệp Excel.";
                return res;
            }

            res.sheetName = targetSheetName;

            final double[] totals = new double[]{0.0};
            final int[] count = new int[]{0};

            class T107SheetHandler implements org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler {
                private int currentRow = 0;
                private final List<Double> rowRatings = new ArrayList<>();
                private String studentCode = null;
                private String studentName = null;
                private String programName = null;

                @Override
                public void startRow(int rowNum) {
                    this.currentRow = rowNum;
                    this.rowRatings.clear();
                    this.studentCode = null;
                    this.studentName = null;
                    this.programName = null;
                }

                @Override
                public void endRow(int rowNum) {
                    if (!rowRatings.isEmpty()) {
                        double rowAvg = rowRatings.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                        if (rowAvg >= 1.0 && rowAvg <= 5.0) {
                            count[0]++;
                            totals[0] += rowAvg;

                            Map<String, Object> respData = new HashMap<>();
                            respData.put("rowIndex", rowNum + 1);
                            respData.put("studentCode", studentCode != null ? studentCode : "");
                            respData.put("studentName", studentName != null ? studentName : "");
                            respData.put("programName", programName != null ? programName : "");
                            respData.put("ratingScore", BigDecimal.valueOf(rowAvg).setScale(2, RoundingMode.HALF_UP).doubleValue());
                            responseRows.add(respData);
                        }
                    }
                }

                @Override
                public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
                    if (formattedValue == null || formattedValue.trim().isEmpty()) return;
                    String val = formattedValue.trim();

                    Double parsedRating = parseRatingValue(null, val);
                    if (parsedRating != null) {
                        rowRatings.add(parsedRating);
                        return;
                    }

                    if (studentCode == null && val.matches("(?i)^[A-Z0-9]{5,15}$")) {
                        studentCode = val;
                    } else if (studentName == null && val.matches("(?i)^[\\p{L}\\s]{4,50}$") 
                            && !val.equalsIgnoreCase("Sinh viên") 
                            && !val.equalsIgnoreCase("Mức độ hài lòng")) {
                        studentName = val;
                    }
                }
            }

            javax.xml.parsers.SAXParserFactory saxFactory = javax.xml.parsers.SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            org.xml.sax.XMLReader parser = saxFactory.newSAXParser().getXMLReader();

            org.xml.sax.ContentHandler handler = new org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler(
                    styles, strings, new T107SheetHandler(), false);
            parser.setContentHandler(handler);

            org.xml.sax.InputSource sheetSource = new org.xml.sax.InputSource(sheetInputStream);
            parser.parse(sheetSource);
            sheetInputStream.close();

            res.validResponsesCount = count[0];
            if (res.validResponsesCount > 0) {
                res.overallAvg = totals[0] / res.validResponsesCount;
                res.success = true;
            } else {
                res.errorMessage = "Không tìm thấy dữ liệu điểm khảo sát (thang điểm 1–5) hợp lệ trong trang tính '" + targetSheetName + "'.";
            }
        } catch (Exception e) {
            log.warn("SAX streaming parser failed, falling back to DOM: {}", e.getMessage());
            res.success = false;
            res.errorMessage = e.getMessage();
        } finally {
            if (pkg != null) {
                try { pkg.close(); } catch (Exception ignored) {}
            }
        }
        return res;
    }
}
