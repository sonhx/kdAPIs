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

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * KpiH403CalculationService - Automated Calculation Service for KPI H4.03
 * "Mức độ hài lòng SV về hoạt động ngoại khóa" (Thang điểm khảo sát 1–5).
 * Targets strictly extracurricular questions (section 2.5), excluding community outreach (2.7).
 */
@Service
public class KpiH403CalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiH403CalculationService.class);
    private static final String KPI_CODE = "H4.03";
    private static final String DETAIL_TABLE = "dbo.kpi_h403_survey_details";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudentSurveyKpiSupport support;

    @Value("${slink.survey.h403.survey-prefix:Khảo sát mức độ hài lòng của sinh viên}")
    private String surveyPrefix;

    @Value("${slink.survey.h403.fallback-survey-prefix:Phiếu khảo sát sinh viên về chương trình đào tạo}")
    private String fallbackSurveyPrefix;

    @Value("${slink.survey.h403.question-keyword:ngoại khóa}")
    private String questionKeyword;

    /**
     * Scheduled Daily Run for KPI H4.03 calculation (01:25 AM daily).
     */
    @Scheduled(cron = "${slink.survey.h403.cron:0 25 1 * * ?}")
    public void scheduledDailyH403Calculation() {
        log.info("Executing scheduled daily calculation for KPI H4.03 (Mức độ hài lòng SV về hoạt động ngoại khóa)...");
        try {
            calculateAndSaveH403(null, false);
        } catch (Exception e) {
            log.error("Error in scheduled daily H4.03 calculation: ", e);
        }
    }

    /**
     * Calculate and save KPI H4.03 data points into kpi_data_points and kpi_h403_survey_details.
     */
    public JSONObject calculateAndSaveH403(Date referenceDate, boolean forceAll) {
        KpiSpec spec = new KpiSpec(
            KPI_CODE, 34, 6, 3.5, DETAIL_TABLE, surveyPrefix, fallbackSurveyPrefix
        );
        return support.calculateAndSave(spec, this::findTargetQuestions, referenceDate, forceAll);
    }

    public List<QuestionMeta> findTargetQuestions(String surveyId) {
        String keyword = StudentSurveyKpiSupport.decodePropertyUtf8(
            questionKeyword != null && !questionKeyword.trim().isEmpty() ? questionKeyword.trim() : "ngoại khóa"
        );
        String sql = "SELECT q.id, q.block_id, q.question_type, q.content, b.title as block_title " +
                     "FROM dbo.survey_questions q WITH (NOLOCK) " +
                     "JOIN dbo.survey_blocks b WITH (NOLOCK) ON q.block_id = b.id " +
                     "WHERE b.survey_id = ? AND (q.content LIKE ? OR q.content LIKE N'%ngoại khóa%') " +
                     "OPTION (MAXDOP 1)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            sql, surveyId, StudentSurveyKpiSupport.toNVarChar("%" + keyword + "%")
        );
        return support.loadQuestionMetaList(rows);
    }

    public JSONObject getBreakdown(String periodCode) {
        return support.getBreakdown(DETAIL_TABLE, KPI_CODE, periodCode);
    }
}
