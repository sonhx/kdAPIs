package com.surveycomments.report;

import java.io.OutputStream;

/**
 * Service for generating aggregated Excel (.xlsx) and CSV reports for completed runs.
 */
public interface ReportService {

    /**
     * Generate a multi-sheet Excel (.xlsx) workbook for the specified runId.
     *
     * @param runId run identifier
     * @param out   output stream to write the workbook bytes
     */
    void generateExcelReport(String runId, OutputStream out);

    /**
     * Generate a CSV summary report for the specified runId.
     *
     * @param runId run identifier
     * @param out   output stream to write the CSV text
     */
    void generateCsvReport(String runId, OutputStream out);
}
