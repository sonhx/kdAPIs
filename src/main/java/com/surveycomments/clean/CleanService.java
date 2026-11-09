package com.surveycomments.clean;

import com.surveycomments.ingest.RawComment;
import java.util.List;

/**
 * Stage 2 – Cleaning.
 *
 * <p>Drops no-content comments ("N/A", "Không có ý kiến", "ko co y kien", etc.) and
 * normalises Unicode to NFC with {@link java.text.Normalizer}.
 * Does NOT de-duplicate identical short comments (two students may both write "Tốt.").
 *
 * <p>Implementation added in T2.
 */
public interface CleanService {

    /**
     * Filter and normalise raw comments.
     *
     * @param raw list of raw comments from the ingest stage
     * @return list of clean records (no-content entries removed, text NFC-normalised)
     */
    List<CleanComment> clean(List<RawComment> raw);
}

