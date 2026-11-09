package com.surveycomments;

import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.evaluate.EvaluationReport;
import com.surveycomments.evaluate.EvaluationServiceImpl;
import com.surveycomments.run.RunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EvaluationServiceImpl} (Task T5).
 * Completely offline: tests metrics calculations (Precision, Recall, F1, Cohen's Kappa).
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private RunRepository runRepository;

    @InjectMocks
    private EvaluationServiceImpl evaluationService;

    private static ClassificationResult buildResult(String id, String sentiment, boolean suggestion,
                                                    List<ClassificationResult.ThemeResult> themes) {
        return new ClassificationResult(
                id, themes, sentiment, 0.95, false, suggestion,
                "gemini-3.8-flash", "p0", "v0.2-draft", 100, 20);
    }

    @Test
    void evaluate_perfectAgreement_returns100PercentAndZeroDisagreements() {
        String goldCsv = """
                comment_id,comment_text,themes,theme_sentiments,overall_sentiment,contains_suggestion
                c1,"Text 1",teaching,teaching:positive,positive,false
                c2,"Text 2",facilities;schedule,facilities:negative;schedule:negative,negative,true
                c3,"Text 3",,,neutral,false
                """;

        List<ClassificationResult> results = List.of(
                buildResult("c1", "positive", false,
                        List.of(new ClassificationResult.ThemeResult("teaching", "positive", "Text 1", true))),
                buildResult("c2", "negative", true,
                        List.of(
                                new ClassificationResult.ThemeResult("facilities", "negative", "Text 2", true),
                                new ClassificationResult.ThemeResult("schedule", "negative", "Text 2", false))),
                buildResult("c3", "neutral", false, List.of())
        );

        when(runRepository.findResultsByRunId("run-1")).thenReturn(results);

        InputStream in = new ByteArrayInputStream(goldCsv.getBytes(StandardCharsets.UTF_8));
        EvaluationReport report = evaluationService.evaluate("run-1", in);

        assertNotNull(report);
        assertEquals(3, report.getTotalGoldItems());
        assertEquals(3, report.getMatchedItems());
        assertEquals(1.0, report.getSentimentAccuracy(), 0.001);
        assertEquals(1.0, report.getSentimentCohenKappa(), 0.001);
        assertEquals(1.0, report.getThemeMacroF1(), 0.001);
        assertEquals(1.0, report.getThemeMicroF1(), 0.001);
        assertEquals(1.0, report.getSuggestionAccuracy(), 0.001);
        assertEquals(0, report.getDisagreementsCount());
        assertTrue(report.getDisagreements().isEmpty());
    }

    @Test
    void evaluate_withDisagreements_computesCorrectMetrics() {
        String goldCsv = """
                comment_id,comment_text,themes,theme_sentiments,overall_sentiment,contains_suggestion
                c1,"Thầy dạy hay nhưng phòng nóng",teaching;facilities,teaching:positive;facilities:negative,mixed,false
                c2,"Lịch học dày",schedule,schedule:negative,negative,false
                c3,"Wifi yếu",facilities,facilities:negative,negative,true
                c4,"Tốt",,,positive,false
                """;

        // c1: pred misses facilities (pred=teaching only, overall=positive instead of mixed)
        // c2: pred matches schedule, negative, false
        // c3: pred gets facilities, negative, but false for suggestion
        // c4: pred matches empty themes, positive, false
        List<ClassificationResult> results = List.of(
                buildResult("c1", "positive", false,
                        List.of(new ClassificationResult.ThemeResult("teaching", "positive", "Thầy dạy hay", true))),
                buildResult("c2", "negative", false,
                        List.of(new ClassificationResult.ThemeResult("schedule", "negative", "Lịch học dày", true))),
                buildResult("c3", "negative", false,
                        List.of(new ClassificationResult.ThemeResult("facilities", "negative", "Wifi yếu", true))),
                buildResult("c4", "positive", false, List.of())
        );

        when(runRepository.findResultsByRunId("run-2")).thenReturn(results);

        InputStream in = new ByteArrayInputStream(goldCsv.getBytes(StandardCharsets.UTF_8));
        EvaluationReport report = evaluationService.evaluate("run-2", in);

        assertEquals(4, report.getMatchedItems());
        // Sentiment: c1 differs (positive vs mixed), c2, c3, c4 match -> 3/4 = 0.75
        assertEquals(0.75, report.getSentimentAccuracy(), 0.001);

        // Suggestions: c3 differs (false vs true), c1, c2, c4 match -> 3/4 = 0.75
        assertEquals(0.75, report.getSuggestionAccuracy(), 0.001);

        // Disagreements count should be 2 (c1 has theme & sentiment mismatch, c3 has suggestion mismatch)
        assertEquals(2, report.getDisagreementsCount());
        assertEquals("c1", report.getDisagreements().get(0).getCommentId());
        assertEquals("c3", report.getDisagreements().get(1).getCommentId());

        // Check Theme metrics:
        // teaching: gold has 1 (c1), pred has 1 (c1) -> TP=1, FP=0, FN=0 -> F1=1.0
        // facilities: gold has 2 (c1, c3), pred has 1 (c3) -> TP=1, FP=0, FN=1 -> Prec=1.0, Rec=0.5, F1=0.667
        // schedule: gold has 1 (c2), pred has 1 (c2) -> TP=1, FP=0, FN=0 -> F1=1.0
        EvaluationReport.ThemeMetric facMetric = report.getThemeMetrics().stream()
                .filter(m -> m.getTheme().equals("facilities"))
                .findFirst().orElseThrow();
        assertEquals(1, facMetric.getTruePositives());
        assertEquals(0, facMetric.getFalsePositives());
        assertEquals(1, facMetric.getFalseNegatives());
        assertEquals(2, facMetric.getSupport());
        assertEquals(1.0, facMetric.getPrecision(), 0.001);
        assertEquals(0.5, facMetric.getRecall(), 0.001);
    }

    @Test
    void evaluate_handlesUtf8BomAndCommaDelimitedThemes() {
        // UTF-8 BOM prefix \uFEFF
        String goldCsv = "\uFEFFcomment_id,comment_text,themes,theme_sentiments,overall_sentiment,contains_suggestion\n"
                + "c100,\"Căng tin dở\",campus_life,campus_life:negative,negative,false\n";

        List<ClassificationResult> results = List.of(
                buildResult("c100", "negative", false,
                        List.of(new ClassificationResult.ThemeResult("campus_life", "negative", "Căng tin dở", true)))
        );

        when(runRepository.findResultsByRunId("run-bom")).thenReturn(results);

        InputStream in = new ByteArrayInputStream(goldCsv.getBytes(StandardCharsets.UTF_8));
        EvaluationReport report = evaluationService.evaluate("run-bom", in);

        assertEquals(1, report.getMatchedItems());
        assertEquals(1.0, report.getSentimentAccuracy(), 0.001);
        assertEquals(0, report.getDisagreementsCount());
    }

    @Test
    void evaluate_noMatchingRunResults_throwsException() {
        when(runRepository.findResultsByRunId("missing-run")).thenReturn(List.of());

        String goldCsv = "comment_id,comment_text,themes,overall_sentiment,contains_suggestion\nc1,text,,neutral,false\n";
        InputStream in = new ByteArrayInputStream(goldCsv.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () ->
                evaluationService.evaluate("missing-run", in));
    }
}
