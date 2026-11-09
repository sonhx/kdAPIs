package com.surveycomments.classify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates Gemini responses locally against the rules and constraints of {@code schema.json}.
 */
@Component
public class LocalSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(LocalSchemaValidator.class);

    private static final Set<String> ALLOWED_THEMES = Set.of(
            "course_content", "teaching", "assessment", "schedule", "facilities",
            "campus_life", "online_systems", "student_support", "admin_fees",
            "career_activities", "other"
    );

    private static final Set<String> ALLOWED_SENTIMENTS = Set.of(
            "positive", "neutral", "negative"
    );

    private static final Set<String> ALLOWED_OVERALL_SENTIMENTS = Set.of(
            "positive", "neutral", "negative", "mixed"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses and validates raw JSON text from Gemini against schema constraints.
     *
     * @param jsonText      raw JSON text returned by the model
     * @param expectedId    expected comment_id to verify identity matching
     * @return validated {@link ParsedClassification}
     * @throws ClassificationException if the JSON is malformed or violates schema constraints
     */
    public ParsedClassification validate(String jsonText, String expectedId) {
        if (jsonText == null || jsonText.isBlank()) {
            throw new ClassificationException("Gemini returned empty or blank response for id=" + expectedId);
        }

        String cleaned = jsonText.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        JsonNode root;
        try {
            root = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new ClassificationException("Malformed JSON from Gemini for id=" + expectedId + ": " + e.getMessage(), e);
        }

        if (!root.isObject()) {
            throw new ClassificationException("Root JSON must be an object for id=" + expectedId);
        }

        // 1. comment_id
        String commentId = root.path("comment_id").asText("");
        if (commentId.isBlank()) {
            commentId = expectedId; // default to expected ID if omitted
        }

        // 2. overall_sentiment
        String overallSentiment = root.path("overall_sentiment").asText("").toLowerCase().trim();
        if (!ALLOWED_OVERALL_SENTIMENTS.contains(overallSentiment)) {
            throw new ClassificationException("Invalid overall_sentiment '" + overallSentiment + "' for id=" + expectedId);
        }

        // 3. confidence
        if (!root.has("confidence") || !root.get("confidence").isNumber()) {
            throw new ClassificationException("Missing or invalid numeric 'confidence' for id=" + expectedId);
        }
        double confidence = root.get("confidence").asDouble();
        if (confidence < 0.0 || confidence > 1.0) {
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }

        // 4. needs_human_review
        boolean needsHumanReview = root.path("needs_human_review").asBoolean(false);

        // 5. contains_suggestion
        boolean containsSuggestion = root.path("contains_suggestion").asBoolean(false);

        // 6. themes
        List<ClassificationResult.ThemeResult> themes = new ArrayList<>();
        JsonNode themesNode = root.path("themes");
        if (themesNode.isArray()) {
            if (themesNode.size() > 3) {
                // Constraint: max 3 themes
                needsHumanReview = true;
            }
            int count = 0;
            for (JsonNode item : themesNode) {
                if (count >= 3) break; // keep top 3
                String themeName = item.path("theme").asText("").trim();
                if (!ALLOWED_THEMES.contains(themeName)) {
                    themeName = "other";
                    needsHumanReview = true;
                }
                String themeSentiment = item.path("sentiment").asText("").toLowerCase().trim();
                if (!ALLOWED_SENTIMENTS.contains(themeSentiment)) {
                    themeSentiment = "neutral";
                }
                String evidence = item.path("evidence").asText("").trim();
                Boolean primary = item.has("primary") ? item.get("primary").asBoolean() : null;

                themes.add(new ClassificationResult.ThemeResult(themeName, themeSentiment, evidence, primary));
                count++;
            }
        }

        // If confidence < 0.6 or theme has "other", automatically flag for review
        if (confidence < 0.6) {
            needsHumanReview = true;
        }
        for (ClassificationResult.ThemeResult tr : themes) {
            if ("other".equalsIgnoreCase(tr.getTheme())) {
                needsHumanReview = true;
                break;
            }
        }

        return new ParsedClassification(commentId, themes, overallSentiment, confidence,
                needsHumanReview, containsSuggestion);
    }

    public static class ParsedClassification {
        public final String commentId;
        public final List<ClassificationResult.ThemeResult> themes;
        public final String overallSentiment;
        public final double confidence;
        public final boolean needsHumanReview;
        public final boolean containsSuggestion;

        public ParsedClassification(String commentId, List<ClassificationResult.ThemeResult> themes,
                                    String overallSentiment, double confidence,
                                    boolean needsHumanReview, boolean containsSuggestion) {
            this.commentId = commentId;
            this.themes = themes;
            this.overallSentiment = overallSentiment;
            this.confidence = confidence;
            this.needsHumanReview = needsHumanReview;
            this.containsSuggestion = containsSuggestion;
        }
    }
}

