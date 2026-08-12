package com.surveys.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.surveys.dto.OptionStatDto;
import com.surveys.dto.QuestionNumericStatDto;
import com.surveys.dto.BlockStatDto;
import com.surveys.dto.OverallStatDto;

@Service
public class SurveyStatsService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void recomputeCampaign(String surveyId, String campaignId) {
        String actualCampaignId = (campaignId == null || "all".equalsIgnoreCase(campaignId)) ? null : campaignId;
        jdbcTemplate.update("EXEC dbo.sp_recompute_campaign ?, ?", surveyId, actualCampaignId);
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
            sql = "SELECT option_id, option_text, count, percentage FROM dbo.survey_question_option_stats WHERE survey_id = ? AND campaign_id IS NULL AND question_id = ?";
            params = new Object[] { surveyId, questionId };
        } else {
            sql = "SELECT option_id, option_text, count, percentage FROM dbo.survey_question_option_stats WHERE survey_id = ? AND campaign_id = ? AND question_id = ?";
            params = new Object[] { surveyId, campaignId, questionId };
        }
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
    }
}
