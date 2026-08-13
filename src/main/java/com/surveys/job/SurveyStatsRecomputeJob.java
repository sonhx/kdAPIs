package com.surveys.job;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.surveys.service.SurveyStatsService;

@Component
public class SurveyStatsRecomputeJob {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SurveyStatsService surveyStatsService;

    // Run once a day at 4:00 AM
    @Scheduled(cron = "0 0 4 * * *")
    public void runIncrementalRecompute() {
        System.out.println("Starting scheduled survey statistics recomputation...");
        try {
            // Find active campaigns
            String sql = "SELECT id, survey_id FROM survey_campaigns WHERE is_active = 1 " +
                         "AND (start_time IS NULL OR start_time <= GETDATE()) " +
                         "AND (end_time IS NULL OR end_time >= GETDATE())";

            List<Map<String, Object>> activeCampaigns = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> campaign : activeCampaigns) {
                String campaignId = (String) campaign.get("id");
                String surveyId = (String) campaign.get("survey_id");
                if (surveyId != null && campaignId != null) {
                    System.out.println("Recomputing stats for Survey ID: " + surveyId + ", Campaign ID: " + campaignId);
                    surveyStatsService.recomputeCampaign(surveyId, campaignId);
                }
            }

            // Also recompute overall stats (campaignId = null) for surveys with responses
            String sqlSurveys = "SELECT DISTINCT survey_id FROM survey_responses";
            List<String> surveyIds = jdbcTemplate.queryForList(sqlSurveys, String.class);
            for (String sId : surveyIds) {
                surveyStatsService.recomputeCampaign(sId, null);
            }

            System.out.println("Completed scheduled survey statistics recomputation successfully.");
        } catch (Exception e) {
            System.err.println("Error in SurveyStatsRecomputeJob: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
