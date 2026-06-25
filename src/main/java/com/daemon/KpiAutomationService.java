package com.daemon;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class KpiAutomationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Transactional
    public void runPeriodAutomation(int targetYear) {
        String sql = "EXEC [dbo].[sp_Run_Automation_Period_Job] ?";
        jdbcTemplate.update(sql, targetYear);
    }
}