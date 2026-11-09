package com.surveycomments;

import com.surveycomments.clean.CleanComment;
import com.surveycomments.clean.CleanServiceImpl;
import com.surveycomments.ingest.IngestException;
import com.surveycomments.ingest.IngestServiceImpl;
import com.surveycomments.ingest.RawComment;
import com.surveycomments.redact.RedactServiceImpl;
import com.surveycomments.redact.RedactedComment;
import com.surveycomments.redact.RedactionResult;
import com.surveycomments.config.SurveyCommentsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T2 unit tests for Ingest, Clean and Redact stages.
 *
 * <p>All tests are fully offline — no network, no database, no Gemini API.
 * Comment text is used only in assertions; it is never logged (the impls obey that rule;
 * test assertions are only in test code which is not production logging).
 *
 * <h3>Acceptance criteria from TASKS.md T2</h3>
 * <ul>
 *   <li>c006: name, email, phone, MSSV all removed.</li>
 *   <li>c008 and c012: dropped by clean (no-content).</li>
 *   <li>c004 and c014 ("Tốt."): BOTH kept (no de-duplication).</li>
 *   <li>UTF-8 BOM is stripped transparently.</li>
 *   <li>Non-UTF-8 is rejected with a clear error.</li>
 *   <li>No-content phrases with and without diacritics are dropped.</li>
 * </ul>
 */
class IngestCleanRedactTest {

    private IngestServiceImpl ingestService;
    private CleanServiceImpl cleanService;
    private RedactServiceImpl redactService;

    @BeforeEach
    void setUp() {
        ingestService = new IngestServiceImpl();
        cleanService  = new CleanServiceImpl();

        // Wire redactService with a default-config properties object
        SurveyCommentsProperties props = new SurveyCommentsProperties();
        // redact-staff-names defaults to false
        redactService = new RedactServiceImpl();
        // Inject props via reflection (avoids needing a full Spring context)
        try {
            java.lang.reflect.Field f = RedactServiceImpl.class.getDeclaredField("props");
            f.setAccessible(true);
            f.set(redactService, props);
        } catch (Exception e) {
            throw new RuntimeException("Could not inject props into RedactServiceImpl", e);
        }
    }

    // =========================================================================
    // Ingest tests
    // =========================================================================

    @Test
    void ingest_sampleFakeCsv_parsesAllRows() {
        InputStream csv = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/sample_fake_comments.csv");
        assertNotNull(csv, "sample_fake_comments.csv must be on the test classpath");

        List<RawComment> rows = ingestService.ingest(csv, "sample_fake_comments.csv", "demo-2026-01");

        assertEquals(24, rows.size(), "Sample CSV has 24 comment rows");
    }

    @Test
    void ingest_stripsUtf8Bom() {
        // Prepend UTF-8 BOM to a valid CSV
        String csvContent = "comment_id,survey_id,cohort,comment_text\nc001,s1,K1,\"Tốt.\"\n";
        byte[] bomBytes   = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes   = csvContent.getBytes(StandardCharsets.UTF_8);
        byte[] combined   = new byte[bomBytes.length + csvBytes.length];
        System.arraycopy(bomBytes, 0, combined, 0, bomBytes.length);
        System.arraycopy(csvBytes, 0, combined, bomBytes.length, csvBytes.length);

        List<RawComment> rows = ingestService.ingest(
                new ByteArrayInputStream(combined), "bom_test.csv", "s1");

        assertEquals(1, rows.size());
        assertEquals("c001", rows.get(0).getCommentId());
    }

    @Test
    void ingest_nonUtf8_throwsIngestException() {
        // Latin-1 / Windows-1252 encoded text that is not valid UTF-8
        byte[] latin1 = "comment_id,survey_id,cohort,comment_text\nc001,s1,K1,\"\u00e9\u00e0\u00f9\"".getBytes(java.nio.charset.Charset.forName("ISO-8859-1"));

        IngestException ex = assertThrows(IngestException.class,
                () -> ingestService.ingest(new ByteArrayInputStream(latin1), "bad.csv", "s1"));
        assertTrue(ex.getMessage().contains("not valid UTF-8"),
                "Error message must mention encoding problem");
    }

    @Test
    void ingest_missingCommentTextColumn_throwsIngestException() {
        String csv = "id,text\nc1,hello\n";
        IngestException ex = assertThrows(IngestException.class,
                () -> ingestService.ingest(toStream(csv), "missing_col.csv", "s1"));
        assertTrue(ex.getMessage().contains("comment_text"));
    }

    @Test
    void ingest_missingCommentId_generatesId() {
        String csv = "comment_id,survey_id,cohort,comment_text\n"
                   + ",s1,K1,\"Phòng học nóng.\"\n";
        List<RawComment> rows = ingestService.ingest(toStream(csv), "noid.csv", "s1");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getCommentId().startsWith("auto-s1-"),
                "Generated id must start with auto-<surveyId>-");
    }

    // =========================================================================
    // Clean tests
    // =========================================================================

    @Test
    void clean_dropsNoContentComments_c008_and_c012() {
        // Load from real sample CSV, then clean
        InputStream csv = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/sample_fake_comments.csv");
        List<RawComment> raw = ingestService.ingest(csv, "sample_fake_comments.csv", "demo-2026-01");
        List<CleanComment> cleaned = cleanService.clean(raw);

        // c008 = "Không có ý kiến gì." — must be dropped
        // c012 = "ko co y kien"        — must be dropped
        boolean c008Present = cleaned.stream().anyMatch(c -> "c008".equals(c.getCommentId()));
        boolean c012Present = cleaned.stream().anyMatch(c -> "c012".equals(c.getCommentId()));
        assertFalse(c008Present, "c008 ('Không có ý kiến gì.') must be dropped");
        assertFalse(c012Present, "c012 ('ko co y kien') must be dropped");
    }

    @Test
    void clean_keepsBothDuplicateShortComments_c004_and_c014() {
        // c004 and c014 are both "Tốt." — both must be kept (no de-duplication)
        InputStream csv = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/sample_fake_comments.csv");
        List<RawComment> raw = ingestService.ingest(csv, "sample_fake_comments.csv", "demo-2026-01");
        List<CleanComment> cleaned = cleanService.clean(raw);

        boolean c004Present = cleaned.stream().anyMatch(c -> "c004".equals(c.getCommentId()));
        boolean c014Present = cleaned.stream().anyMatch(c -> "c014".equals(c.getCommentId()));
        assertTrue(c004Present, "c004 ('Tốt.') must be kept");
        assertTrue(c014Present, "c014 ('Tốt.') must be kept — no de-duplication");
    }

    @Test
    void clean_noContentVariants_droppedCorrectly() {
        // Test a selection of no-content phrases
        List<String> shouldDrop = List.of(
                "Không có ý kiến",
                "Không có ý kiến gì.",
                "ko co y kien",
                "khong co y kien gi",
                "N/A",
                "n/a",
                "."
        );
        for (String phrase : shouldDrop) {
            assertTrue(CleanServiceImpl.isNoContent(phrase),
                    "Should be identified as no-content: '" + phrase + "'");
        }
    }

    @Test
    void clean_substantiveComments_notDropped() {
        // These phrases have real content and must NOT be dropped
        List<String> shouldKeep = List.of(
                "Tốt.",
                "Bình thường.",
                "Cũng được.",
                "giang vien day rat de hieu nhung phong hoc hay mat dien",
                "Thầy dạy rất dễ hiểu."
        );
        for (String phrase : shouldKeep) {
            assertFalse(CleanServiceImpl.isNoContent(phrase),
                    "Should NOT be identified as no-content: '" + phrase + "'");
        }
    }

    @Test
    void clean_normalisesNFC() {
        // "Tốt" decomposed (NFC: T + ố composed) vs composed
        // java.text.Normalizer.normalize should produce identical output for both
        String decomposed = "To\u0301t."; // "Tóت" with combining acute
        String nfc = java.text.Normalizer.normalize(decomposed, java.text.Normalizer.Form.NFC);
        // After NFC they should be equal to "Tót." composed form
        assertEquals(java.text.Normalizer.normalize("Tót.", java.text.Normalizer.Form.NFC), nfc);

        // Now run through cleanService
        RawComment r = new RawComment("x1", "s1", "K1", decomposed);
        List<CleanComment> result = cleanService.clean(List.of(r));
        assertEquals(1, result.size(), "Decomposed text must be kept");
        // Verify it is now NFC
        assertTrue(java.text.Normalizer.isNormalized(result.get(0).getCommentText(),
                java.text.Normalizer.Form.NFC), "Output must be NFC-normalised");
    }

    // =========================================================================
    // Redact tests
    // =========================================================================

    @Test
    void redact_c006_removesNameEmailMssvPhone() {
        // c006: "Em là Nguyễn Văn An (MSSV 20251234), email an.nguyen@example.com.
        //        Em cảm ơn cô Lan bên phòng đào tạo đã hỗ trợ em rất nhiều về học phí.
        //        SĐT 0912 345 678."
        InputStream csv = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/sample_fake_comments.csv");
        List<RawComment> raw = ingestService.ingest(csv, "sample.csv", "demo-2026-01");
        List<CleanComment> cleaned = cleanService.clean(raw);
        List<RedactedComment> redacted = redactService.redact(cleaned);

        RedactedComment c006 = redacted.stream()
                .filter(c -> "c006".equals(c.getCommentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("c006 not found after clean+redact"));

        String redactedText = c006.getRedactedText();

        // Email must be removed
        assertFalse(redactedText.contains("an.nguyen@example.com"),
                "Email must be redacted in c006");
        assertTrue(c006.getEmailsRedacted() >= 1, "emailsRedacted must be >= 1 for c006");

        // Phone must be removed
        assertFalse(redactedText.contains("0912 345 678"),
                "Phone must be redacted in c006");
        assertTrue(c006.getPhonesRedacted() >= 1, "phonesRedacted must be >= 1 for c006");

        // MSSV must be removed
        assertFalse(redactedText.contains("20251234"),
                "MSSV must be redacted in c006");
        assertTrue(c006.getMssvRedacted() >= 1, "mssvRedacted must be >= 1 for c006");

        // Name "Nguyễn Văn An" or partial must be removed
        assertFalse(redactedText.contains("Nguyễn Văn An"),
                "Name 'Nguyễn Văn An' must be redacted in c006");
        // The honorific-based name "cô Lan" must also be removed
        assertFalse(redactedText.contains("cô Lan") && redactedText.contains("Lan"),
                "Honorific-based name 'cô Lan' should be redacted");
    }

    @Test
    void redact_emailPattern_matches() {
        RedactionResult r =
                redactService.redactText("Liên hệ: foo.bar+baz@example.co.vn nhé.", false);
        assertFalse(r.text.contains("@"), "Email must be replaced");
        assertTrue(r.text.contains("[EMAIL]"), "Placeholder [EMAIL] must appear");
        assertEquals(1, r.emails);
    }

    @Test
    void redact_phonePatterns_allVariants() {
        // Compact 10-digit
        assertPhone("SĐT 0912345678 nhé.", 1);
        // Spaced Vietnamese format
        assertPhone("gọi 0912 345 678 nhé.", 1);
        // +84 prefix
        assertPhone("liên hệ +84 912 345 678.", 1);
        // 09 xx xx xx xx spaced
        assertPhone("SĐT 09 12 34 56 78 nhé.", 1);
    }

    @Test
    void redact_mssvKeyword_matches() {
        RedactionResult r =
                redactService.redactText("MSSV 20251234 cần xem lại.", false);
        assertFalse(r.text.contains("20251234"), "MSSV digits must be replaced");
        assertEquals(1, r.mssv);
    }

    @Test
    void redact_nameWithHonorific_matches() {
        RedactionResult r =
                redactService.redactText("Em cảm ơn thầy Nguyễn Văn Bình đã giảng dạy tốt.", false);
        assertFalse(r.text.contains("Nguyễn Văn Bình"), "Name after honorific must be redacted");
        assertTrue(r.names >= 1);
    }

    @Test
    void redact_plainText_notAltered() {
        String plain = "Giáo trình khá ổn nhưng gv ko giảng lại phần khó.";
        RedactionResult r = redactService.redactText(plain, false);
        assertEquals(plain, r.text, "Text without PII must not be altered");
        assertEquals(0, r.totalRedacted());
    }

    @Test
    void redact_placeholders_notRedactedAgain() {
        // If input already contains a placeholder (edge case), it should not be double-counted
        String text = "Email [EMAIL] và SĐT [PHONE] đã xóa.";
        RedactionResult r = redactService.redactText(text, false);
        assertEquals(0, r.emails, "No new emails to redact");
        assertEquals(0, r.phones, "No new phones to redact");
    }

    // =========================================================================
    // End-to-end pipeline test
    // =========================================================================

    @Test
    void pipeline_sampleFakeCsv_correctDropsAndRedaction() {
        InputStream csv = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/sample_fake_comments.csv");
        List<RawComment> raw     = ingestService.ingest(csv, "sample_fake_comments.csv", "demo-2026-01");
        List<CleanComment> clean = cleanService.clean(raw);
        List<RedactedComment> redacted = redactService.redact(clean);

        // c008 and c012 must be absent
        assertFalse(redacted.stream().anyMatch(c -> "c008".equals(c.getCommentId())), "c008 must be dropped");
        assertFalse(redacted.stream().anyMatch(c -> "c012".equals(c.getCommentId())), "c012 must be dropped");

        // c004 and c014 must both be present
        assertTrue(redacted.stream().anyMatch(c -> "c004".equals(c.getCommentId())), "c004 must be kept");
        assertTrue(redacted.stream().anyMatch(c -> "c014".equals(c.getCommentId())), "c014 must be kept");

        // c006 must be present but redacted
        RedactedComment c006 = redacted.stream()
                .filter(c -> "c006".equals(c.getCommentId())).findFirst().orElse(null);
        assertNotNull(c006, "c006 must survive cleaning (it has real content)");
        assertTrue(c006.wasRedacted(), "c006 must have had something redacted");
        assertFalse(c006.getRedactedText().contains("20251234"), "c006 MSSV must be gone");
        assertFalse(c006.getRedactedText().contains("an.nguyen@example.com"), "c006 email must be gone");
        assertFalse(c006.getRedactedText().contains("0912 345 678"), "c006 phone must be gone");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private InputStream toStream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private void assertPhone(String text, int expectedCount) {
        RedactionResult r = redactService.redactText(text, false);
        assertEquals(expectedCount, r.phones,
                "Expected " + expectedCount + " phone(s) redacted in: " + text);
        assertTrue(r.text.contains("[PHONE]"), "Placeholder [PHONE] must appear in: " + text);
    }
}

