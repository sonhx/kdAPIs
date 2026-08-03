package com.khcn.openalex;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ScimagoIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ScimagoIngestionService.class);
    private final JdbcTemplate jdbcTemplate;

    public ScimagoIngestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initDatabaseTables() {
        try {
            log.info("Initializing SCImago Database Tables...");
            jdbcTemplate.execute(
                "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'scimago_rankings') " +
                "BEGIN " +
                "CREATE TABLE scimago_rankings (" +
                "    id INT IDENTITY(1,1) PRIMARY KEY," +
                "    source_id INT NULL," +
                "    title NVARCHAR(500) NOT NULL," +
                "    issn_clean VARCHAR(8) NOT NULL," +
                "    sjr_score FLOAT NULL," +
                "    best_quartile VARCHAR(5) NOT NULL," +
                "    h_index INT NULL," +
                "    ranking_year INT NOT NULL," +
                "    created_at DATETIME2 DEFAULT GETDATE()," +
                "    CONSTRAINT UQ_issn_year UNIQUE (issn_clean, ranking_year)" +
                "); " +
                "CREATE INDEX IX_scimago_lookup ON scimago_rankings(issn_clean, ranking_year, best_quartile);" +
                "END"
            );
            log.info("SCImago Database Tables initialized successfully.");
        } catch (Exception e) {
            log.error("Error initializing SCImago database tables: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled annual ingestion on July 1st at 01:00 AM.
     */
    @Scheduled(cron = "0 0 1 1 7 *")
    public void executeAnnualIngestion() {
        log.info("Starting annual scheduled SCImago dataset ingestion...");
        int defaultYear = LocalDate.now().getYear() - 1; // SCImago releases data for the prior year
        syncRankingsForYear(defaultYear);
    }

    /**
     * Manually triggers SCImago dataset ingestion with an optional year target.
     * If the year target is null, the parser will attempt to extract it from headers or fall back to previous calendar year.
     */
    public synchronized String executeManualIngestion(Integer targetYear) {
        log.info("Manual SCImago dataset ingestion triggered. Requested target year: {}", targetYear);
        int defaultYear = targetYear != null ? targetYear : (LocalDate.now().getYear() - 1);
        return syncRankingsForYear(defaultYear);
    }

    private String syncRankingsForYear(int defaultYear) {
        Path downloadedFile = downloadScimagoCsv();
        if (downloadedFile == null || !Files.exists(downloadedFile)) {
            String errorMsg = "Aborting ingestion: Failed to download SCImago CSV file.";
            log.error(errorMsg);
            return errorMsg;
        }

        try {
            List<JournalRecord> records = parseAndNormalizeCsv(downloadedFile, defaultYear);
            if (records.isEmpty()) {
                String warningMsg = "No records extracted from SCImago CSV.";
                log.warn(warningMsg);
                return warningMsg;
            }

            int detectedYear = records.get(0).rankingYear();
            log.info("Extracted {} normalized journal-ISSN entries for year {}.", records.size(), detectedYear);

            batchUpsertToSqlServer(records);
            String successMsg = "Successfully completed SCImago dataset ingestion. Synced " + records.size() + " entries for year " + detectedYear;
            log.info(successMsg);
            return successMsg;

        } catch (Exception e) {
            log.error("Error occurred while processing SCImago CSV", e);
            return "Ingestion failed with error: " + e.getMessage();
        } finally {
            try {
                Files.deleteIfExists(downloadedFile);
            } catch (Exception ignored) {}
        }
    }

    private Path downloadScimagoCsv() {
        try (Playwright playwright = Playwright.create()) {
            log.info("Launching Playwright browser to download SCImago CSV...");
            
            boolean headless = true;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                headless = false; // Dev environment: run headfully to bypass Cloudflare
                log.info("Windows detected: running Playwright in headful mode.");
            }

            Browser browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(List.of("--disable-blink-features=AutomationControlled"))
            );

            // Create context with a realistic user agent
            BrowserContext context = browser.newContext(
                new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            );

            Page page = context.newPage();
            page.setDefaultNavigationTimeout(90000); // 90 seconds timeout for slower networks

            log.info("Navigating to SCImago Journal Rank page...");
            page.navigate("https://www.scimagojr.com/journalrank.php");

            // Wait a brief moment to ensure page has settled
            page.waitForTimeout(5000);

            log.info("Clicking download link...");
            // Trigger and catch file download
            Download download = page.waitForDownload(() -> {
                page.click("a[href*='out=xls']");
            });

            Path tempFile = Files.createTempFile("scimagojr_", ".csv");
            download.saveAs(tempFile);
            browser.close();

            log.info("Downloaded SCImago CSV to temp path: {}", tempFile);
            return tempFile;

        } catch (Exception e) {
            log.error("Playwright automation failed during download", e);
            return null;
        }
    }

    private List<JournalRecord> parseAndNormalizeCsv(Path csvPath, int defaultYear) throws Exception {
        List<JournalRecord> recordList = new ArrayList<>();

        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(';')
                .setQuote(null)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(Files.newInputStream(csvPath), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, csvFormat)) {

            Map<String, Integer> headerMap = csvParser.getHeaderMap();

            // Resolve exact column headers dynamically
            String titleHeader = findHeader(headerMap, "Title", "title");
            String issnHeader = findHeader(headerMap, "Issn", "issn", "ISSN");
            String quartileHeader = findHeader(headerMap, "SJR Best Quartile", "SJR Q", "SJR Quartile", "Sjr Q", "Quartile", "SJR_Quartile", "Best Quartile");
            String sourceIdHeader = findHeader(headerMap, "Sourceid", "SourceId", "source_id", "sourceid");
            String sjrHeader = findHeader(headerMap, "SJR", "sjr");
            String hIndexHeader = findHeader(headerMap, "H index", "h_index", "H-index", "hindex");

            // Smart year detection from headers (e.g. "Total Docs. (2025)")
            int detectedYear = defaultYear;
            if (headerMap != null) {
                for (String header : headerMap.keySet()) {
                    if (header.contains("Total Docs. (")) {
                        int start = header.indexOf("(") + 1;
                        int end = header.indexOf(")");
                        if (start > 0 && end > start) {
                            try {
                                detectedYear = Integer.parseInt(header.substring(start, end).trim());
                                log.info("Detected SCImago ranking year from CSV headers: {}", detectedYear);
                                break;
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            for (CSVRecord record : csvParser) {
                String title = getRecordVal(record, titleHeader);
                String rawIssnColumn = getRecordVal(record, issnHeader);
                String bestQuartile = getRecordVal(record, quartileHeader);

                if (title == null || title.isBlank()) {
                    continue;
                }
                
                if (rawIssnColumn == null || rawIssnColumn.isBlank() || "-".equals(rawIssnColumn)) {
                    continue;
                }

                if (bestQuartile == null || bestQuartile.isBlank() || "-".equals(bestQuartile)) {
                    bestQuartile = "-";
                }

                Integer sourceId = parseSafeInt(getRecordVal(record, sourceIdHeader));
                
                String rawSjr = getRecordVal(record, sjrHeader);
                Double sjrScore = rawSjr != null ? parseSafeDouble(rawSjr.replace(",", ".")) : null;
                
                Integer hIndex = parseSafeInt(getRecordVal(record, hIndexHeader));

                // Splitting multiple ISSNs separated by commas
                String[] rawIssns = rawIssnColumn.split(",");

                for (String rawIssn : rawIssns) {
                    // Normalization: Remove hyphens, spaces, uppercase
                    String cleanIssn = rawIssn.replaceAll("[^0-9X x]", "").trim().toUpperCase();

                    // Standard ISSN length must be 8 chars
                    if (cleanIssn.length() == 8) {
                        recordList.add(new JournalRecord(
                                sourceId,
                                title,
                                cleanIssn,
                                sjrScore,
                                bestQuartile,
                                hIndex,
                                detectedYear
                        ));
                    }
                }
            }
        }
        return recordList;
    }

    private String findHeader(Map<String, Integer> headerMap, String... candidates) {
        if (headerMap == null) return null;
        for (String cand : candidates) {
            for (String key : headerMap.keySet()) {
                if (key.equalsIgnoreCase(cand)) {
                    return key;
                }
            }
        }
        return candidates.length > 0 ? candidates[0] : null;
    }

    private String getRecordVal(CSVRecord record, String header) {
        if (header != null && record.isMapped(header)) {
            return record.get(header);
        }
        return null;
    }

    private void batchUpsertToSqlServer(List<JournalRecord> records) {
        String sql = """
            MERGE INTO scimago_rankings AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?)) AS source (source_id, title, issn_clean, sjr_score, best_quartile, h_index, ranking_year)
            ON (target.issn_clean = source.issn_clean AND target.ranking_year = source.ranking_year)
            WHEN MATCHED THEN
                UPDATE SET 
                    target.title = source.title,
                    target.sjr_score = source.sjr_score,
                    target.best_quartile = source.best_quartile,
                    target.h_index = source.h_index
            WHEN NOT MATCHED THEN
                INSERT (source_id, title, issn_clean, sjr_score, best_quartile, h_index, ranking_year)
                VALUES (source.source_id, source.title, source.issn_clean, source.sjr_score, source.best_quartile, source.h_index, source.ranking_year);
            """;

        jdbcTemplate.batchUpdate(sql, records, 1000, (ps, r) -> {
            ps.setObject(1, r.sourceId());
            ps.setString(2, r.title());
            ps.setString(3, r.issnClean());
            ps.setObject(4, r.sjrScore());
            ps.setString(5, r.bestQuartile());
            ps.setObject(6, r.hIndex());
            ps.setInt(7, r.rankingYear());
        });
    }

    private Integer parseSafeInt(String val) {
        if (val == null) return null;
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return null; }
    }

    private Double parseSafeDouble(String val) {
        if (val == null) return null;
        try { return Double.parseDouble(val.trim()); } catch (Exception e) { return null; }
    }
}
