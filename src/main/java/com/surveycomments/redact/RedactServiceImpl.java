package com.surveycomments.redact;

import com.surveycomments.clean.CleanComment;
import com.surveycomments.config.SurveyCommentsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based redaction of personal identifiers.
 *
 * <p>Applies in order:
 * <ol>
 *   <li>MSSV (student IDs) — numeric sequences following the word "MSSV"
 *       or standalone 8-10 digit numbers that look like student IDs.</li>
 *   <li>Email addresses.</li>
 *   <li>Phone numbers — Vietnamese formats: 10-digit starting with 03/05/07/08/09,
 *       spaced variants ("09 12 34 56 78", "0912 345 678"), and +84 prefix.</li>
 *   <li>Personal names — honorific (thầy/cô/anh/chị/ông/bà/em/bạn) followed by
 *       one or more capitalised words. If {@code redact-staff-names=true} this
 *       also applies to standalone capitalised multi-word sequences that look like names.</li>
 * </ol>
 *
 * <p>NEVER uses an external API or LLM for redaction (AGENT_RULES rule 5).
 * Raw comment text is NEVER logged — log ids and counts only.
 * The original text is NOT stored in {@link RedactedComment}.
 */
@Service
public class RedactServiceImpl implements RedactService {

    private static final Logger log = LoggerFactory.getLogger(RedactServiceImpl.class);

    @Autowired
    private SurveyCommentsProperties props;

    public RedactServiceImpl() {}

    public RedactServiceImpl(SurveyCommentsProperties props) {
        this.props = props;
    }

    // -------------------------------------------------------------------------
    // Redaction patterns
    // -------------------------------------------------------------------------

    /** MSSV keyword + digits, e.g. "MSSV 20251234" or "MSSV: 20251234". */
    private static final Pattern PAT_MSSV_KEYWORD =
            Pattern.compile("(?i)\\bMSSV\\s*[:.]?\\s*\\d{6,10}\\b");

    /**
     * Standalone 8-10 digit numbers that are likely student IDs.
     * Requires a word boundary to avoid matching phone numbers handled below.
     */
    private static final Pattern PAT_MSSV_BARE =
            Pattern.compile("(?<![+\\d])\\b\\d{8,10}\\b(?![\\d])");

    /** Email addresses (simple but effective for Vietnamese university context). */
    private static final Pattern PAT_EMAIL =
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    /**
     * Vietnamese phone numbers:
     * - 10 digits starting with 03/05/07/08/09 (compact or spaced/dotted/dashed variants)
     * - +84 or 0084 international prefix variants
     * Formats: "0912 345 678", "09 12 34 56 78", "0912-345-678", "+84 912 345 678", "0912345678"
     */
    private static final Pattern PAT_PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:\\+84|0084|\\(\\+84\\))\\s*[-.]?\\s*[35789]|0[35789])(?:[\\s.-]?\\d){8}\\b");

    /**
     * Vietnamese honorifics used to introduce names. Case-insensitive.
     * Followed by one or more capitalised Vietnamese words (NFC, may include diacritics).
     */
    private static final Pattern PAT_NAME_WITH_HONORIFIC = Pattern.compile(
            "(?i)\\b(?:thầy|cô|anh|chị|ông|bà|em|bạn|giáo viên|giảng viên|gs|pgs|ts|ths)\\s+"
            + "(?:[A-ZÁÀẢÃẠĂẮẶẰẲẴÂẤẬẦẨẪĐÉÈẺẼẸÊẾỆỀỂỄÍÌỈĨỊÓÒỎÕỌÔỐỘỒỔỖƠỚỢỜỞỠÚÙỦŨỤƯỨỰỪỬỮÝỲỶỸỴ]"
            + "[a-záàảãạăắặằẳẵâấậầẩẫđéèẻẽẹêếệềểễíìỉĩịóòỏõọôốộồổỗơớợờởỡúùủũụưứựừửữýỳỷỹỵ]*"
            + "(?:\\s+[A-ZÁÀẢÃẠĂẮẶẰẲẴÂẤẬẦẨẪĐÉÈẺẼẸÊẾỆỀỂỄÍÌỈĨỊÓÒỎÕỌÔỐỘỒỔỖƠỚỢỜỞỠÚÙỦŨỤƯỨỰỪỬỮÝỲỶỸỴ]"
            + "[a-záàảãạăắặằẳẵâấậầẩẫđéèẻẽẹêếệềểễíìỉĩịóòỏõọôốộồổỗơớợờởỡúùủũụưứựừửữýỳỷỹỵ]*)*)");

    /**
     * Standalone multi-word sequences that look like full Vietnamese names (3+ capital words).
     * Used when {@code redact-staff-names=true}.
     */
    private static final Pattern PAT_NAME_STANDALONE = Pattern.compile(
            "(?:[A-ZÁÀẢÃẠĂẮẶẰẲẴÂẤẬẦẨẪĐÉÈẺẼẸÊẾỆỀỂỄÍÌỈĨỊÓÒỎÕỌÔỐỘỒỔỖƠỚỢỜỞỠÚÙỦŨỤƯỨỰỪỬỮÝỲỶỸỴ]"
            + "[a-záàảãạăắặằẳẵâấậầẩẫđéèẻẽẹêếệềểễíìỉĩịóòỏõọôốộồổỗơớợờởỡúùủũụưứựừửữýỳỷỹỵ]+"
            + "(?:\\s+"
            + "[A-ZÁÀẢÃẠĂẮẶẰẲẴÂẤẬẦẨẪĐÉÈẺẼẸÊẾỆỀỂỄÍÌỈĨỊÓÒỎÕỌÔỐỘỒỔỖƠỚỢỜỞỠÚÙỦŨỤƯỨỰỪỬỮÝỲỶỸỴ]"
            + "[a-záàảãạăắặằẳẵâấậầẩẫđéèẻẽẹêếệềểễíìỉĩịóòỏõọôốộồổỗơớợờởỡúùủũụưứựừửữýỳỷỹỵ]+){2,})");

    // -------------------------------------------------------------------------
    // Core redaction logic
    // -------------------------------------------------------------------------

    @Override
    public List<RedactedComment> redact(List<CleanComment> comments) {
        boolean redactStaff = props != null ? props.isRedactStaffNames() : true;
        List<RedactedComment> results = new ArrayList<>(comments.size());

        for (CleanComment c : comments) {
            RedactionResult r = redactText(c.getCommentText(), redactStaff);
            results.add(new RedactedComment(
                    c.getCommentId(), c.getSurveyId(), c.getCohort(),
                    r.text,
                    r.emails, r.phones, r.mssv, r.names,
                    c.isVeryShort(), c.isVeryLong()));

            // Log id + counts only — NEVER the text (AGENT_RULES rule 5)
            if (r.totalRedacted() > 0) {
                log.info("[survey-comments] redact: id={} emails={} phones={} mssv={} names={}",
                        c.getCommentId(), r.emails, r.phones, r.mssv, r.names);
            }
        }

        long redactedCount = results.stream().filter(RedactedComment::wasRedacted).count();
        log.info("[survey-comments] redact: total={} had-redaction={}", results.size(), redactedCount);
        return results;
    }

    // -------------------------------------------------------------------------
    // Per-text redaction (package-private for unit tests)
    // -------------------------------------------------------------------------

    /**
     * Apply all redaction rules to a single text.
     * Returns the redacted text and counts. Package-private for testability.
     */
    public RedactionResult redactText(String text, boolean redactStaffNames) {
        int[] counts = {0, 0, 0, 0}; // [emails, phones, mssv, names]

        // 1. MSSV keyword form first (before bare-digits to avoid double-count)
        int[] mssvCount = {0};
        text = replaceAll(text, PAT_MSSV_KEYWORD, "[MSSV]", mssvCount);

        // 2. Email
        int[] emailCount = {0};
        text = replaceAll(text, PAT_EMAIL, "[EMAIL]", emailCount);

        // 3. Phone (after MSSV to avoid mangling digits)
        int[] phoneCount = {0};
        text = replaceAll(text, PAT_PHONE, "[PHONE]", phoneCount);

        // 4. MSSV bare digits (run after phone so we don't eat phone digits)
        text = replaceAll(text, PAT_MSSV_BARE, "[MSSV]", mssvCount);

        // 5. Names with honorifics
        int[] nameCount = {0};
        text = replaceAll(text, PAT_NAME_WITH_HONORIFIC, "[NAME]", nameCount);

        // 6. Standalone multi-word names (optional, controlled by config)
        if (redactStaffNames) {
            text = replaceAll(text, PAT_NAME_STANDALONE, "[NAME]", nameCount);
        }

        return new RedactionResult(text, emailCount[0], phoneCount[0], mssvCount[0], nameCount[0]);
    }

    private String replaceAll(String text, Pattern pattern, String replacement, int[] counter) {
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            counter[0]++;
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

}
