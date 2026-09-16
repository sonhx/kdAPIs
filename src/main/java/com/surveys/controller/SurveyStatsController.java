package com.surveys.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.surveys.dto.OptionStatDto;
import com.surveys.dto.QuestionNumericStatDto;
import com.surveys.dto.BlockStatDto;
import com.surveys.dto.OverallStatDto;
import com.surveys.service.SurveyStatsService;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/api/v1")
public class SurveyStatsController {

    @Autowired
    private SurveyStatsService surveyStatsService;

    @Autowired
    private SessionService sessionService;

    @GetMapping("/surveys/{surveyId}/campaigns/{campaignId}/questions/{questionId}/options")
    public ResponseEntity<List<OptionStatDto>> getQuestionOptionStats(
            @PathVariable("surveyId") String surveyId,
            @PathVariable("campaignId") String campaignId,
            @PathVariable("questionId") String questionId) {
        List<OptionStatDto> stats = surveyStatsService.getQuestionOptionStats(surveyId, campaignId, questionId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/surveys/{surveyId}/campaigns/{campaignId}/questions/{questionId}/stats")
    public ResponseEntity<QuestionNumericStatDto> getQuestionNumericStats(
            @PathVariable("surveyId") String surveyId,
            @PathVariable("campaignId") String campaignId,
            @PathVariable("questionId") String questionId) {
        QuestionNumericStatDto stats = surveyStatsService.getQuestionNumericStats(surveyId, campaignId, questionId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/surveys/{surveyId}/campaigns/{campaignId}/blocks/{blockId}/stats")
    public ResponseEntity<BlockStatDto> getBlockStats(
            @PathVariable("surveyId") String surveyId,
            @PathVariable("campaignId") String campaignId,
            @PathVariable("blockId") String blockId) {
        BlockStatDto stats = surveyStatsService.getBlockStats(surveyId, campaignId, blockId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/surveys/{surveyId}/full-stats")
    public ResponseEntity<com.surveys.dto.SurveyFullStatsDto> getSurveyFullStats(
            @PathVariable("surveyId") String surveyId,
            @RequestParam(value = "campaignId", required = false) String campaignId) {
        com.surveys.dto.SurveyFullStatsDto fullStats = surveyStatsService.getSurveyFullStats(surveyId, campaignId);
        return ResponseEntity.ok(fullStats);
    }

    @GetMapping("/surveys/{surveyId}/campaigns/{campaignId}/full-stats")
    public ResponseEntity<com.surveys.dto.SurveyFullStatsDto> getSurveyFullStatsWithCampaign(
            @PathVariable("surveyId") String surveyId,
            @PathVariable("campaignId") String campaignId) {
        com.surveys.dto.SurveyFullStatsDto fullStats = surveyStatsService.getSurveyFullStats(surveyId, campaignId);
        return ResponseEntity.ok(fullStats);
    }

    @GetMapping("/surveys/{surveyId}/campaigns/{campaignId}/stats")
    public ResponseEntity<OverallStatDto> getOverallStats(
            @PathVariable("surveyId") String surveyId,
            @PathVariable("campaignId") String campaignId) {
        OverallStatDto stats = surveyStatsService.getOverallStats(surveyId, campaignId);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/admin/surveys/{surveyId}/campaigns/{campaignId}/recompute")
    public ResponseEntity<?> recomputeCampaign(
            @PathVariable("surveyId") String surveyId,
            @PathVariable("campaignId") String campaignId,
            @RequestParam(value = "session_id", required = false) String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {
        
        String actualSessionId = sessionId != null ? sessionId : headerSessionId;
        if (actualSessionId != null) {
            struct_session sst = sessionService.getSessionInfo(actualSessionId);
            // Verify if user exists and is authorized (UserType != 4 is admin/faculty, type 4 is normal student/unprivileged user)
            if (sst == null || sst.UserType == 4) {
                return ResponseEntity.status(403).body("{\"status\":\"error\",\"message\":\"Không đủ quyền truy cập\"}");
            }
        }
        
        surveyStatsService.recomputeCampaign(surveyId, campaignId);
        return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"Stats recomputed successfully.\"}");
    }
}
