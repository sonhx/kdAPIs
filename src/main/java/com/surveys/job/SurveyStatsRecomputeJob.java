package com.surveys.job;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.surveys.service.SurveyStatsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SurveyStatsRecomputeJob {

    private static final Logger log = LoggerFactory.getLogger(SurveyStatsRecomputeJob.class);

    /** Skip the scheduler if it fires within this many ms after app startup. */
    private static final long STARTUP_GRACE_MS = 10 * 60 * 1000L; // 10 minutes

    /** Recorded once when the bean is instantiated (i.e. at app start). */
    private final long appStartTime = System.currentTimeMillis();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SurveyStatsService surveyStatsService;

    // Run periodically every 5 hours
    @Scheduled(cron = "0 0 */5 * * *")
    public void runIncrementalRecompute() {
        // Guard: skip if the app has not been running long enough.
        // This prevents the cron from firing immediately on startup when the app
        // boots near a scheduled trigger time (e.g., top of the hour).
        if (System.currentTimeMillis() - appStartTime < STARTUP_GRACE_MS) {
            log.info("Skipping survey stats recompute — still within startup grace period ({} min).",
                STARTUP_GRACE_MS / 60_000);
            return;
        }

        log.info("Starting scheduled survey statistics recomputation...");
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
                    log.info("Triggering async stats recompute for Survey ID: {}, Campaign ID: {}", surveyId, campaignId);
                    surveyStatsService.recomputeCampaignAsync(surveyId, campaignId);
                }
            }

            // Only recompute overall stats for surveys where stats are missing or stale (>6h old).
            // This prevents recomputing every survey on every scheduler run.
            String sqlSurveys =
                "SELECT DISTINCT sr.survey_id " +
                "FROM survey_responses sr " +
                "LEFT JOIN dbo.survey_overall_stats sos " +
                "    ON sos.survey_id = sr.survey_id AND sos.campaign_id IS NULL " +
                "WHERE sos.survey_id IS NULL " +
                "   OR sos.computed_at < DATEADD(HOUR, -6, GETDATE())";
            List<String> surveyIds = jdbcTemplate.queryForList(sqlSurveys, String.class);
            log.info("Found {} survey(s) needing overall stats recompute (missing or stale >6h).", surveyIds.size());
            for (String sId : surveyIds) {
                log.info("Triggering async overall stats recompute for Survey ID: {}", sId);
                surveyStatsService.recomputeCampaignAsync(sId, null);
            }

            log.info("Completed scheduled survey statistics recomputation successfully.");
        } catch (Exception e) {
            log.error("Error in SurveyStatsRecomputeJob: {}", e.getMessage(), e);
        }
    }
}
