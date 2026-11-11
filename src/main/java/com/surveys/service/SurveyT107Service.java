package com.surveys.service;

import com.kpi.KpiH402CalculationService;
import com.kpi.KpiH403CalculationService;
import com.kpi.KpiH404CalculationService;
import com.kpi.KpiH405CalculationService;
import com.kpi.StudentSurveyKpiSupport;
import jakarta.annotation.PostConstruct;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Types;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SurveyT107Service {

    private static final Logger log = LoggerFactory.getLogger(SurveyT107Service.class);
    private static final String SURVEY_ID = "T1.07";
    private static final String BLOCK_ID = "T107_BLOCK_MAIN";
    private static final Pattern SECTION_PREFIX_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)+\\s*:\\s*(.+)$");
    private static final Pattern NUMERIC_SECTION_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)*\\s*:");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SurveyStatsService surveyStatsService;

    @Autowired(required = false)
    private com.kpi.kpiExtend kpiExtendService;

    @Autowired(required = false)
    private KpiH402CalculationService kpiH402CalculationService;

    @Autowired(required = false)
    private KpiH403CalculationService kpiH403CalculationService;

    @Autowired(required = false)
    private KpiH404CalculationService kpiH404CalculationService;

    @Autowired(required = false)
    private KpiH405CalculationService kpiH405CalculationService;

    @PostConstruct
    public void initDatabaseTables() {
        // Core survey tables are established in database migrations.
    }

    public JSONObject processT107Excel(InputStream inputStream, String fileName, String periodId, String uploadedBy) {
        return processT107Excel(inputStream, fileName, periodId, uploadedBy, null);
    }

    public JSONObject processT107Excel(InputStream inputStream, String fileName, String periodId, String uploadedBy, String departmentId) {
        JSONObject result = new JSONObject();

        try {
            org.apache.poi.util.IOUtils.setByteArrayMaxOverride(1_000_000_000);
        } catch (Throwable t) {
            log.warn("Could not set IOUtils byteArrayMaxOverride: {}", t.getMessage());
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("survey_t107_", ".xlsx");
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("Could not buffer stream to temp file: {}", e.getMessage());
        }

        ParsedWorkbook parsed = null;
        if (tempFile != null && tempFile.length() > 0) {
            parsed = parseT107ExcelSAX(tempFile);
            if (parsed.success) {
                log.info("Parsed T1.07 Excel via SAX streaming. Rows: {}, Avg: {}, StructuredQuestions: {}",
                    parsed.validResponsesCount, parsed.overallAvg, parsed.surveySchema.gridQuestions.size());
            } else if (parsed.errorMessage != null && parsed.errorMessage.contains("Không tìm thấy trang tính")) {
                deleteQuietly(tempFile);
                result.put("code", 400);
                result.put("description", parsed.errorMessage);
                return result;
            }
        }

        if (parsed == null || !parsed.success) {
            parsed = parseT107ExcelDOM(tempFile, inputStream);
            if (!parsed.success) {
                deleteQuietly(tempFile);
                result.put("code", parsed.errorCode > 0 ? parsed.errorCode : 400);
                result.put("description", parsed.errorMessage != null ? parsed.errorMessage : "Không thể đọc tệp Excel.");
                return result;
            }
        }

        deleteQuietly(tempFile);

        final ParsedWorkbook finalParsed = parsed;
        final double finalAverageScore = finalParsed.overallAvg;
        final int finalResponses = finalParsed.validResponsesCount;
        final String targetSheet = finalParsed.sheetName;
        final boolean hasStructuredQuestions = finalParsed.surveySchema.hasGridQuestions();

        try {
            initDatabaseTables();
            JSONObject txResult = transactionTemplate.execute(status -> {
                BigDecimal roundedAverage = BigDecimal.valueOf(finalAverageScore).setScale(2, RoundingMode.HALF_UP);
                String safeUploadedBy = (uploadedBy != null && !uploadedBy.isEmpty()) ? uploadedBy : "Admin";

                // Resolve target period_id & period_code BEFORE creating/updating the campaign
                PeriodContext periodCtx = resolveT107PeriodContext(periodId);
                String campaignPeriodCode = periodCtx.periodCode != null ? periodCtx.periodCode : "2025-2026";
                String campaignId = buildCampaignId(campaignPeriodCode);

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
                    "    UPDATE dbo.surveys SET survey_type = ?, input_method = ?, is_ignored = 0, updated_at = GETDATE() WHERE id = ?; " +
                    "END;";
                jdbcTemplate.update(upsertSurveySql, SURVEY_ID, SURVEY_ID, surveyType, inputMethod, surveyTitle, safeUploadedBy, surveyType, inputMethod, SURVEY_ID);

                // 2. Clean up any legacy truncated campaign records for this exact semester_code before upserting
                Set<String> campaignIdsToClean = new LinkedHashSet<>();
                campaignIdsToClean.add(campaignId);
                String legacyCampaignId = buildLegacyTruncatedCampaignId(campaignPeriodCode);
                if (!legacyCampaignId.equals(campaignId)) {
                    List<Map<String, Object>> legacyRows = jdbcTemplate.queryForList(
                        "SELECT id, semester_code FROM dbo.survey_campaigns WITH (NOLOCK) WHERE survey_id = ? AND id = ?",
                        SURVEY_ID, legacyCampaignId
                    );
                    if (!legacyRows.isEmpty()) {
                        Object legSem = legacyRows.get(0).get("semester_code");
                        if (legSem == null || campaignPeriodCode.equalsIgnoreCase(legSem.toString().trim())) {
                            campaignIdsToClean.add(legacyCampaignId);
                        }
                    }
                }

                // 3. Upsert campaign entry in survey_campaigns
                String campaignTitle = "Đợt tải lên Excel T1.07 (" + fileName + ")";
                String semesterName = com.kpi.kpiExtend.formatPeriodDisplay(campaignPeriodCode);
                String upsertCampaignSql =
                    "IF NOT EXISTS (SELECT 1 FROM dbo.survey_campaigns WHERE id = ?) " +
                    "BEGIN " +
                    "    INSERT INTO dbo.survey_campaigns (id, survey_id, title, semester_code, semester_name, max_responses, is_active, created_at, updated_at) " +
                    "    VALUES (?, ?, ?, ?, ?, 1, 1, GETDATE(), GETDATE()); " +
                    "END " +
                    "ELSE " +
                    "BEGIN " +
                    "    UPDATE dbo.survey_campaigns SET title = ?, semester_code = ?, semester_name = ?, updated_at = GETDATE() WHERE id = ?; " +
                    "END;";
                jdbcTemplate.update(
                    upsertCampaignSql,
                    campaignId, campaignId, SURVEY_ID, toNVarChar(campaignTitle), campaignPeriodCode, toNVarChar(semesterName),
                    toNVarChar(campaignTitle), campaignPeriodCode, toNVarChar(semesterName), campaignId
                );

                // 4. Upsert structured question/matrix schema when multi-question columns exist
                if (hasStructuredQuestions) {
                    upsertStructuredSurveySchema(finalParsed.surveySchema);
                }

                // 5. Replace responses & answers for this campaign cleanly
                deleteCampaignResponses(campaignIdsToClean, campaignId);
                bulkInsertResponsesAndAnswers(SURVEY_ID, campaignId, finalParsed);

                Integer syncedPeriodId = syncToKpiDataPoints(roundedAverage.doubleValue(), fileName, periodId, finalResponses, departmentId);

                try {
                    surveyStatsService.recomputeCampaignAsync(SURVEY_ID, campaignId);
                } catch (Exception e) {
                    log.warn("Notice triggering async stats compute for T1.07: {}", e.getMessage());
                }

                JSONObject res = new JSONObject();
                res.put("code", 200);
                res.put("description", "Tải lên và lưu trữ thành công " + finalResponses + " lượt trả lời khảo sát T1.07 vào hệ thống!");
                res.put("survey_id", SURVEY_ID);
                res.put("campaign_id", campaignId);
                res.put("file_name", fileName);
                res.put("sheet_name", targetSheet);
                res.put("period_id", campaignPeriodCode);
                if (syncedPeriodId != null) {
                    res.put("synced_period_id", syncedPeriodId);
                }
                res.put("total_responses", finalResponses);
                res.put("average_score", roundedAverage.doubleValue());
                res.put("structured_questions_count", finalParsed.surveySchema.gridQuestions.size());
                res.put("kpi_code", "T1.07");
                return res;
            });

            if (txResult != null && txResult.optInt("code") == 200) {
                if (kpiExtendService != null) {
                    try {
                        kpiExtendService.invalidateKpisWithAssignmentsCache();
                    } catch (Exception e) {
                        log.warn("Could not invalidate KPI assignments cache after T1.07 upload: {}", e.getMessage());
                    }
                }

                if (hasStructuredQuestions) {
                    JSONObject relatedKpis = triggerDependentStudentSatisfactionKpis();
                    txResult.put("related_kpis", relatedKpis);
                }
            }
            return txResult;
        } catch (Exception e) {
            log.error("Error persisting T1.07 survey batch data: ", e);
            result.put("code", 500);
            result.put("description", "Lỗi lưu dữ liệu cơ sở dữ liệu: " + e.getMessage());
            return result;
        }
    }

    private JSONObject triggerDependentStudentSatisfactionKpis() {
        JSONObject related = new JSONObject();
        if (kpiH402CalculationService != null) {
            related.put("H4.02", safeCalculate(() -> kpiH402CalculationService.calculateAndSaveH402(null, true)));
        }
        if (kpiH403CalculationService != null) {
            related.put("H4.03", safeCalculate(() -> kpiH403CalculationService.calculateAndSaveH403(null, true)));
        }
        if (kpiH404CalculationService != null) {
            related.put("H4.04", safeCalculate(() -> kpiH404CalculationService.calculateAndSaveH404(null, true)));
        }
        if (kpiH405CalculationService != null) {
            related.put("H4.05", safeCalculate(() -> kpiH405CalculationService.calculateAndSaveH405(null, true)));
        }
        return related;
    }

    private JSONObject safeCalculate(java.util.function.Supplier<JSONObject> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return err;
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try { file.delete(); } catch (Exception ignored) {}
        }
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new SqlParameterValue(Types.NVARCHAR, val);
    }

    private String buildCampaignId(String periodCode) {
        String clean = periodCode.replaceAll("[^a-zA-Z0-9_-]", "");
        if (clean.length() > 93) {
            clean = clean.substring(0, 93);
        }
        return "C_T107_" + clean;
    }

    private String buildLegacyTruncatedCampaignId(String periodCode) {
        String clean = periodCode.replaceAll("[^a-zA-Z0-9_-]", "");
        if (clean.length() > 13) {
            clean = clean.substring(0, 13);
        }
        return "C_T107_" + clean;
    }

    private PeriodContext resolveT107PeriodContext(String periodInput) {
        String normalized = normalizePeriodInput(periodInput);
        List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
            "SELECT TOP 1 k.kpi_id, k.cycle_id, c.cycle_type " +
            "FROM dbo.kpi_definitions k " +
            "LEFT JOIN dbo.cycle_definitions c ON k.cycle_id = c.cycle_id " +
            "WHERE k.kpi_code = 'T1.07' AND (k.is_deleted = 0 OR k.is_deleted IS NULL)"
        );
        if (kpiRows.isEmpty()) {
            return new PeriodContext(null, normalized != null ? normalized : "2025-2026");
        }

        Map<String, Object> kpiDef = kpiRows.get(0);
        Integer cycleId = kpiDef.get("cycle_id") != null ? ((Number) kpiDef.get("cycle_id")).intValue() : null;
        String cycleType = kpiDef.get("cycle_type") != null ? kpiDef.get("cycle_type").toString().trim() : null;

        int resolvedId = resolveTargetPeriodId(cycleId, cycleType, periodInput);
        String resolvedCode = normalized != null ? normalized : "2025-2026";
        try {
            List<Map<String, Object>> pRows = jdbcTemplate.queryForList(
                "SELECT TOP 1 period_code FROM dbo.period_instances WITH (NOLOCK) WHERE period_id = ?",
                resolvedId
            );
            if (!pRows.isEmpty() && pRows.get(0).get("period_code") != null) {
                resolvedCode = pRows.get(0).get("period_code").toString().trim();
            }
        } catch (Exception ignored) {}

        return new PeriodContext(resolvedId, resolvedCode);
    }

    private void upsertStructuredSurveySchema(SurveySchema schema) {
        // Block
        jdbcTemplate.update(
            "IF NOT EXISTS (SELECT 1 FROM dbo.survey_blocks WHERE id = ?) " +
            "    INSERT INTO dbo.survey_blocks (id, survey_id, title, is_visible, is_clo_evaluation) VALUES (?, ?, N'Nội dung khảo sát sinh viên', 1, 0) " +
            "ELSE " +
            "    UPDATE dbo.survey_blocks SET survey_id = ? WHERE id = ?",
            BLOCK_ID, BLOCK_ID, SURVEY_ID, SURVEY_ID, BLOCK_ID
        );

        for (GridQuestionSpec q : schema.gridQuestions.values()) {
            jdbcTemplate.update(
                "IF NOT EXISTS (SELECT 1 FROM dbo.survey_questions WHERE id = ?) " +
                "    INSERT INTO dbo.survey_questions (id, block_id, question_type, is_required, content, has_other_option, is_from_category) " +
                "    VALUES (?, ?, 'GridSingleChoice', 0, ?, 0, 0) " +
                "ELSE " +
                "    UPDATE dbo.survey_questions SET block_id = ?, question_type = 'GridSingleChoice', content = ? WHERE id = ?",
                q.questionId, q.questionId, BLOCK_ID, toNVarChar(q.title), BLOCK_ID, toNVarChar(q.title), q.questionId
            );

            for (GridRowSpec r : q.rows.values()) {
                jdbcTemplate.update(
                    "IF NOT EXISTS (SELECT 1 FROM dbo.question_matrix_rows WHERE id = ?) " +
                    "    INSERT INTO dbo.question_matrix_rows (id, question_id, content) VALUES (?, ?, ?) " +
                    "ELSE " +
                    "    UPDATE dbo.question_matrix_rows SET question_id = ?, content = ? WHERE id = ?",
                    r.rowId, r.rowId, q.questionId, toNVarChar(r.rowTitle), q.questionId, toNVarChar(r.rowTitle), r.rowId
                );
            }

            for (int score = 1; score <= 5; score++) {
                String colId = q.colIdsByScore.get(score);
                String colLabel = String.valueOf(score);
                jdbcTemplate.update(
                    "IF NOT EXISTS (SELECT 1 FROM dbo.question_matrix_cols WHERE id = ?) " +
                    "    INSERT INTO dbo.question_matrix_cols (id, question_id, content) VALUES (?, ?, ?) " +
                    "ELSE " +
                    "    UPDATE dbo.question_matrix_cols SET question_id = ?, content = ? WHERE id = ?",
                    colId, colId, q.questionId, toNVarChar(colLabel), q.questionId, toNVarChar(colLabel), colId
                );
            }
        }
    }

    private void deleteCampaignResponses(Set<String> campaignIdsToClean, String currentCampaignId) {
        for (String cid : campaignIdsToClean) {
            jdbcTemplate.update(
                "DELETE a FROM dbo.survey_response_answers a " +
                "INNER JOIN dbo.survey_responses r ON a.response_id = r.id " +
                "WHERE r.survey_id = ? AND r.campaign_id = ?",
                SURVEY_ID, cid
            );
            jdbcTemplate.update(
                "DELETE FROM dbo.survey_responses WHERE survey_id = ? AND campaign_id = ?",
                SURVEY_ID, cid
            );
            if (!cid.equals(currentCampaignId)) {
                jdbcTemplate.update(
                    "DELETE FROM dbo.survey_campaigns WHERE survey_id = ? AND id = ?",
                    SURVEY_ID, cid
                );
            }
        }
    }

    private void bulkInsertResponsesAndAnswers(String surveyId, String campaignId, ParsedWorkbook parsed) {
        List<ParsedRow> responseRows = parsed.rows;
        if (responseRows == null || responseRows.isEmpty()) return;

        final int maxResponsesPerStmt = 250;
        for (int i = 0; i < responseRows.size(); i += maxResponsesPerStmt) {
            int end = Math.min(i + maxResponsesPerStmt, responseRows.size());
            List<ParsedRow> subList = responseRows.subList(i, end);

            StringBuilder sql = new StringBuilder(
                "INSERT INTO dbo.survey_responses (id, survey_id, campaign_id, user_code, full_name, role, class_code, is_answered, created_at, updated_at) VALUES "
            );

            List<Object> params = new ArrayList<>(subList.size() * 6);
            for (int j = 0; j < subList.size(); j++) {
                ParsedRow resp = subList.get(j);
                if (resp.respId == null || resp.respId.length() > 24) {
                    resp.respId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                }
                String studentCode = truncate(resp.studentCode, 50);
                String studentName = truncate(resp.studentName, 150);
                String programName = truncate(resp.programName, 100);

                if (j > 0) sql.append(",");
                sql.append("(?, ?, ?, ?, ?, N'Sinh viên', ?, 1, GETDATE(), GETDATE())");

                params.add(resp.respId);
                params.add(surveyId);
                params.add(campaignId);
                params.add(studentCode);
                params.add(studentName);
                params.add(programName);
            }

            jdbcTemplate.update(sql.toString(), params.toArray());
        }

        if (!parsed.surveySchema.hasGridQuestions()) {
            insertScalarT107Answers(responseRows);
        } else {
            insertStructuredGridAnswers(parsed.surveySchema, responseRows);
        }
    }

    private void insertScalarT107Answers(List<ParsedRow> responseRows) {
        final int maxAnswersPerStmt = 500;
        for (int i = 0; i < responseRows.size(); i += maxAnswersPerStmt) {
            int end = Math.min(i + maxAnswersPerStmt, responseRows.size());
            List<ParsedRow> subList = responseRows.subList(i, end);

            StringBuilder sql = new StringBuilder(
                "INSERT INTO dbo.survey_response_answers (id, response_id, question_id, choices) VALUES "
            );

            List<Object> params = new ArrayList<>(subList.size() * 3);
            for (int j = 0; j < subList.size(); j++) {
                ParsedRow resp = subList.get(j);
                String answerId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                if (j > 0) sql.append(",");
                sql.append("(?, ?, 'Q_T1.07_RATING', ?)");

                params.add(answerId);
                params.add(resp.respId);
                params.add(String.valueOf(resp.roundedAverage));
            }
            jdbcTemplate.update(sql.toString(), params.toArray());
        }
    }

    private void insertStructuredGridAnswers(SurveySchema schema, List<ParsedRow> responseRows) {
        List<AnswerInsertItem> batch = new ArrayList<>(400);
        Collection<GridQuestionSpec> questions = schema.gridQuestions.values();

        for (ParsedRow resp : responseRows) {
            if (resp.gridScores == null) continue;
            for (GridQuestionSpec q : questions) {
                JSONArray choicesArr = new JSONArray();
                for (GridRowSpec r : q.rows.values()) {
                    double score = resp.gridScores[r.scoreSlot];
                    if (Double.isNaN(score) || score < 1.0 || score > 5.0) continue;

                    JSONObject item = new JSONObject();
                    item.put("idHang", r.rowId);
                    item.put("textHang", r.rowTitle);

                    int roundedInt = (int) Math.round(score);
                    if (Math.abs(score - roundedInt) < 1e-6 && roundedInt >= 1 && roundedInt <= 5) {
                        item.put("idCot", q.colIdsByScore.get(roundedInt));
                        item.put("textCot", String.valueOf(roundedInt));
                    } else {
                        item.put("textCot", String.valueOf(score));
                    }
                    choicesArr.put(item);
                }

                if (choicesArr.length() > 0) {
                    batch.add(new AnswerInsertItem(resp.respId, q.questionId, choicesArr.toString()));
                    if (batch.size() >= 400) {
                        flushAnswerBatch(batch);
                        batch.clear();
                    }
                }
            }
        }
        if (!batch.isEmpty()) {
            flushAnswerBatch(batch);
        }
    }

    private void flushAnswerBatch(List<AnswerInsertItem> batch) {
        StringBuilder sql = new StringBuilder(
            "INSERT INTO dbo.survey_response_answers (id, response_id, question_id, choices) VALUES "
        );
        List<Object> params = new ArrayList<>(batch.size() * 4);
        for (int i = 0; i < batch.size(); i++) {
            AnswerInsertItem item = batch.get(i);
            String answerId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            if (i > 0) sql.append(",");
            sql.append("(?, ?, ?, ?)");
            params.add(answerId);
            params.add(item.responseId);
            params.add(item.questionId);
            params.add(toNVarChar(item.choicesJson));
        }
        jdbcTemplate.update(sql.toString(), params.toArray());
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) return "";
        return val.length() > maxLen ? val.substring(0, maxLen) : val;
    }

    Integer syncToKpiDataPoints(double averageScore, String fileName, String periodId, int totalResponses, String requestedDeptId) {
        try {
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT TOP 1 k.kpi_id, k.cycle_id, c.cycle_type " +
                "FROM dbo.kpi_definitions k " +
                "LEFT JOIN dbo.cycle_definitions c ON k.cycle_id = c.cycle_id " +
                "WHERE k.kpi_code = 'T1.07' AND (k.is_deleted = 0 OR k.is_deleted IS NULL)"
            );
            if (kpiRows.isEmpty()) {
                log.warn("KPI code T1.07 not found in kpi_definitions table.");
                return null;
            }

            Map<String, Object> kpiDef = kpiRows.get(0);
            Integer kpiId = ((Number) kpiDef.get("kpi_id")).intValue();
            Integer cycleId = kpiDef.get("cycle_id") != null ? ((Number) kpiDef.get("cycle_id")).intValue() : null;
            String cycleType = kpiDef.get("cycle_type") != null ? kpiDef.get("cycle_type").toString().trim() : null;

            int intPeriodId = resolveTargetPeriodId(cycleId, cycleType, periodId);
            String notesText = "Trung bình điểm khảo sát SV về CTĐT (Tên tệp: " + fileName + ", Tổng số lượt: " + totalResponses + ")";

            upsertKpiDataPointForDept(kpiId, intPeriodId, null, averageScore, notesText);

            Set<String> targetDeptIds = new LinkedHashSet<>();
            if (requestedDeptId != null && !requestedDeptId.trim().isEmpty() && !"null".equalsIgnoreCase(requestedDeptId.trim())) {
                targetDeptIds.add(requestedDeptId.trim());
            }
            try {
                List<String> assignedDeptIds = jdbcTemplate.queryForList(
                    "SELECT DISTINCT CAST(department_id AS VARCHAR(100)) FROM dbo.kpi_assignments WHERE kpi_id = ? AND department_id IS NOT NULL",
                    String.class, kpiId
                );
                for (String d : assignedDeptIds) {
                    if (d != null && !d.trim().isEmpty() && !"null".equalsIgnoreCase(d.trim())) {
                        targetDeptIds.add(d.trim());
                    }
                }
            } catch (Exception deptEx) {
                log.warn("Could not query assigned departments for T1.07: {}", deptEx.getMessage());
            }

            for (String deptId : targetDeptIds) {
                upsertKpiDataPointForDept(kpiId, intPeriodId, deptId, averageScore, notesText);
            }

            log.info("Synced KPI T1.07 actual_value to {} for period_id {} (departments: {})", averageScore, intPeriodId, targetDeptIds.size());
            return intPeriodId;
        } catch (Exception e) {
            log.error("Error syncing T1.07 score to kpi_data_points: ", e);
            return null;
        }
    }

    private void upsertKpiDataPointForDept(int kpiId, int periodId, String departmentId, double averageScore, String notesText) {
        List<Integer> existingIds;
        if (departmentId == null) {
            existingIds = jdbcTemplate.query(
                "SELECT TOP 1 data_id FROM dbo.kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC",
                (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId
            );
        } else {
            existingIds = jdbcTemplate.query(
                "SELECT TOP 1 data_id FROM dbo.kpi_data_points WHERE kpi_id = ? AND period_id = ? AND CAST(department_id AS VARCHAR(100)) = ? ORDER BY data_id DESC",
                (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId, departmentId
            );
        }

        if (!existingIds.isEmpty()) {
            jdbcTemplate.update(
                "UPDATE dbo.kpi_data_points SET actual_value = ?, status_id = 5, notes = ?, updated_at = GETDATE() WHERE data_id = ?",
                averageScore, notesText, existingIds.get(0)
            );
        } else if (departmentId == null) {
            jdbcTemplate.update(
                "INSERT INTO dbo.kpi_data_points (kpi_id, period_id, actual_value, status_id, notes, updated_at) VALUES (?, ?, ?, 5, ?, GETDATE())",
                kpiId, periodId, averageScore, notesText
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO dbo.kpi_data_points (kpi_id, period_id, department_id, actual_value, status_id, notes, updated_at) VALUES (?, ?, ?, ?, 5, ?, GETDATE())",
                kpiId, periodId, departmentId, averageScore, notesText
            );
        }
    }

    private int resolveTargetPeriodId(Integer cycleId, String cycleType, String periodInput) {
        String normalized = normalizePeriodInput(periodInput);

        if (normalized != null && normalized.matches("^\\d+$")) {
            try {
                int parsed = Integer.parseInt(normalized);
                List<Integer> ids = cycleId != null
                    ? jdbcTemplate.query("SELECT TOP 1 period_id FROM dbo.period_instances WHERE period_id = ? AND cycle_id = ?", (rs, rowNum) -> rs.getInt("period_id"), parsed, cycleId)
                    : jdbcTemplate.query("SELECT TOP 1 period_id FROM dbo.period_instances WHERE period_id = ?", (rs, rowNum) -> rs.getInt("period_id"), parsed);
                if (!ids.isEmpty()) {
                    return ids.get(0);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (cycleId != null) {
            if (normalized != null) {
                String stripped = normalized.replaceAll("^(SCHOOLYEAR_|ANNUAL_|TERM_|QUARTERLY_|MONTHLY_)", "");
                List<Integer> ids = jdbcTemplate.query(
                    "SELECT TOP 1 period_id FROM dbo.period_instances WHERE cycle_id = ? AND (period_code = ? OR period_code LIKE ?) ORDER BY period_id DESC",
                    (rs, rowNum) -> rs.getInt("period_id"), cycleId, normalized, "%" + stripped
                );
                if (!ids.isEmpty()) {
                    return ids.get(0);
                }
                if (kpiExtendService != null && cycleType != null && isPeriodCompatibleWithCycle(cycleType, normalized)) {
                    int resolved = kpiExtendService.resolveOrCreatePeriodInstanceByCode(cycleId, cycleType, normalized);
                    if (resolved > 0) {
                        return resolved;
                    }
                }
            }

            if (kpiExtendService != null && cycleType != null) {
                Date refDate = new Date();
                int currPid = kpiExtendService.getOrCreatePeriodInstance(cycleId, cycleType, refDate);
                List<Map<String, Object>> currRows = jdbcTemplate.queryForList(
                    "SELECT start_date, end_date FROM dbo.period_instances WHERE period_id = ?", currPid
                );
                if (!currRows.isEmpty()) {
                    java.sql.Date currStart = (java.sql.Date) currRows.get(0).get("start_date");
                    java.sql.Date currEnd = (java.sql.Date) currRows.get(0).get("end_date");
                    if (currEnd != null && refDate.before(currEnd) && currStart != null) {
                        Calendar prevCal = Calendar.getInstance();
                        prevCal.setTime(currStart);
                        prevCal.add(Calendar.DAY_OF_YEAR, -1);
                        return kpiExtendService.getOrCreatePeriodInstance(cycleId, cycleType, prevCal.getTime());
                    }
                }
                return currPid;
            }

            List<Integer> latestInCycle = jdbcTemplate.query(
                "SELECT TOP 1 period_id FROM dbo.period_instances WHERE cycle_id = ? ORDER BY start_date DESC, period_id DESC",
                (rs, rowNum) -> rs.getInt("period_id"), cycleId
            );
            if (!latestInCycle.isEmpty()) {
                return latestInCycle.get(0);
            }
        }

        if (normalized != null) {
            List<Integer> anyCycle = jdbcTemplate.query(
                "SELECT TOP 1 period_id FROM dbo.period_instances WHERE period_code = ? ORDER BY period_id DESC",
                (rs, rowNum) -> rs.getInt("period_id"), normalized
            );
            if (!anyCycle.isEmpty()) {
                return anyCycle.get(0);
            }
        }

        List<Integer> fallback = jdbcTemplate.query(
            "SELECT TOP 1 period_id FROM dbo.period_instances ORDER BY period_id DESC",
            (rs, rowNum) -> rs.getInt("period_id")
        );
        return !fallback.isEmpty() ? fallback.get(0) : 1;
    }

    static String normalizePeriodInput(String rawPeriod) {
        if (rawPeriod == null) return null;
        String s = rawPeriod.trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "N/A".equalsIgnoreCase(s)) return null;

        Matcher mDisplayTerm = Pattern.compile("^Học\\s*kỳ\\s*(\\d+)\\s*\\(Năm\\s*học\\s*(\\d{4}-\\d{4})\\)$", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mDisplayTerm.find()) {
            return "TERM_" + mDisplayTerm.group(2) + "_HK" + mDisplayTerm.group(1);
        }
        Matcher mShortTerm = Pattern.compile("^Học\\s*kỳ\\s*(\\d+)\\s*năm\\s*(\\d{4})$", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mShortTerm.find()) {
            return mShortTerm.group(2) + "-HK" + mShortTerm.group(1);
        }
        Matcher mSy = Pattern.compile("^Năm\\s*học\\s*(\\d{4}-\\d{4})$", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mSy.find()) {
            return mSy.group(1);
        }
        Matcher mYear = Pattern.compile("^Năm\\s*(\\d{4})$", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mYear.find()) {
            return mYear.group(1);
        }
        Matcher mMonth = Pattern.compile("^Tháng\\s*(\\d{1,2})/(\\d{4})$", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mMonth.find()) {
            return String.format("%s-%02d", mMonth.group(2), Integer.parseInt(mMonth.group(1)));
        }
        Matcher mQuarter = Pattern.compile("^Quý\\s*(\\d)/(\\d{4})$", Pattern.CASE_INSENSITIVE).matcher(s);
        if (mQuarter.find()) {
            return mQuarter.group(2) + "-Q" + mQuarter.group(1);
        }
        return s;
    }

    private boolean isPeriodCompatibleWithCycle(String cycleType, String code) {
        if (cycleType == null || code == null) return false;
        String ct = cycleType.trim().toLowerCase();
        if ("term".equals(ct)) {
            return code.matches("(?i)^(?:TERM_\\d{4}-\\d{4}_HK\\d+|\\d{4}-HK\\d+)$");
        }
        if ("schoolyear".equals(ct)) {
            return code.matches("(?i)^(?:SCHOOLYEAR_)?\\d{4}[-_]\\d{4}$");
        }
        if ("annual".equals(ct) || "yearly".equals(ct)) {
            return code.matches("(?i)^(?:ANNUAL_)?\\d{4}$");
        }
        if ("quarterly".equals(ct)) {
            return code.matches("(?i)^(?:QUARTERLY_)?\\d{4}[-_]Q\\d$");
        }
        if ("monthly".equals(ct)) {
            return code.matches("(?i)^\\d{4}-(?:M)?\\d{1,2}$");
        }
        return false;
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
        return StudentSurveyKpiSupport.parseLikertScore(strToParse, null, null);
    }

    public byte[] generateT107ExcelTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("data");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"STT", "Mã sinh viên", "Họ và tên", "Chương trình đào tạo", "Mức độ hài lòng (1-5)", "Ghi chú / Ý kiến đóng góp"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            Object[][] sampleData = {
                {1, "B21DCCN001", "Nguyễn Văn An", "Công nghệ thông tin", 5, "Chương trình đào tạo tốt, sát với thực tế"},
                {2, "B21DCCN002", "Trần Thị Bình", "An toàn thông tin", 4, "Giảng viên nhiệt tình, tài liệu bài giảng phong phú"},
                {3, "B21DCCN003", "Lê Hoàng Cường", "Khoa học dữ liệu", 4, "Môn thực hành chất lượng, đáp ứng chuẩn đầu ra"},
                {4, "B21DCCN004", "Phạm Minh Đức", "Công nghệ thông tin", 5, "Rất hài lòng với nội dung học phần và dự án thực tế"},
                {5, "B21DCCN005", "Vũ Thị Mai", "Truyền thông đa phương tiện", 3, "Cần bổ sung thêm thời lượng thực hành phòng lab"}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] rowData = sampleData[r];
                for (int c = 0; c < rowData.length; c++) {
                    Cell cell = row.createCell(c);
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

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating T107 Excel template: ", e);
            return new byte[0];
        }
    }

    private ParsedWorkbook parseT107ExcelDOM(File tempFile, InputStream fallbackStream) {
        ParsedWorkbook res = new ParsedWorkbook();
        Workbook workbook = null;
        try {
            if (tempFile != null && tempFile.length() > 0) {
                workbook = WorkbookFactory.create(tempFile);
            } else {
                workbook = WorkbookFactory.create(fallbackStream);
            }

            Sheet sheet = selectTargetSheet(workbook);
            if (sheet == null) {
                res.errorCode = 400;
                res.errorMessage = "Không tìm thấy trang tính (worksheet) 'data' trong tệp Excel.";
                return res;
            }
            res.sheetName = sheet.getSheetName();

            RowAccumulator acc = new RowAccumulator(res);
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();

            for (int r = firstRowNum; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<Integer, String> rowCells = new LinkedHashMap<>();
                Map<Integer, Double> preParsedNumeric = new HashMap<>();
                int lastCellNum = row.getLastCellNum();
                for (int c = 0; c < lastCellNum; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) continue;
                    if (cell.getCellType() == CellType.NUMERIC) {
                        double d = cell.getNumericCellValue();
                        if (d >= 1.0 && d <= 5.0) {
                            preParsedNumeric.put(c, d);
                        }
                    }
                    String text = getCellStringValue(cell).trim();
                    if (!text.isEmpty()) {
                        rowCells.put(c, text);
                    }
                }
                acc.acceptRow(r, rowCells, preParsedNumeric);
            }

            if (res.validResponsesCount == 0) {
                res.errorCode = 400;
                res.errorMessage = "Không tìm thấy dữ liệu điểm khảo sát (thang điểm 1–5) hợp lệ trong trang tính '" + res.sheetName + "'.";
                return res;
            }

            res.overallAvg = acc.totalRatingSum / res.validResponsesCount;
            res.success = true;
            return res;
        } catch (Exception e) {
            log.error("Error parsing T1.07 Excel file via DOM fallback: ", e);
            res.errorCode = 500;
            res.errorMessage = "Lỗi đọc tệp Excel: " + e.getMessage();
            return res;
        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (Exception ignored) {}
            }
        }
    }

    private Sheet selectTargetSheet(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name != null && name.trim().equalsIgnoreCase("data")) {
                return workbook.getSheetAt(i);
            }
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String name = workbook.getSheetName(i);
            if (name != null) {
                String clean = name.trim().toLowerCase(Locale.ROOT);
                if (clean.contains("data") || clean.contains("sinh") || clean.contains("sheet")) {
                    return workbook.getSheetAt(i);
                }
            }
        }
        return workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
    }

    private ParsedWorkbook parseT107ExcelSAX(File tempFile) {
        ParsedWorkbook res = new ParsedWorkbook();
        OPCPackage pkg = null;
        try {
            pkg = OPCPackage.open(tempFile, PackageAccess.READ);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader xssfReader = new XSSFReader(pkg);
            StylesTable styles = xssfReader.getStylesTable();

            InputStream sheetInputStream = null;
            String targetSheetName = "data";

            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            while (iter.hasNext()) {
                InputStream stream = iter.next();
                String name = iter.getSheetName();
                if (name != null && name.trim().equalsIgnoreCase("data")) {
                    sheetInputStream = stream;
                    targetSheetName = name;
                    break;
                }
                stream.close();
            }

            if (sheetInputStream == null) {
                iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
                while (iter.hasNext()) {
                    InputStream stream = iter.next();
                    String name = iter.getSheetName();
                    if (name != null) {
                        String clean = name.trim().toLowerCase(Locale.ROOT);
                        if (clean.contains("data") || clean.contains("sinh") || clean.contains("sheet")) {
                            sheetInputStream = stream;
                            targetSheetName = name;
                            break;
                        }
                    }
                    stream.close();
                }
            }

            if (sheetInputStream == null) {
                iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
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
            RowAccumulator acc = new RowAccumulator(res);

            SAXParserFactory saxFactory = SAXParserFactory.newInstance();
            saxFactory.setNamespaceAware(true);
            XMLReader parser = saxFactory.newSAXParser().getXMLReader();

            ContentHandler handler = new XSSFSheetXMLHandler(
                styles, strings, new T107SheetHandler(acc), false
            );
            parser.setContentHandler(handler);
            parser.parse(new InputSource(sheetInputStream));
            sheetInputStream.close();

            if (res.validResponsesCount > 0) {
                res.overallAvg = acc.totalRatingSum / res.validResponsesCount;
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

    private static class T107SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final RowAccumulator acc;
        private final Map<Integer, String> rowCells = new LinkedHashMap<>();
        private int fallbackColIdx = 0;

        T107SheetHandler(RowAccumulator acc) {
            this.acc = acc;
        }

        @Override
        public void startRow(int rowNum) {
            rowCells.clear();
            fallbackColIdx = 0;
        }

        @Override
        public void endRow(int rowNum) {
            acc.acceptRow(rowNum, rowCells, Collections.emptyMap());
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int colIdx = fallbackColIdx++;
            if (cellReference != null && !cellReference.isEmpty()) {
                try {
                    colIdx = new CellReference(cellReference).getCol();
                    fallbackColIdx = colIdx + 1;
                } catch (Exception ignored) {}
            }
            if (formattedValue == null) return;
            String val = formattedValue.trim();
            if (!val.isEmpty()) {
                rowCells.put(colIdx, val);
            }
        }
    }

    private static class RowAccumulator {
        final ParsedWorkbook workbook;
        boolean headerChecked = false;
        boolean headerlessMode = false;
        final Map<Integer, ColumnSpec> columnSpecs = new HashMap<>();
        double totalRatingSum = 0.0;

        RowAccumulator(ParsedWorkbook workbook) {
            this.workbook = workbook;
        }

        void acceptRow(int rowNum, Map<Integer, String> rowCells, Map<Integer, Double> preParsedNumeric) {
            if (rowCells.isEmpty()) return;

            if (!headerChecked && !headerlessMode) {
                if (looksLikeHeaderRow(rowCells)) {
                    headerChecked = true;
                    buildSchemaFromHeaders(rowCells);
                    return;
                }
                // Ignore a lone merged title banner row above the real header row
                if (rowCells.size() == 1 && rowNum == 0) {
                    return;
                }
                headerChecked = true;
                headerlessMode = true;
            }

            String studentCode = null;
            String studentName = null;
            String programName = null;

            int gridSlotCount = workbook.surveySchema.totalGridSlots;
            double[] gridScores = gridSlotCount > 0 ? new double[gridSlotCount] : null;
            if (gridScores != null) {
                Arrays.fill(gridScores, Double.NaN);
            }

            List<Double> rowRatings = new ArrayList<>();

            for (Map.Entry<Integer, String> entry : rowCells.entrySet()) {
                int colIdx = entry.getKey();
                String cellText = entry.getValue();
                ColumnSpec spec = columnSpecs.get(colIdx);

                if (spec != null) {
                    switch (spec.role) {
                        case STUDENT_CODE:
                            if (studentCode == null) studentCode = cellText;
                            break;
                        case STUDENT_NAME:
                            if (studentName == null) studentName = cellText;
                            break;
                        case PROGRAM:
                            if (programName == null) programName = cellText;
                            break;
                        case METADATA_SKIP:
                        case OPEN_TEXT:
                            break;
                        case RATING:
                            Double score = preParsedNumeric.containsKey(colIdx)
                                ? preParsedNumeric.get(colIdx)
                                : StudentSurveyKpiSupport.parseLikertScore(cellText, null, null);
                            if (score != null && score >= 1.0 && score <= 5.0) {
                                rowRatings.add(score);
                                if (gridScores != null && spec.gridScoreSlot >= 0) {
                                    gridScores[spec.gridScoreSlot] = score;
                                }
                            }
                            break;
                    }
                } else {
                    Double score = preParsedNumeric.containsKey(colIdx)
                        ? preParsedNumeric.get(colIdx)
                        : StudentSurveyKpiSupport.parseLikertScore(cellText, null, null);
                    if (score != null && score >= 1.0 && score <= 5.0) {
                        rowRatings.add(score);
                    } else if (studentCode == null && cellText.matches("(?i)^[A-Z0-9]{5,15}$")) {
                        studentCode = cellText;
                    } else if (studentName == null && cellText.matches("(?i)^[\\p{L}\\s]{4,50}$")
                        && !cellText.equalsIgnoreCase("Sinh viên")
                        && !cellText.equalsIgnoreCase("Mức độ hài lòng")) {
                        studentName = cellText;
                    }
                }
            }

            if (rowRatings.isEmpty()) return;

            double sum = 0.0;
            for (Double d : rowRatings) sum += d;
            double rowAvg = sum / rowRatings.size();
            if (rowAvg < 1.0 || rowAvg > 5.0) return;

            workbook.validResponsesCount++;
            totalRatingSum += rowAvg;

            ParsedRow pr = new ParsedRow();
            pr.studentCode = studentCode != null ? studentCode : "";
            pr.studentName = studentName != null ? studentName : "";
            pr.programName = programName != null ? programName : "";
            pr.roundedAverage = BigDecimal.valueOf(rowAvg).setScale(2, RoundingMode.HALF_UP).doubleValue();
            pr.gridScores = gridScores;
            workbook.rows.add(pr);
        }

        private boolean looksLikeHeaderRow(Map<Integer, String> rowCells) {
            if (rowCells.size() < 2) return false;
            int headerCueCount = 0;
            boolean hasSectionColon = false;
            for (String v : rowCells.values()) {
                if (NUMERIC_SECTION_PATTERN.matcher(v).find()) {
                    hasSectionColon = true;
                    break;
                }
                String lower = v.toLowerCase(Locale.ROOT);
                if (lower.equals("stt") || lower.contains("mã sinh viên") || lower.contains("mã sv") || lower.equals("mssv")
                    || lower.contains("họ và tên") || lower.contains("họ tên")
                    || lower.contains("mức độ hài lòng") || lower.contains("chương trình đào tạo")
                    || lower.contains("ghi chú") || lower.contains("ý kiến") || lower.contains("đề xuất")) {
                    headerCueCount++;
                }
            }
            return hasSectionColon || headerCueCount >= 2;
        }

        private void buildSchemaFromHeaders(Map<Integer, String> headerCells) {
            for (Map.Entry<Integer, String> e : headerCells.entrySet()) {
                ColumnSpec spec = classifyHeaderColumn(e.getKey(), e.getValue());
                if (spec.role == ColumnRole.RATING && spec.rowTitle != null) {
                    GridQuestionSpec qSpec = workbook.surveySchema.getOrCreateQuestion(spec.questionTitle);
                    GridRowSpec rSpec = qSpec.getOrCreateRow(spec.rowTitle, workbook.surveySchema);
                    spec.gridScoreSlot = rSpec.scoreSlot;
                }
                columnSpecs.put(e.getKey(), spec);
            }
        }
    }

    static ColumnSpec classifyHeaderColumn(int colIndex, String rawHeader) {
        String raw = rawHeader != null ? rawHeader.trim() : "";
        String lower = raw.toLowerCase(Locale.ROOT);

        Matcher secMatcher = SECTION_PREFIX_PATTERN.matcher(raw);
        if (secMatcher.matches()) {
            String body = secMatcher.group(1).trim();
            int dashIdx = body.indexOf(" - ");
            if (dashIdx > 0) {
                String qTitle = body.substring(0, dashIdx).trim();
                String rTitle = body.substring(dashIdx + 3).trim();
                return ColumnSpec.rating(colIndex, qTitle, rTitle);
            }
            String bodyLower = body.toLowerCase(Locale.ROOT);
            if (!bodyLower.contains("hài lòng")
                && (bodyLower.contains("đề xuất") || bodyLower.contains("góp ý")
                    || bodyLower.contains("ý kiến") || bodyLower.contains("nhận xét")
                    || bodyLower.contains("điểm mạnh") || bodyLower.contains("cải tiến")
                    || bodyLower.contains("ghi chú"))) {
                return new ColumnSpec(colIndex, ColumnRole.OPEN_TEXT, body, null);
            }
            return ColumnSpec.rating(colIndex, body, null);
        }

        if (lower.contains("mã sinh viên") || lower.contains("mã sv") || lower.equals("mssv") || lower.contains("student code")) {
            return new ColumnSpec(colIndex, ColumnRole.STUDENT_CODE, raw, null);
        }
        if (lower.contains("họ và tên") || lower.contains("họ tên") || lower.contains("student name")) {
            return new ColumnSpec(colIndex, ColumnRole.STUDENT_NAME, raw, null);
        }
        if (lower.equals("stt") || lower.equals("email") || lower.contains("thời gian") || lower.contains("timestamp")
            || lower.equals("khóa") || lower.startsWith("khóa ") || lower.contains("năm thứ")) {
            return new ColumnSpec(colIndex, ColumnRole.METADATA_SKIP, raw, null);
        }
        if (!lower.contains("hài lòng") && raw.length() <= 60
            && (lower.equals("chương trình đào tạo") || lower.equals("ngành") || lower.equals("ngành đào tạo")
                || lower.equals("lớp") || lower.equals("khoa"))) {
            return new ColumnSpec(colIndex, ColumnRole.PROGRAM, raw, null);
        }
        if (!lower.contains("hài lòng")
            && (lower.contains("ghi chú") || lower.contains("ý kiến") || lower.contains("đề xuất") || lower.contains("góp ý"))) {
            return new ColumnSpec(colIndex, ColumnRole.OPEN_TEXT, raw, null);
        }

        int dashIdx = raw.indexOf(" - ");
        if (dashIdx > 0 && !lower.startsWith("stt")) {
            return ColumnSpec.rating(colIndex, raw.substring(0, dashIdx).trim(), raw.substring(dashIdx + 3).trim());
        }
        return ColumnSpec.rating(colIndex, raw, null);
    }

    private static String shortDeterministicId(String prefix, String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(prefix.length() + 18);
            hex.append(prefix);
            for (int i = 0; i < 9; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            String fallback = prefix + Integer.toHexString(key.hashCode());
            return fallback.length() > 24 ? fallback.substring(0, 24) : fallback;
        }
    }

    enum ColumnRole {
        STUDENT_CODE, STUDENT_NAME, PROGRAM, METADATA_SKIP, OPEN_TEXT, RATING
    }

    static class ColumnSpec {
        final int colIndex;
        final ColumnRole role;
        final String questionTitle;
        final String rowTitle;
        int gridScoreSlot = -1;

        ColumnSpec(int colIndex, ColumnRole role, String questionTitle, String rowTitle) {
            this.colIndex = colIndex;
            this.role = role;
            this.questionTitle = questionTitle;
            this.rowTitle = rowTitle;
        }

        static ColumnSpec rating(int colIndex, String questionTitle, String rowTitle) {
            return new ColumnSpec(colIndex, ColumnRole.RATING, questionTitle, rowTitle);
        }
    }

    private static class GridRowSpec {
        final String rowId;
        final String rowTitle;
        final int scoreSlot;

        GridRowSpec(String rowId, String rowTitle, int scoreSlot) {
            this.rowId = rowId;
            this.rowTitle = rowTitle;
            this.scoreSlot = scoreSlot;
        }
    }

    private static class GridQuestionSpec {
        final String questionId;
        final String title;
        final Map<String, GridRowSpec> rows = new LinkedHashMap<>();
        final Map<Integer, String> colIdsByScore = new LinkedHashMap<>();

        GridQuestionSpec(String questionId, String title) {
            this.questionId = questionId;
            this.title = title;
            for (int s = 1; s <= 5; s++) {
                colIdsByScore.put(s, shortDeterministicId("T107C_", questionId + "||" + s));
            }
        }

        GridRowSpec getOrCreateRow(String rowTitle, SurveySchema schema) {
            String rowId = shortDeterministicId("T107R_", title + "||" + rowTitle);
            return rows.computeIfAbsent(rowId, k -> new GridRowSpec(rowId, rowTitle, schema.allocateSlot()));
        }
    }

    private static class SurveySchema {
        final Map<String, GridQuestionSpec> gridQuestions = new LinkedHashMap<>();
        int totalGridSlots = 0;

        GridQuestionSpec getOrCreateQuestion(String title) {
            String qId = shortDeterministicId("T107Q_", title);
            return gridQuestions.computeIfAbsent(qId, k -> new GridQuestionSpec(qId, title));
        }

        int allocateSlot() {
            return totalGridSlots++;
        }

        boolean hasGridQuestions() {
            return !gridQuestions.isEmpty();
        }
    }

    private static class ParsedRow {
        String respId;
        String studentCode;
        String studentName;
        String programName;
        double roundedAverage;
        double[] gridScores;
    }

    private static class ParsedWorkbook {
        boolean success = false;
        int errorCode = 0;
        String errorMessage = null;
        String sheetName = "data";
        double overallAvg = 0.0;
        int validResponsesCount = 0;
        final SurveySchema surveySchema = new SurveySchema();
        final List<ParsedRow> rows = new ArrayList<>();
    }

    private static class AnswerInsertItem {
        final String responseId;
        final String questionId;
        final String choicesJson;

        AnswerInsertItem(String responseId, String questionId, String choicesJson) {
            this.responseId = responseId;
            this.questionId = questionId;
            this.choicesJson = choicesJson;
        }
    }

    private static class PeriodContext {
        final Integer periodId;
        final String periodCode;

        PeriodContext(Integer periodId, String periodCode) {
            this.periodId = periodId;
            this.periodCode = periodCode;
        }
    }
}
