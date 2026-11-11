package com.kpi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiExtendPeriodFieldsTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private com.notification.NotificationExtend notificationExtend;

    @InjectMocks
    private kpiExtend kpiExtendService;

    @BeforeEach
    void setUp() {
        kpiExtendService.invalidateKpisWithAssignmentsCache();
    }

    @Test
    void formatPeriodDisplayHandlesAllSupportedFormats() {
        assertEquals("Học kỳ 2 (Năm học 2025-2026)", kpiExtend.formatPeriodDisplay("TERM_2025-2026_HK2"));
        assertEquals("Học kỳ 1 năm 2025", kpiExtend.formatPeriodDisplay("2025-HK1"));
        assertEquals("Năm học 2025-2026", kpiExtend.formatPeriodDisplay("SCHOOLYEAR_2025-2026"));
        assertEquals("Năm học 2025-2026", kpiExtend.formatPeriodDisplay("2025-2026"));
        assertEquals("Năm 2026", kpiExtend.formatPeriodDisplay("ANNUAL_2026"));
        assertEquals("Năm 2026", kpiExtend.formatPeriodDisplay("2026"));
        assertEquals("Tháng 11/2026", kpiExtend.formatPeriodDisplay("2026-11"));
        assertEquals("Quý 1/2026", kpiExtend.formatPeriodDisplay("QUARTERLY_2026_Q1"));
        assertEquals("Quý 2/2026", kpiExtend.formatPeriodDisplay("2026-Q2"));
        assertEquals("Tuần 45/2026", kpiExtend.formatPeriodDisplay("2026-W45"));
    }

    @Test
    void getKpisWithAssignmentsIncludesPeriodStartAndEndDates() {
        Map<String, Object> kpiRow = new HashMap<>();
        kpiRow.put("kpi_id", 10);
        kpiRow.put("kpi_code", "T1.03");
        kpiRow.put("kpi_name", "Tỷ lệ sinh viên tốt nghiệp đúng hạn");
        kpiRow.put("category", "T – Đào tạo & Đảm bảo chất lượng");
        kpiRow.put("unit", "%");
        kpiRow.put("measurement", "SV tốt nghiệp đúng hạn / Tổng SV × 100%");
        kpiRow.put("source", "SIS");
        kpiRow.put("cycle", "Năm học");
        kpiRow.put("weight", 1.0);
        kpiRow.put("cycle_id", 7);
        kpiRow.put("cycle_type", "schoolyear");
        kpiRow.put("target", 80.0);
        kpiRow.put("target_rationale", "");
        kpiRow.put("vertex_name", "T");

        Map<String, Object> dpRow = new HashMap<>();
        dpRow.put("data_id", 101);
        dpRow.put("kpi_id", 10);
        dpRow.put("period_id", 77);
        dpRow.put("actual_value", 85.4);
        dpRow.put("target", 80.0);
        dpRow.put("status_id", 5);
        dpRow.put("updated_at", Timestamp.valueOf("2026-10-15 09:00:00"));
        dpRow.put("period_code", "SCHOOLYEAR_2025-2026");
        dpRow.put("start_date", Date.valueOf("2025-09-01"));
        dpRow.put("end_date", Date.valueOf("2026-08-31"));
        dpRow.put("is_approved", 1);
        dpRow.put("approved_by_name", "admin@ptit.edu.vn");

        Map<String, Object> currPeriodRow = new HashMap<>();
        currPeriodRow.put("period_code", "SCHOOLYEAR_2026-2027");
        currPeriodRow.put("start_date", Date.valueOf("2026-09-01"));
        currPeriodRow.put("end_date", Date.valueOf("2027-08-31"));

        Map<String, Object> prevPeriodRow = new HashMap<>();
        prevPeriodRow.put("period_code", "SCHOOLYEAR_2025-2026");
        prevPeriodRow.put("start_date", Date.valueOf("2025-09-01"));
        prevPeriodRow.put("end_date", Date.valueOf("2026-08-31"));

        when(jdbcTemplate.queryForList(contains("FROM kpi_definitions k WITH (NOLOCK)")))
                .thenReturn(List.of(kpiRow));
        when(jdbcTemplate.queryForList(contains("FROM kpi_assignments a WITH (NOLOCK)")))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(contains("FROM kpi_employee_assignments ea WITH (NOLOCK)")))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(contains("FROM kpi_data_points dp WITH (NOLOCK)")))
                .thenReturn(List.of(dpRow));

        when(jdbcTemplate.query(
                contains("SELECT period_id FROM period_instances WHERE cycle_id = ?"),
                any(RowMapper.class),
                eq(7),
                any(Date.class)))
                .thenReturn(List.of(78), List.of(77));

        when(jdbcTemplate.queryForList(contains("SELECT period_code, start_date, end_date FROM period_instances"), eq(78)))
                .thenReturn(List.of(currPeriodRow));
        when(jdbcTemplate.queryForList(contains("SELECT period_code, start_date, end_date FROM period_instances"), eq(77)))
                .thenReturn(List.of(prevPeriodRow));

        JSONArray result = kpiExtendService.getKpisWithAssignments();
        assertEquals(1, result.length());

        JSONObject kpi = result.getJSONObject(0);
        assertEquals("SCHOOLYEAR_2025-2026", kpi.optString("period"));
        assertEquals("Năm học 2025-2026", kpi.optString("period_name"));
        assertEquals("2025-09-01", kpi.optString("start_date"));
        assertEquals("2026-08-31", kpi.optString("end_date"));
        assertEquals("schoolyear", kpi.optString("cycle_type"));

        JSONArray dps = kpi.getJSONArray("data_points");
        assertEquals(1, dps.length());
        JSONObject dp = dps.getJSONObject(0);
        assertEquals("SCHOOLYEAR_2025-2026", dp.optString("period_code"));
        assertEquals("Năm học 2025-2026", dp.optString("period_name"));
        assertEquals("2025-09-01", dp.optString("start_date"));
        assertEquals("2026-08-31", dp.optString("end_date"));
    }
}

