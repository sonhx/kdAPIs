package com.kpi;

import com.kpi.StudentSurveyKpiSupport.KpiSpec;
import com.kpi.StudentSurveyKpiSupport.QuestionMeta;
import com.surveys.SurveySyncScheduler;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KpiH402ToH405CalculationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private kpiExtend kpiExtendService;

    @Mock
    private SurveySyncScheduler surveySyncScheduler;

    @InjectMocks
    private StudentSurveyKpiSupport support;

    @Test
    void decodePropertyUtf8RestoresMojibakeAndPreservesNativeUtf8() {
        String original = "Khảo sát mức độ hài lòng của sinh viên";
        String mojibake = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);

        assertEquals(original, StudentSurveyKpiSupport.decodePropertyUtf8(mojibake));
        assertEquals(original, StudentSurveyKpiSupport.decodePropertyUtf8(original));
        assertEquals("việc làm", StudentSurveyKpiSupport.decodePropertyUtf8(
            new String("việc làm".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1)
        ));
    }

    @Test
    void parseLikertScoreRespectsStrongNegativesBeforeWeakNegativesAndPositives() {
        assertEquals(5.0, StudentSurveyKpiSupport.parseLikertScore("5 = Rất hài lòng", null, null));
        assertEquals(5.0, StudentSurveyKpiSupport.parseLikertScore("Rất hài lòng", null, null));
        assertEquals(1.0, StudentSurveyKpiSupport.parseLikertScore("Rất không hài lòng", null, null));
        assertEquals(1.0, StudentSurveyKpiSupport.parseLikertScore("Rất không đồng ý", null, null));
        assertEquals(1.0, StudentSurveyKpiSupport.parseLikertScore("Rất kém", null, null));
        assertEquals(2.0, StudentSurveyKpiSupport.parseLikertScore("Không hài lòng", null, null));
        assertEquals(2.0, StudentSurveyKpiSupport.parseLikertScore("Kém", null, null));
        assertEquals(3.0, StudentSurveyKpiSupport.parseLikertScore("Bình thường", null, null));
        assertEquals(4.0, StudentSurveyKpiSupport.parseLikertScore("Hài lòng", null, null));
    }

    @Test
    void h403FindTargetQuestionsMatchesOnly25Excludes27() {
        KpiH403CalculationService service = new KpiH403CalculationService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "support", support);
        ReflectionTestUtils.setField(service, "questionKeyword", "ngoại khóa");

        Map<String, Object> q25 = Map.of(
            "id", "Q25", "question_type", "GridSingleChoice",
            "content", "5. Hoạt động ngoại khóa phát triển kỹ năng"
        );
        when(jdbcTemplate.queryForList(contains("FROM dbo.survey_questions q"), eq("T1.07"), any(SqlParameterValue.class)))
            .thenReturn(List.of(q25));
        when(jdbcTemplate.queryForList(contains("FROM dbo.question_matrix_rows"), eq("Q25")))
            .thenReturn(List.of(
                Map.of("id", "R25_1", "content", "Các hoạt động ngoại khóa được tổ chức đa dạng, phong phú."),
                Map.of("id", "R25_2", "content", "Hoạt động ngoại khóa giúp bạn phát triển kỹ năng mềm.")
            ));
        when(jdbcTemplate.queryForList(contains("FROM dbo.question_matrix_cols"), eq("Q25")))
            .thenReturn(Collections.emptyList());

        List<QuestionMeta> questions = service.findTargetQuestions("T1.07");
        assertEquals(1, questions.size());
        assertEquals("Q25", questions.get(0).questionId);
        assertEquals(2, questions.get(0).matrixRows.size());
        verify(jdbcTemplate).queryForList(
            argThat((String sql) -> sql.contains("N'%ngoại khóa%'") && !sql.contains("gắn kết cộng đồng")),
            eq("T1.07"), any(SqlParameterValue.class)
        );
    }

    @Test
    void h404FindTargetQuestionsMatchesOnly212Row2() {
        KpiH404CalculationService service = new KpiH404CalculationService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "support", support);
        ReflectionTestUtils.setField(service, "rowKeyword", "học liệu");

        Map<String, Object> q212 = Map.of(
            "id", "Q212", "question_type", "GridSingleChoice",
            "content", "12. Chất lượng phục vụ của các phòng, trung tâm, dịch vụ hỗ trợ"
        );
        when(jdbcTemplate.queryForList(contains("FROM dbo.survey_questions q"), eq("T1.07"), any(SqlParameterValue.class)))
            .thenReturn(List.of(q212));
        when(jdbcTemplate.queryForList(contains("FROM dbo.question_matrix_rows"), eq("Q212")))
            .thenReturn(List.of(
                Map.of("id", "R212_1", "content", "Các phòng/ban hành chính phục vụ sinh viên nhiệt tình, nhanh chóng."),
                Map.of("id", "R212_2", "content", "Thư viện, phòng thí nghiệm, trung tâm học liệu đáp ứng tốt nhu cầu học tập."),
                Map.of("id", "R212_3", "content", "Các dịch vụ hỗ trợ (CNTT, y tế, ký túc xá, căng tin…) hoạt động hiệu quả.")
            ));
        when(jdbcTemplate.queryForList(contains("FROM dbo.question_matrix_cols"), eq("Q212")))
            .thenReturn(Collections.emptyList());

        List<QuestionMeta> questions = service.findTargetQuestions("T1.07");
        assertEquals(1, questions.size());
        assertEquals(Set.of("R212_2"), questions.get(0).matrixRows.keySet());
    }

    @Test
    void h405FindTargetQuestionsPrefers212Row2Over214WhenLabRowExists() {
        KpiH405CalculationService service = new KpiH405CalculationService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "support", support);
        ReflectionTestUtils.setField(service, "rowKeyword", "cơ sở vật chất");

        Map<String, Object> q212 = Map.of(
            "id", "Q212", "question_type", "GridSingleChoice",
            "content", "12. Chất lượng phục vụ của các phòng, trung tâm, dịch vụ hỗ trợ"
        );
        Map<String, Object> q214 = Map.of(
            "id", "Q214", "question_type", "GridSingleChoice",
            "content", "14. Môi trường học tập, cơ sở vật chất, hạ tầng hỗ trợ"
        );
        when(jdbcTemplate.queryForList(contains("FROM dbo.survey_questions q"), eq("T1.07"), any(SqlParameterValue.class)))
            .thenReturn(List.of(q212, q214));
        when(jdbcTemplate.queryForList(contains("FROM dbo.question_matrix_rows"), eq("Q212")))
            .thenReturn(List.of(
                Map.of("id", "R212_1", "content", "Các phòng/ban hành chính phục vụ sinh viên nhiệt tình, nhanh chóng."),
                Map.of("id", "R212_2", "content", "Thư viện, phòng thí nghiệm, trung tâm học liệu đáp ứng tốt nhu cầu học tập."),
                Map.of("id", "R212_3", "content", "Các dịch vụ hỗ trợ (CNTT, y tế, ký túc xá, căng tin…) hoạt động hiệu quả.")
            ));
        when(jdbcTemplate.queryForList(contains("FROM dbo.question_matrix_rows"), eq("Q214")))
            .thenReturn(List.of(
                Map.of("id", "R214_1", "content", "Phòng học, giảng đường, thiết bị phục vụ học tập đáp ứng yêu cầu."),
                Map.of("id", "R214_2", "content", "Hạ tầng CNTT (wifi, phần mềm học tập, hệ thống LMS) ổn định, tiện lợi")
            ));
        when(jdbcTemplate.queryForList(contains("FROM dbo.question_matrix_cols"), anyString()))
            .thenReturn(Collections.emptyList());

        List<QuestionMeta> questions = service.findTargetQuestions("T1.07");
        assertEquals(1, questions.size());
        assertEquals("Q212", questions.get(0).questionId);
        assertEquals(Set.of("R212_2"), questions.get(0).matrixRows.keySet());
    }

    @Test
    @SuppressWarnings("unchecked")
    void calculateAndSaveProcessesBothTermsForManualExcelWithoutCallingSlinkSyncAndFiltersRows() {
        when(jdbcTemplate.queryForList(contains("FROM kpi_definitions"), eq("H4.04")))
            .thenReturn(List.of(Map.of("kpi_id", 35, "cycle_id", 6, "target", 3.5)));

        when(jdbcTemplate.queryForList(contains("FROM dbo.surveys WITH (NOLOCK)"), any(SqlParameterValue.class), any(SqlParameterValue.class)))
            .thenReturn(List.of(Map.of("id", "T1.07", "title", "Khảo sát T1.07", "is_active", true, "input_method", "MANUAL_EXCEL")));

        when(jdbcTemplate.queryForList(contains("FROM dbo.survey_campaigns WITH (NOLOCK)"), eq("T1.07")))
            .thenReturn(List.of(
                mapCampaign("C_T107_TERM_2025-2026_HK2", "TERM_2025-2026_HK2"),
                mapCampaign("C_T107_TERM_2026-2027_HK3", "TERM_2026-2027_HK3")
            ));

        when(jdbcTemplate.queryForList(contains("FROM dbo.period_instances WITH (NOLOCK) WHERE cycle_id = ? AND period_code = ?"), eq(6), eq("TERM_2025-2026_HK2")))
            .thenReturn(List.of(Map.of("period_id", 639, "period_code", "TERM_2025-2026_HK2")));
        when(jdbcTemplate.queryForList(contains("FROM dbo.period_instances WITH (NOLOCK) WHERE cycle_id = ? AND period_code = ?"), eq(6), eq("TERM_2026-2027_HK3")))
            .thenReturn(List.of(Map.of("period_id", 715, "period_code", "TERM_2026-2027_HK3")));

        String row1Choice = new JSONArray(List.of(
            Map.of("idHang", "R212_1", "textHang", "Hành chính", "textCot", "1"),
            Map.of("idHang", "R212_2", "textHang", "Thư viện, phòng thí nghiệm, trung tâm học liệu", "textCot", "4"),
            Map.of("idHang", "R212_3", "textHang", "CNTT, y tế", "textCot", "2")
        )).toString();
        String row2Choice = new JSONArray(List.of(
            Map.of("idHang", "R212_1", "textHang", "Hành chính", "textCot", "1"),
            Map.of("idHang", "R212_2", "textHang", "Thư viện, phòng thí nghiệm, trung tâm học liệu", "textCot", "5"),
            Map.of("idHang", "R212_3", "textHang", "CNTT, y tế", "textCot", "1")
        )).toString();

        when(jdbcTemplate.queryForList(contains("FROM dbo.survey_responses r"), eq("T1.07"), eq("C_T107_TERM_2025-2026_HK2"), eq("Q212")))
            .thenReturn(List.of(Map.of("choices", row1Choice)));
        when(jdbcTemplate.queryForList(contains("FROM dbo.survey_responses r"), eq("T1.07"), eq("C_T107_TERM_2026-2027_HK3"), eq("Q212")))
            .thenReturn(List.of(Map.of("choices", row2Choice)));

        when(jdbcTemplate.query(contains("FROM kpi_data_points"), any(RowMapper.class), eq(35), eq(639)))
            .thenReturn(List.of(598));
        when(jdbcTemplate.query(contains("FROM kpi_data_points"), any(RowMapper.class), eq(35), eq(715)))
            .thenReturn(List.of(700));
        when(jdbcTemplate.queryForObject(contains("FROM dbo.kpi_h404_survey_details"), eq(Integer.class), anyInt(), anyString()))
            .thenReturn(0);

        QuestionMeta targetQ = new QuestionMeta("Q212", "GridSingleChoice", "12. Chất lượng phục vụ",
            Map.of("R212_2", "Thư viện, phòng thí nghiệm, trung tâm học liệu"), Collections.emptyMap());
        KpiSpec spec = new KpiSpec("H4.04", 35, 6, 3.5, "dbo.kpi_h404_survey_details",
            "Phiếu khảo sát sinh viên về chương trình đào tạo", "Khảo sát mức độ hài lòng của sinh viên");

        JSONObject result = support.calculateAndSave(spec, sid -> List.of(targetQ), null, true);

        assertEquals("SUCCESS", result.getString("status"));
        assertEquals(2, result.getInt("total_periods_updated"));
        JSONArray periods = result.getJSONArray("processed_periods");
        assertEquals(639, periods.getJSONObject(0).getInt("period_id"));
        assertEquals(4.0, periods.getJSONObject(0).getDouble("actual_value"));
        assertEquals(715, periods.getJSONObject(1).getInt("period_id"));
        assertEquals(5.0, periods.getJSONObject(1).getDouble("actual_value"));

        verify(surveySyncScheduler, never()).syncSurveyDetailsById(anyString());
        verify(kpiExtendService, times(1)).invalidateKpisWithAssignmentsCache();
    }

    private Map<String, Object> mapCampaign(String id, String semCode) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("survey_id", "T1.07");
        m.put("title", "Đợt tải lên Excel T1.07");
        m.put("semester_code", semCode);
        m.put("semester_name", semCode);
        m.put("start_time", null);
        m.put("end_time", null);
        m.put("is_active", Boolean.TRUE);
        return m;
    }
}
