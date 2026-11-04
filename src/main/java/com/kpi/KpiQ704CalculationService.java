package com.kpi;

import com.surveys.SurveySyncScheduler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KpiQ704CalculationService - Automated Calculation Service for KPI Q7.04
 * "Mức độ hài lòng cán bộ về môi trường làm việc" (Thang điểm khảo sát 1–5)
 * 
 * Future-Proof & Generic Architecture:
 * 1. Checks if a dedicated survey for all staff / employees exists:
 *    - Prefix / keyword matching: "Phiếu khảo sát cán bộ về môi trường làm việc", "khảo sát người lao động", etc.
 * 2. If dedicated staff survey is found:
 *    - Calculates satisfaction scores directly from staff survey campaigns.
 * 3. Fallback Mapping (current state when dedicated staff survey is not yet available):
 *    - Maps dynamically to the lecturer satisfaction survey ("Phiếu khảo sát giảng viên về CTĐT và điều kiện đảm bảo chất lượng").
 *    - Uses question "Điều kiện đảm bảo chất lượng giảng dạy" (measuring facilities, work environment, equipment).
 *    - Transparently tags notes: "[tạm đồng bộ trong khi chờ khảo sát toàn thể cán bộ]".
 *    - When the university launches the all-employee survey in future cycles, it will automatically switch without code changes.
 * 4. Ultra-fast index-seek join with OPTION (MAXDOP 1).
 */
@Service
public class KpiQ704CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiQ704CalculationService.class);

    private static final String KPI_CODE = "Q7.04";
    private static final String FALLBACK_KPI_CODE = "G2.09";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private kpiExtend kpiExtendService;

    @Autowired(required = false)
    private SurveySyncScheduler surveySyncScheduler;

    // Dedicated staff survey prefix (when available in future)
    @Value("${slink.survey.q704.staff-survey-prefix:Phiếu khảo sát cán bộ về môi trường làm việc}")
    private String staffSurveyPrefix;

    @Value("${slink.survey.q704.staff-question-keyword:Môi trường làm việc}")
    private String staffQuestionKeyword;

    // Fallback lecturer survey prefix (current source)
    @Value("${slink.survey.g209.survey-prefix:Phiếu khảo sát giảng viên về CTĐT và điều kiện đảm bảo chất lượng}")
    private String fallbackSurveyPrefix;

    @Value("${slink.survey.g209.question-keyword:Điều kiện đảm bảo chất lượng giảng dạy}")
    private String fallbackQuestionKeyword;

    private Object toNVarChar(String val) {
        return val == null ? null : new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NVARCHAR, val);
    }

    private int toInt(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double toDouble(Object obj, double defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Scheduled Daily Run for KPI Q7.04 calculation.
     * Runs at 01:15 AM every day.
     */
    @Scheduled(cron = "${slink.survey.q704.cron:0 15 1 * * ?}")
    public void scheduledDailyQ704Calculation() {
        log.info("Executing scheduled daily calculation for KPI Q7.04 (Mức độ hài lòng cán bộ về môi trường làm việc)...");
        try {
            calculateAndSaveQ704(null, false);
        } catch (Exception e) {
            log.error("Error in scheduled daily Q7.04 calculation: ", e);
        }
    }

    /**
     * Calculate and save KPI Q7.04 data points into kpi_data_points and kpi_q704_survey_details.
     *
     * @param referenceDate Optional reference date
     * @param forceAll If true, recalculates even closed campaigns
     * @return JSONObject containing status and summary of calculated periods
     */
    public JSONObject calculateAndSaveQ704(Date referenceDate, boolean forceAll) {
        JSONObject result = new JSONObject();
        JSONArray processedPeriods = new JSONArray();

        try {
            Date refDate = referenceDate != null ? referenceDate : new Date();

            // 1. Fetch KPI definition for Q7.04
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT kpi_id, cycle_id, target FROM kpi_definitions WHERE kpi_code = ? AND (is_deleted = 0 OR is_deleted IS NULL)",
                KPI_CODE
            );

            if (kpiRows.isEmpty()) {
                log.warn("KPI Q7.04 definition not found in kpi_definitions table.");
                result.put("status", "ERROR");
                result.put("message", "KPI Q7.04 definition not found in kpi_definitions.");
                return result;
            }

            int kpiId = toInt(kpiRows.get(0).get("kpi_id"), 53);
            int cycleId = toInt(kpiRows.get(0).get("cycle_id"), 6);
            double targetVal = toDouble(kpiRows.get(0).get("target"), 3.5);

            // 2. Determine survey source: check for dedicated staff survey first
            boolean isUsingDedicatedStaffSurvey = false;
            String activeSurveyPrefix = staffSurveyPrefix.trim();
            String activeQuestionKeyword = staffQuestionKeyword.trim();

            List<Map<String, Object>> matchingSurveys = jdbcTemplate.queryForList(
                "SELECT id, title, is_active FROM dbo.surveys WITH (NOLOCK) WHERE title LIKE ? OR (title LIKE '%cán bộ%' AND title LIKE '%môi trường làm việc%') ORDER BY created_at DESC",
                activeSurveyPrefix + "%"
            );

            if (!matchingSurveys.isEmpty()) {
                isUsingDedicatedStaffSurvey = true;
                log.info("Found {} dedicated staff survey(s) matching prefix '{}'", matchingSurveys.size(), activeSurveyPrefix);
            } else {
                // Fallback to lecturer survey as instructed by user
                activeSurveyPrefix = fallbackSurveyPrefix.trim();
                activeQuestionKeyword = fallbackQuestionKeyword.trim();
                matchingSurveys = jdbcTemplate.queryForList(
                    "SELECT id, title, is_active FROM dbo.surveys WITH (NOLOCK) WHERE title LIKE ? ORDER BY created_at DESC",
                    activeSurveyPrefix + "%"
                );
                log.info("Dedicated staff survey not found. Falling back to lecturer survey prefix '{}' ({} surveys found)", activeSurveyPrefix, matchingSurveys.size());
            }

            if (matchingSurveys.isEmpty()) {
                log.warn("No surveys found matching prefix: '{}'", activeSurveyPrefix);
                result.put("status", "WARNING");
                result.put("message", "Không tìm thấy phiếu khảo sát nào bắt đầu bằng: " + activeSurveyPrefix);
                return result;
            }

            // Group campaigns by resolved periodId
            Map<Integer, PeriodCampaignData> periodCampaignMap = new LinkedHashMap<>();

            for (Map<String, Object> sRow : matchingSurveys) {
                String surveyId = (String) sRow.get("id");
                String surveyTitle = (String) sRow.get("title");

                List<Map<String, Object>> campaigns = jdbcTemplate.queryForList(
                    "SELECT id, survey_id, title, semester_code, semester_name, start_time, end_time, is_active " +
                    "FROM dbo.survey_campaigns WITH (NOLOCK) WHERE survey_id = ? ORDER BY start_time DESC",
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
                            "SELECT COUNT(*) FROM dbo.kpi_q704_survey_details WHERE campaign_id = ?",
                            Integer.class, campaignId
                        );
                        if (existingCount != null && existingCount > 0) {
                            log.debug("Campaign {} already closed and computed for Q7.04. Skipping daily re-sync.", campaignId);
                            continue;
                        }
                    }

                    if (isOpen && surveySyncScheduler != null) {
                        try {
                            log.info("Syncing latest responses for open survey {} (campaign {})...", surveyId, campaignId);
                            surveySyncScheduler.syncSurveyDetailsById(surveyId);
                        } catch (Exception ex) {
                            log.warn("Notice syncing responses from Slink API for survey {}: {}", surveyId, ex.getMessage());
                        }
                    }

                    PeriodResolution periodRes = resolvePeriodForCampaign(cRow, cycleId, refDate);
                    if (periodRes == null || periodRes.periodId <= 0) {
                        log.warn("Could not resolve period for campaign {} ({})", campaignId, campaignTitle);
                        continue;
                    }

                    QuestionMeta targetQuestion = findTargetQuestion(surveyId, activeQuestionKeyword);
                    if (targetQuestion == null) {
                        log.warn("Target question matching '{}' not found in survey {}", activeQuestionKeyword, surveyId);
                        continue;
                    }

                    LikertCalculationResult calcRes = calculateCampaignLikertScores(surveyId, campaignId, targetQuestion);

                    if (calcRes.totalRespondents > 0) {
                        PeriodCampaignData pData = periodCampaignMap.computeIfAbsent(
                            periodRes.periodId,
                            k -> new PeriodCampaignData(periodRes.periodId, periodRes.periodCode)
                        );
                        pData.addCampaignResult(surveyId, surveyTitle, campaignId, campaignTitle, targetQuestion, isOpen ? "OPEN" : "CLOSED", calcRes);
                    }
                }
            }

            // 3. Persist aggregated results into kpi_data_points and kpi_q704_survey_details
            for (Map.Entry<Integer, PeriodCampaignData> entry : periodCampaignMap.entrySet()) {
                int periodId = entry.getKey();
                PeriodCampaignData pData = entry.getValue();

                double actualValue = pData.getOverallAverageScore();
                int totalResp = pData.getTotalRespondents();
                String periodCode = pData.periodCode;

                String sourceNoteTag = isUsingDedicatedStaffSurvey 
                    ? "Khảo sát Cán bộ toàn Học viện"
                    : "Khảo sát GV (tạm đồng bộ trong khi chờ khảo sát toàn thể CB)";

                String notes = String.format(
                    "Tự động tính từ %s (%s): %d phản hồi, điểm TB %.2f/5.0 (%d đợt)",
                    sourceNoteTag, periodCode, totalResp, actualValue, pData.campaignResults.size()
                );

                // Upsert kpi_data_points for Q7.04
                String checkExistSql = "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ? AND department_id IS NULL ORDER BY data_id DESC";
                List<Integer> existingDataIds = jdbcTemplate.query(checkExistSql, (rs, rowNum) -> rs.getInt("data_id"), kpiId, periodId);

                int dataId;
                int statusId = (actualValue >= targetVal) ? 5 : 4; // 5: Achieved, 4: Warning

                if (!existingDataIds.isEmpty()) {
                    dataId = existingDataIds.get(0);
                    String updateSql = "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), status_id = ?, notes = ? WHERE data_id = ?";
                    jdbcTemplate.update(updateSql, actualValue, statusId, toNVarChar(notes), dataId);
                    log.info("Updated kpi_data_points (data_id: {}) for KPI Q7.04 period {}: {} Điểm", dataId, periodCode, actualValue);
                } else {
                    String insertSql = "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, notes) VALUES (?, ?, ?, GETDATE(), ?, ?)";
                    jdbcTemplate.update(insertSql, kpiId, periodId, actualValue, statusId, toNVarChar(notes));
                    dataId = jdbcTemplate.queryForObject(checkExistSql, Integer.class, kpiId, periodId);
                    log.info("Inserted new kpi_data_points (data_id: {}) for KPI Q7.04 period {}: {} Điểm", dataId, periodCode, actualValue);
                }

                // Upsert into kpi_q704_survey_details
                for (CampaignItemResult cr : pData.campaignResults) {
                    upsertDetailRecord(periodId, periodCode, cr);
                }

                JSONObject pObj = new JSONObject();
                pObj.put("period_id", periodId);
                pObj.put("period_code", periodCode);
                pObj.put("actual_value", actualValue);
                pObj.put("total_respondents", totalResp);
                pObj.put("is_dedicated_staff_survey", isUsingDedicatedStaffSurvey);
                pObj.put("notes", notes);
                processedPeriods.put(pObj);
            }

            // 4. Update kpi_assignments sync tracking
            jdbcTemplate.update(
                "UPDATE kpi_assignments SET last_synced_at = GETDATE(), sync_status = 'SUCCESS', sync_error_log = NULL " +
                "WHERE kpi_id = ? AND update_mode = 'AUTOMATED'",
                kpiId
            );

            result.put("status", "SUCCESS");
            result.put("kpi_code", KPI_CODE);
            result.put("is_dedicated_staff_survey", isUsingDedicatedStaffSurvey);
            result.put("processed_periods", processedPeriods);
            result.put("total_periods_updated", processedPeriods.length());

        } catch (Exception e) {
            log.error("Error calculating KPI Q7.04: ", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi tính toán KPI Q7.04: " + e.getMessage());

            try {
                jdbcTemplate.update(
                    "UPDATE kpi_assignments SET last_synced_at = GETDATE(), sync_status = 'ERROR', sync_error_log = ? " +
                    "WHERE kpi_id = (SELECT TOP 1 kpi_id FROM kpi_definitions WHERE kpi_code = 'Q7.04') AND update_mode = 'AUTOMATED'",
                    toNVarChar(e.getMessage())
                );
            } catch (Exception ignored) {}
        }

        return result;
    }

    /**
     * Dynamically finds the target question matching keyword in survey blocks.
     */
    public QuestionMeta findTargetQuestion(String surveyId, String keyword) {
        String sql = "SELECT q.id, q.block_id, q.question_type, q.content, b.title as block_title " +
                     "FROM dbo.survey_questions q WITH (NOLOCK) " +
                     "JOIN dbo.survey_blocks b WITH (NOLOCK) ON q.block_id = b.id " +
                     "WHERE b.survey_id = ? AND (q.content LIKE ? OR q.content LIKE ? OR q.content LIKE ?) " +
                     "OPTION (MAXDOP 1)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            sql, surveyId, "%" + keyword + "%", "%môi trường làm việc%", "%Điều kiện đảm bảo chất lượng%"
        );

        if (rows.isEmpty()) return null;

        Map<String, Object> row = rows.get(0);
        String qId = (String) row.get("id");
        String qType = (String) row.get("question_type");
        String content = (String) row.get("content");

        Map<String, String> matrixRows = new LinkedHashMap<>();
        if ("GridSingleChoice".equalsIgnoreCase(qType) || "GridMultipleChoice".equalsIgnoreCase(qType)) {
            List<Map<String, Object>> mRows = jdbcTemplate.queryForList(
                "SELECT id, content FROM dbo.question_matrix_rows WITH (NOLOCK) WHERE question_id = ? ORDER BY id OPTION (MAXDOP 1)", qId
            );
            for (Map<String, Object> mr : mRows) {
                matrixRows.put((String) mr.get("id"), (String) mr.get("content"));
            }
        }

        Map<String, String> matrixCols = new LinkedHashMap<>();
        List<Map<String, Object>> mCols = jdbcTemplate.queryForList(
            "SELECT id, content FROM dbo.question_matrix_cols WITH (NOLOCK) WHERE question_id = ? ORDER BY id OPTION (MAXDOP 1)", qId
        );
        for (Map<String, Object> mc : mCols) {
            matrixCols.put((String) mc.get("id"), (String) mc.get("content"));
        }

        return new QuestionMeta(qId, qType, content, matrixRows, matrixCols);
    }

    /**
     * Resolves and auto-provisions period_instances for a campaign.
     */
    public PeriodResolution resolvePeriodForCampaign(Map<String, Object> campaign, int cycleId, Date defaultRefDate) {
        String title = (String) campaign.get("title");
        String semName = (String) campaign.get("semester_name");
        String semCode = (String) campaign.get("semester_code");
        Timestamp startTime = (Timestamp) campaign.get("start_time");
        Timestamp endTime = (Timestamp) campaign.get("end_time");

        String fullText = String.format("%s %s %s", title != null ? title : "", semName != null ? semName : "", semCode != null ? semCode : "");

        int semesterNumber = 0;
        int startYear = 0;
        int endYear = 0;

        Matcher semMatcher = Pattern.compile("(?i)(?:Học\\s*kỳ|Kỳ|HK)\\s*([1-3])").matcher(fullText);
        if (semMatcher.find()) {
            semesterNumber = Integer.parseInt(semMatcher.group(1));
        } else if (semCode != null && semCode.matches("^\\d{4}([1-3])$")) {
            semesterNumber = Integer.parseInt(semCode.substring(semCode.length() - 1));
        }

        Matcher yearMatcher = Pattern.compile("(\\d{4})\\s*[-–/]\\s*(\\d{4})").matcher(fullText);
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
            "SELECT period_id, period_code FROM dbo.period_instances WHERE cycle_id = ? AND period_code = ?",
            cycleId, periodCode
        );

        if (!pRows.isEmpty()) {
            int pId = toInt(pRows.get(0).get("period_id"), 0);
            return new PeriodResolution(pId, periodCode);
        }

        // Auto-provision if missing
        log.info("Period code {} not found in period_instances. Auto-provisioning for cycle {}...", periodCode, cycleId);
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
                "SELECT period_id FROM dbo.period_instances WHERE cycle_id = ? AND period_code = ?",
                Integer.class, cycleId, periodCode
            );

            if (newId != null) {
                log.info("Successfully auto-provisioned period_instance id {} for {}", newId, periodCode);
                return new PeriodResolution(newId, periodCode);
            }
        } catch (Exception e) {
            log.warn("Notice during auto-provisioning period: {}", e.getMessage());
        }

        int fallbackId = kpiExtendService.getOrCreatePeriodInstance(cycleId, "term", dateForCheck);
        String fallbackCode = jdbcTemplate.queryForObject(
            "SELECT period_code FROM dbo.period_instances WHERE period_id = ?",
            String.class, fallbackId
        );
        return new PeriodResolution(fallbackId, fallbackCode != null ? fallbackCode : periodCode);
    }

    /**
     * Calculates Likert ratings for the target question using indexed seek query with MAXDOP 1.
     */
    public LikertCalculationResult calculateCampaignLikertScores(String surveyId, String campaignId, QuestionMeta qMeta) {
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

                    Double score = parseLikertScore(textCot, item.optString("idCot", null), qMeta);
                    if (score != null && score >= 1.0 && score <= 5.0) {
                        overallSum += score;
                        totalItemRatings++;
                        hasValidItem = true;

                        String critKey = idHang != null && !idHang.isEmpty() ? idHang : (textHang != null ? textHang : "unknown_" + j);
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

        return new LikertCalculationResult(totalRespondents, totalItemRatings, overallAvg, criterionStatsMap);
    }

    private Double parseLikertScore(String textCot, String idCot, QuestionMeta qMeta) {
        if (textCot != null && !textCot.trim().isEmpty()) {
            String s = textCot.trim();

            Matcher m = Pattern.compile("^([1-5])\\s*=").matcher(s);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }

            String lower = s.toLowerCase();
            if (lower.contains("rất phù hợp") || lower.contains("rất hài lòng") || lower.contains("hoàn toàn đồng ý") || lower.contains("xuất sắc") || lower.contains("rất tốt")) {
                return 5.0;
            }
            if (lower.contains("chưa phù hợp") || lower.contains("ít phù hợp") || lower.contains("không hài lòng") || lower.contains("không đồng ý") || lower.contains("kém")) {
                return 2.0;
            }
            if (lower.contains("hoàn toàn không phù hợp") || lower.contains("rất không hài lòng") || lower.contains("rất kém")) {
                return 1.0;
            }
            if (lower.contains("phù hợp 1 phần") || lower.contains("bình thường") || lower.contains("trung bình") || lower.contains("tương đối")) {
                return 3.0;
            }
            if (lower.contains("phù hợp") || lower.contains("hài lòng") || lower.contains("đồng ý") || lower.contains("tốt")) {
                return 4.0;
            }

            try {
                double val = Double.parseDouble(s);
                if (val >= 1.0 && val <= 5.0) return val;
            } catch (Exception ignored) {}
        }

        if (idCot != null && qMeta.matrixCols.containsKey(idCot)) {
            String colContent = qMeta.matrixCols.get(idCot);
            return parseLikertScore(colContent, null, qMeta);
        }

        return null;
    }

    private void upsertDetailRecord(int periodId, String periodCode, CampaignItemResult cr) {
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

            String checkSql = "SELECT COUNT(*) FROM dbo.kpi_q704_survey_details WHERE period_id = ? AND campaign_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, periodId, cr.campaignId);

            if (count != null && count > 0) {
                String updateSql = "UPDATE dbo.kpi_q704_survey_details SET " +
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
                String insertSql = "INSERT INTO dbo.kpi_q704_survey_details " +
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
            log.warn("Error upserting detail record for campaign {}: {}", cr.campaignId, e.getMessage());
        }
    }

    /**
     * Retrieves stored breakdown records for UI rendering.
     */
    public JSONObject getBreakdown(String periodCode) {
        JSONObject res = new JSONObject();
        try {
            String sql = "SELECT * FROM dbo.kpi_q704_survey_details WITH (NOLOCK) ";
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
            res.put("data", details);
        } catch (Exception e) {
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
        }
        return res;
    }

    // Helper classes
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
        public final Map<String, CriterionStats> criterionStatsMap;

        public LikertCalculationResult(int totalRespondents, int totalItemRatings, double overallAverage, Map<String, CriterionStats> criterionStatsMap) {
            this.totalRespondents = totalRespondents;
            this.totalItemRatings = totalItemRatings;
            this.overallAverage = overallAverage;
            this.criterionStatsMap = criterionStatsMap;
        }
    }

    public static class CriterionStats {
        public final String id;
        public final String name;
        public int count = 0;
        public double sum = 0.0;
        public final Map<Integer, Integer> distribution = new TreeMap<>();

        public CriterionStats(String id, String name) {
            this.id = id;
            this.name = name;
            for (int i = 1; i <= 5; i++) distribution.put(i, 0);
        }

        public void addScore(double score) {
            count++;
            sum += score;
            int rounded = (int) Math.round(score);
            distribution.put(rounded, distribution.getOrDefault(rounded, 0) + 1);
        }

        public double getAverage() {
            if (count == 0) return 0.0;
            return BigDecimal.valueOf(sum / count).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }

    public static class CampaignItemResult {
        public final String surveyId;
        public final String surveyTitle;
        public final String campaignId;
        public final String campaignTitle;
        public final QuestionMeta targetQuestion;
        public final String campaignStatus;
        public final LikertCalculationResult calcResult;

        public CampaignItemResult(String surveyId, String surveyTitle, String campaignId, String campaignTitle,
                                  QuestionMeta targetQuestion, String campaignStatus, LikertCalculationResult calcResult) {
            this.surveyId = surveyId;
            this.surveyTitle = surveyTitle;
            this.campaignId = campaignId;
            this.campaignTitle = campaignTitle;
            this.targetQuestion = targetQuestion;
            this.campaignStatus = campaignStatus;
            this.calcResult = calcResult;
        }
    }

    public static class PeriodCampaignData {
        public final int periodId;
        public final String periodCode;
        public final List<CampaignItemResult> campaignResults = new ArrayList<>();

        public PeriodCampaignData(int periodId, String periodCode) {
            this.periodId = periodId;
            this.periodCode = periodCode;
        }

        public void addCampaignResult(String surveyId, String surveyTitle, String campaignId, String campaignTitle,
                                      QuestionMeta targetQuestion, String campaignStatus, LikertCalculationResult calcResult) {
            campaignResults.add(new CampaignItemResult(surveyId, surveyTitle, campaignId, campaignTitle, targetQuestion, campaignStatus, calcResult));
        }

        public double getOverallAverageScore() {
            int totalRatings = 0;
            double sumRatings = 0.0;
            for (CampaignItemResult cr : campaignResults) {
                totalRatings += cr.calcResult.totalItemRatings;
                sumRatings += (cr.calcResult.overallAverage * cr.calcResult.totalItemRatings);
            }
            if (totalRatings == 0) return 0.0;
            return BigDecimal.valueOf(sumRatings / totalRatings).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }

        public int getTotalRespondents() {
            int total = 0;
            for (CampaignItemResult cr : campaignResults) {
                total += cr.calcResult.totalRespondents;
            }
            return total;
        }
    }
}
