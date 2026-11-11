package com.kpi;

import com.kpi.StudentSurveyKpiSupport.KpiSpec;
import com.kpi.StudentSurveyKpiSupport.QuestionMeta;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * KpiH405CalculationService - Automated Calculation Service for KPI H4.05
 * "Mức độ hài lòng về phòng máy, thí nghiệm" (Thang điểm khảo sát 1–5).
 * Prioritizes explicit lab/computer-room rows (section 2.12 row 2: "phòng thí nghiệm"),
 * falling back to broader facilities rows only when a survey has no lab-specific item.
 */
@Service
public class KpiH405CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiH405CalculationService.class);
    private static final String KPI_CODE = "H4.05";
    private static final String DETAIL_TABLE = "dbo.kpi_h405_survey_details";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudentSurveyKpiSupport support;

    @Value("${slink.survey.h405.survey-prefix:Phiếu khảo sát sinh viên về chương trình đào tạo}")
    private String surveyPrefix;

    @Value("${slink.survey.h405.fallback-survey-prefix:Khảo sát mức độ hài lòng của sinh viên}")
    private String fallbackSurveyPrefix;

    @Value("${slink.survey.h405.row-keyword:cơ sở vật chất}")
    private String rowKeyword;

    /**
     * Scheduled Daily Run for KPI H4.05 calculation (01:35 AM daily).
     */
    @Scheduled(cron = "${slink.survey.h405.cron:0 35 1 * * ?}")
    public void scheduledDailyH405Calculation() {
        log.info("Executing scheduled daily calculation for KPI H4.05 (Mức độ hài lòng về phòng máy, thí nghiệm)...");
        try {
            calculateAndSaveH405(null, false);
        } catch (Exception e) {
            log.error("Error in scheduled daily H4.05 calculation: ", e);
        }
    }

    /**
     * Calculate and save KPI H4.05 data points into kpi_data_points and kpi_h405_survey_details.
     */
    public JSONObject calculateAndSaveH405(Date referenceDate, boolean forceAll) {
        KpiSpec spec = new KpiSpec(
            KPI_CODE, 36, 6, 3.5, DETAIL_TABLE, surveyPrefix, fallbackSurveyPrefix
        );
        return support.calculateAndSave(spec, this::findTargetQuestions, referenceDate, forceAll);
    }

    public List<QuestionMeta> findTargetQuestions(String surveyId) {
        String keyword = StudentSurveyKpiSupport.decodePropertyUtf8(
            rowKeyword != null && !rowKeyword.trim().isEmpty() ? rowKeyword.trim() : "cơ sở vật chất"
        );
        String sql = "SELECT q.id, q.block_id, q.question_type, q.content, b.title as block_title " +
                     "FROM dbo.survey_questions q WITH (NOLOCK) " +
                     "JOIN dbo.survey_blocks b WITH (NOLOCK) ON q.block_id = b.id " +
                     "WHERE b.survey_id = ? AND (q.content LIKE ? OR q.content LIKE N'%phòng máy%' OR q.content LIKE N'%thí nghiệm%' " +
                     "   OR q.content LIKE N'%chất lượng phục vụ%' OR q.content LIKE N'%phòng, trung tâm, dịch vụ hỗ trợ%' " +
                     "   OR q.content LIKE N'%Điều kiện học tập%' OR q.content LIKE N'%cơ sở vật chất, hạ tầng%') " +
                     "OPTION (MAXDOP 1)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            sql, surveyId, StudentSurveyKpiSupport.toNVarChar("%" + keyword + "%")
        );

        List<QuestionMeta> strictLabResults = new ArrayList<>();
        List<QuestionMeta> fallbackFacilityResults = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String qId = (String) row.get("id");
            String qType = (String) row.get("question_type");
            String content = (String) row.get("content");
            String contentLower = content != null ? content.toLowerCase(Locale.ROOT) : "";

            boolean isGrid = "GridSingleChoice".equalsIgnoreCase(qType) || "GridMultipleChoice".equalsIgnoreCase(qType);
            boolean isStrictLabQuestion = contentLower.contains("phòng máy") || contentLower.contains("thí nghiệm");
            boolean isBroadQuestion = contentLower.contains("điều kiện học tập")
                || contentLower.contains("cơ sở vật chất, hạ tầng")
                || contentLower.contains("chất lượng phục vụ")
                || contentLower.contains("dịch vụ hỗ trợ");

            Map<String, String> strictRows = new LinkedHashMap<>();
            Map<String, String> fallbackRows = new LinkedHashMap<>();
            Map<String, String> matrixCols = support.loadMatrixCols(qId);

            if (isGrid) {
                List<Map<String, Object>> mRows = jdbcTemplate.queryForList(
                    "SELECT id, content FROM dbo.question_matrix_rows WITH (NOLOCK) WHERE question_id = ? ORDER BY id OPTION (MAXDOP 1)", qId
                );
                for (Map<String, Object> mr : mRows) {
                    String rId = (String) mr.get("id");
                    String rContent = (String) mr.get("content");
                    String rLower = rContent != null ? rContent.toLowerCase(Locale.ROOT) : "";

                    if (isStrictLabQuestion || rLower.contains("phòng máy") || rLower.contains("thí nghiệm")) {
                        strictRows.put(rId, rContent);
                    }
                    if (!isBroadQuestion
                        || rLower.contains("cơ sở vật chất") || rLower.contains("thiết bị")
                        || rLower.contains("phòng học") || rLower.contains("giảng đường")
                        || rLower.contains("thí nghiệm") || rLower.contains("phòng máy")
                        || rLower.contains("hạ tầng") || rLower.contains("cntt")) {
                        fallbackRows.put(rId, rContent);
                    }
                }
            }

            if (!strictRows.isEmpty() || (!isGrid && isStrictLabQuestion)) {
                strictLabResults.add(new QuestionMeta(qId, qType, content, strictRows, matrixCols));
            }
            if (!fallbackRows.isEmpty() || !isGrid) {
                fallbackFacilityResults.add(new QuestionMeta(qId, qType, content, fallbackRows, matrixCols));
            }
        }

        // Prefer dedicated lab/computer-room criteria (e.g. 2.12 row 2) when present.
        return !strictLabResults.isEmpty() ? strictLabResults : fallbackFacilityResults;
    }

    public JSONObject getBreakdown(String periodCode) {
        return support.getBreakdown(DETAIL_TABLE, KPI_CODE, periodCode);
    }
}
