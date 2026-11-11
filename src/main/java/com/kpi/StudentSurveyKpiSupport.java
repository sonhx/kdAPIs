package com.kpi;

import com.surveys.SurveySyncScheduler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared calculation, Likert-scoring, period-resolution, and persistence support
 * for student-satisfaction survey KPIs (H4.02-H4.05).
 */
@Component
public class StudentSurveyKpiSupport {

    private static final Logger log = LoggerFactory.getLogger(StudentSurveyKpiSupport.class);
    private static final Pattern LEADING_LIKERT_PATTERN = Pattern.compile("^([1-5])\\s*=");
    private static final Pattern SEMESTER_PATTERN = Pattern.compile("(?i)(?:Học\\s*kỳ|Kỳ|HK)\\s*([1-3])");
    private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("(\\d{4})\\s*[-–/]\\s*(\\d{4})");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private kpiExtend kpiExtendService;

    @Autowired(required = false)
    private SurveySyncScheduler surveySyncScheduler;

    @FunctionalInterface
    public interface QuestionFinder {
        List<QuestionMeta> findQuestions(String surveyId);
    }

    public static class KpiSpec {
        public final String kpiCode;
        public final int defaultKpiId;
        public final int defaultCycleId;
        public final double defaultTarget;
        public final String detailTable;
        public final String surveyPrefix;
        public final String fallbackSurveyPrefix;

        public KpiSpec(
            String kpiCode,
            int defaultKpiId,
            int defaultCycleId,
            double defaultTarget,
            String detailTable,
            String surveyPrefix,
            String fallbackSurveyPrefix
        ) {
            this.kpiCode = kpiCode;
            this.defaultKpiId = defaultKpiId;
            this.defaultCycleId = defaultCycleId;
            this.defaultTarget = defaultTarget;
            this.detailTable = detailTable;
            this.surveyPrefix = surveyPrefix;
            this.fallbackSurveyPrefix = fallbackSurveyPrefix;
        }
    }

    /**
     * Decodes UTF-8 strings that Spring's ISO-8859-1 .properties loader misread into Latin-1 chars.
     */
    public static String decodePropertyUtf8(String value) {
        if (value == null || value.isEmpty()) return value;

        boolean hasNonAscii = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c > 0xFF) {
                return value; // Already contains non-Latin-1 Unicode code points (proper UTF-8)
            }
            if (c > 0x7F) {
                hasNonAscii = true;
            }
        }
        if (!hasNonAscii) return value;

        try {
            byte[] rawBytes = value.getBytes(StandardCharsets.ISO_8859_1);
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(rawBytes))
                .toString();
        } catch (CharacterCodingException e) {
            return value;
        }
    }

    public static Object toNVarChar(String val) {
        return val == null ? null : new SqlParameterValue(Types.NVARCHAR, val);
    }

    public static int toInt(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static double toDouble(Object obj, double defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Parses a 1-5 Likert rating from either numeric strings, "N = Label", or Vietnamese phrases.
     * Checks extreme/negated labels ("rất không ...") before simple negatives ("không ...")
     * and positives ("hài lòng").
     */
    public static Double parseLikertScore(String textCot, String idCot, Map<String, String> matrixCols) {
        if (textCot != null && !textCot.trim().isEmpty()) {
            String s = textCot.trim();

            Matcher m = LEADING_LIKERT_PATTERN.matcher(s);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }

            try {
                double val = Double.parseDouble(s);
                if (val >= 1.0 && val <= 5.0) return val;
            } catch (Exception ignored) {}

            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.contains("rất phù hợp") || lower.contains("rất hài lòng") || lower.contains("rat hai long")
                || lower.contains("hoàn toàn đồng ý") || lower.contains("rất đồng ý")
                || lower.contains("xuất sắc") || lower.contains("rất tốt") || lower.contains("rat tot")) {
                return 5.0;
            }
            if (lower.contains("hoàn toàn không") || lower.contains("rất không hài lòng") || lower.contains("rat khong hai long")
                || lower.contains("rất không đồng ý") || lower.contains("rất kém") || lower.contains("rat kem")) {
                return 1.0;
            }
            if (lower.contains("chưa phù hợp") || lower.contains("ít phù hợp")
                || lower.contains("không hài lòng") || lower.contains("khong hai long")
                || lower.contains("không đồng ý") || lower.contains("kém") || lower.contains("kem")) {
                return 2.0;
            }
            if (lower.contains("phù hợp 1 phần") || lower.contains("bình thường") || lower.contains("binh thuong")
                || lower.contains("trung bình") || lower.contains("trung binh")
                || lower.contains("tương đối") || lower.contains("phân vân")) {
                return 3.0;
            }
            if (lower.contains("phù hợp") || lower.contains("hài lòng") || lower.contains("hai long")
                || lower.contains("đồng ý") || lower.contains("tốt") || lower.contains("tot")) {
                return 4.0;
            }
        }

        if (idCot != null && matrixCols != null && matrixCols.containsKey(idCot)) {
            return parseLikertScore(matrixCols.get(idCot), null, null);
        }

        return null;
    }

    public JSONObject calculateAndSave(KpiSpec spec, QuestionFinder questionFinder, Date referenceDate, boolean forceAll) {
        JSONObject result = new JSONObject();
        JSONArray processedPeriods = new JSONArray();

        try {
            Date refDate = referenceDate != null ? referenceDate : new Date();

            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT kpi_id, cycle_id, target FROM kpi_definitions WITH (NOLOCK) WHERE kpi_code = ? AND (is_deleted = 0 OR is_deleted IS NULL)",
                spec.kpiCode
            );

            if (kpiRows.isEmpty()) {
                log.warn("KPI {} definition not found in kpi_definitions table.", spec.kpiCode);
                result.put("status", "ERROR");
                result.put("message", "KPI " + spec.kpiCode + " definition not found in kpi_definitions.");
                return result;
            }

            int kpiId = toInt(kpiRows.get(0).get("kpi_id"), spec.defaultKpiId);
            int cycleId = toInt(kpiRows.get(0).get("cycle_id"), spec.defaultCycleId);
            double targetVal = toDouble(kpiRows.get(0).get("target"), spec.defaultTarget);

            List<Map<String, Object>> matchingSurveys = findMatchingSurveys(spec.surveyPrefix, spec.fallbackSurveyPrefix);
            if (matchingSurveys.isEmpty()) {
                String cleanPrefix = decodePropertyUtf8(spec.surveyPrefix);
                log.warn("No surveys found matching prefixes or T1.07 for {}: '{}'", spec.kpiCode, cleanPrefix);
                result.put("status", "WARNING");
                result.put("message", "Không tìm thấy phiếu khảo sát nào phù hợp cho " + spec.kpiCode);
                return result;
            }

            log.info("{}: Scanning {} matching survey(s)", spec.kpiCode, matchingSurveys.size());
            Map<Integer, PeriodCampaignData> periodCampaignMap = new LinkedHashMap<>();

            for (Map<String, Object> sRow : matchingSurveys) {
                String surveyId = (String) sRow.get("id");
                String surveyTitle = (String) sRow.get("title");
                String inputMethod = sRow.get("input_method") != null ? sRow.get("input_method").toString() : null;
                boolean isManualUpload = "MANUAL_EXCEL".equalsIgnoreCase(inputMethod) || "T1.07".equalsIgnoreCase(surveyId);

                List<QuestionMeta> targetQuestions = questionFinder.findQuestions(surveyId);
                if (targetQuestions.isEmpty()) {
                    continue;
                }

                List<Map<String, Object>> campaigns = jdbcTemplate.queryForList(
                    "SELECT id, survey_id, title, semester_code, semester_name, start_time, end_time, is_active " +
                    "FROM dbo.survey_campaigns WITH (NOLOCK) WHERE survey_id = ? ORDER BY start_time DESC, updated_at DESC",
                    surveyId
                );

                for (Map<String, Object> cRow : campaigns) {
                    String campaignId = (String) cRow.get("id");
                    String campaignTitle = (String) cRow.get("title");
                    Boolean isActive = (Boolean) cRow.get("is_active");
                    Timestamp startTime = (Timestamp) cRow.get("start_time");
                    Timestamp endTime = (Timestamp) cRow.get("end_time");

                    boolean isOpen = Boolean.TRUE.equals(isActive)
                        && (startTime == null || !startTime.after(refDate))
                        && (endTime == null || !endTime.before(refDate));

                    if (!forceAll && !isOpen) {
                        Integer existingCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + spec.detailTable + " WITH (NOLOCK) WHERE campaign_id = ?",
                            Integer.class, campaignId
                        );
                        if (existingCount != null && existingCount > 0) {
                            log.debug("Campaign {} already computed for {}. Skipping.", campaignId, spec.kpiCode);
                            continue;
                        }
                    }

                    if (isOpen && !isManualUpload && surveySyncScheduler != null) {
                        try {
                            log.info("Syncing latest responses for open survey {} (campaign {})...", surveyId, campaignId);
                            surveySyncScheduler.syncSurveyDetailsById(surveyId);
                        } catch (Exception ex) {
                            log.warn("Notice syncing responses from Slink API for survey {}: {}", surveyId, ex.getMessage());
                        }
                    }

                    PeriodResolution periodRes = resolvePeriodForCampaign(cRow, cycleId, refDate, spec.kpiCode);
                    if (periodRes == null || periodRes.periodId <= 0) {
                        continue;
                    }

                    for (QuestionMeta targetQuestion : targetQuestions) {
                        LikertCalculationResult calcRes = calculateCampaignLikertScores(surveyId, campaignId, targetQuestion, true);
                        if (calcRes.totalRespondents > 0) {
                            PeriodCampaignData pData = periodCampaignMap.computeIfAbsent(
                                periodRes.periodId,
                                k -> new PeriodCampaignData(periodRes.periodId, periodRes.periodCode)
                            );
                            pData.addCampaignResult(surveyId, surveyTitle, campaignId, campaignTitle, targetQuestion, isOpen ? "OPEN" : "CLOSED", calcRes);
                        }
                    }
                }
            }

            for (Map.Entry<Integer, PeriodCampaignData> entry : periodCampaignMap.entrySet()) {
                int periodId = entry.getKey();
                PeriodCampaignData pData = entry.getValue();

                double actualValue = pData.getOverallAverageScore();
                int totalResp = pData.getTotalRespondents();
                String periodCode = pData.periodCode;

                String notes = String.format(
                    "Tự động tính từ Khảo sát SV (%s): %d phản hồi, điểm TB %.2f/5.0 (%d đợt)",
                    periodCode, totalResp, actualValue, pData.campaignResults.size()
                );

                String checkExistSql = "SELECT TOP 1 data_id FROM kpi_data_points WITH (NOLOCK) WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
                List<Integer> existingDataIds = jdbcTemplate.query(checkExistSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

                int dataId;
                int statusId = (actualValue >= targetVal) ? 5 : 4;

                if (!existingDataIds.isEmpty()) {
                    dataId = existingDataIds.get(0);
                    jdbcTemplate.update(
                        "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), status_id = ?, notes = ? WHERE data_id = ?",
                        actualValue, statusId, toNVarChar(notes), dataId
                    );
                    log.info("Updated kpi_data_points (data_id: {}) for KPI {} period {}: {} Điểm", dataId, spec.kpiCode, periodCode, actualValue);
                } else {
                    jdbcTemplate.update(
                        "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes) VALUES (?, ?, ?, GETDATE(), ?, ?)",
                        kpiId, periodId, actualValue, statusId, toNVarChar(notes)
                    );
                    dataId = jdbcTemplate.queryForObject(checkExistSql, Integer.class, kpiId, periodId);
                    log.info("Inserted new kpi_data_points (data_id: {}) for KPI {} period {}: {} Điểm", dataId, spec.kpiCode, periodCode, actualValue);
                }

                for (CampaignItemResult cr : pData.campaignResults) {
                    upsertDetailRecord(spec.detailTable, spec.kpiCode, periodId, periodCode, cr);
                }

                JSONObject pObj = new JSONObject();
                pObj.put("period_id", periodId);
                pObj.put("period_code", periodCode);
                pObj.put("actual_value", actualValue);
                pObj.put("total_respondents", totalResp);
                pObj.put("notes", notes);
                processedPeriods.put(pObj);
            }

            jdbcTemplate.update(
                "UPDATE kpi_assignments SET last_synced_at = GETDATE(), sync_status = 'SUCCESS', sync_error_log = NULL " +
                "WHERE kpi_id = ? AND update_mode = 'AUTOMATED'",
                kpiId
            );

            result.put("status", "SUCCESS");
            result.put("kpi_code", spec.kpiCode);
            result.put("processed_periods", processedPeriods);
            result.put("total_periods_updated", processedPeriods.length());

            if (processedPeriods.length() > 0) {
                JSONObject latestPeriodObj = processedPeriods.getJSONObject(processedPeriods.length() - 1);
                result.put("actual_value", latestPeriodObj.optDouble("actual_value"));
                result.put("notes", latestPeriodObj.optString("notes"));
                result.put("period_id", latestPeriodObj.optInt("period_id"));
                result.put("period_code", latestPeriodObj.optString("period_code"));
            }

            if (kpiExtendService != null) {
                kpiExtendService.invalidateKpisWithAssignmentsCache();
            }

        } catch (Exception e) {
            log.error("Error calculating KPI {}: ", spec.kpiCode, e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi tính toán KPI " + spec.kpiCode + ": " + e.getMessage());

            try {
                jdbcTemplate.update(
                    "UPDATE kpi_assignments SET last_synced_at = GETDATE(), sync_status = 'ERROR', sync_error_log = ? " +
                    "WHERE kpi_id = (SELECT TOP 1 kpi_id FROM kpi_definitions WHERE kpi_code = ?) AND update_mode = 'AUTOMATED'",
                    toNVarChar(e.getMessage()), spec.kpiCode
                );
            } catch (Exception ignored) {}
        }

        return result;
    }

    public List<Map<String, Object>> findMatchingSurveys(String rawPrimaryPrefix, String rawFallbackPrefix) {
        String primaryPrefix = decodePropertyUtf8(rawPrimaryPrefix != null ? rawPrimaryPrefix.trim() : "");
        String fallbackPrefix = decodePropertyUtf8(rawFallbackPrefix != null ? rawFallbackPrefix.trim() : "");
        String primaryLike = primaryPrefix.isEmpty() ? N_NOOP_LIKE : primaryPrefix + "%";
        String fallbackLike = fallbackPrefix.isEmpty() ? N_NOOP_LIKE : fallbackPrefix + "%";

        String sql = "SELECT id, title, is_active, input_method FROM dbo.surveys WITH (NOLOCK) " +
                     "WHERE id = 'T1.07' " +
                     "   OR input_method = 'MANUAL_EXCEL' " +
                     "   OR title LIKE ? " +
                     "   OR title LIKE ? " +
                     "   OR (title LIKE N'%sinh viên%' AND title LIKE N'%chương trình đào tạo%') " +
                     "   OR (title LIKE N'%hài lòng%' AND (title LIKE N'%sinh viên%' OR title LIKE N'%SV%')) " +
                     "ORDER BY created_at DESC OPTION (MAXDOP 1)";

        return jdbcTemplate.queryForList(sql, toNVarChar(primaryLike), toNVarChar(fallbackLike));
    }

    private static final String N_NOOP_LIKE = "__NO_MATCH_PREFIX__%";

    public List<QuestionMeta> loadQuestionMetaList(List<Map<String, Object>> rows) {
        List<QuestionMeta> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String qId = (String) row.get("id");
            String qType = (String) row.get("question_type");
            String content = (String) row.get("content");

            Map<String, String> matrixRows = new LinkedHashMap<>();
            if ("GridSingleChoice".equalsIgnoreCase(qType) || "GridMultipleChoice".equalsIgnoreCase(qType)) {
                List<Map<String, Object>> mRows = jdbcTemplate.queryForList(
                    "SELECT id, content FROM dbo.question_matrix_rows WITH (NOLOCK) WHERE question_id = ? ORDER BY id OPTION (MAXDOP 1)",
                    qId
                );
                for (Map<String, Object> mr : mRows) {
                    matrixRows.put((String) mr.get("id"), (String) mr.get("content"));
                }
            }

            Map<String, String> matrixCols = loadMatrixCols(qId);
            results.add(new QuestionMeta(qId, qType, content, matrixRows, matrixCols));
        }
        return results;
    }

    public Map<String, String> loadMatrixCols(String questionId) {
        Map<String, String> matrixCols = new LinkedHashMap<>();
        List<Map<String, Object>> mCols = jdbcTemplate.queryForList(
            "SELECT id, content FROM dbo.question_matrix_cols WITH (NOLOCK) WHERE question_id = ? ORDER BY id OPTION (MAXDOP 1)",
            questionId
        );
        for (Map<String, Object> mc : mCols) {
            matrixCols.put((String) mc.get("id"), (String) mc.get("content"));
        }
        return matrixCols;
    }

    public PeriodResolution resolvePeriodForCampaign(Map<String, Object> campaign, int cycleId, Date defaultRefDate, String kpiCodeForLog) {
        String title = (String) campaign.get("title");
        String semName = (String) campaign.get("semester_name");
        String semCode = (String) campaign.get("semester_code");
        Timestamp startTime = (Timestamp) campaign.get("start_time");
        Timestamp endTime = (Timestamp) campaign.get("end_time");

        if (semCode != null && !semCode.trim().isEmpty()) {
            List<Map<String, Object>> directRows = jdbcTemplate.queryForList(
                "SELECT period_id, period_code FROM dbo.period_instances WITH (NOLOCK) WHERE cycle_id = ? AND period_code = ?",
                cycleId, semCode.trim()
            );
            if (!directRows.isEmpty()) {
                return new PeriodResolution(toInt(directRows.get(0).get("period_id"), 0), (String) directRows.get(0).get("period_code"));
            }
        }

        String fullText = String.format("%s %s %s", title != null ? title : "", semName != null ? semName : "", semCode != null ? semCode : "");

        int semesterNumber = 0;
        int startYear = 0;
        int endYear = 0;

        Matcher semMatcher = SEMESTER_PATTERN.matcher(fullText);
        if (semMatcher.find()) {
            semesterNumber = Integer.parseInt(semMatcher.group(1));
        } else if (semCode != null && semCode.matches("^\\d{4}([1-3])$")) {
            semesterNumber = Integer.parseInt(semCode.substring(semCode.length() - 1));
        }

        Matcher yearMatcher = YEAR_RANGE_PATTERN.matcher(fullText);
        if (yearMatcher.find()) {
            startYear = Integer.parseInt(yearMatcher.group(1));
            endYear = Integer.parseInt(yearMatcher.group(2));
        } else if (semCode != null && semCode.matches("^(\\d{4})[1-3]$")) {
            startYear = Integer.parseInt(semCode.substring(0, 4));
            endYear = startYear + 1;
        }

        Date dateForCheck = startTime != null ? startTime : (endTime != null ? endTime : defaultRefDate);
        LocalDate lDate = dateForCheck.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        if (startYear == 0) {
            int calYear = lDate.getYear();
            int month = lDate.getMonthValue();
            if (month >= 9) {
                startYear = calYear;
                endYear = calYear + 1;
            } else {
                startYear = calYear - 1;
                endYear = calYear;
            }
        }

        if (semesterNumber == 0) {
            int month = lDate.getMonthValue();
            if (month >= 9 || month == 1) {
                semesterNumber = 1;
            } else if (month >= 2 && month <= 5) {
                semesterNumber = 2;
            } else {
                semesterNumber = 3;
            }
        }

        String periodCode = String.format("TERM_%d-%d_HK%d", startYear, endYear, semesterNumber);

        List<Map<String, Object>> pRows = jdbcTemplate.queryForList(
            "SELECT period_id, period_code FROM dbo.period_instances WITH (NOLOCK) WHERE cycle_id = ? AND period_code = ?",
            cycleId, periodCode
        );

        if (!pRows.isEmpty()) {
            int pId = toInt(pRows.get(0).get("period_id"), 0);
            return new PeriodResolution(pId, periodCode);
        }

        try {
            LocalDate pStart;
            LocalDate pEnd;
            if (semesterNumber == 1) {
                pStart = LocalDate.of(startYear, 9, 1);
                pEnd = LocalDate.of(endYear, 1, 31);
            } else if (semesterNumber == 2) {
                pStart = LocalDate.of(endYear, 2, 1);
                pEnd = LocalDate.of(endYear, 5, 31);
            } else {
                pStart = LocalDate.of(endYear, 6, 1);
                pEnd = LocalDate.of(endYear, 8, 31);
            }

            jdbcTemplate.update(
                "INSERT INTO dbo.period_instances (cycle_id, period_code, start_date, end_date) VALUES (?, ?, ?, ?)",
                cycleId, periodCode, java.sql.Date.valueOf(pStart), java.sql.Date.valueOf(pEnd)
            );

            Integer newId = jdbcTemplate.queryForObject(
                "SELECT period_id FROM dbo.period_instances WITH (NOLOCK) WHERE cycle_id = ? AND period_code = ?",
                Integer.class, cycleId, periodCode
            );

            if (newId != null) {
                return new PeriodResolution(newId, periodCode);
            }
        } catch (Exception e) {
            log.warn("Notice during auto-provisioning period for {}: {}", kpiCodeForLog, e.getMessage());
        }

        if (kpiExtendService != null) {
            int fallbackId = kpiExtendService.getOrCreatePeriodInstance(cycleId, "term", dateForCheck);
            String fallbackCode = jdbcTemplate.queryForObject(
                "SELECT period_code FROM dbo.period_instances WITH (NOLOCK) WHERE period_id = ?",
                String.class, fallbackId
            );
            return new PeriodResolution(fallbackId, fallbackCode != null ? fallbackCode : periodCode);
        }

        return null;
    }

    public LikertCalculationResult calculateCampaignLikertScores(String surveyId, String campaignId, QuestionMeta qMeta, boolean filterByTargetMatrixRows) {
        Map<String, CriterionStats> criterionStatsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : qMeta.matrixRows.entrySet()) {
            criterionStatsMap.put(e.getKey(), new CriterionStats(e.getKey(), e.getValue()));
        }

        int totalRespondents = 0;
        double overallSum = 0.0;
        int totalItemRatings = 0;

        String sql = "SELECT a.choices FROM dbo.survey_responses r WITH (NOLOCK) " +
                     "INNER JOIN dbo.survey_response_answers a WITH (NOLOCK) ON a.response_id = r.id " +
                     "WHERE r.survey_id = CAST(? AS VARCHAR(100)) " +
                     "  AND r.campaign_id = CAST(? AS VARCHAR(100)) " +
                     "  AND a.question_id = CAST(? AS VARCHAR(100)) " +
                     "  AND a.choices IS NOT NULL " +
                     "OPTION (MAXDOP 1)";

        List<Map<String, Object>> answerRows = jdbcTemplate.queryForList(sql, surveyId, campaignId, qMeta.questionId);

        for (Map<String, Object> row : answerRows) {
            String choicesJson = (String) row.get("choices");
            if (choicesJson == null || choicesJson.trim().isEmpty()) continue;

            try {
                JSONArray arr = new JSONArray(choicesJson);
                if (arr.length() == 0) continue;

                boolean hasValidItem = false;
                for (int j = 0; j < arr.length(); j++) {
                    JSONObject item = arr.getJSONObject(j);
                    String textCot = item.optString("textCot", null);
                    String idHang = item.optString("idHang", null);
                    String textHang = item.optString("textHang", null);

                    if (filterByTargetMatrixRows && !qMeta.matrixRows.isEmpty()) {
                        boolean rowMatched = (idHang != null && !idHang.isEmpty() && qMeta.matrixRows.containsKey(idHang))
                            || (textHang != null && !textHang.isEmpty() && qMeta.matrixRows.containsValue(textHang));
                        if (!rowMatched) {
                            continue;
                        }
                    }

                    Double score = parseLikertScore(textCot, item.optString("idCot", null), qMeta.matrixCols);
                    if (score != null && score >= 1.0 && score <= 5.0) {
                        overallSum += score;
                        totalItemRatings++;
                        hasValidItem = true;

                        String critKey = idHang != null && !idHang.isEmpty() ? idHang : (textHang != null ? textHang : "item_" + j);
                        CriterionStats cs = criterionStatsMap.computeIfAbsent(
                            critKey,
                            k -> new CriterionStats(critKey, textHang != null ? textHang : critKey)
                        );
                        cs.addScore(score);
                    }
                }

                if (hasValidItem) {
                    totalRespondents++;
                }
            } catch (Exception ignored) {}
        }

        double overallAvg = totalItemRatings > 0 ? (overallSum / totalItemRatings) : 0.0;
        overallAvg = BigDecimal.valueOf(overallAvg).setScale(2, RoundingMode.HALF_UP).doubleValue();

        return new LikertCalculationResult(totalRespondents, totalItemRatings, overallAvg, overallSum, criterionStatsMap);
    }

    private void upsertDetailRecord(String detailTable, String kpiCode, int periodId, String periodCode, CampaignItemResult cr) {
        try {
            JSONArray itemsJson = new JSONArray();
            for (CriterionStats cs : cr.calcResult.criterionStatsMap.values()) {
                JSONObject cObj = new JSONObject();
                cObj.put("id", cs.id);
                cObj.put("name", cs.name);
                cObj.put("average_score", cs.getAverage());
                cObj.put("total_ratings", cs.count);
                cObj.put("distribution", cs.distribution);
                itemsJson.put(cObj);
            }

            String itemsJsonStr = itemsJson.toString();

            String checkSql = "SELECT COUNT(*) FROM " + detailTable + " WITH (NOLOCK) WHERE period_id = ? AND campaign_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, periodId, cr.campaignId);

            if (count != null && count > 0) {
                String updateSql = "UPDATE " + detailTable + " SET " +
                                   "period_code = ?, survey_id = ?, survey_title = ?, campaign_title = ?, " +
                                   "question_id = ?, question_title = ?, total_respondents = ?, average_score = ?, " +
                                   "items_breakdown_json = ?, campaign_status = ?, calculated_at = SYSDATETIME() " +
                                   "WHERE period_id = ? AND campaign_id = ?";
                jdbcTemplate.update(
                    updateSql, periodCode, cr.surveyId, toNVarChar(cr.surveyTitle), toNVarChar(cr.campaignTitle),
                    cr.targetQuestion.questionId, toNVarChar(cr.targetQuestion.content),
                    cr.calcResult.totalRespondents, cr.calcResult.overallAverage,
                    toNVarChar(itemsJsonStr), cr.campaignStatus, periodId, cr.campaignId
                );
            } else {
                String insertSql = "INSERT INTO " + detailTable + " " +
                                   "(period_id, period_code, survey_id, survey_title, campaign_id, campaign_title, " +
                                   "question_id, question_title, total_respondents, average_score, items_breakdown_json, campaign_status, calculated_at) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATETIME())";
                jdbcTemplate.update(
                    insertSql, periodId, periodCode, cr.surveyId, toNVarChar(cr.surveyTitle), cr.campaignId, toNVarChar(cr.campaignTitle),
                    cr.targetQuestion.questionId, toNVarChar(cr.targetQuestion.content),
                    cr.calcResult.totalRespondents, cr.calcResult.overallAverage,
                    toNVarChar(itemsJsonStr), cr.campaignStatus
                );
            }
        } catch (Exception e) {
            log.warn("Error upserting {} detail record for campaign {}: {}", kpiCode, cr.campaignId, e.getMessage());
        }
    }

    public JSONObject getBreakdown(String detailTable, String kpiCode, String periodCode) {
        JSONObject res = new JSONObject();
        try {
            String sql = "SELECT * FROM " + detailTable + " WITH (NOLOCK) ";
            List<Map<String, Object>> rows;
            if (periodCode != null && !periodCode.trim().isEmpty()) {
                sql += "WHERE period_code = ? ORDER BY calculated_at DESC";
                rows = jdbcTemplate.queryForList(sql, periodCode.trim());
            } else {
                sql += "ORDER BY period_code DESC, calculated_at DESC";
                rows = jdbcTemplate.queryForList(sql);
            }

            JSONArray details = new JSONArray();
            for (Map<String, Object> r : rows) {
                JSONObject d = new JSONObject();
                d.put("id", r.get("id"));
                d.put("period_id", r.get("period_id"));
                d.put("period_code", r.get("period_code"));
                d.put("survey_id", r.get("survey_id"));
                d.put("survey_title", r.get("survey_title"));
                d.put("campaign_id", r.get("campaign_id"));
                d.put("campaign_title", r.get("campaign_title"));
                d.put("question_id", r.get("question_id"));
                d.put("question_title", r.get("question_title"));
                d.put("total_respondents", r.get("total_respondents"));
                d.put("average_score", r.get("average_score"));
                d.put("campaign_status", r.get("campaign_status"));
                d.put("calculated_at", r.get("calculated_at") != null ? r.get("calculated_at").toString() : null);

                String jsonStr = (String) r.get("items_breakdown_json");
                if (jsonStr != null && !jsonStr.isEmpty()) {
                    try { d.put("items_breakdown", new JSONArray(jsonStr)); } catch (Exception ignored) {}
                }
                details.put(d);
            }

            res.put("status", "SUCCESS");
            res.put("kpi_code", kpiCode);
            res.put("data", details);
        } catch (Exception e) {
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
        }
        return res;
    }

    // --- Shared DTOs ---

    public static class QuestionMeta {
        public final String questionId;
        public final String questionType;
        public final String content;
        public final Map<String, String> matrixRows;
        public final Map<String, String> matrixCols;

        public QuestionMeta(String questionId, String questionType, String content, Map<String, String> matrixRows, Map<String, String> matrixCols) {
            this.questionId = questionId;
            this.questionType = questionType;
            this.content = content;
            this.matrixRows = matrixRows;
            this.matrixCols = matrixCols;
        }
    }

    public static class PeriodResolution {
        public final int periodId;
        public final String periodCode;

        public PeriodResolution(int periodId, String periodCode) {
            this.periodId = periodId;
            this.periodCode = periodCode;
        }
    }

    public static class LikertCalculationResult {
        public final int totalRespondents;
        public final int totalItemRatings;
        public final double overallAverage;
        public final double overallSum;
        public final Map<String, CriterionStats> criterionStatsMap;

        public LikertCalculationResult(int totalRespondents, int totalItemRatings, double overallAverage, Map<String, CriterionStats> criterionStatsMap) {
            this(totalRespondents, totalItemRatings, overallAverage, overallAverage * totalItemRatings, criterionStatsMap);
        }

        public LikertCalculationResult(int totalRespondents, int totalItemRatings, double overallAverage, double overallSum, Map<String, CriterionStats> criterionStatsMap) {
            this.totalRespondents = totalRespondents;
            this.totalItemRatings = totalItemRatings;
            this.overallAverage = overallAverage;
            this.overallSum = overallSum;
            this.criterionStatsMap = criterionStatsMap;
        }
    }

    public static class CriterionStats {
        public final String id;
        public final String name;
        public double sum = 0.0;
        public int count = 0;
        public final Map<Integer, Integer> distribution = new LinkedHashMap<>();

        public CriterionStats(String id, String name) {
            this.id = id;
            this.name = name;
            for (int i = 1; i <= 5; i++) distribution.put(i, 0);
        }

        public void addScore(double score) {
            sum += score;
            count++;
            int intScore = (int) Math.round(score);
            if (intScore >= 1 && intScore <= 5) {
                distribution.put(intScore, distribution.get(intScore) + 1);
            }
        }

        public double getAverage() {
            if (count == 0) return 0.0;
            return BigDecimal.valueOf(sum / count).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }

    private static class CampaignItemResult {
        public final String surveyId;
        public final String surveyTitle;
        public final String campaignId;
        public final String campaignTitle;
        public final QuestionMeta targetQuestion;
        public final String campaignStatus;
        public final LikertCalculationResult calcResult;

        public CampaignItemResult(String surveyId, String surveyTitle, String campaignId, String campaignTitle, QuestionMeta targetQuestion, String campaignStatus, LikertCalculationResult calcResult) {
            this.surveyId = surveyId;
            this.surveyTitle = surveyTitle;
            this.campaignId = campaignId;
            this.campaignTitle = campaignTitle;
            this.targetQuestion = targetQuestion;
            this.campaignStatus = campaignStatus;
            this.calcResult = calcResult;
        }
    }

    private static class PeriodCampaignData {
        public final int periodId;
        public final String periodCode;
        public final List<CampaignItemResult> campaignResults = new ArrayList<>();

        public PeriodCampaignData(int periodId, String periodCode) {
            this.periodId = periodId;
            this.periodCode = periodCode;
        }

        public void addCampaignResult(String surveyId, String surveyTitle, String campaignId, String campaignTitle, QuestionMeta targetQuestion, String campaignStatus, LikertCalculationResult calcResult) {
            campaignResults.add(new CampaignItemResult(surveyId, surveyTitle, campaignId, campaignTitle, targetQuestion, campaignStatus, calcResult));
        }

        public int getTotalRespondents() {
            int sum = 0;
            for (CampaignItemResult cr : campaignResults) sum += cr.calcResult.totalRespondents;
            return sum;
        }

        public double getOverallAverageScore() {
            double totalScoreSum = 0.0;
            int totalRatings = 0;
            for (CampaignItemResult cr : campaignResults) {
                totalScoreSum += cr.calcResult.overallSum;
                totalRatings += cr.calcResult.totalItemRatings;
            }
            if (totalRatings == 0) return 0.0;
            return BigDecimal.valueOf(totalScoreSum / totalRatings).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }
}
