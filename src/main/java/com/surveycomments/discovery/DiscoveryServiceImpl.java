package com.surveycomments.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surveycomments.clean.CleanComment;
import com.surveycomments.clean.CleanService;
import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.ingest.IngestService;
import com.surveycomments.ingest.RawComment;
import com.surveycomments.redact.RedactService;
import com.surveycomments.redact.RedactedComment;
import com.surveycomments.classify.GeminiClientService;
import com.surveycomments.classify.GeminiGenerationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link DiscoveryService}.
 * Prepares comments through ingest -> clean -> redact, then sends them in a long-context
 * request to Gemini to discover and propose improvements to the starter codebook.
 */
@Service
public class DiscoveryServiceImpl implements DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryServiceImpl.class);

    @Autowired
    private IngestService ingestService;

    @Autowired
    private CleanService cleanService;

    @Autowired
    private RedactService redactService;

    @Autowired
    private GeminiClientService geminiClientService;

    @Autowired
    private SurveyCommentsProperties props;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DiscoveryResult discoverThemes(InputStream inputStream, String filename, String surveyId, String modelId) {
        log.info("[survey-comments] Starting codebook discovery for file={} surveyId={}", filename, surveyId);

        // 1. Ingest, clean, and redact
        List<RawComment> raw = ingestService.ingest(inputStream, filename, surveyId);
        List<CleanComment> cleaned = cleanService.clean(raw);
        List<RedactedComment> redacted = redactService.redact(cleaned);

        // 2. Load existing codebook as baseline
        String currentCodebook = loadBaseCodebook();

        // 3. Build long-context prompt
        String prompt = buildDiscoveryPrompt(currentCodebook, redacted);
        String systemInstruction = buildSystemInstruction();

        String chosenModel = (modelId != null && !modelId.isBlank())
                ? modelId
                : props.getGemini().getModel();

        if (chosenModel == null || chosenModel.isBlank()) {
            chosenModel = "gemini-3.8-flash"; // default fallback model if unconfigured
        }

        // 4. Send request to Gemini
        GeminiGenerationResponse genResponse = geminiClientService.generate(
                chosenModel,
                prompt,
                systemInstruction,
                "application/json",
                0.2 // lower temperature for stability in schema/codebook synthesis
        );

        // 5. Parse discovered themes
        List<DraftTheme> themes = parseThemesFromJson(genResponse.getText());

        log.info("[survey-comments] Discovery completed: discoveredThemes={} promptTokens={} candidateTokens={}",
                themes.size(), genResponse.getPromptTokens(), genResponse.getCandidateTokens());

        return new DiscoveryResult(
                props.getCodebookVersion(),
                raw.size(),
                redacted.size(),
                chosenModel,
                themes,
                genResponse.getPromptTokens(),
                genResponse.getCandidateTokens(),
                genResponse.getText()
        );
    }

    private String loadBaseCodebook() {
        try {
            ClassPathResource res = new ClassPathResource("survey-comments/codebook.md");
            try (InputStream in = res.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("[survey-comments] Could not load codebook.md from classpath: {}", e.getMessage());
            return "(No base codebook available)";
        }
    }

    private String buildSystemInstruction() {
        return "Bạn là một chuyên gia phân tích chất lượng giáo dục đại học (IQA). "
                + "Nhiệm vụ của bạn là phân tích ý kiến khảo sát sinh viên bằng tiếng Việt, so sánh với khung chủ đề (codebook) hiện tại, "
                + "và đề xuất điều chỉnh bảng mã chủ đề (codebook) phù hợp nhất với dữ liệu thực tế.\n"
                + "Quy tắc:\n"
                + "1. Chỉ đề xuất các chủ đề thực sự phản ánh phản hồi của sinh viên.\n"
                + "2. Các hành động (action) đối với mỗi chủ đề gồm: KEEP (giữ nguyên), MERGE (hợp nhất), SPLIT (tách), NEW (thêm mới), REMOVE (loại bỏ vì không có phản hồi).\n"
                + "3. Trích dẫn đúng 2 ví dụ thực tế từ danh sách phản hồi đầu vào (copy chính xác câu nói của sinh viên, không dịch hay sửa đổi).\n"
                + "4. Trả về đúng định dạng JSON tuân thủ cấu trúc được yêu cầu.";
    }

    private String buildDiscoveryPrompt(String currentCodebook, List<RedactedComment> comments) {
        StringBuilder sb = new StringBuilder();
        sb.append("### BẢNG MÃ CHỦ ĐỀ HIỆN TẠI (BASE CODEBOOK):\n");
        sb.append(currentCodebook).append("\n\n");

        sb.append("### DANH SÁCH Ý KIẾN SINH VIÊN ĐÃ ĐƯỢC XỬ LÝ VÀ CHE THÔNG TIN CÁ NHÂN (REDACTED COMMENTS):\n");
        for (RedactedComment c : comments) {
            sb.append("- [").append(c.getCommentId()).append("] ").append(c.getRedactedText()).append("\n");
        }

        sb.append("\n### YÊU CẦU ĐẦU RA:\n");
        sb.append("Hãy phân tích toàn bộ các ý kiến trên và trả về kết quả dưới định dạng JSON với cấu trúc:\n");
        sb.append("{\n");
        sb.append("  \"themes\": [\n");
        sb.append("    {\n");
        sb.append("      \"id\": \"theme_id_tieng_anh\",\n");
        sb.append("      \"name_vi\": \"Tên chủ đề tiếng Việt\",\n");
        sb.append("      \"definition\": \"Định nghĩa phạm vi của chủ đề\",\n");
        sb.append("      \"examples\": [\"Ví dụ trích dẫn 1\", \"Ví dụ trích dẫn 2\"],\n");
        sb.append("      \"boundary_rules\": \"Thuộc chủ đề khi... Không thuộc khi...\",\n");
        sb.append("      \"action\": \"KEEP | MERGE | SPLIT | NEW | REMOVE\",\n");
        sb.append("      \"evidence_count\": 5,\n");
        sb.append("      \"notes\": \"Lý do và ghi chú phân tích đề xuất...\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private List<DraftTheme> parseThemesFromJson(String jsonText) {
        List<DraftTheme> themes = new ArrayList<>();
        if (jsonText == null || jsonText.isBlank()) {
            return themes;
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

        try {
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode arrayNode = root.has("themes") ? root.get("themes") : root;

            if (arrayNode != null && arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    DraftTheme theme = new DraftTheme();
                    theme.setId(item.path("id").asText(""));
                    theme.setNameVi(item.path("name_vi").asText(""));
                    theme.setDefinition(item.path("definition").asText(""));

                    List<String> examples = new ArrayList<>();
                    JsonNode exNode = item.path("examples");
                    if (exNode.isArray()) {
                        for (JsonNode e : exNode) {
                            examples.add(e.asText());
                        }
                    }
                    theme.setExamples(examples);

                    theme.setBoundaryRules(item.path("boundary_rules").asText(""));
                    theme.setAction(item.path("action").asText("KEEP"));
                    theme.setEvidenceCount(item.path("evidence_count").asInt(0));
                    theme.setNotes(item.path("notes").asText(""));

                    themes.add(theme);
                }
            }
        } catch (Exception e) {
            log.error("[survey-comments] Failed to parse discovery response JSON: {}", e.getMessage());
        }

        return themes;
    }
}

