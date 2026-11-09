package com.surveycomments.run;

import com.surveycomments.clean.CleanComment;
import com.surveycomments.clean.CleanService;
import com.surveycomments.ingest.IngestService;
import com.surveycomments.ingest.RawComment;
import com.surveycomments.redact.RedactService;
import com.surveycomments.redact.RedactedComment;
import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.classify.CommentClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Orchestrates background processing of survey-comment analysis runs on a bounded executor.
 */
@Service
public class RunProcessingService {

    private static final Logger log = LoggerFactory.getLogger(RunProcessingService.class);

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private IngestService ingestService;

    @Autowired
    private CleanService cleanService;

    @Autowired
    private RedactService redactService;

    @Autowired
    private CommentClassifier commentClassifier;

    @Autowired
    @Qualifier("surveyCommentsExecutor")
    private Executor executor;

    /**
     * Submit an asynchronous execution job for the given run.
     */
    public void submitRun(String runId, byte[] csvBytes, String filename) {
        executor.execute(() -> processRun(runId, csvBytes, filename));
    }

    /**
     * Synchronous execution logic executed inside the worker thread.
     */
    public void processRun(String runId, byte[] csvBytes, String filename) {
        log.info("[survey-comments] Starting run processing: runId={}", runId);
        Run run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            log.error("[survey-comments] Run not found: {}", runId);
            return;
        }

        try {
            // 1. Ingest
            run.setStatus(RunStatus.CLEANING);
            runRepository.update(run);

            List<RawComment> raw = ingestService.ingest(new ByteArrayInputStream(csvBytes), filename, run.getSurveyId());
            run.setTotalComments(raw.size());

            // 2. Clean
            List<CleanComment> cleaned = cleanService.clean(raw);
            run.setCleanedComments(cleaned.size());

            // 3. Redact
            List<RedactedComment> redacted = redactService.redact(cleaned);

            // 4. Classify
            run.setStatus(RunStatus.CLASSIFYING);
            runRepository.update(run);

            List<ClassificationResult> results = new ArrayList<>();
            int reviewCount = 0;

            for (RedactedComment comment : redacted) {
                ClassificationResult res = commentClassifier.classify(comment);
                results.add(res);

                if (res.isNeedsHumanReview() || res.getConfidence() < 0.6 || hasOtherTheme(res)) {
                    reviewCount++;
                }

                run.setClassifiedComments(results.size());
                run.setReviewQueueSize(reviewCount);
                runRepository.update(run);
            }

            // 5. Save results and complete
            runRepository.saveResults(runId, results);
            run.setStatus(RunStatus.DONE);
            runRepository.update(run);

            log.info("[survey-comments] Run completed successfully: runId={} total={} classified={} reviewQueue={}",
                    runId, run.getTotalComments(), run.getClassifiedComments(), run.getReviewQueueSize());

        } catch (Exception e) {
            log.error("[survey-comments] Run failed: runId={} error={}", runId, e.getMessage(), e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            runRepository.update(run);
        }
    }

    private boolean hasOtherTheme(ClassificationResult r) {
        if (r.getThemes() == null) return false;
        return r.getThemes().stream().anyMatch(t -> "other".equalsIgnoreCase(t.getTheme()));
    }
}

