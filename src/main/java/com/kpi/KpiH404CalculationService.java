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
 * KpiH404CalculationService - Automated Calculation Service for KPI H4.04
 * "Mức độ hài lòng về thư viện và học liệu số" (Thang điểm khảo sát 1–5).
 * Targets strictly library/learning-material criteria (section 2.12 row 2).
 */
@Service
public class KpiH404CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiH404CalculationService.class);
    private static final String KPI_CODE = "H4.04";
    private static final String DETAIL_TABLE = "dbo.kpi_h404_survey_details";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudentSurveyKpiSupport support;

    @Value("${slink.survey.h404.survey-prefix:Phiếu khảo sát sinh viên về chương trình đào tạo}")
    private String surveyPrefix;

    @Value("${slink.survey.h404.fallback-survey-prefix:Khảo sát mức độ hài lòng của sinh viên}")
    private String fallbackSurveyPrefix;

    @Value("${slink.survey.h404.row-keyword:học liệu}")
    private String rowKeyword;

    /**
     * Scheduled Daily Run for KPI H4.04 calculation (01:30 AM daily).
     */
    @Scheduled(cron = "${slink.survey.h404.cron:0 30 1 * * ?}")
    public void scheduledDailyH404Calculation() {
        log.info("Executing scheduled daily calculation for KPI H4.04 (Mức độ hài lòng về thư viện và học liệu số)...");
        try {
            calculateAndSaveH404(null, false);
        } catch (Exception e) {
            log.error("Error in scheduled daily H4.04 calculation: ", e);
        }
    }

    /**
     * Calculate and save KPI H4.04 data points into kpi_data_points and kpi_h404_survey_details.
     */
    public JSONObject calculateAndSaveH404(Date referenceDate, boolean forceAll) {
        KpiSpec spec = new KpiSpec(
            KPI_CODE, 35, 6, 3.5, DETAIL_TABLE, surveyPrefix, fallbackSurveyPrefix
        );
        return support.calculateAndSave(spec, this::findTargetQuestions, referenceDate, forceAll);
    }

    public List<QuestionMeta> findTargetQuestions(String surveyId) {
        String keyword = StudentSurveyKpiSupport.decodePropertyUtf8(
            rowKeyword != null && !rowKeyword.trim().isEmpty() ? rowKeyword.trim() : "học liệu"
        );
        String sql = "SELECT q.id, q.block_id, q.question_type, q.content, b.title as block_title " +
                     "FROM dbo.survey_questions q WITH (NOLOCK) " +
                     "JOIN dbo.survey_blocks b WITH (NOLOCK) ON q.block_id = b.id " +
                     "WHERE b.survey_id = ? AND (q.content LIKE ? OR q.content LIKE N'%học liệu%' OR q.content LIKE N'%thư viện%' " +
                     "   OR q.content LIKE N'%Điều kiện học tập%' OR q.content LIKE N'%phòng, trung tâm, dịch vụ hỗ trợ%' OR q.content LIKE N'%chất lượng phục vụ%') " +
                     "OPTION (MAXDOP 1)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            sql, surveyId, StudentSurveyKpiSupport.toNVarChar("%" + keyword + "%")
        );

        String keywordLower = keyword.toLowerCase(Locale.ROOT);
        List<QuestionMeta> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String qId = (String) row.get("id");
            String qType = (String) row.get("question_type");
            String content = (String) row.get("content");
            String contentLower = content != null ? content.toLowerCase(Locale.ROOT) : "";

            boolean isBroadQuestion = contentLower.contains("điều kiện học tập")
                || contentLower.contains("dịch vụ hỗ trợ")
                || contentLower.contains("chất lượng phục vụ");

            Map<String, String> matrixRows = new LinkedHashMap<>();
            if ("GridSingleChoice".equalsIgnoreCase(qType) || "GridMultipleChoice".equalsIgnoreCase(qType)) {
                List<Map<String, Object>> mRows = jdbcTemplate.queryForList(
                    "SELECT id, content FROM dbo.question_matrix_rows WITH (NOLOCK) WHERE question_id = ? ORDER BY id OPTION (MAXDOP 1)", qId
                );
                for (Map<String, Object> mr : mRows) {
                    String rContent = (String) mr.get("content");
                    String rLower = rContent != null ? rContent.toLowerCase(Locale.ROOT) : "";
                    if (!isBroadQuestion || rLower.contains("học liệu") || rLower.contains("thư viện") || rLower.contains(keywordLower)) {
                        matrixRows.put((String) mr.get("id"), rContent);
                    }
                }
            }

            if (!matrixRows.isEmpty() || (!"GridSingleChoice".equalsIgnoreCase(qType) && !"GridMultipleChoice".equalsIgnoreCase(qType))) {
                results.add(new QuestionMeta(qId, qType, content, matrixRows, support.loadMatrixCols(qId)));
            }
        }
        return results;
    }

    public JSONObject getBreakdown(String periodCode) {
        return support.getBreakdown(DETAIL_TABLE, KPI_CODE, periodCode);
    }
}
