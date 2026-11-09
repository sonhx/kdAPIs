package com.surveycomments.classify;

import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.redact.RedactedComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Production implementation of {@link CommentClassifier} utilizing Google Gemini API.
 *
 * <p>Key features:
 * <ul>
 *   <li>Prompts stamped with codebook version and prompt version.</li>
 *   <li>Strict rate limiting (RPM) and exponential backoff retry for transient errors / HTTP 429.</li>
 *   <li>SHA-256 hash caching of classification outputs.</li>
 *   <li>Local schema validation against {@code schema.json} constraints.</li>
 *   <li>Review queue routing for confidence &lt; 0.6, theme "other", or {@code needs_human_review=true}.</li>
 * </ul>
 */
@Service
@Primary
public class GeminiCommentClassifier implements CommentClassifier {

    private static final Logger log = LoggerFactory.getLogger(GeminiCommentClassifier.class);

    @Autowired
    private GeminiClientService geminiClientService;

    @Autowired
    private ClassificationCache cache;

    @Autowired
    private LocalSchemaValidator schemaValidator;

    @Autowired
    private SurveyCommentsProperties props;

    private volatile RateLimiter rateLimiter;
    private volatile String cachedCodebookContent;

    public GeminiCommentClassifier() {
    }

    public GeminiCommentClassifier(GeminiClientService geminiClientService,
                                   ClassificationCache cache,
                                   LocalSchemaValidator schemaValidator,
                                   SurveyCommentsProperties props) {
        this.geminiClientService = geminiClientService;
        this.cache = cache;
        this.schemaValidator = schemaValidator;
        this.props = props;
    }

    private synchronized RateLimiter getRateLimiter() {
        if (rateLimiter == null) {
            int rpm = props.getGemini().getRequestsPerMinute();
            rateLimiter = new RateLimiter(rpm);
            log.info("[survey-comments] Initialized RateLimiter with RPM={}", rpm);
        }
        return rateLimiter;
    }

    private String getCodebookContent() {
        if (cachedCodebookContent == null) {
            synchronized (this) {
                if (cachedCodebookContent == null) {
                    try {
                        ClassPathResource res = new ClassPathResource("survey-comments/codebook.md");
                        try (InputStream in = res.getInputStream()) {
                            cachedCodebookContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    } catch (Exception e) {
                        log.warn("[survey-comments] Failed to load codebook.md from classpath: {}", e.getMessage());
                        cachedCodebookContent = "(Default codebook rules)";
                    }
                }
            }
        }
        return cachedCodebookContent;
    }

    @Override
    public ClassificationResult classify(RedactedComment comment) {
        String commentId = comment.getCommentId();
        String text = comment.getRedactedText();

        String codebookVersion = props.getCodebookVersion();
        String promptVersion = props.getPromptVersion();
        String model = props.getGemini().getModel();
        if (model == null || model.isBlank()) {
            model = "gemini-3.8-flash"; // default stable model
        }

        // 1. Check Cache
        Optional<ClassificationResult> cached = cache.get(text, codebookVersion, promptVersion, model);
        if (cached.isPresent()) {
            ClassificationResult c = cached.get();
            // Return cached result with current commentId and cohort
            return new ClassificationResult(
                    commentId,
                    comment.getCohort(),
                    c.getThemes(),
                    c.getOverallSentiment(),
                    c.getConfidence(),
                    c.isNeedsHumanReview(),
                    c.isContainsSuggestion(),
                    c.getModel(),
                    c.getPromptVersion(),
                    c.getCodebookVersion(),
                    0, 0 // cached call consumes 0 API tokens
            );
        }

        // 2. Build Prompt & System Instruction
        String systemInstruction = buildSystemInstruction();
        String prompt = buildPrompt(commentId, text);

        int maxRetries = Math.max(1, props.getGemini().getMaxRetries());
        long backoffMs = 1500L;

        LocalSchemaValidator.ParsedClassification parsed = null;
        int promptTokens = 0;
        int candidateTokens = 0;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Apply Rate Limiting
            getRateLimiter().acquire();

            try {
                GeminiGenerationResponse response = geminiClientService.generate(
                        model,
                        prompt,
                        systemInstruction,
                        "application/json",
                        0.1 // low temperature for deterministic classification
                );

                promptTokens = response.getPromptTokens();
                candidateTokens = response.getCandidateTokens();

                // 3. Local Schema Validation
                parsed = schemaValidator.validate(response.getText(), commentId);
                break; // success!

            } catch (Exception e) {
                log.warn("[survey-comments] Attempt {}/{} failed for id={}: {}",
                        attempt, maxRetries, commentId, e.getMessage());

                if (attempt == maxRetries) {
                    throw new ClassificationException(
                            "Failed to classify comment id=" + commentId + " after " + maxRetries + " attempts: " + e.getMessage(), e);
                }

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ClassificationException("Classification interrupted for id=" + commentId, ie);
                }
                backoffMs *= 2; // exponential backoff
            }
        }

        ClassificationResult result = new ClassificationResult(
                commentId,
                comment.getCohort(),
                parsed.themes,
                parsed.overallSentiment,
                parsed.confidence,
                parsed.needsHumanReview,
                parsed.containsSuggestion,
                model,
                promptVersion,
                codebookVersion,
                promptTokens,
                candidateTokens
        );

        // 4. Store in cache
        cache.put(text, codebookVersion, promptVersion, model, result);

        return result;
    }

    private String buildSystemInstruction() {
        return "Bạn là một hệ thống phân loại chất lượng giáo dục tự động cho phản hồi của sinh viên Việt Nam.\n"
                + "Bạn phải tuân thủ nghiêm ngặt bảng mã chủ đề (codebook) và quy tắc ngôn ngữ sau:\n"
                + getCodebookContent() + "\n\n"
                + "Yêu cầu cấu trúc JSON đầu ra:\n"
                + "{\n"
                + "  \"comment_id\": \"id_cua_comment\",\n"
                + "  \"themes\": [\n"
                + "    {\n"
                + "      \"theme\": \"ten_theme_trong_codebook\",\n"
                + "      \"sentiment\": \"positive | neutral | negative\",\n"
                + "      \"evidence\": \"trích dẫn NGUYÊN VĂN từ ý kiến sinh viên (không dịch, không sửa)\",\n"
                + "      \"primary\": true\n"
                + "    }\n"
                + "  ],\n"
                + "  \"overall_sentiment\": \"positive | neutral | negative | mixed\",\n"
                + "  \"confidence\": 0.95,\n"
                + "  \"needs_human_review\": false,\n"
                + "  \"contains_suggestion\": false\n"
                + "}\n"
                + "QUY TẮC BẮT BUỘC:\n"
                + "- Tối đa 3 chủ đề trong themes (nếu có nhiều hơn, chỉ lấy 3 chủ đề quan trọng nhất và đặt needs_human_review = true).\n"
                + "- Nếu câu chỉ mang tính cảm thán chung hoặc không có chủ đề rõ ràng (như 'Tốt.', 'Cũng được.', 'Bình thường.'), trả về themes là mảng rỗng [] và xác định overall_sentiment tương ứng.\n"
                + "- Trích dẫn 'evidence' phải là câu trích dẫn CHÍNH XÁC từ văn bản đầu vào.\n"
                + "- Đặt contains_suggestion = true nếu sinh viên đưa ra kiến nghị/đề xuất/mong muốn thay đổi.";
    }

    private String buildPrompt(String commentId, String text) {
        return "Phân loại ý kiến sinh viên sau và trả về kết quả JSON:\n"
                + "comment_id: \"" + commentId + "\"\n"
                + "comment_text: \"" + text.replace("\"", "\\\"") + "\"";
    }
}

