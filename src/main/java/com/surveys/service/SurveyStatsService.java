package com.surveys.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.surveys.dto.OptionStatDto;
import com.surveys.dto.QuestionNumericStatDto;
import com.surveys.dto.BlockStatDto;
import com.surveys.dto.OverallStatDto;
import com.surveys.dto.SurveyFullStatsDto;

@Service
public class SurveyStatsService {

    private static final Logger log = LoggerFactory.getLogger(SurveyStatsService.class);
    private final ConcurrentHashMap<String, Boolean> computeLocks = new ConcurrentHashMap<>();

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

    @Async
    public void recomputeCampaignAsync(String surveyId, String campaignId) {
        String key = surveyId + "_" + (campaignId == null || "all".equalsIgnoreCase(campaignId) ? "ALL" : campaignId);
        if (computeLocks.putIfAbsent(key, Boolean.TRUE) != null) {
            log.info("Recomputation for key={} is already in progress. Skipping duplicate async task.", key);
            return;
        }
        try {
            log.info("Starting background async recomputation for key={}", key);
            recomputeCampaign(surveyId, campaignId);
            log.info("Completed background async recomputation for key={}", key);
        } catch (Exception e) {
            log.error("Error in recomputeCampaignAsync for key={}: {}", key, e.getMessage(), e);
        } finally {
            computeLocks.remove(key);
        }
    }

    public SurveyFullStatsDto getSurveyFullStats(String surveyId, String campaignId) {
        String actualCampaignId = (campaignId == null || "all".equalsIgnoreCase(campaignId)) ? null : campaignId;

        // 1. Fetch Overall Stats
        OverallStatDto overall = getOverallStats(surveyId, actualCampaignId);

        // 2. Fetch Block Stats
        Map<String, BlockStatDto> blockMap = new HashMap<>();
        String blockSql = (actualCampaignId == null) 
            ? "SELECT block_id, total_responses, mean, std_dev FROM dbo.survey_block_stats WHERE survey_id = ? AND campaign_id IS NULL"
            : "SELECT block_id, total_responses, mean, std_dev FROM dbo.survey_block_stats WHERE survey_id = ? AND campaign_id = ?";
        Object[] blockParams = (actualCampaignId == null) ? new Object[]{surveyId} : new Object[]{surveyId, actualCampaignId};
        
        try {
            jdbcTemplate.query(blockSql, (rs, rowNum) -> {
                String bId = rs.getString("block_id");
                blockMap.put(bId, new BlockStatDto(bId, rs.getInt("total_responses"), rs.getBigDecimal("mean"), rs.getBigDecimal("std_dev")));
                return null;
            }, blockParams);
        } catch (Exception e) {
            log.warn("Error fetching bulk block stats: {}", e.getMessage());
        }

        // 3. Fetch Numeric Question Stats
        Map<String, QuestionNumericStatDto> numericMap = new HashMap<>();
        String numSql = (actualCampaignId == null)
            ? "SELECT question_id, total_responses, mean, std_dev, min_value, max_value, text_count FROM dbo.survey_question_stats WHERE survey_id = ? AND campaign_id IS NULL"
            : "SELECT question_id, total_responses, mean, std_dev, min_value, max_value, text_count FROM dbo.survey_question_stats WHERE survey_id = ? AND campaign_id = ?";
        Object[] numParams = (actualCampaignId == null) ? new Object[]{surveyId} : new Object[]{surveyId, actualCampaignId};

        try {
            jdbcTemplate.query(numSql, (rs, rowNum) -> {
                String qId = rs.getString("question_id");
                numericMap.put(qId, new QuestionNumericStatDto(
                    qId, rs.getInt("total_responses"), rs.getBigDecimal("mean"), rs.getBigDecimal("std_dev"),
                    rs.getBigDecimal("min_value"), rs.getBigDecimal("max_value"), rs.getInt("text_count")
                ));
                return null;
            }, numParams);
        } catch (Exception e) {
            log.warn("Error fetching bulk numeric stats: {}", e.getMessage());
        }

        // 4. Fetch Option Question Stats
        Map<String, List<OptionStatDto>> optionMap = new HashMap<>();
        String optSql = (actualCampaignId == null)
            ? "SELECT question_id, option_id, option_text, count, percentage FROM dbo.survey_question_option_stats WHERE survey_id = ? AND campaign_id IS NULL ORDER BY question_id, option_text ASC"
            : "SELECT question_id, option_id, option_text, count, percentage FROM dbo.survey_question_option_stats WHERE survey_id = ? AND campaign_id = ? ORDER BY question_id, option_text ASC";
        Object[] optParams = (actualCampaignId == null) ? new Object[]{surveyId} : new Object[]{surveyId, actualCampaignId};

        try {
            jdbcTemplate.query(optSql, (rs, rowNum) -> {
                String qId = rs.getString("question_id");
                OptionStatDto dto = new OptionStatDto(rs.getString("option_id"), rs.getString("option_text"), rs.getInt("count"), rs.getBigDecimal("percentage"));
                optionMap.computeIfAbsent(qId, k -> new ArrayList<>()).add(dto);
                return null;
            }, optParams);
        } catch (Exception e) {
            log.warn("Error fetching bulk option stats: {}", e.getMessage());
        }

        // If cache is empty & survey has responses, trigger non-blocking background recompute
        if (numericMap.isEmpty() && optionMap.isEmpty() && hasSurveyResponses(surveyId)) {
            recomputeCampaignAsync(surveyId, actualCampaignId);
        }

        // Combine into QuestionFullStatDto map
        Map<String, SurveyFullStatsDto.QuestionFullStatDto> questionMap = new HashMap<>();
        Set<String> allQuestionIds = new HashSet<>();
        allQuestionIds.addAll(numericMap.keySet());
        allQuestionIds.addAll(optionMap.keySet());

        for (String qId : allQuestionIds) {
            QuestionNumericStatDto numDto = numericMap.get(qId);
            List<OptionStatDto> optList = optionMap.get(qId);
            questionMap.put(qId, new SurveyFullStatsDto.QuestionFullStatDto(qId, numDto, optList));
        }

        return new SurveyFullStatsDto(surveyId, actualCampaignId, overall, blockMap, questionMap);
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
