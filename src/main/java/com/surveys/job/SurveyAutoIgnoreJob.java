package com.surveys.job;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class SurveyAutoIgnoreJob {

    private static final Logger log = LoggerFactory.getLogger(SurveyAutoIgnoreJob.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTableSchema() {
        try {
            ClassPathResource resource = new ClassPathResource("survey_ignore_rules_schema.sql");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    String sql = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    jdbcTemplate.execute(sql);
                    // Un-ignore Excel summary surveys that were mistakenly auto-flagged as empty
                    jdbcTemplate.update("UPDATE dbo.surveys SET is_ignored = 0 WHERE input_method IN ('MANUAL_EXCEL', 'EXCEL', 'EXCEL_SUMMARY') AND is_ignored = 1");
                    log.info("Initialized survey_auto_ignore_rules schema successfully.");
                }
            }
        } catch (Exception e) {
            log.warn("Notice initializing survey_auto_ignore_rules schema: {}", e.getMessage());
        }
    }

    /**
     * Daily auto-ignore scan running at 00:20 AM (20 minutes after midnight).
     * Cron expression: "0 20 0 * * *"
     */
    @Scheduled(cron = "0 20 0 * * *")
    public void runDailySurveyAutoIgnoreScan() {
        log.info("Starting scheduled daily survey auto-ignore scan at 00:20 AM...");
        try {
            int totalAutoIgnored = executeAutoIgnoreScan();
            log.info("Completed daily survey auto-ignore scan. Total surveys auto-flagged as ignored: {}", totalAutoIgnored);
        } catch (Exception e) {
            log.error("Error executing daily survey auto-ignore scan: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes dynamic auto-ignore rules against the surveys table.
     * Reads active rules from dbo.survey_auto_ignore_rules WHERE is_enabled = 1.
     */
    public int executeAutoIgnoreScan() {
        initTableSchema();
        int totalFlagged = 0;

        try {
            List<Map<String, Object>> activeRules = jdbcTemplate.queryForList(
                "SELECT rule_code, rule_name, rule_type, pattern_value FROM dbo.survey_auto_ignore_rules WHERE is_enabled = 1"
            );

            for (Map<String, Object> rule : activeRules) {
                String ruleCode = (String) rule.get("rule_code");
                String ruleType = (String) rule.get("rule_type");
                String patternValue = (String) rule.get("pattern_value");

                if ("keyword".equalsIgnoreCase(ruleType) && patternValue != null && !patternValue.trim().isEmpty()) {
                    String[] keywords = patternValue.split(",");
                    StringBuilder sb = new StringBuilder();
                    sb.append("UPDATE s SET s.is_ignored = 1 FROM dbo.surveys s ");
                    sb.append("WHERE ISNULL(s.is_ignored, 0) = 0 AND (");

                    boolean first = true;
                    for (String kw : keywords) {
                        String cleanKw = kw.trim();
                        if (cleanKw.isEmpty() || "thử nghiệm".equalsIgnoreCase(cleanKw)) continue;
                        if (!first) sb.append(" OR ");
                        sb.append("LOWER(s.title) LIKE N'%").append(cleanKw.toLowerCase().replace("'", "''")).append("%'");
                        first = false;
                    }
                    sb.append(")");

                    if (!first) { // At least one keyword was added
                        int kwCount = jdbcTemplate.update(sb.toString());
                        if (kwCount > 0) {
                            log.info("Auto-ignored {} surveys using active Keyword Rule ({})", kwCount, ruleCode);
                            totalFlagged += kwCount;
                        }
                    }

                } else if ("empty_survey".equalsIgnoreCase(ruleType)) {
                    String emptySql = "UPDATE s SET s.is_ignored = 1 FROM dbo.surveys s " +
                                      "WHERE ISNULL(s.is_ignored, 0) = 0 " +
                                      "  AND ISNULL(s.input_method, '') NOT IN ('MANUAL_EXCEL', 'EXCEL', 'EXCEL_SUMMARY') " +
                                      "  AND NOT EXISTS (" +
                                      "    SELECT 1 FROM dbo.survey_blocks sb " +
                                      "    JOIN dbo.survey_questions q ON sb.id = q.block_id " +
                                      "    WHERE sb.survey_id = s.id" +
                                      ")";
                    int emptyCount = jdbcTemplate.update(emptySql);
                    if (emptyCount > 0) {
                        log.info("Auto-ignored {} empty surveys using active Rule ({})", emptyCount, ruleCode);
                        totalFlagged += emptyCount;
                    }

                } else if ("min_responses".equalsIgnoreCase(ruleType) && patternValue != null) {
                    int minResponses = 10;
                    try {
                        minResponses = Integer.parseInt(patternValue.trim());
                    } catch (Exception ignored) {}

                    String lowResponseSql = "UPDATE s SET s.is_ignored = 1 FROM dbo.surveys s " +
                                            "LEFT JOIN (" +
                                            "    SELECT survey_id, COUNT(*) AS resp_count FROM dbo.survey_responses GROUP BY survey_id" +
                                            ") r ON s.id = r.survey_id " +
                                            "WHERE ISNULL(s.is_ignored, 0) = 0 " +
                                            "  AND ISNULL(r.resp_count, 0) < ? " +
                                            "  AND (s.is_active = 0 OR s.created_at <= DATEADD(day, -3, GETDATE()))";
                    int lowCount = jdbcTemplate.update(lowResponseSql, minResponses);
                    if (lowCount > 0) {
                        log.info("Auto-ignored {} surveys with < {} responses using active Rule ({})", lowCount, minResponses, ruleCode);
                        totalFlagged += lowCount;
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error evaluating dynamic auto-ignore rules: {}", e.getMessage(), e);
        }

        return totalFlagged;
    }
}
