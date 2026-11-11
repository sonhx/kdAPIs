package com.surveycomments.run;

import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.config.SurveyCommentsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory + file-backed implementation of {@link RunRepository}.
 */
@Repository
public class FileRunRepository implements RunRepository {

    private static final Logger log = LoggerFactory.getLogger(FileRunRepository.class);

    private final Map<String, Run> store = new ConcurrentHashMap<>();
    private final Map<String, List<ClassificationResult>> resultsStore = new ConcurrentHashMap<>();

    @Autowired
    private SurveyCommentsProperties props;

    @Override
    public void save(Run run) {
        if (store.putIfAbsent(run.getRunId(), run) != null) {
            throw new IllegalStateException("Run already exists: " + run.getRunId());
        }
        log.info("[survey-comments] run saved: id={} surveyId={} mode={}", run.getRunId(), run.getSurveyId(), run.getMode());
    }

    @Override
    public void update(Run run) {
        store.put(run.getRunId(), run);
        log.debug("[survey-comments] run updated: id={} status={} cleaned={} classified={}",
                run.getRunId(), run.getStatus(), run.getCleanedComments(), run.getClassifiedComments());
    }

    @Override
    public Optional<Run> findById(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    @Override
    public List<Run> findAll() {
        return store.values().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    @Override
    public void saveResults(String runId, List<ClassificationResult> results) {
        resultsStore.put(runId, new ArrayList<>(results));
        log.info("[survey-comments] saved {} classification results for runId={}", results.size(), runId);
    }

    @Override
    public List<ClassificationResult> findResultsByRunId(String runId) {
        List<ClassificationResult> list = resultsStore.get(runId);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    @Override
    public List<ClassificationResult> findReviewQueueByRunId(String runId) {
        List<ClassificationResult> list = resultsStore.get(runId);
        if (list == null) return Collections.emptyList();

        return list.stream()
                .filter(r -> r.isNeedsHumanReview() || r.getConfidence() < 0.6 || hasOtherTheme(r))
                .collect(Collectors.toList());
    }

    private boolean hasOtherTheme(ClassificationResult r) {
        if (r.getThemes() == null) return false;
        return r.getThemes().stream().anyMatch(t -> "other".equalsIgnoreCase(t.getTheme()));
    }
}

