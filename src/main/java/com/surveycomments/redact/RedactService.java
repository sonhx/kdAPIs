package com.surveycomments.redact;

import com.surveycomments.clean.CleanComment;
import java.util.List;

/**
 * Stage 3 – Redaction.
 *
 * <p>Applies rule-based redaction to remove personal identifiers before any API call:
 * emails, phone numbers (spaced Vietnamese formats and +84), MSSV (student IDs),
 * and personal names (honorific + capitalised words). Optionally redacts staff names.
 *
 * <p>NEVER uses an external API or LLM for redaction (AGENT_RULES rule 5).
 * Raw text is never stored after this stage; only the redacted form is passed forward.
 *
 * <p>Implementation added in T2.
 */
public interface RedactService {

    /**
     * Redact all personal identifiers from each clean comment.
     *
     * @param comments list of NFC-normalised clean comments
     * @return list of redacted records with counts of what was removed
     */
    List<RedactedComment> redact(List<CleanComment> comments);
}

