package com.surveycomments;

import com.session.SessionService;
import com.session.struct_session;
import com.surveycomments.api.SurveyCommentsController;
import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.discovery.DiscoveryService;
import com.surveycomments.run.Run;
import com.surveycomments.run.RunProcessingService;
import com.surveycomments.run.RunRepository;
import com.surveycomments.run.RunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc unit tests for {@link SurveyCommentsController} (Task T4).
 * Fast, offline tests verifying REST contracts, status codes, and authorization rules.
 */
@ExtendWith(MockitoExtension.class)
class SurveyCommentsControllerMockMvcTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private RunRepository runRepository;

    @Mock
    private RunProcessingService runProcessingService;

    @Mock
    private DiscoveryService discoveryService;

    @Mock
    private com.surveycomments.evaluate.EvaluationService evaluationService;

    @Mock
    private com.surveycomments.report.ReportService reportService;

    @Mock
    private com.surveycomments.cost.CostService costService;

    @Mock
    private com.surveycomments.retention.RetentionService retentionService;

    @InjectMocks
    private SurveyCommentsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    private struct_session createAdminSession() {
        struct_session ss = new struct_session();
        ss.UserID = 1;
        ss.UserType = 1; // Admin
        return ss;
    }

    private struct_session createStudentSession() {
        struct_session ss = new struct_session();
        ss.UserID = 100;
        ss.UserType = 4; // Student (unprivileged)
        return ss;
    }

    @Test
    void createRun_withoutAuth_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "col1,col2\nval1,val2".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/survey-comments/runs")
                        .file(file)
                        .param("surveyId", "s1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("session_id")));
    }

    @Test
    void createRun_withStudentRole_returns403() throws Exception {
        when(sessionService.getSessionInfo("student-session")).thenReturn(createStudentSession());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "col1,col2\nval1,val2".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/survey-comments/runs")
                        .file(file)
                        .param("surveyId", "s1")
                        .header("X-Session-Id", "student-session"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("quyền")));
    }

    @Test
    void createRun_withAdminRole_returns202Accepted() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "comment_id,comment_text\nc1,Tốt.".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/survey-comments/runs")
                        .file(file)
                        .param("surveyId", "demo-2026-01")
                        .param("mode", "SYNC")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.surveyId").value("demo-2026-01"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(runRepository).save(any(Run.class));
        verify(runProcessingService).submitRun(anyString(), any(), eq("test.csv"));
    }

    @Test
    void getRun_found_returns200() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        Run run = new Run("run-123", "demo-survey", "SYNC");
        run.setStatus(RunStatus.CLASSIFYING);
        run.setTotalComments(24);
        run.setClassifiedComments(15);
        when(runRepository.findById("run-123")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-123"))
                .andExpect(jsonPath("$.status").value("CLASSIFYING"))
                .andExpect(jsonPath("$.totalComments").value(24))
                .andExpect(jsonPath("$.classifiedComments").value(15));
    }

    @Test
    void getRun_notFound_returns404() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());
        when(runRepository.findById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/survey-comments/runs/non-existent")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Run not found: non-existent"));
    }

    @Test
    void getResults_returns200WithList() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        Run run = new Run("run-123", "demo-survey", "SYNC");
        when(runRepository.findById("run-123")).thenReturn(Optional.of(run));

        ClassificationResult r1 = new ClassificationResult(
                "c001",
                List.of(new ClassificationResult.ThemeResult("teaching", "positive", "Thầy dạy tốt", true)),
                "positive", 0.95, false, false, "gemini-2.5-flash", "p0", "v0.2-draft", 100, 20);

        when(runRepository.findResultsByRunId("run-123")).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/results")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commentId").value("c001"))
                .andExpect(jsonPath("$[0].overallSentiment").value("positive"))
                .andExpect(jsonPath("$[0].confidence").value(0.95));
    }

    @Test
    void getReviewQueue_returnsCsv() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        Run run = new Run("run-123", "demo-survey", "SYNC");
        when(runRepository.findById("run-123")).thenReturn(Optional.of(run));

        ClassificationResult r1 = new ClassificationResult(
                "c011",
                List.of(new ClassificationResult.ThemeResult("other", "neutral", "ý kiến khác", false)),
                "neutral", 0.50, true, true, "gemini-2.5-flash", "p0", "v0.2-draft", 150, 25);

        when(runRepository.findReviewQueueByRunId("run-123")).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/review-queue")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("c011,neutral,0.5,\"other:neutral\",true,true")));
    }

    @Test
    void evaluate_withoutAuth_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "goldFile", "gold.csv", "text/csv", "comment_id,overall_sentiment\nc1,positive".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/survey-comments/evaluations")
                        .file(file)
                        .param("runId", "run-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void evaluate_withStudentRole_returns403() throws Exception {
        when(sessionService.getSessionInfo("student-session")).thenReturn(createStudentSession());

        MockMultipartFile file = new MockMultipartFile(
                "goldFile", "gold.csv", "text/csv", "comment_id,overall_sentiment\nc1,positive".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/survey-comments/evaluations")
                        .file(file)
                        .param("runId", "run-123")
                        .header("X-Session-Id", "student-session"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void evaluate_withAdminRole_returns200Ok() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        com.surveycomments.evaluate.EvaluationReport report = new com.surveycomments.evaluate.EvaluationReport(
                "run-123", 10, 10, 0.90, 0.85, 0.88, 0.89, 0.95, List.of(), 1, List.of());

        when(evaluationService.evaluate(org.mockito.ArgumentMatchers.eq("run-123"), any()))
                .thenReturn(report);

        MockMultipartFile file = new MockMultipartFile(
                "goldFile", "gold.csv", "text/csv", "comment_id,overall_sentiment\nc1,positive".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/survey-comments/evaluations")
                        .file(file)
                        .param("runId", "run-123")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-123"))
                .andExpect(jsonPath("$.totalGoldItems").value(10))
                .andExpect(jsonPath("$.sentimentAccuracy").value(0.90))
                .andExpect(jsonPath("$.sentimentCohenKappa").value(0.85))
                .andExpect(jsonPath("$.themeMacroF1").value(0.88))
                .andExpect(jsonPath("$.disagreementsCount").value(1));
    }

    @Test
    void getReport_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/report"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void getReport_withStudentRole_returns403() throws Exception {
        when(sessionService.getSessionInfo("student-session")).thenReturn(createStudentSession());

        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/report")
                        .header("X-Session-Id", "student-session"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void getReport_withAdminRole_returnsExcel() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/report")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("survey_comments_report_run-123.xlsx")));
    }

    @Test
    void getReport_withFormatCsv_returnsCsv() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/report")
                        .param("format", "csv")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("report_run-123.csv")));
    }

    @Test
    void getCost_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/cost"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void getCost_withAdminRole_returnsCostReport() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());

        com.surveycomments.cost.CostReport report = new com.surveycomments.cost.CostReport(
                "run-123", 20, 30_000, 2_000, 0.0057, 145, 0.15, 0.60);

        when(costService.calculateCost("run-123")).thenReturn(report);

        mockMvc.perform(get("/api/v1/survey-comments/runs/run-123/cost")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-123"))
                .andExpect(jsonPath("$.totalTokens").value(32_000))
                .andExpect(jsonPath("$.costUsd").value(0.0057))
                .andExpect(jsonPath("$.costVnd").value(145));
    }

    @Test
    void cleanup_withoutAuth_returns401() throws Exception {
        mockMvc.perform(multipart("/api/v1/survey-comments/maintenance/cleanup"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void cleanup_withAdminRole_returnsPurgedCount() throws Exception {
        when(sessionService.getSessionInfo("demo-session")).thenReturn(createAdminSession());
        when(retentionService.purgeExpiredRuns()).thenReturn(3);

        mockMvc.perform(multipart("/api/v1/survey-comments/maintenance/cleanup")
                        .header("X-Session-Id", "demo-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.purgedRuns").value(3));
    }
}
