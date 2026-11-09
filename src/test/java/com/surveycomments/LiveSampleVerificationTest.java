package com.surveycomments;

import com.surveycomments.clean.CleanComment;
import com.surveycomments.clean.CleanServiceImpl;
import com.surveycomments.classify.*;
import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.ingest.IngestServiceImpl;
import com.surveycomments.ingest.RawComment;
import com.surveycomments.redact.RedactServiceImpl;
import com.surveycomments.redact.RedactedComment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Live verification test for Task T4 running the real Gemini API against sample_fake_comments.csv.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Only runs when the {@code GEMINI_API_KEY} environment variable is present (skipped otherwise).</li>
 *   <li>Validates that PII is redacted BEFORE sending to Gemini (AGENT_RULES rule 5).</li>
 *   <li>Compares outputs against {@code sample_gold_fake.csv}.</li>
 * </ul>
 */
public class LiveSampleVerificationTest {

    @Test
    void runLiveVerificationAgainstSampleFakeComments() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("\n[NOTICE] GEMINI_API_KEY environment variable is not set. Skipping live LLM call.");
            System.err.println("To run live verification, set GEMINI_API_KEY in your environment:\n"
                    + "  PowerShell: $env:GEMINI_API_KEY=\"<your-api-key>\"\n"
                    + "  or Windows User Environment Variable GEMINI_API_KEY\n");
        }
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Skipping live test: GEMINI_API_KEY not set in environment");

        // 1. Pipeline setup
        SurveyCommentsProperties props = new SurveyCommentsProperties();
        props.getGemini().setRequestsPerMinute(15);
        props.getGemini().setMaxRetries(2);

        IngestServiceImpl ingestService = new IngestServiceImpl();
        CleanServiceImpl cleanService = new CleanServiceImpl();
        RedactServiceImpl redactService = new RedactServiceImpl();

        GeminiClientService clientService = new GeminiClientServiceImpl();
        LocalSchemaValidator schemaValidator = new LocalSchemaValidator();
        ClassificationCache cache = new ClassificationCache();

        GeminiCommentClassifier classifier = new GeminiCommentClassifier(
                clientService, cache, schemaValidator, props);

        // 2. Load input CSV
        ClassPathResource inputRes = new ClassPathResource("survey-comments/sample_fake_comments.csv");
        List<RawComment> rawComments = ingestService.ingest(inputRes.getInputStream(), "sample_fake_comments.csv", "demo-2026-01");

        // 3. Clean
        List<CleanComment> cleanComments = cleanService.clean(rawComments);

        // 4. Redact
        List<RedactedComment> redactedComments = redactService.redact(cleanComments);

        // Verify no raw PII in redacted text
        for (RedactedComment r : redactedComments) {
            assertFalse(r.getRedactedText().contains("0912 345 678"), "Phone must be redacted");
            assertFalse(r.getRedactedText().contains("an.nguyen@example.com"), "Email must be redacted");
            assertFalse(r.getRedactedText().contains("20251234"), "MSSV must be redacted");
        }

        // 5. Load Gold labels for comparison
        ClassPathResource goldRes = new ClassPathResource("survey-comments/sample_gold_fake.csv");
        Map<String, Map<String, String>> goldMap = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(goldRes.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);
                if (!cols.isEmpty()) {
                    String id = cols.get(0).trim();
                    Map<String, String> row = new HashMap<>();
                    row.put("themes", cols.size() > 2 ? cols.get(2).trim() : "");
                    row.put("theme_sentiments", cols.size() > 3 ? cols.get(3).trim() : "");
                    row.put("overall_sentiment", cols.size() > 4 ? cols.get(4).trim() : "");
                    row.put("contains_suggestion", cols.size() > 5 ? cols.get(5).trim() : "false");
                    row.put("notes", cols.size() > 6 ? cols.get(6).trim() : "");
                    goldMap.put(id, row);
                }
            }
        }

        // 6. Run Classification on all redacted comments
        System.out.println("\n==========================================================================================");
        System.out.println("LIVE CLASSIFICATION VERIFICATION: sample_fake_comments.csv (" + redactedComments.size() + " items)");
        System.out.println("==========================================================================================");
        System.out.printf("%-6s | %-18s | %-16s | %-8s | %-12s | %-8s | %s%n",
                "ID", "PRED THEMES", "GOLD THEMES", "PRED SNT", "GOLD SNT", "SUGGEST", "MATCH / REVIEW");
        System.out.println("------------------------------------------------------------------------------------------");

        int sentimentMatches = 0;
        int themeMatches = 0;
        int reviewCount = 0;

        for (RedactedComment comment : redactedComments) {
            ClassificationResult result = classifier.classify(comment);
            assertNotNull(result);

            String id = comment.getCommentId();
            Map<String, String> gold = goldMap.getOrDefault(id, Collections.emptyMap());

            String predThemes = result.getThemes().stream()
                    .map(ClassificationResult.ThemeResult::getTheme)
                    .collect(Collectors.joining(";"));
            String goldThemes = gold.getOrDefault("themes", "");
            String predSentiment = result.getOverallSentiment();
            String goldSentiment = gold.getOrDefault("overall_sentiment", "");
            boolean predSug = result.isContainsSuggestion();
            boolean goldSug = Boolean.parseBoolean(gold.getOrDefault("contains_suggestion", "false"));

            boolean sntMatch = predSentiment.equalsIgnoreCase(goldSentiment);
            boolean thmMatch = predThemes.equalsIgnoreCase(goldThemes);
            if (sntMatch) sentimentMatches++;
            if (thmMatch) themeMatches++;
            if (result.isNeedsHumanReview()) reviewCount++;

            String matchStatus = (sntMatch && thmMatch) ? "MATCH" : (result.isNeedsHumanReview() ? "IN_REVIEW_QUEUE" : "DIFF");

            System.out.printf("%-6s | %-18s | %-16s | %-8s | %-12s | %-8s | %s%n",
                    id,
                    truncate(predThemes, 18),
                    truncate(goldThemes, 16),
                    predSentiment,
                    goldSentiment,
                    predSug + (predSug == goldSug ? " (ok)" : " (diff)"),
                    matchStatus);
        }

        System.out.println("------------------------------------------------------------------------------------------");
        System.out.printf("Summary: Total=%d | Theme exact matches=%d/%d (%.1f%%) | Sentiment matches=%d/%d (%.1f%%) | In Review Queue=%d%n",
                redactedComments.size(),
                themeMatches, redactedComments.size(), (themeMatches * 100.0 / redactedComments.size()),
                sentimentMatches, redactedComments.size(), (sentimentMatches * 100.0 / redactedComments.size()),
                reviewCount);
        System.out.println("==========================================================================================\n");
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 2) + "..";
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result;
    }
}
