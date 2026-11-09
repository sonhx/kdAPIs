package com.surveycomments;

import com.surveycomments.clean.CleanServiceImpl;
import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.discovery.DiscoveryResult;
import com.surveycomments.discovery.DiscoveryServiceImpl;
import com.surveycomments.discovery.DraftTheme;
import com.surveycomments.ingest.IngestServiceImpl;
import com.surveycomments.redact.RedactServiceImpl;
import com.surveycomments.classify.GeminiClientService;
import com.surveycomments.classify.GeminiGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiscoveryServiceImpl} (Task T3).
 * Mocks the Gemini client to guarantee 100% offline test execution without network access
 * or API key requirements (AGENT_RULES rule 9).
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock
    private GeminiClientService geminiClientService;

    @Spy
    private IngestServiceImpl ingestService = new IngestServiceImpl();

    @Spy
    private CleanServiceImpl cleanService = new CleanServiceImpl();

    @Spy
    private RedactServiceImpl redactService = new RedactServiceImpl();

    @Spy
    private SurveyCommentsProperties props = new SurveyCommentsProperties();

    @InjectMocks
    private DiscoveryServiceImpl discoveryService;

    @BeforeEach
    void setUp() throws Exception {
        // Wire props into redactService
        java.lang.reflect.Field f = RedactServiceImpl.class.getDeclaredField("props");
        f.setAccessible(true);
        f.set(redactService, props);
    }

    @Test
    void discoverThemes_processesCommentsAndParsesModelThemes() {
        // Sample simulated JSON response from Gemini
        String mockAiResponse = """
            {
              "themes": [
                {
                  "id": "teaching",
                  "name_vi": "Giảng viên & giảng dạy",
                  "definition": "Chất lượng, thái độ và phương pháp giảng dạy của giảng viên",
                  "examples": [
                    "Thầy dạy rất dễ hiểu và nhiệt tình",
                    "giang vien day rat de hieu"
                  ],
                  "boundary_rules": "Thuộc chủ đề này khi nhận xét về cách dạy, thái độ GV; không tính tài liệu môn học",
                  "action": "KEEP",
                  "evidence_count": 6,
                  "notes": "Chủ đề xuất hiện thường xuyên, phản ánh rõ ràng"
                },
                {
                  "id": "facilities_ac",
                  "name_vi": "Điều hòa & nhiệt độ phòng học",
                  "definition": "Phản ánh cụ thể về phòng học quá nóng, điều hòa hỏng",
                  "examples": [
                    "phòng học quá nóng, máy lạnh hỏng mấy tuần rồi"
                  ],
                  "boundary_rules": "Thuộc chủ đề khi nói về máy lạnh/nhiệt độ",
                  "action": "SPLIT",
                  "evidence_count": 2,
                  "notes": "Đề xuất tách từ facilities chung do nhiều phản ánh bức xúc"
                }
              ]
            }
            """;

        when(geminiClientService.generate(anyString(), anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(new GeminiGenerationResponse(mockAiResponse, 1200, 350));

        InputStream csvStream = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/sample_fake_comments.csv");
        assertNotNull(csvStream, "sample_fake_comments.csv must exist");

        DiscoveryResult result = discoveryService.discoverThemes(csvStream, "sample_fake_comments.csv", "demo-2026-01", null);

        assertNotNull(result);
        assertEquals(24, result.getTotalInputComments());
        assertEquals(22, result.getProcessedComments()); // 2 dropped (c008, c012)
        assertEquals(1200, result.getPromptTokens());
        assertEquals(350, result.getCandidateTokens());

        List<DraftTheme> themes = result.getThemes();
        assertNotNull(themes);
        assertEquals(2, themes.size());

        DraftTheme t1 = themes.get(0);
        assertEquals("teaching", t1.getId());
        assertEquals("KEEP", t1.getAction());
        assertEquals(2, t1.getExamples().size());
        assertEquals(6, t1.getEvidenceCount());

        DraftTheme t2 = themes.get(1);
        assertEquals("facilities_ac", t2.getId());
        assertEquals("SPLIT", t2.getAction());
        assertEquals(2, t2.getEvidenceCount());

        // Verify prompt captured by mock does NOT contain unredacted PII (c006 email, phone, MSSV)
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClientService).generate(anyString(), promptCaptor.capture(), anyString(), anyString(), anyDouble());

        String capturedPrompt = promptCaptor.getValue();
        assertFalse(capturedPrompt.contains("an.nguyen@example.com"), "Prompt sent to Gemini must not contain student email");
        assertFalse(capturedPrompt.contains("0912 345 678"), "Prompt sent to Gemini must not contain student phone");
        assertFalse(capturedPrompt.contains("20251234"), "Prompt sent to Gemini must not contain student MSSV");
        assertTrue(capturedPrompt.contains("[EMAIL]"), "Prompt must contain redacted placeholder [EMAIL]");
        assertTrue(capturedPrompt.contains("[PHONE]"), "Prompt must contain redacted placeholder [PHONE]");
    }
}

