package com.surveycomments.evaluate;

import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.run.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link EvaluationService} calculating per-theme precision/recall/F1,
 * macro/micro F1, sentiment accuracy, Cohen's kappa, and disagreement list.
 */
@Service
public class EvaluationServiceImpl implements EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationServiceImpl.class);

    private static final List<String> SENTIMENT_CLASSES =
            List.of("positive", "neutral", "negative", "mixed");

    @Autowired
    private RunRepository runRepository;

    public EvaluationServiceImpl() {}

    public EvaluationServiceImpl(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public EvaluationReport evaluate(String runId, InputStream goldCsvIn) {
        log.info("[survey-comments] Starting evaluation for runId={}", runId);

        List<ClassificationResult> results = runRepository.findResultsByRunId(runId);
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("No classification results found for runId: " + runId);
        }

        Map<String, ClassificationResult> predMap = results.stream()
                .collect(Collectors.toMap(ClassificationResult::getCommentId, r -> r, (a, b) -> a));

        Map<String, GoldRow> goldMap = parseGoldCsv(goldCsvIn);

        List<EvaluationReport.Disagreement> disagreements = new ArrayList<>();
        int matched = 0;
        int sentimentMatches = 0;
        int suggestionMatches = 0;

        // Sentiment marginal distributions for Cohen's Kappa
        Map<String, Integer> goldSentimentCounts = new HashMap<>();
        Map<String, Integer> predSentimentCounts = new HashMap<>();
        for (String c : SENTIMENT_CLASSES) {
            goldSentimentCounts.put(c, 0);
            predSentimentCounts.put(c, 0);
        }

        // Distinct themes union
        Set<String> allThemes = new TreeSet<>();

        // Store per-comment theme sets for matched rows
        List<Set<String>> matchedGoldThemes = new ArrayList<>();
        List<Set<String>> matchedPredThemes = new ArrayList<>();

        for (Map.Entry<String, GoldRow> entry : goldMap.entrySet()) {
            String commentId = entry.getKey();
            GoldRow gold = entry.getValue();

            ClassificationResult pred = predMap.get(commentId);
            if (pred == null) {
                continue;
            }
            matched++;

            Set<String> gThemes = gold.themes;
            Set<String> pThemes = pred.getThemes().stream()
                    .map(t -> t.getTheme().toLowerCase().trim())
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toCollection(TreeSet::new));

            allThemes.addAll(gThemes);
            allThemes.addAll(pThemes);

            matchedGoldThemes.add(gThemes);
            matchedPredThemes.add(pThemes);

            String gSent = gold.overallSentiment.toLowerCase().trim();
            String pSent = pred.getOverallSentiment() != null
                    ? pred.getOverallSentiment().toLowerCase().trim()
                    : "";

            boolean gSug = gold.containsSuggestion;
            boolean pSug = pred.isContainsSuggestion();

            boolean sntMatch = gSent.equals(pSent);
            boolean sugMatch = (gSug == pSug);
            boolean thmMatch = gThemes.equals(pThemes);

            if (sntMatch) sentimentMatches++;
            if (sugMatch) suggestionMatches++;

            goldSentimentCounts.put(gSent, goldSentimentCounts.getOrDefault(gSent, 0) + 1);
            predSentimentCounts.put(pSent, predSentimentCounts.getOrDefault(pSent, 0) + 1);

            if (!sntMatch || !sugMatch || !thmMatch) {
                List<String> evidence = pred.getThemes().stream()
                        .map(ClassificationResult.ThemeResult::getEvidence)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                disagreements.add(new EvaluationReport.Disagreement(
                        commentId,
                        String.join(";", gThemes),
                        String.join(";", pThemes),
                        gSent,
                        pSent,
                        gSug,
                        pSug,
                        evidence
                ));
            }
        }

        if (matched == 0) {
            throw new IllegalArgumentException("No overlapping comment_id found between gold set and run results");
        }

        // 1. Sentiment Accuracy & Cohen's Kappa
        double sentimentAccuracy = (double) sentimentMatches / matched;
        double po = sentimentAccuracy;
        double pe = 0.0;
        for (String c : SENTIMENT_CLASSES) {
            double pGold = (double) goldSentimentCounts.getOrDefault(c, 0) / matched;
            double pPred = (double) predSentimentCounts.getOrDefault(c, 0) / matched;
            pe += (pGold * pPred);
        }
        double cohenKappa = (1.0 - pe == 0.0) ? 1.0 : (po - pe) / (1.0 - pe);

        // 2. Suggestion Accuracy
        double suggestionAccuracy = (double) suggestionMatches / matched;

        // 3. Per-Theme Metrics
        List<EvaluationReport.ThemeMetric> themeMetrics = new ArrayList<>();
        int totalTp = 0;
        int totalFp = 0;
        int totalFn = 0;
        double sumF1 = 0.0;
        int themesWithSupport = 0;

        for (String theme : allThemes) {
            int tp = 0;
            int fp = 0;
            int fn = 0;

            for (int i = 0; i < matched; i++) {
                boolean inGold = matchedGoldThemes.get(i).contains(theme);
                boolean inPred = matchedPredThemes.get(i).contains(theme);
                if (inGold && inPred) tp++;
                else if (!inGold && inPred) fp++;
                else if (inGold && !inPred) fn++;
            }

            int support = tp + fn;
            double precision = (tp + fp > 0) ? (double) tp / (tp + fp) : 0.0;
            double recall = (tp + fn > 0) ? (double) tp / (tp + fn) : 0.0;
            double f1 = (precision + recall > 0) ? (2.0 * precision * recall) / (precision + recall) : 0.0;

            themeMetrics.add(new EvaluationReport.ThemeMetric(
                    theme, precision, recall, f1, tp, fp, fn, support));

            totalTp += tp;
            totalFp += fp;
            totalFn += fn;

            if (support > 0) {
                sumF1 += f1;
                themesWithSupport++;
            }
        }

        // 4. Macro and Micro F1
        double macroF1 = (themesWithSupport > 0) ? sumF1 / themesWithSupport : 0.0;
        double microPrecision = (totalTp + totalFp > 0) ? (double) totalTp / (totalTp + totalFp) : 0.0;
        double microRecall = (totalTp + totalFn > 0) ? (double) totalTp / (totalTp + totalFn) : 0.0;
        double microF1 = (microPrecision + microRecall > 0)
                ? (2.0 * microPrecision * microRecall) / (microPrecision + microRecall)
                : 0.0;

        log.info("[survey-comments] Evaluation completed: matched={}/{} sntAcc={}% kappa={} macroF1={} microF1={} disagreements={}",
                matched, goldMap.size(),
                String.format("%.1f", sentimentAccuracy * 100.0),
                String.format("%.3f", cohenKappa),
                String.format("%.3f", macroF1),
                String.format("%.3f", microF1),
                disagreements.size());

        return new EvaluationReport(
                runId,
                goldMap.size(),
                matched,
                sentimentAccuracy,
                cohenKappa,
                macroF1,
                microF1,
                suggestionAccuracy,
                themeMetrics,
                disagreements.size(),
                disagreements
        );
    }

    // -------------------------------------------------------------------------
    // CSV Parsing Helper
    // -------------------------------------------------------------------------

    private static class GoldRow {
        final Set<String> themes;
        final String overallSentiment;
        final boolean containsSuggestion;

        GoldRow(Set<String> themes, String overallSentiment, boolean containsSuggestion) {
            this.themes = themes;
            this.overallSentiment = overallSentiment;
            this.containsSuggestion = containsSuggestion;
        }
    }

    private Map<String, GoldRow> parseGoldCsv(InputStream in) {
        Map<String, GoldRow> map = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return map;

            // Strip UTF-8 BOM if present
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            List<String> headers = parseCsvLine(headerLine);
            int idIdx = findColumn(headers, "comment_id");
            int themesIdx = findColumn(headers, "themes");
            int sentimentIdx = findColumn(headers, "overall_sentiment");
            int suggestionIdx = findColumn(headers, "contains_suggestion");

            if (idIdx == -1 || sentimentIdx == -1) {
                throw new IllegalArgumentException("Gold CSV must contain 'comment_id' and 'overall_sentiment' columns");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);
                if (cols.size() <= idIdx) continue;

                String id = cols.get(idIdx).trim();
                if (id.isEmpty()) continue;

                String rawThemes = (themesIdx != -1 && cols.size() > themesIdx) ? cols.get(themesIdx).trim() : "";
                Set<String> themes = new TreeSet<>();
                if (!rawThemes.isEmpty()) {
                    for (String t : rawThemes.split("[;,]")) {
                        String clean = t.trim().toLowerCase();
                        if (!clean.isEmpty()) themes.add(clean);
                    }
                }

                String snt = (sentimentIdx != -1 && cols.size() > sentimentIdx)
                        ? cols.get(sentimentIdx).trim().toLowerCase()
                        : "neutral";

                boolean sug = (suggestionIdx != -1 && cols.size() > suggestionIdx)
                        && Boolean.parseBoolean(cols.get(suggestionIdx).trim());

                map.put(id, new GoldRow(themes, snt, sug));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read gold CSV: " + e.getMessage(), e);
        }
        return map;
    }

    private static int findColumn(List<String> headers, String target) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).trim().equalsIgnoreCase(target)) return i;
        }
        return -1;
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
