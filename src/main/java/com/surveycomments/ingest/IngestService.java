package com.surveycomments.ingest;

import java.io.InputStream;
import java.util.List;

/**
 * Stage 1 – CSV ingestion.
 *
 * <p>Reads an uploaded CSV stream, validates UTF-8 encoding (with or without BOM),
 * and returns a list of raw comment records with the columns:
 * {@code comment_id, survey_id, cohort, comment_text}.
 *
 * <p>Implementation added in T2.
 */
public interface IngestService {

    /**
     * Parse and validate the uploaded CSV.
     *
     * @param input       raw byte stream of the uploaded file
     * @param filename    original filename (used only for logging and error messages)
     * @param surveyId    survey identifier attached to every record
     * @return list of raw (unvalidated) comment records
     * @throws IngestException if the encoding is not UTF-8 or the CSV is structurally invalid
     */
    List<RawComment> ingest(InputStream input, String filename, String surveyId);
}

