package com.surveys.service;

import com.kpi.KpiH402CalculationService;
import com.kpi.KpiH403CalculationService;
import com.kpi.KpiH404CalculationService;
import com.kpi.KpiH405CalculationService;
import com.kpi.kpiExtend;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurveyT107ServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SurveyStatsService surveyStatsService;

    @Mock
    private kpiExtend kpiExtendService;

    @Mock
    private KpiH402CalculationService kpiH402CalculationService;

    @Mock
    private KpiH403CalculationService kpiH403CalculationService;

    @Mock
    private KpiH404CalculationService kpiH404CalculationService;

    @Mock
    private KpiH405CalculationService kpiH405CalculationService;

    @InjectMocks
    private SurveyT107Service surveyT107Service;

    @Test
    void normalizePeriodInputConvertsDisplayLabelsToCodes() {
        assertEquals("TERM_2025-2026_HK2", SurveyT107Service.normalizePeriodInput("Học kỳ 2 (Năm học 2025-2026)"));
        assertEquals("2025-HK1", SurveyT107Service.normalizePeriodInput("Học kỳ 1 năm 2025"));
        assertEquals("2025-2026", SurveyT107Service.normalizePeriodInput("Năm học 2025-2026"));
        assertEquals("2026", SurveyT107Service.normalizePeriodInput("Năm 2026"));
        assertEquals("2026-11", SurveyT107Service.normalizePeriodInput("Tháng 11/2026"));
        assertEquals("2026-Q1", SurveyT107Service.normalizePeriodInput("Quý 1/2026"));
        assertNull(SurveyT107Service.normalizePeriodInput("null"));
        assertNull(SurveyT107Service.normalizePeriodInput(""));
    }

    @Test
    void syncToKpiDataPointsResolvesCycleScopedPeriodAndUpsertsInstitutionalAndDepartmentRows() {
        Map<String, Object> kpiDef = new HashMap<>();
        kpiDef.put("kpi_id", 45);
        kpiDef.put("cycle_id", 2);
        kpiDef.put("cycle_type", "term");

        when(jdbcTemplate.queryForList(contains("FROM dbo.kpi_definitions")))
                .thenReturn(List.of(kpiDef));

        // Searching period_instances within cycle_id = 2 for default "2025-2026" returns empty,
        // and incompatible schoolyear string must NOT call resolveOrCreatePeriodInstanceByCode.
        when(jdbcTemplate.query(
                contains("WHERE cycle_id = ? AND (period_code = ? OR period_code LIKE ?)"),
                any(RowMapper.class),
                eq(2),
                eq("2025-2026"),
                eq("%2025-2026")))
                .thenReturn(Collections.emptyList());

        // Falls back to T1.07's active reporting period (current period in progress -> previous period)
        when(kpiExtendService.getOrCreatePeriodInstance(eq(2), eq("term"), any(java.util.Date.class)))
                .thenReturn(716, 715);

        Map<String, Object> currDates = new HashMap<>();
        currDates.put("start_date", Date.valueOf("2026-08-01"));
        currDates.put("end_date", Date.valueOf("2099-01-31"));
        when(jdbcTemplate.queryForList(contains("SELECT start_date, end_date FROM dbo.period_instances"), eq(716)))
                .thenReturn(List.of(currDates));

        // Institutional row missing -> INSERT without target column; department 101 exists -> UPDATE
        when(jdbcTemplate.query(
                contains("AND department_id IS NULL"),
                any(RowMapper.class),
                eq(45),
                eq(715)))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(
                contains("FROM dbo.kpi_assignments WHERE kpi_id = ?"),
                eq(String.class),
                eq(45)))
                .thenReturn(List.of("101"));
        when(jdbcTemplate.query(
                contains("CAST(department_id AS VARCHAR(100)) = ?"),
                any(RowMapper.class),
                eq(45),
                eq(715),
                eq("101")))
                .thenReturn(List.of(9001));

        Integer syncedPeriod = surveyT107Service.syncToKpiDataPoints(3.3, "survey.xlsx", "2025-2026", 15700, null);

        assertEquals(715, syncedPeriod);
        verify(kpiExtendService, never()).resolveOrCreatePeriodInstanceByCode(anyInt(), anyString(), anyString());
        verify(jdbcTemplate).update(
                contains("INSERT INTO dbo.kpi_data_points (kpi_id, period_id, actual_value, status_id, notes, updated_at)"),
                eq(45),
                eq(715),
                eq(3.3),
                contains("15700")
        );
        verify(jdbcTemplate).update(
                contains("UPDATE dbo.kpi_data_points SET actual_value = ?, status_id = 5, notes = ?"),
                eq(3.3),
                contains("15700"),
                eq(9001)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void processT107ExcelInvalidatesAssignmentsCacheAfterTransactionCommits() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã sinh viên");
            header.createCell(1).setCellValue("Họ và tên");
            header.createCell(2).setCellValue("Mức độ hài lòng");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("B21DCCN001");
            r1.createCell(1).setCellValue("Nguyen Van A");
            r1.createCell(2).setCellValue(4);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            xlsx = out.toByteArray();
        }

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<JSONObject> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        when(jdbcTemplate.queryForList(contains("FROM dbo.kpi_definitions")))
                .thenReturn(Collections.emptyList());

        JSONObject res = surveyT107Service.processT107Excel(
                new ByteArrayInputStream(xlsx),
                "test_t107.xlsx",
                "TERM_2025-2026_HK2",
                "Admin",
                null
        );

        assertEquals(200, res.getInt("code"));
        assertEquals(4.0, res.getDouble("average_score"));
        verify(kpiExtendService, times(1)).invalidateKpisWithAssignmentsCache();
    }

    @Test
    @SuppressWarnings("unchecked")
    void processT107ExcelParsesStructuredGridHeadersExcludesSttAndOpenTextAndTriggersH4Kpis() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("STT");
            header.createCell(1).setCellValue("Mã sinh viên");
            header.createCell(2).setCellValue("Họ và tên");
            header.createCell(3).setCellValue("2.5: 5. Hoạt động ngoại khóa phát triển kỹ năng - Các hoạt động ngoại khóa được tổ chức đa dạng, phong phú.");
            header.createCell(4).setCellValue("2.8: 8. Hoạt động tư vấn việc làm - Bạn nhận được thông tin việc làm/kết nối doanh nghiệp kịp thời.");
            header.createCell(5).setCellValue("2.12: 12. Chất lượng phục vụ của các phòng, trung tâm, dịch vụ hỗ trợ - Các phòng/ban hành chính phục vụ sinh viên nhiệt tình, nhanh chóng.");
            header.createCell(6).setCellValue("2.12: 12. Chất lượng phục vụ của các phòng, trung tâm, dịch vụ hỗ trợ - Thư viện, phòng thí nghiệm, trung tâm học liệu đáp ứng tốt nhu cầu học tập.");
            header.createCell(7).setCellValue("2.16: Bạn có đề xuất hay góp ý nào để Học viện cải thiện tốt hơn các dịch vụ hỗ trợ sinh viên không?");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue(1); // STT=1 must NOT be counted as rating
            r1.createCell(1).setCellValue("B21DCCN001");
            r1.createCell(2).setCellValue("Nguyen Van A");
            r1.createCell(3).setCellValue(5);
            r1.createCell(4).setCellValue(4);
            r1.createCell(5).setCellValue(3);
            r1.createCell(6).setCellValue(4);
            r1.createCell(7).setCellValue("5"); // open text must NOT be counted as rating

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            xlsx = out.toByteArray();
        }

        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<JSONObject> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        when(jdbcTemplate.queryForList(contains("FROM dbo.kpi_definitions")))
                .thenReturn(Collections.emptyList());
        when(kpiH402CalculationService.calculateAndSaveH402(isNull(), eq(true)))
                .thenReturn(new JSONObject().put("status", "SUCCESS").put("actual_value", 4.0));
        when(kpiH403CalculationService.calculateAndSaveH403(isNull(), eq(true)))
                .thenReturn(new JSONObject().put("status", "SUCCESS").put("actual_value", 5.0));
        when(kpiH404CalculationService.calculateAndSaveH404(isNull(), eq(true)))
                .thenReturn(new JSONObject().put("status", "SUCCESS").put("actual_value", 4.0));
        when(kpiH405CalculationService.calculateAndSaveH405(isNull(), eq(true)))
                .thenReturn(new JSONObject().put("status", "SUCCESS").put("actual_value", 4.0));

        JSONObject res = surveyT107Service.processT107Excel(
                new ByteArrayInputStream(xlsx),
                "Khảo sát hài lòng SV về học phần.xlsx",
                "TERM_2025-2026_HK2",
                "Admin",
                null
        );

        assertEquals(200, res.getInt("code"));
        assertEquals(1, res.getInt("total_responses"));
        // Average across cols 3..6 only: (5 + 4 + 3 + 4) / 4 = 4.0 (STT=1 and open text "5" excluded)
        assertEquals(4.0, res.getDouble("average_score"));
        assertEquals(3, res.getInt("structured_questions_count"));
        assertTrue(res.has("related_kpis"));
        assertEquals(4.0, res.getJSONObject("related_kpis").getJSONObject("H4.02").getDouble("actual_value"));
        assertEquals(5.0, res.getJSONObject("related_kpis").getJSONObject("H4.03").getDouble("actual_value"));

        // Verify structured survey_questions upserts and JSON choices answer batch
        verify(jdbcTemplate, times(3)).update(contains("INTO dbo.survey_questions"), any(Object[].class));
        ArgumentCaptor<Object[]> answerBatchCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO dbo.survey_response_answers"), answerBatchCaptor.capture());

        Object[] capturedParams = answerBatchCaptor.getValue();
        // 3 grid questions for 1 student = 3 answer rows * 4 params = 12 params
        assertEquals(12, capturedParams.length);
        // Check question 2.12 (3rd question) JSON contains both matrix rows
        String q212Choices = ((SqlParameterValue) capturedParams[11]).getValue().toString();
        JSONArray arr = new JSONArray(q212Choices);
        assertEquals(2, arr.length());
        assertEquals("3", arr.getJSONObject(0).getString("textCot"));
        assertEquals("4", arr.getJSONObject(1).getString("textCot"));

        verify(kpiH402CalculationService).calculateAndSaveH402(null, true);
        verify(kpiH403CalculationService).calculateAndSaveH403(null, true);
        verify(kpiH404CalculationService).calculateAndSaveH404(null, true);
        verify(kpiH405CalculationService).calculateAndSaveH405(null, true);
    }
}

