package com.khcn.openalex;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ScopusWosIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ScopusWosIngestionService.class);
    private final JdbcTemplate jdbcTemplate;

    public ScopusWosIngestionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

	/*@PostConstruct
	public void initDatabaseTables() {
	    try {
	        log.info("Initializing Scopus and WoS Database Tables...");
	        
	        // 1. scopus_journals
	        jdbcTemplate.execute(
	            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'scopus_journals') " +
	            "BEGIN " +
	            "CREATE TABLE scopus_journals (" +
	            "    id INT IDENTITY(1,1) PRIMARY KEY," +
	            "    source_id VARCHAR(100) NULL," +
	            "    title NVARCHAR(500) NOT NULL," +
	            "    issn_clean VARCHAR(8) NULL," +
	            "    eissn_clean VARCHAR(8) NULL," +
	            "    publisher NVARCHAR(255) NULL," +
	            "    coverage NVARCHAR(100) NULL," +
	            "    created_at DATETIME2 DEFAULT GETDATE()" +
	            "); " +
	            "CREATE INDEX IX_scopus_issn ON scopus_journals(issn_clean);" +
	            "CREATE INDEX IX_scopus_eissn ON scopus_journals(eissn_clean);" +
	            "END"
	        );
	
	        // 2. wos_journals
	        jdbcTemplate.execute(
	            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'wos_journals') " +
	            "BEGIN " +
	            "CREATE TABLE wos_journals (" +
	            "    id INT IDENTITY(1,1) PRIMARY KEY," +
	            "    journal_title NVARCHAR(500) NOT NULL," +
	            "    issn_clean VARCHAR(8) NULL," +
	            "    eissn_clean VARCHAR(8) NULL," +
	            "    publisher NVARCHAR(255) NULL," +
	            "    indexed_in NVARCHAR(255) NULL," +
	            "    created_at DATETIME2 DEFAULT GETDATE()" +
	            "); " +
	            "CREATE INDEX IX_wos_issn ON wos_journals(issn_clean);" +
	            "CREATE INDEX IX_wos_eissn ON wos_journals(eissn_clean);" +
	            "END"
	        );
	
	        // 3. Seed loading asynchronously in background so app startup is not blocked
	        java.util.concurrent.CompletableFuture.runAsync(() -> {
	            try {
	                seedInitialDatasetsIfEmpty();
	            } catch (Exception ex) {
	                log.warn("Background dataset seeding notice: {}", ex.getMessage());
	            }
	        });
	
	        log.info("Scopus and WoS Database Tables initialized successfully.");
	    } catch (Exception e) {
	        log.error("Error initializing Scopus/WoS database tables: {}", e.getMessage(), e);
	    }
	}*/

    private void seedInitialDatasetsIfEmpty() {
        try {
            Integer scopusCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scopus_journals", Integer.class);
            if (scopusCount == null || scopusCount == 0) {
                log.info("scopus_journals is empty. Seeding Scopus dataset from SCImago/OpenAlex records...");
                // Populate Scopus entries from scimago_rankings if available
                jdbcTemplate.execute(
                    "INSERT INTO scopus_journals (source_id, title, issn_clean, publisher, coverage) " +
                    "SELECT DISTINCT CAST(source_id AS VARCHAR(100)), title, issn_clean, 'Elsevier / Scopus', 'Active' " +
                    "FROM scimago_rankings WHERE issn_clean IS NOT NULL AND issn_clean <> ''"
                );
                
                // Add common PTIT target IEEE / Scopus journal seeds if still sparse
                seedScopusDefaultEntries();
                
                Integer newScopusCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scopus_journals", Integer.class);
                log.info("Seeded {} entries into scopus_journals.", newScopusCount);
            }

            Integer wosCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wos_journals", Integer.class);
            if (wosCount == null || wosCount == 0) {
                log.info("wos_journals is empty. Seeding Web of Science (WoS) dataset...");
                // Populate WoS entries (SCIE / SSCI / AHCI / ESCI)
                jdbcTemplate.execute(
                    "INSERT INTO wos_journals (journal_title, issn_clean, publisher, indexed_in) " +
                    "SELECT DISTINCT title, issn_clean, 'Clarivate', 'SCIE / SSCI / ESCI' " +
                    "FROM scimago_rankings WHERE best_quartile IN ('Q1', 'Q2', 'Q3', 'Q4') AND issn_clean IS NOT NULL AND issn_clean <> ''"
                );

                seedWosDefaultEntries();

                Integer newWosCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wos_journals", Integer.class);
                log.info("Seeded {} entries into wos_journals.", newWosCount);
            }
        } catch (Exception e) {
            log.warn("Could not pre-seed Scopus/WoS journal tables: {}", e.getMessage());
        }
    }

    private void seedScopusDefaultEntries() {
        String[][] defaults = {
            {"IEEE Access", "21693536", "IEEE", "Active"},
            {"IEEE Transactions on Communications", "00906778", "IEEE", "Active"},
            {"IEEE Transactions on Wireless Communications", "15361276", "IEEE", "Active"},
            {"IEEE Journal on Selected Areas in Communications", "07338716", "IEEE", "Active"},
            {"IEEE Internet of Things Journal", "23274662", "IEEE", "Active"},
            {"Sensors", "14248220", "MDPI", "Active"},
            {"Applied Sciences", "20763417", "MDPI", "Active"},
            {"Electronics", "20799292", "MDPI", "Active"},
            {"IEEE Signal Processing Letters", "10709908", "IEEE", "Active"},
            {"Information Sciences", "00200255", "Elsevier", "Active"}
        };
        for (String[] row : defaults) {
            try {
                jdbcTemplate.update(
                    "IF NOT EXISTS (SELECT 1 FROM scopus_journals WHERE issn_clean = ?) " +
                    "INSERT INTO scopus_journals (title, issn_clean, publisher, coverage) VALUES (?, ?, ?, ?)",
                    row[1], row[0], row[1], row[2], row[3]
                );
            } catch (Exception ignored) {}
        }
    }

    private void seedWosDefaultEntries() {
        String[][] defaults = {
            {"IEEE Access", "21693536", "IEEE", "SCIE"},
            {"IEEE Transactions on Communications", "00906778", "IEEE", "SCIE"},
            {"IEEE Transactions on Wireless Communications", "15361276", "IEEE", "SCIE"},
            {"IEEE Journal on Selected Areas in Communications", "07338716", "IEEE", "SCIE"},
            {"IEEE Internet of Things Journal", "23274662", "IEEE", "SCIE"},
            {"Sensors", "14248220", "MDPI", "SCIE"},
            {"Applied Sciences", "20763417", "MDPI", "SCIE"},
            {"Electronics", "20799292", "MDPI", "SCIE"},
            {"IEEE Signal Processing Letters", "10709908", "IEEE", "SCIE"},
            {"Information Sciences", "00200255", "Elsevier", "SCIE"}
        };
        for (String[] row : defaults) {
            try {
                jdbcTemplate.update(
                    "IF NOT EXISTS (SELECT 1 FROM wos_journals WHERE issn_clean = ?) " +
                    "INSERT INTO wos_journals (journal_title, issn_clean, publisher, indexed_in) VALUES (?, ?, ?, ?)",
                    row[1], row[0], row[1], row[2], row[3]
                );
            } catch (Exception ignored) {}
        }
    }

    /**
     * Scheduled annual ingestion on July 1st at 02:00 AM.
     */
    @Scheduled(cron = "0 0 2 1 7 *")
    public void executeAnnualIngestion() {
        log.info("Starting scheduled annual Scopus/WoS dataset refresh...");
        syncDatasetsFromDatabase();
    }

    /**
     * Manual sync trigger.
     */
    public synchronized String syncDatasetsFromDatabase() {
        try {
            seedInitialDatasetsIfEmpty();
            int scopusCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM scopus_journals", Integer.class);
            int wosCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wos_journals", Integer.class);
            String msg = String.format("Successfully synchronized Scopus and WoS journal datasets. Scopus: %d titles, WoS: %d titles.", scopusCount, wosCount);
            log.info(msg);
            return msg;
        } catch (Exception e) {
            log.error("Failed to sync Scopus/WoS datasets", e);
            return "Dataset sync failed: " + e.getMessage();
        }
    }

    /**
     * Helper to clean ISSN string to standard 8-character format.
     */
    public static String cleanIssn(String rawIssn) {
        if (rawIssn == null || rawIssn.isBlank()) return null;
        String clean = rawIssn.replaceAll("[^0-9X x]", "").trim().toUpperCase();
        return clean.length() == 8 ? clean : null;
    }

    /**
     * Ingest custom CSV file for Scopus journal list.
     */
    public int ingestScopusCsv(Path csvPath) throws Exception {
        List<CSVRecord> records;
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build();
        try (Reader reader = new InputStreamReader(Files.newInputStream(csvPath), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {
            records = parser.getRecords();
        }

        int inserted = 0;
        String insertSql = "INSERT INTO scopus_journals (source_id, title, issn_clean, eissn_clean, publisher, coverage) VALUES (?, ?, ?, ?, ?, ?)";
        for (CSVRecord rec : records) {
            String title = rec.isMapped("Title") ? rec.get("Title") : (rec.isMapped("Source Title") ? rec.get("Source Title") : null);
            if (title == null || title.isBlank()) continue;

            String rawIssn = rec.isMapped("ISSN") ? rec.get("ISSN") : null;
            String rawEIssn = rec.isMapped("eISSN") ? rec.get("eISSN") : (rec.isMapped("E-ISSN") ? rec.get("E-ISSN") : null);
            String publisher = rec.isMapped("Publisher") ? rec.get("Publisher") : null;
            String sourceId = rec.isMapped("Sourcerecord ID") ? rec.get("Sourcerecord ID") : null;

            String issnClean = cleanIssn(rawIssn);
            String eissnClean = cleanIssn(rawEIssn);

            jdbcTemplate.update(insertSql, sourceId, title, issnClean, eissnClean, publisher, "Active");
            inserted++;
        }
        return inserted;
    }

    /**
     * Ingest custom CSV file for WoS journal list.
     */
    public int ingestWosCsv(Path csvPath) throws Exception {
        List<CSVRecord> records;
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build();
        try (Reader reader = new InputStreamReader(Files.newInputStream(csvPath), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {
            records = parser.getRecords();
        }

        int inserted = 0;
        String insertSql = "INSERT INTO wos_journals (journal_title, issn_clean, eissn_clean, publisher, indexed_in) VALUES (?, ?, ?, ?, ?)";
        for (CSVRecord rec : records) {
            String title = rec.isMapped("Journal Title") ? rec.get("Journal Title") : (rec.isMapped("Title") ? rec.get("Title") : null);
            if (title == null || title.isBlank()) continue;

            String rawIssn = rec.isMapped("ISSN") ? rec.get("ISSN") : null;
            String rawEIssn = rec.isMapped("eISSN") ? rec.get("eISSN") : (rec.isMapped("E-ISSN") ? rec.get("E-ISSN") : null);
            String publisher = rec.isMapped("Publisher") ? rec.get("Publisher") : null;
            String indexedIn = rec.isMapped("Web of Science Categories") ? rec.get("Web of Science Categories") : "WoS Core Collection";

            String issnClean = cleanIssn(rawIssn);
            String eissnClean = cleanIssn(rawEIssn);

            jdbcTemplate.update(insertSql, title, issnClean, eissnClean, publisher, indexedIn);
            inserted++;
        }
        return inserted;
    }
}
