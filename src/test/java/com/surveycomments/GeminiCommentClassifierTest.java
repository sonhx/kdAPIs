package com.surveycomments;

import com.surveycomments.classify.*;
import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.redact.RedactedComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GeminiCommentClassifier} (Task T4).
 * Completely offline: mocks {@link GeminiClientService} with no network calls or API keys.
 */
@ExtendWith(MockitoExtension.class)
class GeminiCommentClassifierTest {

    @Mock
    private GeminiClientService geminiClientService;

    @Spy
    private ClassificationCache cache = new ClassificationCache();

    @Spy
    private LocalSchemaValidator schemaValidator = new LocalSchemaValidator();

    @Spy
    private SurveyCommentsProperties props = new SurveyCommentsProperties();

    @InjectMocks
    private GeminiCommentClassifier classifier;

    @BeforeEach
    void setUp() {
        cache.clear();
        props.getGemini().setRequestsPerMinute(600); // fast RPM for test execution
        props.getGemini().setMaxRetries(2);
    }

    @Test
    void classify_validResponse_parsedCorrectly() {
        String json = """
            {
              "comment_id": "c001",
              "themes": [
                {
                  "theme": "teaching",
                  "sentiment": "positive",
                  "evidence": "Thầy dạy rất dễ hiểu",
                  "primary": true
                },
                {
                  "theme": "facilities",
                  "sentiment": "negative",
                  "evidence": "phòng học quá nóng"
                }
              ],
              "overall_sentiment": "mixed",
              "confidence": 0.92,
              "needs_human_review": false,
              "contains_suggestion": false
            }
            """;

        when(geminiClientService.generate(any(), any(), any(), any(), any()))
                .thenReturn(new GeminiGenerationResponse(json, 450, 80));

        RedactedComment comment = new RedactedComment(
                "c001", "demo-2026-01", "K12",
                "Thầy dạy rất dễ hiểu nhưng phòng học quá nóng.",
                0, 0, 0, 0, false, false);

        ClassificationResult result = classifier.classify(comment);

        assertNotNull(result);
        assertEquals("c001", result.getCommentId());
        assertEquals("mixed", result.getOverallSentiment());
        assertEquals(0.92, result.getConfidence(), 0.001);
        assertFalse(result.isNeedsHumanReview());
        assertEquals(2, result.getThemes().size());
        assertEquals("teaching", result.getThemes().get(0).getTheme());
        assertEquals("positive", result.getThemes().get(0).getSentiment());
        assertEquals("facilities", result.getThemes().get(1).getTheme());
        assertEquals("negative", result.getThemes().get(1).getSentiment());
        assertEquals(450, result.getInputTokens());
        assertEquals(80, result.getOutputTokens());
    }

    @Test
    void classify_caching_avoidsSecondApiCall() {
        String json = """
            {
              "comment_id": "c004",
              "themes": [],
              "overall_sentiment": "positive",
              "confidence": 0.95,
              "needs_human_review": false,
              "contains_suggestion": false
            }
            """;

        when(geminiClientService.generate(any(), any(), any(), any(), any()))
                .thenReturn(new GeminiGenerationResponse(json, 200, 30));

        RedactedComment comment1 = new RedactedComment(
                "c004", "demo-2026-01", "K12",
                "Tốt.",
                0, 0, 0, 0, false, false);

        RedactedComment comment2 = new RedactedComment(
                "c014", "demo-2026-01", "K12",
                "Tốt.", // identical text
                0, 0, 0, 0, false, false);

        ClassificationResult r1 = classifier.classify(comment1);
        ClassificationResult r2 = classifier.classify(comment2);

        // Model should only be called once
        verify(geminiClientService, times(1)).generate(any(), any(), any(), any(), any());

        assertEquals("c004", r1.getCommentId());
        assertEquals("c014", r2.getCommentId());
        assertEquals("positive", r2.getOverallSentiment());
        assertEquals(0, r2.getInputTokens()); // cached result consumes 0 tokens
    }

    @Test
    void classify_lowConfidenceOrOtherTheme_routesToReview() {
        String json = """
            {
              "comment_id": "c099",
              "themes": [
                {
                  "theme": "other",
                  "sentiment": "neutral",
                  "evidence": "chuyện khác"
                }
              ],
              "overall_sentiment": "neutral",
              "confidence": 0.55,
              "needs_human_review": false,
              "contains_suggestion": true
            }
            """;

        when(geminiClientService.generate(any(), any(), any(), any(), any()))
                .thenReturn(new GeminiGenerationResponse(json, 300, 50));

        RedactedComment comment = new RedactedComment(
                "c099", "s1", "K1", "chuyện khác",
                0, 0, 0, 0, false, false);

        ClassificationResult result = classifier.classify(comment);

        // Even though model set needs_human_review=false, confidence < 0.6 and theme "other" force it to true
        assertTrue(result.isNeedsHumanReview(), "Must flag for human review when confidence < 0.6 or theme is 'other'");
        assertTrue(result.isContainsSuggestion());
    }
}

