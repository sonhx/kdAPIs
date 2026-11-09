package com.surveycomments.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads an uploaded CSV stream, strips an optional UTF-8 BOM, validates that the
 * content is well-formed UTF-8, and returns a list of {@link RawComment} records.
 *
 * <p>Required columns: {@code comment_id}, {@code survey_id}, {@code cohort},
 * {@code comment_text}. If {@code comment_id} is missing or blank for a row, a
 * deterministic id of the form {@code auto-<surveyId>-<rowIndex>} is generated.
 *
 * <p>Non-UTF-8 content is rejected immediately with a clear {@link IngestException}.
 * No comment text is ever logged (AGENT_RULES rule 5).
 */
@Service
public class IngestServiceImpl implements IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestServiceImpl.class);

    /** UTF-8 BOM bytes (EF BB BF). */
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    @Override
    public List<RawComment> ingest(InputStream input, String filename, String surveyId) {
        log.info("[survey-comments] ingest: filename={} surveyId={}", filename, surveyId);

        byte[] raw;
        try {
            raw = input.readAllBytes();
        } catch (IOException e) {
            throw new IngestException("Cannot read uploaded file: " + e.getMessage(), e);
        }

        // 1. Strip UTF-8 BOM if present
        byte[] content = stripBom(raw);

        // 2. Validate UTF-8 encoding — reject anything non-UTF-8 immediately
        validateUtf8(content, filename);

        // 3. Parse CSV using Commons CSV
        String csvText = new String(content, StandardCharsets.UTF_8);
        return parseCsv(csvText, surveyId, filename);
    }

    // -------------------------------------------------------------------------
    // BOM handling
    // -------------------------------------------------------------------------

    private byte[] stripBom(byte[] raw) {
        if (raw.length >= 3
                && raw[0] == UTF8_BOM[0]
                && raw[1] == UTF8_BOM[1]
                && raw[2] == UTF8_BOM[2]) {
            log.debug("[survey-comments] ingest: UTF-8 BOM detected and stripped");
            byte[] stripped = new byte[raw.length - 3];
            System.arraycopy(raw, 3, stripped, 0, stripped.length);
            return stripped;
        }
        return raw;
    }

    // -------------------------------------------------------------------------
    // UTF-8 validation
    // -------------------------------------------------------------------------

    private void validateUtf8(byte[] content, String filename) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(java.nio.ByteBuffer.wrap(content));
        } catch (CharacterCodingException e) {
            throw new IngestException(
                    "File '" + filename + "' is not valid UTF-8. "
                    + "Please save the file as UTF-8 (with or without BOM) and re-upload. "
                    + "Detail: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // CSV parsing
    // -------------------------------------------------------------------------

    private List<RawComment> parseCsv(String csvText, String surveyId, String filename) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<RawComment> results = new ArrayList<>();
        AtomicInteger rowIdx = new AtomicInteger(0);

        try (CSVParser parser = CSVParser.parse(csvText, format)) {

            // Validate required columns are present in the header
            java.util.Set<String> headers = parser.getHeaderNames() == null
                    ? java.util.Collections.emptySet()
                    : new java.util.HashSet<>(parser.getHeaderNames());

            if (!headers.contains("comment_text")) {
                throw new IngestException(
                        "File '" + filename + "' is missing the required column 'comment_text'. "
                        + "Found columns: " + headers);
            }

            for (CSVRecord record : parser) {
                int idx = rowIdx.incrementAndGet();

                String commentText = get(record, "comment_text");
                String commentId   = get(record, "comment_id");
                String cohort      = get(record, "cohort");

                // Generate deterministic id if missing
                if (commentId == null || commentId.isBlank()) {
                    commentId = "auto-" + surveyId + "-" + idx;
                }

                // survey_id from column takes priority; fall back to the parameter
                String sid = get(record, "survey_id");
                if (sid == null || sid.isBlank()) {
                    sid = surveyId;
                }

                results.add(new RawComment(commentId, sid, cohort, commentText));
            }

        } catch (IngestException e) {
            throw e;
        } catch (Exception e) {
            throw new IngestException("Failed to parse CSV from '" + filename + "': " + e.getMessage(), e);
        }

        // Log count only — no text (AGENT_RULES rule 5)
        log.info("[survey-comments] ingest: parsed {} rows from {}", results.size(), filename);
        return results;
    }

    /** Safely retrieve a column value; returns null if the column is absent. */
    private String get(CSVRecord record, String column) {
        try {
            return record.isMapped(column) ? record.get(column) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

