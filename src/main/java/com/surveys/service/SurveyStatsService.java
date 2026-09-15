package com.surveys.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.surveys.dto.OptionStatDto;
import com.surveys.dto.QuestionNumericStatDto;
import com.surveys.dto.BlockStatDto;
import com.surveys.dto.OverallStatDto;

@Service
public class SurveyStatsService {

    private static final Logger log = LoggerFactory.getLogger(SurveyStatsService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initProcedures() {
        try {
            ClassPathResource resource = new ClassPathResource("surveys_stats_schema.sql");
            if (resource.exists()) {
                InputStream is = resource.getInputStream();
                String content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                String[] batches = content.split("(?i)\\r?\\nGO\\r?\\n");
                for (String batch : batches) {
                    String trimmed = batch.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            jdbcTemplate.execute(trimmed);
                        } catch (Exception e) {
                            log.warn("Notice executing SQL batch during startup: {}", e.getMessage());
                        }
                    }
                }
                log.info("Successfully refreshed survey stats schema and stored procedures.");
            }
        } catch (Exception e) {
            log.warn("Notice refreshing survey stats schema: {}", e.getMessage());
        }
    }

    public void recomputeCampaign(String surveyId, String campaignId) {
        try {
            String actualCampaignId = (campaignId == null || "all".equalsIgnoreCase(campaignId)) ? null : campaignId;
            jdbcTemplate.update("EXEC dbo.sp_recompute_campaign ?, ?", surveyId, actualCampaignId);
        } catch (Exception e) {
            log.warn("Exception in recomputeCampaign for surveyId={}, campaignId={}: {}", surveyId, campaignId, e.getMessage());
        }
    }

    private boolean hasSurveyResponses(String surveyId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.survey_responses WHERE survey_id = ?", 
                Integer.class, 
                surveyId
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<OptionStatDto> getQuestionOptionStats(String surveyId, String campaignId, String questionId) {
        String sql;
        Object[] params;
        if (campaignId == null || "all".equalsIgnoreCase(campaignId)) {
            sql = "SELECT option_id, option_text, count, percentage FROM dbo.survey_question_option_stats WHERE survey_id = ? AND campaign_id IS NULL AND question_id = ? ORDER BY option_text ASC";
            params = new Object[] { surveyId, questionId };
        } else {
            sql = "SELECT option_id, option_text, count, percentage FROM dbo.survey_question_option_stats WHERE survey_id = ? AND campaign_id = ? AND question_id = ? ORDER BY option_text ASC";
            params = new Object[] { surveyId, campaignId, questionId };
        }

        try {
            List<OptionStatDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> new OptionStatDto(
                rs.getString("option_id"),
                rs.getString("option_text"),
                rs.getInt("count"),
                rs.getBigDecimal("percentage")
            ), params);

            if (list.isEmpty() && hasSurveyResponses(surveyId)) {
                recomputeCampaign(surveyId, campaignId);
                list = jdbcTemplate.query(sql, (rs, rowNum) -> new OptionStatDto(
                    rs.getString("option_id"),
                    rs.getString("option_text"),
                    rs.getInt("count"),
                    rs.getBigDecimal("percentage")
                ), params);
            }
            return list;
        } catch (Exception e) {
            log.warn("Notice querying question option stats (running auto-repair): {}", e.getMessage());
            initProcedures();
            if (hasSurveyResponses(surveyId)) {
                recomputeCampaign(surveyId, campaignId);
            }
            try {
                return jdbcTemplate.query(sql, (rs, rowNum) -> new OptionStatDto(
                    rs.getString("option_id"),
                    rs.getString("option_text"),
                    rs.getInt("count"),
                    rs.getBigDecimal("percentage")
                ), params);
            } catch (Exception ex) {
                log.error("Failed to fetch option stats for questionId={}: {}", questionId, ex.getMessage());
                return java.util.Collections.emptyList();
            }
        }
    }

    public QuestionNumericStatDto getQuestionNumericStats(String surveyId, String campaignId, String questionId) {
        String sql;
        Object[] params;
        if (campaignId == null || "all".equalsIgnoreCase(campaignId)) {
            sql = "SELECT total_responses, mean, std_dev, min_value, max_value, text_count FROM dbo.survey_question_stats WHERE survey_id = ? AND campaign_id IS NULL AND question_id = ?";
            params = new Object[] { surveyId, questionId };
        } else {
            sql = "SELECT total_responses, mean, std_dev, min_value, max_value, text_count FROM dbo.survey_question_stats WHERE survey_id = ? AND campaign_id = ? AND question_id = ?";
            params = new Object[] { surveyId, campaignId, questionId };
        }

        try {
            List<QuestionNumericStatDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> new QuestionNumericStatDto(
                questionId,
                rs.getInt("total_responses"),
                rs.getBigDecimal("mean"),
                rs.getBigDecimal("std_dev"),
                rs.getBigDecimal("min_value"),
                rs.getBigDecimal("max_value"),
                rs.getInt("text_count")
            ), params);

            if (list.isEmpty() && hasSurveyResponses(surveyId)) {
                recomputeCampaign(surveyId, campaignId);
                list = jdbcTemplate.query(sql, (rs, rowNum) -> new QuestionNumericStatDto(
                    questionId,
                    rs.getInt("total_responses"),
                    rs.getBigDecimal("mean"),
                    rs.getBigDecimal("std_dev"),
                    rs.getBigDecimal("min_value"),
                    rs.getBigDecimal("max_value"),
                    rs.getInt("text_count")
                ), params);
            }

            return list.isEmpty() ? new QuestionNumericStatDto(questionId, 0, null, null, null, null, 0) : list.get(0);
        } catch (Exception e) {
            log.warn("Notice querying question numeric stats (running auto-repair): {}", e.getMessage());
            initProcedures();
            if (hasSurveyResponses(surveyId)) {
                recomputeCampaign(surveyId, campaignId);
            }
            try {
                List<QuestionNumericStatDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> new QuestionNumericStatDto(
                    questionId,
                    rs.getInt("total_responses"),
                    rs.getBigDecimal("mean"),
                    rs.getBigDecimal("std_dev"),
                    rs.getBigDecimal("min_value"),
                    rs.getBigDecimal("max_value"),
                    rs.getInt("text_count")
                ), params);
                return list.isEmpty() ? new QuestionNumericStatDto(questionId, 0, null, null, null, null, 0) : list.get(0);
            } catch (Exception ex) {
                log.error("Failed to fetch numeric stats for questionId={}: {}", questionId, ex.getMessage());
                return new QuestionNumericStatDto(questionId, 0, null, null, null, null, 0);
            }
        }
    }

    public BlockStatDto getBlockStats(String surveyId, String campaignId, String blockId) {
        String sql;
        Object[] params;
        if (campaignId == null || "all".equalsIgnoreCase(campaignId)) {
            sql = "SELECT total_responses, mean, std_dev FROM dbo.survey_block_stats WHERE survey_id = ? AND campaign_id IS NULL AND block_id = ?";
            params = new Object[] { surveyId, blockId };
        } else {
            sql = "SELECT total_responses, mean, std_dev FROM dbo.survey_block_stats WHERE survey_id = ? AND campaign_id = ? AND block_id = ?";
            params = new Object[] { surveyId, campaignId, blockId };
        }

        try {
            List<BlockStatDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> new BlockStatDto(
                blockId,
                rs.getInt("total_responses"),
                rs.getBigDecimal("mean"),
                rs.getBigDecimal("std_dev")
            ), params);

            if (list.isEmpty() && hasSurveyResponses(surveyId)) {
                recomputeCampaign(surveyId, campaignId);
                list = jdbcTemplate.query(sql, (rs, rowNum) -> new BlockStatDto(
                    blockId,
                    rs.getInt("total_responses"),
                    rs.getBigDecimal("mean"),
                    rs.getBigDecimal("std_dev")
                ), params);
            }

            return list.isEmpty() ? new BlockStatDto(blockId, 0, null, null) : list.get(0);
        } catch (Exception e) {
            log.warn("Notice querying block stats (running auto-repair): {}", e.getMessage());
            initProcedures();
            if (hasSurveyResponses(surveyId)) {
                recomputeCampaign(surveyId, campaignId);
            }
            try {
                List<BlockStatDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> new BlockStatDto(
                    blockId,
                    rs.getInt("total_responses"),
                    rs.getBigDecimal("mean"),
                    rs.getBigDecimal("std_dev")
                ), params);
                return list.isEmpty() ? new BlockStatDto(blockId, 0, null, null) : list.get(0);
            } catch (Exception ex) {
                log.error("Failed to fetch block stats for blockId={}: {}", blockId, ex.getMessage());
                return new BlockStatDto(blockId, 0, null, null);
            }
        }
    }

    public OverallStatDto getOverallStats(String surveyId, String campaignId) {
        String sql;
        Object[] params;
        if (campaignId == null || "all".equalsIgnoreCase(campaignId)) {
            sql = "SELECT total_responses, CONVERT(VARCHAR(24), computed_at, 126) as computed_at FROM dbo.survey_overall_stats WHERE survey_id = ? AND campaign_id IS NULL";
            params = new Object[] { surveyId };
        } else {
            sql = "SELECT total_responses, CONVERT(VARCHAR(24), computed_at, 126) as computed_at FROM dbo.survey_overall_stats WHERE survey_id = ? AND campaign_id = ?";
            params = new Object[] { surveyId, campaignId };
        }

        try {
            List<OverallStatDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> new OverallStatDto(
                surveyId,
                campaignId,
                rs.getInt("total_responses"),
                rs.getString("computed_at")
            ), params);

            if (list.isEmpty() || (list.get(0).getTotalResponses() == 0 && hasSurveyResponses(surveyId))) {
                recomputeCampaign(surveyId, campaignId);
                list = jdbcTemplate.query(sql, (rs, rowNum) -> new OverallStatDto(
                    surveyId,
                    campaignId,
                    rs.getInt("total_responses"),
                    rs.getString("computed_at")
                ), params);
            }

            return list.isEmpty() ? new OverallStatDto(surveyId, campaignId, 0, null) : list.get(0);
        } catch (Exception e) {
            log.warn("Notice querying overall stats (running auto-repair): {}", e.getMessage());
            initProcedures();
            if (hasSurveyResponses(surveyId)) {
                recomputeCampaign(surveyId, campaignId);
            }
            try {
                List<OverallStatDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> new OverallStatDto(
                    surveyId,
                    campaignId,
                    rs.getInt("total_responses"),
                    rs.getString("computed_at")
                ), params);
                return list.isEmpty() ? new OverallStatDto(surveyId, campaignId, 0, null) : list.get(0);
            } catch (Exception ex) {
                log.error("Failed to fetch overall stats for surveyId={}: {}", surveyId, ex.getMessage());
                return new OverallStatDto(surveyId, campaignId, 0, null);
            }
        }
    }
}
