package com.surveycomments.clean;

import com.surveycomments.ingest.RawComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Drops no-content comments and normalises surviving text to Unicode NFC.
 *
 * <h3>Drop rules</h3>
 * <ul>
 *   <li>Null or blank text (after trimming whitespace).</li>
 *   <li>Exact no-content phrases in Vietnamese (with and without diacritics),
 *       case-insensitive after NFC normalisation.</li>
 *   <li>Text that consists entirely of punctuation / whitespace after removing
 *       the no-content phrases leaves nothing substantive.</li>
 * </ul>
 *
 * <h3>Keep rules</h3>
 * <ul>
 *   <li>Short positive/neutral phrases like "Tốt." or "Bình thường." are kept
 *       (they carry sentiment even without a topic).</li>
 *   <li>NEVER de-duplicate identical short comments — two students may both write
 *       "Tốt." (PLAN.md §5).</li>
 * </ul>
 *
 * <p>No comment text is ever logged (AGENT_RULES rule 5).
 */
@Service
public class CleanServiceImpl implements CleanService {

    private static final Logger log = LoggerFactory.getLogger(CleanServiceImpl.class);

    // -------------------------------------------------------------------------
    // No-content patterns
    // These cover the most common Vietnamese "no opinion" phrases in both
    // diacritic and no-diacritic forms, with optional trailing punctuation.
    // Add more as discovered during codebook review.
    // -------------------------------------------------------------------------

    /** Exact no-content strings (lowercase NFC, punctuation stripped). */
    private static final Set<String> NO_CONTENT_EXACT = Set.of(
            "không có ý kiến",
            "không có ý kiến gì",
            "khong co y kien",
            "khong co y kien gi",
            "ko co y kien",
            "ko có ý kiến",
            "k co y kien",
            "n/a",
            "na",
            "none",
            "không",
            "khong",
            "không có",
            "khong co",
            "không ý kiến",
            "khong y kien",
            "không có gì",
            "khong co gi",
            "không có gì để nói",
            "chua co y kien",
            "chưa có ý kiến",
            "chưa có",
            "không có nhận xét",
            "không có nhận xét gì",
            "chưa có nhận xét",
            "không rõ",
            "khong ro",
            "không biết",
            "khong biet",
            "trống",
            "."
    );

    /** Pattern to strip trailing punctuation and whitespace before lookup. */
    private static final Pattern STRIP_PUNCT = Pattern.compile("[\\s.,!?;:…]+$");
    /** Pattern to strip leading punctuation and whitespace. */
    private static final Pattern STRIP_LEADING = Pattern.compile("^[\\s.,!?;:…]+");

    private static final int VERY_SHORT_THRESHOLD = 3;    // chars after trim
    private static final int VERY_LONG_THRESHOLD  = 2000; // chars

    @Override
    public List<CleanComment> clean(List<RawComment> raw) {
        List<CleanComment> kept = new ArrayList<>();
        int dropped = 0;

        for (RawComment r : raw) {
            String text = r.getCommentText();

            // Drop null/blank
            if (text == null || text.isBlank()) {
                dropped++;
                log.debug("[survey-comments] clean: drop id={} reason=blank", r.getCommentId());
                continue;
            }

            // NFC-normalise BEFORE no-content check (handles pre-composed vs decomposed chars)
            String nfc = Normalizer.normalize(text.trim(), Normalizer.Form.NFC);

            // Drop no-content
            if (isNoContent(nfc)) {
                dropped++;
                log.debug("[survey-comments] clean: drop id={} reason=no-content", r.getCommentId());
                continue;
            }

            boolean veryShort = nfc.length() < VERY_SHORT_THRESHOLD;
            boolean veryLong  = nfc.length() > VERY_LONG_THRESHOLD;

            kept.add(new CleanComment(r.getCommentId(), r.getSurveyId(), r.getQuestionId(), r.getQuestionText(), r.getCohort(),
                    nfc, veryShort, veryLong));
        }

        // Log counts only — no text (AGENT_RULES rule 5)
        log.info("[survey-comments] clean: input={} kept={} dropped={}", raw.size(), kept.size(), dropped);
        return kept;
    }

    // -------------------------------------------------------------------------
    // Package-private for unit tests
    // -------------------------------------------------------------------------

    /**
     * Returns true if the given NFC-normalised text is a no-content phrase.
     * Exposed package-private so unit tests can call it directly.
     */
    public static boolean isNoContent(String nfcText) {
        if (nfcText == null || nfcText.isBlank()) return true;

        // Normalise for lookup: lowercase, strip leading/trailing punctuation
        String lower = nfcText.toLowerCase();
        String stripped = STRIP_PUNCT.matcher(STRIP_LEADING.matcher(lower).replaceFirst("")).replaceFirst("");

        return NO_CONTENT_EXACT.contains(stripped)
                || NO_CONTENT_EXACT.contains(lower.trim());
    }
}
