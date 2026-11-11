package com.surveycomments.report;

import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.run.Run;
import com.surveycomments.run.RunRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production implementation of {@link ReportService} using Apache POI (5.2.3).
 *
 * <p>Generates rich Excel (.xlsx) workbooks containing:
 * <ol>
 *   <li><b>Tổng quan & Chủ đề</b>: Share of commenters per theme (>100%), per-theme sentiment split, evidence quotes.</li>
 *   <li><b>Phân tích theo khóa</b>: Cohort-level theme and sentiment breakdown.</li>
 *   <li><b>Kiến nghị & Đề xuất</b>: Filtered items with {@code contains_suggestion=true}.</li>
 *   <li><b>Hàng đợi kiểm duyệt</b>: Review queue items (confidence &lt; 0.6, needs_review, or "other").</li>
 *   <li><b>Mẫu kiểm tra ngẫu nhiên</b>: Deterministic 5% random audit sample.</li>
 * </ol>
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private static final Map<String, String> THEME_NAMES = Map.ofEntries(
            Map.entry("course_content", "Nội dung môn học / chương trình"),
            Map.entry("teaching", "Giảng viên & giảng dạy"),
            Map.entry("assessment", "Thi cử, kiểm tra, đánh giá"),
            Map.entry("schedule", "Lịch học, thời khóa biểu"),
            Map.entry("facilities", "Cơ sở vật chất học tập"),
            Map.entry("campus_life", "Tiện ích & môi trường khuôn viên"),
            Map.entry("online_systems", "Hệ thống trực tuyến"),
            Map.entry("student_support", "Hỗ trợ sinh viên"),
            Map.entry("admin_fees", "Thủ tục hành chính & học phí"),
            Map.entry("career_activities", "Hướng nghiệp & hoạt động"),
            Map.entry("other", "Khác")
    );

    @Autowired
    private RunRepository runRepository;

    public ReportServiceImpl() {}

    public ReportServiceImpl(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public void generateExcelReport(String runId, OutputStream out) {
        log.info("[survey-comments] Generating Excel report for runId={}", runId);

        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        List<ClassificationResult> results = runRepository.findResultsByRunId(runId);
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("No classified results found for runId: " + runId);
        }

        try (Workbook wb = new XSSFWorkbook()) {
            // Cell styles
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle subHeaderStyle = createSubHeaderStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);
            CellStyle percentStyle = createPercentStyle(wb);
            CellStyle boldStyle = createBoldStyle(wb);

            // 1. Sheet 1: Tổng quan & Chủ đề
            buildOverviewSheet(wb, run, results, headerStyle, subHeaderStyle, dataStyle, percentStyle, boldStyle);

            // 2. Sheet 2: Phân tích theo khóa
            buildCohortSheet(wb, results, headerStyle, dataStyle, boldStyle);

            // 3. Sheet 3: Kiến nghị & Đề xuất
            buildSuggestionsSheet(wb, results, headerStyle, dataStyle);

            // 4. Sheet 4: Hàng đợi kiểm duyệt
            buildReviewQueueSheet(wb, results, headerStyle, dataStyle);

            // 5. Sheet 5: Mẫu kiểm tra ngẫu nhiên (5%)
            buildSpotCheckSheet(wb, runId, results, headerStyle, dataStyle);

            wb.write(out);
            log.info("[survey-comments] Excel report successfully generated for runId={}", runId);
        } catch (Exception e) {
            log.error("[survey-comments] Failed to generate Excel report: {}", e.getMessage(), e);
            throw new RuntimeException("Excel report generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void generateCsvReport(String runId, OutputStream out) {
        log.info("[survey-comments] Generating CSV summary report for runId={}", runId);

        List<ClassificationResult> results = runRepository.findResultsByRunId(runId);
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("No classified results found for runId: " + runId);
        }

        int total = results.size();
        Map<String, ThemeStats> statsMap = computeThemeStats(results);

        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            // UTF-8 BOM for Microsoft Excel
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);

            writer.println("theme_id,theme_name_vi,mentions,commenter_share_pct,positive,neutral,negative,mixed");
            for (Map.Entry<String, ThemeStats> e : statsMap.entrySet()) {
                ThemeStats s = e.getValue();
                double share = total > 0 ? (s.mentions * 100.0 / total) : 0.0;
                writer.printf("\"%s\",\"%s\",%d,%.1f%%,%d,%d,%d,%d%n",
                        e.getKey(),
                        THEME_NAMES.getOrDefault(e.getKey(), e.getKey()),
                        s.mentions,
                        share,
                        s.positive,
                        s.neutral,
                        s.negative,
                        s.mixed);
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV report generation failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Sheet Builders
    // -------------------------------------------------------------------------

    private void buildOverviewSheet(Workbook wb, Run run, List<ClassificationResult> results,
                                    CellStyle headerStyle, CellStyle subHeaderStyle,
                                    CellStyle dataStyle, CellStyle percentStyle, CellStyle boldStyle) {
        Sheet sheet = wb.createSheet("Tổng quan & Chủ đề");
        int rowIdx = 0;

        // Title
        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("BÁO CÁO PHÂN TÍCH Ý KIẾN KHẢO SÁT SINH VIÊN");
        titleCell.setCellStyle(boldStyle);

        rowIdx++; // blank line

        // Metadata
        createMetaRow(sheet, rowIdx++, "Mã đợt phân tích (Run ID):", run.getRunId(), boldStyle, dataStyle);
        createMetaRow(sheet, rowIdx++, "Mã khảo sát (Survey ID):", run.getSurveyId(), boldStyle, dataStyle);
        createMetaRow(sheet, rowIdx++, "Tổng số ý kiến phân tích:", String.valueOf(results.size()), boldStyle, dataStyle);

        // Overall Sentiment breakdown
        long posCount = results.stream().filter(r -> "positive".equalsIgnoreCase(r.getOverallSentiment())).count();
        long neuCount = results.stream().filter(r -> "neutral".equalsIgnoreCase(r.getOverallSentiment())).count();
        long negCount = results.stream().filter(r -> "negative".equalsIgnoreCase(r.getOverallSentiment())).count();
        long mixCount = results.stream().filter(r -> "mixed".equalsIgnoreCase(r.getOverallSentiment())).count();
        int total = results.size();

        String sentSummary = String.format("Tích cực: %d (%.1f%%) | Trung lập: %d (%.1f%%) | Tiêu cực: %d (%.1f%%) | Hỗn hợp: %d (%.1f%%)",
                posCount, total > 0 ? (posCount * 100.0 / total) : 0.0,
                neuCount, total > 0 ? (neuCount * 100.0 / total) : 0.0,
                negCount, total > 0 ? (negCount * 100.0 / total) : 0.0,
                mixCount, total > 0 ? (mixCount * 100.0 / total) : 0.0);
        createMetaRow(sheet, rowIdx++, "Cảm xúc chung toàn đợt:", sentSummary, boldStyle, dataStyle);

        rowIdx++; // blank line

        // Note on multi-label
        Row noteRow = sheet.createRow(rowIdx++);
        Cell noteCell = noteRow.createCell(0);
        noteCell.setCellValue("* Lưu ý: Mỗi ý kiến có thể đề cập đến nhiều chủ đề (tối đa 3), do đó tổng tỷ lệ người góp ý có thể vượt quá 100%.");

        // Theme table headers
        Row thRow = sheet.createRow(rowIdx++);
        String[] headers = {
                "STT", "Mã chủ đề", "Tên chủ đề tiếng Việt", "Số lượt nhắc", "Tỷ lệ SV đề cập",
                "Tích cực", "Trung lập", "Tiêu cực", "Hỗn hợp", "Trích dẫn tiêu biểu"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell c = thRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        // Theme rows
        Map<String, ThemeStats> statsMap = computeThemeStats(results);
        int stt = 1;
        for (Map.Entry<String, ThemeStats> entry : statsMap.entrySet()) {
            Row r = sheet.createRow(rowIdx++);
            ThemeStats s = entry.getValue();

            createCell(r, 0, stt++, dataStyle);
            createCell(r, 1, entry.getKey(), dataStyle);
            createCell(r, 2, THEME_NAMES.getOrDefault(entry.getKey(), entry.getKey()), dataStyle);
            createCell(r, 3, s.mentions, dataStyle);

            Cell shareCell = r.createCell(4);
            shareCell.setCellValue(total > 0 ? (double) s.mentions / total : 0.0);
            shareCell.setCellStyle(percentStyle);

            createCell(r, 5, s.positive, dataStyle);
            createCell(r, 6, s.neutral, dataStyle);
            createCell(r, 7, s.negative, dataStyle);
            createCell(r, 8, s.mixed, dataStyle);

            String quotes = String.join(" | ", s.sampleEvidence.stream().limit(2).toList());
            createCell(r, 9, quotes, dataStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildCohortSheet(Workbook wb, List<ClassificationResult> results,
                                  CellStyle headerStyle, CellStyle dataStyle, CellStyle boldStyle) {
        Sheet sheet = wb.createSheet("Phân tích theo khóa");

        // Distinct cohorts
        Set<String> cohorts = results.stream()
                .map(ClassificationResult::getCohort)
                .map(c -> (c == null || c.isBlank()) ? "Chưa rõ" : c)
                .collect(Collectors.toCollection(TreeSet::new));

        Row th = sheet.createRow(0);
        String[] headers = {"Khóa", "Tổng ý kiến", "Tích cực", "Trung lập", "Tiêu cực", "Hỗn hợp", "Có kiến nghị"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = th.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (String cohort : cohorts) {
            List<ClassificationResult> cohortResults = results.stream()
                    .filter(r -> {
                        String c = r.getCohort();
                        if (c == null || c.isBlank()) c = "Chưa rõ";
                        return c.equalsIgnoreCase(cohort);
                    })
                    .toList();

            Row r = sheet.createRow(rowIdx++);
            long pos = cohortResults.stream().filter(res -> "positive".equalsIgnoreCase(res.getOverallSentiment())).count();
            long neu = cohortResults.stream().filter(res -> "neutral".equalsIgnoreCase(res.getOverallSentiment())).count();
            long neg = cohortResults.stream().filter(res -> "negative".equalsIgnoreCase(res.getOverallSentiment())).count();
            long mix = cohortResults.stream().filter(res -> "mixed".equalsIgnoreCase(res.getOverallSentiment())).count();
            long sug = cohortResults.stream().filter(ClassificationResult::isContainsSuggestion).count();

            createCell(r, 0, cohort, boldStyle);
            createCell(r, 1, cohortResults.size(), dataStyle);
            createCell(r, 2, (int) pos, dataStyle);
            createCell(r, 3, (int) neu, dataStyle);
            createCell(r, 4, (int) neg, dataStyle);
            createCell(r, 5, (int) mix, dataStyle);
            createCell(r, 6, (int) sug, dataStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildSuggestionsSheet(Workbook wb, List<ClassificationResult> results,
                                       CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = wb.createSheet("Kiến nghị & Đề xuất");

        List<ClassificationResult> suggestions = results.stream()
                .filter(ClassificationResult::isContainsSuggestion)
                .toList();

        Row th = sheet.createRow(0);
        String[] headers = {"STT", "Mã ý kiến", "Câu hỏi khảo sát", "Khóa", "Cảm xúc chung", "Chủ đề", "Trích dẫn kiến nghị"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = th.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        int stt = 1;
        for (ClassificationResult r : suggestions) {
            Row row = sheet.createRow(rowIdx++);
            String themesStr = r.getThemes().stream()
                    .map(ClassificationResult.ThemeResult::getTheme)
                    .collect(Collectors.joining("; "));

            String evidenceStr = r.getThemes().stream()
                    .map(ClassificationResult.ThemeResult::getEvidence)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" | "));

            String qText = r.getQuestionText() != null && !r.getQuestionText().isBlank() ? r.getQuestionText() : "Ý kiến chung";

            createCell(row, 0, stt++, dataStyle);
            createCell(row, 1, r.getCommentId(), dataStyle);
            createCell(row, 2, qText, dataStyle);
            createCell(row, 3, r.getCohort(), dataStyle);
            createCell(row, 4, r.getOverallSentiment(), dataStyle);
            createCell(row, 5, themesStr, dataStyle);
            createCell(row, 6, evidenceStr, dataStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildReviewQueueSheet(Workbook wb, List<ClassificationResult> results,
                                       CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = wb.createSheet("Hàng đợi kiểm duyệt");

        List<ClassificationResult> queue = results.stream()
                .filter(r -> r.isNeedsHumanReview() || r.getConfidence() < 0.6 || hasOtherTheme(r))
                .toList();

        Row th = sheet.createRow(0);
        String[] headers = {"STT", "Mã ý kiến", "Câu hỏi khảo sát", "Khóa", "Độ tin cậy", "Cảm xúc", "Chủ đề", "Lý do kiểm duyệt"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = th.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        int stt = 1;
        for (ClassificationResult r : queue) {
            Row row = sheet.createRow(rowIdx++);
            String reason = r.isNeedsHumanReview() ? "LLM flagged review"
                    : (r.getConfidence() < 0.6 ? "Low confidence (<0.6)" : "Theme 'other'");

            String themesStr = r.getThemes().stream()
                    .map(ClassificationResult.ThemeResult::getTheme)
                    .collect(Collectors.joining("; "));

            String qText = r.getQuestionText() != null && !r.getQuestionText().isBlank() ? r.getQuestionText() : "Ý kiến chung";

            createCell(row, 0, stt++, dataStyle);
            createCell(row, 1, r.getCommentId(), dataStyle);
            createCell(row, 2, qText, dataStyle);
            createCell(row, 3, r.getCohort(), dataStyle);
            createCell(row, 4, String.format("%.2f", r.getConfidence()), dataStyle);
            createCell(row, 5, r.getOverallSentiment(), dataStyle);
            createCell(row, 6, themesStr, dataStyle);
            createCell(row, 7, reason, dataStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void buildSpotCheckSheet(Workbook wb, String runId, List<ClassificationResult> results,
                                     CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = wb.createSheet("Mẫu kiểm tra ngẫu nhiên");

        // 5% sample (minimum 1, deterministic seeded with runId hash)
        int sampleSize = Math.max(1, (int) Math.round(results.size() * 0.05));
        List<ClassificationResult> copy = new ArrayList<>(results);
        Collections.shuffle(copy, new Random(runId.hashCode()));
        List<ClassificationResult> sample = copy.stream().limit(sampleSize).toList();

        Row th = sheet.createRow(0);
        String[] headers = {"STT", "Mã ý kiến", "Câu hỏi khảo sát", "Khóa", "Cảm xúc", "Chủ đề & Cảm xúc", "Trích dẫn minh chứng"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = th.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        int stt = 1;
        for (ClassificationResult r : sample) {
            Row row = sheet.createRow(rowIdx++);
            String themeSnt = r.getThemes().stream()
                    .map(t -> t.getTheme() + ":" + t.getSentiment())
                    .collect(Collectors.joining("; "));

            String evidence = r.getThemes().stream()
                    .map(ClassificationResult.ThemeResult::getEvidence)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" | "));

            String qText = r.getQuestionText() != null && !r.getQuestionText().isBlank() ? r.getQuestionText() : "Ý kiến chung";

            createCell(row, 0, stt++, dataStyle);
            createCell(row, 1, r.getCommentId(), dataStyle);
            createCell(row, 2, qText, dataStyle);
            createCell(row, 3, r.getCohort(), dataStyle);
            createCell(row, 4, r.getOverallSentiment(), dataStyle);
            createCell(row, 5, themeSnt, dataStyle);
            createCell(row, 6, evidence, dataStyle);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // -------------------------------------------------------------------------
    // Stats calculation helper
    // -------------------------------------------------------------------------

    private static class ThemeStats {
        int mentions = 0;
        int positive = 0;
        int neutral = 0;
        int negative = 0;
        int mixed = 0;
        final List<String> sampleEvidence = new ArrayList<>();
    }

    private Map<String, ThemeStats> computeThemeStats(List<ClassificationResult> results) {
        Map<String, ThemeStats> map = new LinkedHashMap<>();

        // Initialize with default known themes in canonical order
        for (String k : THEME_NAMES.keySet()) {
            map.put(k, new ThemeStats());
        }

        for (ClassificationResult res : results) {
            for (ClassificationResult.ThemeResult t : res.getThemes()) {
                String key = t.getTheme().toLowerCase().trim();
                ThemeStats s = map.computeIfAbsent(key, k -> new ThemeStats());
                s.mentions++;

                String snt = t.getSentiment() != null ? t.getSentiment().toLowerCase().trim() : "neutral";
                switch (snt) {
                    case "positive" -> s.positive++;
                    case "negative" -> s.negative++;
                    case "mixed"    -> s.mixed++;
                    default         -> s.neutral++;
                }

                if (t.getEvidence() != null && !t.getEvidence().isBlank() && s.sampleEvidence.size() < 5) {
                    s.sampleEvidence.add(t.getEvidence());
                }
            }
        }
        return map;
    }

    private static boolean hasOtherTheme(ClassificationResult res) {
        return res.getThemes().stream().anyMatch(t -> "other".equalsIgnoreCase(t.getTheme()));
    }

    // -------------------------------------------------------------------------
    // Cell Creation & Styling Helpers
    // -------------------------------------------------------------------------

    private void createMetaRow(Sheet sheet, int rowIdx, String label, String val,
                               CellStyle labelStyle, CellStyle valStyle) {
        Row r = sheet.createRow(rowIdx);
        Cell c0 = r.createCell(0);
        c0.setCellValue(label);
        c0.setCellStyle(labelStyle);

        Cell c1 = r.createCell(1);
        c1.setCellValue(val);
        c1.setCellStyle(valStyle);
    }

    private void createCell(Row r, int col, Object val, CellStyle style) {
        Cell c = r.createCell(col);
        if (val instanceof Number n) {
            c.setCellValue(n.doubleValue());
        } else {
            c.setCellValue(val != null ? val.toString() : "");
        }
        c.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(f);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private CellStyle createSubHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        style.setFont(f);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        setBorders(style);
        return style;
    }

    private CellStyle createBoldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        style.setFont(f);
        return style;
    }

    private CellStyle createPercentStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat df = wb.createDataFormat();
        style.setDataFormat(df.getFormat("0.0%"));
        setBorders(style);
        return style;
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}

