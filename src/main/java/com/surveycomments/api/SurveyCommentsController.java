package com.surveycomments.api;

import com.surveycomments.run.Run;
import com.surveycomments.run.RunRepository;
import com.surveycomments.run.RunProcessingService;
import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.discovery.DiscoveryService;
import com.surveycomments.discovery.DiscoveryResult;
import com.surveycomments.evaluate.EvaluationService;
import com.surveycomments.evaluate.EvaluationReport;
import com.surveycomments.report.ReportService;
import com.surveycomments.cost.CostService;
import com.surveycomments.cost.CostReport;
import com.surveycomments.retention.RetentionService;
import com.session.SessionService;
import com.session.struct_session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for the survey-comments module.
 *
 * <p>All endpoints are under {@code /api/v1/survey-comments} and are restricted to
 * authenticated admin users (UserType != 4). Session is passed via the
 * {@code X-Session-Id} header or the {@code session_id} query parameter, matching
 * kdAPIs convention.
 *
 * <p>Processing endpoints (POST /runs) are implemented in T4; the stubs here return
 * HTTP 501 to make the routing clear without connecting to any network service.
 */
@RestController
@RequestMapping("/api/v1/survey-comments")
@CrossOrigin(originPatterns = "*", maxAge = 3600, allowCredentials = "true")
public class SurveyCommentsController {

    private static final Logger log = LoggerFactory.getLogger(SurveyCommentsController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private RunProcessingService runProcessingService;

    @Autowired
    private DiscoveryService discoveryService;

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private CostService costService;

    @Autowired
    private RetentionService retentionService;

    // -------------------------------------------------------------------------
    // Auth helper (mirrors SurveyStatsController pattern)
    // -------------------------------------------------------------------------

    /**
     * Validate session and ensure the caller has admin-level access (UserType != 4).
     * Returns null on success; returns a 401/403 ResponseEntity on failure.
     */
    private ResponseEntity<String> requireAdmin(String sessionId, String headerSessionId) {
        String sid = sessionId != null ? sessionId : headerSessionId;
        if (sid == null || sid.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Thiếu session_id\"}");
        }
        struct_session sst = sessionService.getSessionInfo(sid);
        if (sst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Người sử dụng chưa đăng nhập\"}");
        }
        if (sst.UserType == 4) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Không đủ quyền truy cập\"}");
        }
        return null; // ok
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/survey-comments/runs
    // -------------------------------------------------------------------------

    /**
     * Upload a CSV file to create a new analysis run.
     * Returns HTTP 202 Accepted immediately; processing happens asynchronously in T4.
     *
     * <p>Implementation stub — returns 501 until T4 is complete.
     */
    @PostMapping("/runs")
    public ResponseEntity<?> createRun(
            @RequestParam("file")     MultipartFile file,
            @RequestParam("surveyId") String surveyId,
            @RequestParam(value = "mode", defaultValue = "SYNC") String mode,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"Vui lòng chọn tệp CSV khảo sát\"}");
        }

        log.info("[survey-comments] POST /runs: surveyId={} mode={} fileSize={}", surveyId, mode, file.getSize());

        try {
            String runId = UUID.randomUUID().toString();
            Run run = new Run(runId, surveyId, mode);
            runRepository.save(run);

            byte[] bytes = file.getBytes();
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "comments.csv";

            runProcessingService.submitRun(runId, bytes, originalFilename);

            return ResponseEntity.accepted().body(RunResponse.from(run));
        } catch (Exception e) {
            log.error("[survey-comments] Failed to initiate run: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/survey-comments/runs/{runId}
    // -------------------------------------------------------------------------

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRun(
            @PathVariable("runId") String runId,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        return runRepository.findById(runId)
                .map(run -> ResponseEntity.ok((Object) RunResponse.from(run)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body("{\"status\":\"error\",\"message\":\"Run not found: " + runId + "\"}"));
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/survey-comments/runs/{runId}/results
    // -------------------------------------------------------------------------

    @GetMapping("/runs/{runId}/results")
    public ResponseEntity<?> getResults(
            @PathVariable("runId") String runId,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        if (runRepository.findById(runId).isEmpty()) {
            return ResponseEntity.status(404).body("{\"status\":\"error\",\"message\":\"Run not found: " + runId + "\"}");
        }

        List<ClassificationResult> results = runRepository.findResultsByRunId(runId);
        return ResponseEntity.ok(results);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/survey-comments/runs/{runId}/review-queue
    // -------------------------------------------------------------------------

    @GetMapping(value = "/runs/{runId}/review-queue", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<?> getReviewQueue(
            @PathVariable("runId") String runId,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        if (runRepository.findById(runId).isEmpty()) {
            return ResponseEntity.status(404).body("{\"status\":\"error\",\"message\":\"Run not found: " + runId + "\"}");
        }

        List<ClassificationResult> queue = runRepository.findReviewQueueByRunId(runId);

        StringBuilder sb = new StringBuilder();
        sb.append("comment_id,overall_sentiment,confidence,themes,contains_suggestion,needs_human_review\n");
        for (ClassificationResult r : queue) {
            String themesStr = "";
            if (r.getThemes() != null) {
                themesStr = r.getThemes().stream()
                        .map(t -> t.getTheme() + ":" + t.getSentiment())
                        .collect(Collectors.joining(";"));
            }
            sb.append(r.getCommentId()).append(",")
                    .append(r.getOverallSentiment()).append(",")
                    .append(r.getConfidence()).append(",")
                    .append("\"").append(themesStr.replace("\"", "\"\"")).append("\",")
                    .append(r.isContainsSuggestion()).append(",")
                    .append(r.isNeedsHumanReview()).append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"review_queue_" + runId + ".csv\"")
                .body(sb.toString());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/survey-comments/runs/{runId}/report
    // -------------------------------------------------------------------------

    @GetMapping("/runs/{runId}/report")
    public ResponseEntity<?> getReport(
            @PathVariable("runId") String runId,
            @RequestParam(value = "format", defaultValue = "xlsx") String format,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            if ("csv".equalsIgnoreCase(format)) {
                reportService.generateCsvReport(runId, baos);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report_" + runId + ".csv\"")
                        .body(baos.toByteArray());
            } else {
                reportService.generateExcelReport(runId, baos);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"survey_comments_report_" + runId + ".xlsx\"")
                        .body(baos.toByteArray());
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("[survey-comments] Error generating report for runId={}: {}", runId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/survey-comments/discovery
    // -------------------------------------------------------------------------

    @PostMapping("/discovery")
    public ResponseEntity<?> discovery(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "surveyId", defaultValue = "discovery") String surveyId,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"Vui lòng chọn tệp CSV khảo sát\"}");
        }

        try {
            DiscoveryResult result = discoveryService.discoverThemes(
                    file.getInputStream(),
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "comments.csv",
                    surveyId,
                    model
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[survey-comments] Error during codebook discovery: {}", e.getMessage());
            return ResponseEntity.status(500).body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/survey-comments/evaluations
    // -------------------------------------------------------------------------

    @PostMapping("/evaluations")
    public ResponseEntity<?> evaluate(
            @RequestParam(value = "goldFile", required = false) MultipartFile goldFile,
            @RequestParam(value = "file",     required = false) MultipartFile file,
            @RequestParam("runId")    String runId,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        MultipartFile targetFile = goldFile != null ? goldFile : file;
        if (targetFile == null || targetFile.isEmpty()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"Vui lòng chọn tệp CSV nhãn chuẩn (gold standard)\"}");
        }

        try {
            EvaluationReport report = evaluationService.evaluate(runId, targetFile.getInputStream());
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            log.warn("[survey-comments] Evaluation bad request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("[survey-comments] Error evaluating runId={}: {}", runId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/survey-comments/runs/{runId}/cost
    // -------------------------------------------------------------------------

    @GetMapping("/runs/{runId}/cost")
    public ResponseEntity<?> getCost(
            @PathVariable("runId") String runId,
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        try {
            CostReport report = costService.calculateCost(runId);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("[survey-comments] Error calculating cost for runId={}: {}", runId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/survey-comments/maintenance/cleanup
    // -------------------------------------------------------------------------

    @PostMapping("/maintenance/cleanup")
    public ResponseEntity<?> cleanup(
            @RequestParam(value = "session_id", required = false)   String sessionId,
            @RequestHeader(value = "X-Session-Id", required = false) String headerSessionId) {

        ResponseEntity<String> authError = requireAdmin(sessionId, headerSessionId);
        if (authError != null) return authError;

        try {
            int count = retentionService.purgeExpiredRuns();
            return ResponseEntity.ok("{\"status\":\"ok\",\"purgedRuns\":" + count + "}");
        } catch (Exception e) {
            log.error("[survey-comments] Error during maintenance cleanup: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}

