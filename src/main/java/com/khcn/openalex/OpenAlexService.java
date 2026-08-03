package com.khcn.openalex;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.SqlParameterValue;
import java.sql.Types;

@Service
public class OpenAlexService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAlexService.class);

    private static final String PTIT_INSTITUTION_ID = "I4400600977";
    private static final String API_KEY = "mPjrshrfAVIafDYQjN3BT2";
    private static final String EMAIL_CONTACT = "sonhx@ptit.edu.vn";

    private static final String OPENALEX_INSTITUTION_URL = "https://api.openalex.org/institutions/" + PTIT_INSTITUTION_ID;
    private static final String OPENALEX_AUTHORS_URL = "https://api.openalex.org/authors?filter=last_known_institutions.id:" 
            + PTIT_INSTITUTION_ID + "&sort=cited_by_count:desc&per-page=100&api_key=" + API_KEY;
    private static final String OPENALEX_WORKS_URL = "https://api.openalex.org/works";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initDatabaseTables() {
        try {
            logger.info("Initializing OpenAlex Database Tables (unified lowercase without TBL_ prefix)...");

            // 1. openalex_institution_stats
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'openalex_institution_stats') " +
                    "BEGIN " +
                    "CREATE TABLE openalex_institution_stats (" +
                    "    id INT IDENTITY(1,1) PRIMARY KEY," +
                    "    institution_id NVARCHAR(100)," +
                    "    display_name NVARCHAR(255)," +
                    "    works_count INT," +
                    "    cited_by_count INT," +
                    "    h_index INT," +
                    "    i10_index INT," +
                    "    mean_citedness_2yr FLOAT," +
                    "    ror NVARCHAR(255)," +
                    "    homepage_url NVARCHAR(255)," +
                    "    last_updated DATETIME DEFAULT GETDATE()" +
                    "); " +
                    "END");

            // 2. openalex_yearly_stats
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'openalex_yearly_stats') " +
                    "BEGIN " +
                    "CREATE TABLE openalex_yearly_stats (" +
                    "    id INT IDENTITY(1,1) PRIMARY KEY," +
                    "    institution_id NVARCHAR(100)," +
                    "    year INT," +
                    "    works_count INT," +
                    "    oa_works_count INT," +
                    "    cited_by_count INT," +
                    "    last_updated DATETIME DEFAULT GETDATE()" +
                    "); " +
                    "END");

            // 3. openalex_authors
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'openalex_authors') " +
                    "BEGIN " +
                    "CREATE TABLE openalex_authors (" +
                    "    id INT IDENTITY(1,1) PRIMARY KEY," +
                    "    author_id NVARCHAR(100)," +
                    "    display_name NVARCHAR(255)," +
                    "    orcid NVARCHAR(255)," +
                    "    works_count INT," +
                    "    cited_by_count INT," +
                    "    h_index INT," +
                    "    i10_index INT," +
                    "    mean_citedness_2yr FLOAT," +
                    "    last_known_institution NVARCHAR(255)," +
                    "    works_api_url NVARCHAR(500)," +
                    "    last_updated DATETIME DEFAULT GETDATE()" +
                    "); " +
                    "END");

            // 4. openalex_topics
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'openalex_topics') " +
                    "BEGIN " +
                    "CREATE TABLE openalex_topics (" +
                    "    id INT IDENTITY(1,1) PRIMARY KEY," +
                    "    institution_id NVARCHAR(100)," +
                    "    topic_id NVARCHAR(100)," +
                    "    display_name NVARCHAR(255)," +
                    "    works_count INT," +
                    "    subfield NVARCHAR(255)," +
                    "    field NVARCHAR(255)," +
                    "    domain NVARCHAR(255)," +
                    "    last_updated DATETIME DEFAULT GETDATE()" +
                    "); " +
                    "END");

            // 5. openalex_area_stats (statistics by area / field / subfield / topic)
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'openalex_area_stats') " +
                    "BEGIN " +
                    "CREATE TABLE openalex_area_stats (" +
                    "    id INT IDENTITY(1,1) PRIMARY KEY," +
                    "    institution_id NVARCHAR(100)," +
                    "    field_name NVARCHAR(255)," +
                    "    subfield_name NVARCHAR(255)," +
                    "    topic_name NVARCHAR(255)," +
                    "    works_count INT," +
                    "    total_citations INT," +
                    "    avg_citations FLOAT," +
                    "    last_updated DATETIME DEFAULT GETDATE()" +
                    "); " +
                    "END");

            // 6. openalex_works (detailed list of papers)
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'openalex_works') " +
                    "BEGIN " +
                    "CREATE TABLE openalex_works (" +
                    "    id INT IDENTITY(1,1) PRIMARY KEY," +
                    "    openalex_work_id NVARCHAR(255) UNIQUE," +
                    "    title NVARCHAR(1000)," +
                    "    authors NVARCHAR(MAX)," +
                    "    publication_year INT," +
                    "    journal_source NVARCHAR(500)," +
                    "    citations_count INT," +
                    "    field_name NVARCHAR(255)," +
                    "    subfield_name NVARCHAR(255)," +
                    "    topic_name NVARCHAR(255)," +
                    "    doi NVARCHAR(500)," +
                    "    issn_clean VARCHAR(8) NULL," +
                    "    institution_id NVARCHAR(100)," +
                    "    last_updated DATETIME DEFAULT GETDATE()" +
                    "); " +
                    "END");

            // Migration: Alter openalex_works to add issn_clean if it does not exist
            try {
                jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('openalex_works') AND name = 'issn_clean') " +
                        "ALTER TABLE openalex_works ADD issn_clean VARCHAR(8) NULL;");
            } catch (Exception e) {
                logger.error("Error altering openalex_works to add issn_clean: {}", e.getMessage());
            }

            logger.info("OpenAlex Database Tables initialized successfully.");
        } catch (Exception e) {
            logger.error("Error initializing OpenAlex database tables: {}", e.getMessage(), e);
        }
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new SqlParameterValue(Types.NVARCHAR, val);
    }

    private String fetchUrlContent(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("mailto", EMAIL_CONTACT);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("HTTP GET failed with code " + responseCode + " for URL: " + urlString);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    public synchronized JSONObject syncOpenAlexData() {
        JSONObject result = new JSONObject();
        try {
            logger.info("Starting OpenAlex Institution & Author Data Synchronization...");

            // 1. Institution Stats
            String instJsonStr = fetchUrlContent(OPENALEX_INSTITUTION_URL + "?api_key=" + API_KEY);
            JSONObject instJson = new JSONObject(instJsonStr);

            String displayName = instJson.optString("display_name", "Posts and Telecommunications Institute of Technology");
            int worksCount = instJson.optInt("works_count", 0);
            int citedByCount = instJson.optInt("cited_by_count", 0);
            
            JSONObject summaryStats = instJson.optJSONObject("summary_stats");
            int hIndex = summaryStats != null ? summaryStats.optInt("h_index", 0) : 0;
            int i10Index = summaryStats != null ? summaryStats.optInt("i10_index", 0) : 0;
            double meanCitedness2yr = summaryStats != null ? summaryStats.optDouble("2yr_mean_citedness", 0.0) : 0.0;
            
            String ror = instJson.optString("ror", "");
            String homepageUrl = instJson.optString("homepage_url", "");

            jdbcTemplate.update("DELETE FROM openalex_institution_stats WHERE institution_id = ?", toNVarChar(PTIT_INSTITUTION_ID));
            jdbcTemplate.update("INSERT INTO openalex_institution_stats (institution_id, display_name, works_count, cited_by_count, h_index, i10_index, mean_citedness_2yr, ror, homepage_url, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE())", 
                    toNVarChar(PTIT_INSTITUTION_ID), toNVarChar(displayName), worksCount, citedByCount, hIndex, i10Index, meanCitedness2yr, toNVarChar(ror), toNVarChar(homepageUrl));

            // 2. Yearly Breakdown
            JSONArray yearlyArr = instJson.optJSONArray("counts_by_year");
            if (yearlyArr != null) {
                jdbcTemplate.update("DELETE FROM openalex_yearly_stats WHERE institution_id = ?", toNVarChar(PTIT_INSTITUTION_ID));
                String insertYearlySql = "INSERT INTO openalex_yearly_stats (institution_id, year, works_count, oa_works_count, cited_by_count, last_updated) " +
                        "VALUES (?, ?, ?, ?, ?, GETDATE())";
                for (int i = 0; i < yearlyArr.length(); i++) {
                    JSONObject yObj = yearlyArr.getJSONObject(i);
                    jdbcTemplate.update(insertYearlySql, toNVarChar(PTIT_INSTITUTION_ID), yObj.optInt("year", 0), yObj.optInt("works_count", 0), yObj.optInt("oa_works_count", 0), yObj.optInt("cited_by_count", 0));
                }
            }

            // 3. Topics
            JSONArray topicsArr = instJson.optJSONArray("topics");
            if (topicsArr != null) {
                jdbcTemplate.update("DELETE FROM openalex_topics WHERE institution_id = ?", toNVarChar(PTIT_INSTITUTION_ID));
                String insertTopicSql = "INSERT INTO openalex_topics (institution_id, topic_id, display_name, works_count, subfield, field, domain, last_updated) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, GETDATE())";
                for (int i = 0; i < topicsArr.length(); i++) {
                    JSONObject tObj = topicsArr.getJSONObject(i);
                    JSONObject subfieldObj = tObj.optJSONObject("subfield");
                    JSONObject fieldObj = tObj.optJSONObject("field");
                    JSONObject domainObj = tObj.optJSONObject("domain");

                    jdbcTemplate.update(insertTopicSql, 
                            toNVarChar(PTIT_INSTITUTION_ID), 
                            toNVarChar(tObj.optString("id", "")), 
                            toNVarChar(tObj.optString("display_name", "")), 
                            tObj.optInt("count", 0),
                            toNVarChar(subfieldObj != null ? subfieldObj.optString("display_name", "") : ""),
                            toNVarChar(fieldObj != null ? fieldObj.optString("display_name", "") : ""),
                            toNVarChar(domainObj != null ? domainObj.optString("display_name", "") : ""));
                }
            }

            // 4. Authors
            String authorsJsonStr = fetchUrlContent(OPENALEX_AUTHORS_URL);
            JSONObject authorsJson = new JSONObject(authorsJsonStr);
            JSONArray authorsArr = authorsJson.optJSONArray("results");

            int authorSavedCount = 0;
            if (authorsArr != null) {
                jdbcTemplate.update("DELETE FROM openalex_authors");
                String insertAuthorSql = "INSERT INTO openalex_authors (author_id, display_name, orcid, works_count, cited_by_count, h_index, i10_index, mean_citedness_2yr, last_known_institution, works_api_url, last_updated) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE())";

                for (int i = 0; i < authorsArr.length(); i++) {
                    JSONObject aObj = authorsArr.getJSONObject(i);
                    JSONObject aSummary = aObj.optJSONObject("summary_stats");
                    JSONArray lastInstArr = aObj.optJSONArray("last_known_institutions");
                    String lastInst = lastInstArr != null && lastInstArr.length() > 0 ? lastInstArr.getJSONObject(0).optString("display_name", displayName) : displayName;

                    jdbcTemplate.update(insertAuthorSql,
                            toNVarChar(aObj.optString("id", "")),
                            toNVarChar(aObj.optString("display_name", "")),
                            toNVarChar(aObj.optString("orcid", "")),
                            aObj.optInt("works_count", 0),
                            aObj.optInt("cited_by_count", 0),
                            aSummary != null ? aSummary.optInt("h_index", 0) : 0,
                            aSummary != null ? aSummary.optInt("i10_index", 0) : 0,
                            aSummary != null ? aSummary.optDouble("2yr_mean_citedness", 0.0) : 0.0,
                            toNVarChar(lastInst),
                            toNVarChar(aObj.optString("works_api_url", "")));
                    authorSavedCount++;
                }
            }

            result.put("status", "SUCCESS");
            result.put("message", "OpenAlex institutional statistics synchronized successfully.");
            result.put("authorsSaved", authorSavedCount);
        } catch (Exception e) {
            logger.error("Error during OpenAlex institution/author synchronization: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * Sync detailed works from OpenAlex.
     * @param isFull if true, fetches ALL works via cursor. If false, fetches current/recent year works only (lite).
     */
    public synchronized JSONObject syncOpenAlexWorks(boolean isFull) {
        JSONObject result = new JSONObject();
        int totalSaved = 0;
        int pageCount = 0;

        try {
            logger.info("Starting OpenAlex Works Synchronization (Mode: {})...", isFull ? "FULL" : "LITE");

            String cursor = "*";
            String baseUrl = OPENALEX_WORKS_URL + "?filter=institutions.id:" + PTIT_INSTITUTION_ID;

            if (!isFull) {
                // Lite mode: current year works (dynamically gets current calendar year)
                int currentYear = java.time.Year.now().getValue();
                baseUrl += ",publication_year:" + currentYear; 
            }

            baseUrl += "&per_page=100&select=id,title,publication_year,primary_location,authorships,cited_by_count,primary_topic,doi&api_key=" + API_KEY;

            while (true) {
                pageCount++;
                String currentUrl = baseUrl + "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8.name());
                
                logger.info("Fetching OpenAlex Works page {} (cursor: {})...", pageCount, cursor);
                String jsonStr = fetchUrlContent(currentUrl);
                JSONObject resJson = new JSONObject(jsonStr);

                JSONArray results = resJson.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    break;
                }

                for (int i = 0; i < results.length(); i++) {
                    JSONObject work = results.getJSONObject(i);
                    upsertWork(work);
                    totalSaved++;
                }

                JSONObject meta = resJson.optJSONObject("meta");
                String nextCursor = meta != null ? meta.optString("next_cursor", null) : null;

                if (nextCursor == null || nextCursor.isEmpty() || nextCursor.equals(cursor) || (!isFull && pageCount >= 3)) {
                    break;
                }

                cursor = nextCursor;
                Thread.sleep(150); // rate-limiting protection
            }

            // Recalculate area stats from openalex_works table
            recalculateAreaStats();

            result.put("status", "SUCCESS");
            result.put("mode", isFull ? "FULL" : "LITE");
            result.put("worksSynced", totalSaved);
            result.put("pagesProcessed", pageCount);
            result.put("message", "OpenAlex works synchronized successfully.");

            logger.info("OpenAlex Works Sync completed. Mode: {}, Works: {}", isFull ? "FULL" : "LITE", totalSaved);
        } catch (Exception e) {
            logger.error("Error during OpenAlex works synchronization: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }

        return result;
    }

    private void upsertWork(JSONObject work) {
        String openalexWorkId = work.optString("id", "");
        if (openalexWorkId.isEmpty()) return;

        String title = work.optString("title", "Không có tiêu đề");
        int pubYear = work.optInt("publication_year", 0);
        int citations = work.optInt("cited_by_count", 0);
        String doi = work.optString("doi", "");

        // Primary location -> Source / Journal name
        JSONObject location = work.optJSONObject("primary_location");
        JSONObject sourceObj = location != null ? location.optJSONObject("source") : null;
        String journalSource = sourceObj != null ? sourceObj.optString("display_name", "Không rõ Tạp chí/Hội thảo") : "Không rõ Tạp chí/Hội thảo";

        // Extract and clean ISSN
        String issnClean = null;
        if (sourceObj != null) {
            String issnL = sourceObj.optString("issn_l", "");
            if (!issnL.isEmpty()) {
                issnClean = issnL.replaceAll("[^0-9X]", "").trim().toUpperCase();
            }
            if (issnClean == null || issnClean.length() != 8) {
                JSONArray issns = sourceObj.optJSONArray("issns");
                if (issns != null && issns.length() > 0) {
                    for (int k = 0; k < issns.length(); k++) {
                        String rawIssn = issns.optString(k, "");
                        String clean = rawIssn.replaceAll("[^0-9X]", "").trim().toUpperCase();
                        if (clean.length() == 8) {
                            issnClean = clean;
                            break;
                        }
                    }
                }
            }
        }

        // Authorships -> Author list string
        JSONArray authorships = work.optJSONArray("authorships");
        List<String> authorNames = new ArrayList<>();
        if (authorships != null) {
            for (int j = 0; j < authorships.length(); j++) {
                JSONObject authObj = authorships.getJSONObject(j).optJSONObject("author");
                if (authObj != null) {
                    String aName = authObj.optString("display_name", "");
                    if (!aName.isEmpty()) authorNames.add(aName);
                }
            }
        }
        String authorsStr = !authorNames.isEmpty() ? String.join(", ", authorNames) : "Không rõ tác giả";

        // Primary topic -> Field, Subfield, Topic
        JSONObject topicObj = work.optJSONObject("primary_topic");
        String topicName = "Chưa phân loại";
        String fieldName = "Chưa phân loại";
        String subfieldName = "Chưa phân loại";

        if (topicObj != null) {
            topicName = topicObj.optString("display_name", "Chưa phân loại");
            
            JSONObject fieldObj = topicObj.optJSONObject("field");
            if (fieldObj != null) fieldName = fieldObj.optString("display_name", "Chưa phân loại");
            
            JSONObject subfieldObj = topicObj.optJSONObject("subfield");
            if (subfieldObj != null) subfieldName = subfieldObj.optString("display_name", "Chưa phân loại");
        }

        String mergeSql = "MERGE INTO openalex_works WITH (HOLDLOCK) AS target " +
                "USING (SELECT ? AS openalex_work_id) AS source " +
                "ON (target.openalex_work_id = source.openalex_work_id) " +
                "WHEN MATCHED THEN " +
                "    UPDATE SET title = ?, authors = ?, publication_year = ?, journal_source = ?, citations_count = ?, " +
                "               field_name = ?, subfield_name = ?, topic_name = ?, doi = ?, issn_clean = ?, last_updated = GETDATE() " +
                "WHEN NOT MATCHED THEN " +
                "    INSERT (openalex_work_id, title, authors, publication_year, journal_source, citations_count, field_name, subfield_name, topic_name, doi, issn_clean, institution_id, last_updated) " +
                "    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE());";

        jdbcTemplate.update(mergeSql,
                toNVarChar(openalexWorkId), toNVarChar(title), toNVarChar(authorsStr), pubYear, toNVarChar(journalSource), citations, toNVarChar(fieldName), toNVarChar(subfieldName), toNVarChar(topicName), toNVarChar(doi), issnClean,
                toNVarChar(openalexWorkId), toNVarChar(title), toNVarChar(authorsStr), pubYear, toNVarChar(journalSource), citations, toNVarChar(fieldName), toNVarChar(subfieldName), toNVarChar(topicName), toNVarChar(doi), issnClean, toNVarChar(PTIT_INSTITUTION_ID));
    }

    private void recalculateAreaStats() {
        logger.info("Recalculating OpenAlex Area Statistics from openalex_works table...");
        jdbcTemplate.update("DELETE FROM openalex_area_stats WHERE institution_id = ?", toNVarChar(PTIT_INSTITUTION_ID));

        String calcSql = "INSERT INTO openalex_area_stats (institution_id, field_name, subfield_name, topic_name, works_count, total_citations, avg_citations, last_updated) " +
                "SELECT ?, field_name, subfield_name, topic_name, COUNT(*) AS works_count, SUM(citations_count) AS total_citations, " +
                "ROUND(CAST(SUM(citations_count) AS FLOAT) / COUNT(*), 2) AS avg_citations, GETDATE() " +
                "FROM openalex_works " +
                "WHERE institution_id = ? " +
                "GROUP BY field_name, subfield_name, topic_name " +
                "ORDER BY COUNT(*) DESC";

        jdbcTemplate.update(calcSql, toNVarChar(PTIT_INSTITUTION_ID), toNVarChar(PTIT_INSTITUTION_ID));
        logger.info("OpenAlex Area Statistics recalculated successfully.");
    }

    // =========================================================================
    // GETTER APIs
    // =========================================================================

    /**
     * Get statistics by areas (Field, Subfield, Topic) matching Thong_ke_PTIT_Server.xlsx.
     */
    public JSONObject getAreaStats() {
        JSONObject res = new JSONObject();
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT field_name AS field, subfield_name AS subfield, topic_name AS topic, " +
                "works_count AS worksCount, total_citations AS totalCitations, avg_citations AS avgCitations " +
                "FROM openalex_area_stats WHERE institution_id = ? ORDER BY works_count DESC", toNVarChar(PTIT_INSTITUTION_ID));

        if (list.isEmpty()) {
            logger.info("openalex_area_stats is empty. Triggering lite works sync...");
            syncOpenAlexWorks(false);
            list = jdbcTemplate.queryForList(
                    "SELECT field_name AS field, subfield_name AS subfield, topic_name AS topic, " +
                    "works_count AS worksCount, total_citations AS totalCitations, avg_citations AS avgCitations " +
                    "FROM openalex_area_stats WHERE institution_id = ? ORDER BY works_count DESC", toNVarChar(PTIT_INSTITUTION_ID));
        }

        res.put("status", "SUCCESS");
        res.put("total", list.size());
        res.put("data", new JSONArray(list));
        return res;
    }

    /**
     * Get detailed list of cited papers matching Danh_sach_chi_tiet_tung_bai_bao.xlsx.
     */
    public JSONObject getWorks(int page, int size, Integer year, String search, String sort) {
        JSONObject res = new JSONObject();
        
        Integer totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM openalex_works WHERE institution_id = ?", Integer.class, toNVarChar(PTIT_INSTITUTION_ID));
        if (totalCount == null || totalCount == 0) {
            logger.info("openalex_works is empty. Triggering lite works sync...");
            syncOpenAlexWorks(false);
            totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM openalex_works WHERE institution_id = ?", Integer.class, toNVarChar(PTIT_INSTITUTION_ID));
        }

        int pageSize = size > 0 ? Math.min(size, 200) : 50;
        int pageNum = Math.max(page, 1);
        int offset = (pageNum - 1) * pageSize;

        String validSort = "w.citations_count";
        if ("publication_year".equalsIgnoreCase(sort) || "year".equalsIgnoreCase(sort)) {
            validSort = "w.publication_year";
        } else if ("title".equalsIgnoreCase(sort)) {
            validSort = "w.title";
        }

        StringBuilder sql = new StringBuilder(
                "SELECT w.openalex_work_id AS workId, w.title, w.authors, w.publication_year AS year, " +
                "w.journal_source AS journalSource, w.citations_count AS citations, w.field_name AS field, " +
                "w.subfield_name AS subfield, w.topic_name AS topic, w.doi, w.issn_clean AS issnClean, " +
                "r.best_quartile AS bestQuartile, r.sjr_score AS sjrScore " +
                "FROM openalex_works w " +
                "OUTER APPLY ( " +
                "    SELECT TOP 1 best_quartile, sjr_score " +
                "    FROM scimago_rankings " +
                "    WHERE issn_clean = w.issn_clean " +
                "    ORDER BY ABS(ranking_year - w.publication_year) ASC, ranking_year DESC " +
                ") r " +
                "WHERE w.institution_id = ? "
        );
        
        List<Object> paramsList = new ArrayList<>();
        paramsList.add(toNVarChar(PTIT_INSTITUTION_ID));

        if (year != null && year > 0) {
            sql.append("AND w.publication_year = ? ");
            paramsList.add(year);
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (w.title LIKE ? OR w.authors LIKE ? OR w.journal_source LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            paramsList.add(toNVarChar(searchPattern));
            paramsList.add(toNVarChar(searchPattern));
            paramsList.add(toNVarChar(searchPattern));
        }

        sql.append("ORDER BY ").append(validSort).append(" DESC ");
        sql.append("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        paramsList.add(offset);
        paramsList.add(pageSize);

        List<Map<String, Object>> worksList = jdbcTemplate.queryForList(sql.toString(), paramsList.toArray());

        res.put("status", "SUCCESS");
        res.put("page", pageNum);
        res.put("pageSize", pageSize);
        res.put("totalRecords", totalCount);
        res.put("data", new JSONArray(worksList));
        return res;
    }

    public JSONObject getInstitutionStats() {
        JSONObject res = new JSONObject();
        List<Map<String, Object>> instList = jdbcTemplate.queryForList("SELECT TOP 1 * FROM openalex_institution_stats WHERE institution_id = ?", toNVarChar(PTIT_INSTITUTION_ID));
        
        if (instList.isEmpty()) {
            syncOpenAlexData();
            instList = jdbcTemplate.queryForList("SELECT TOP 1 * FROM openalex_institution_stats WHERE institution_id = ?", toNVarChar(PTIT_INSTITUTION_ID));
        }

        res.put("institution", !instList.isEmpty() ? new JSONObject(instList.get(0)) : new JSONObject());

        List<Map<String, Object>> yearlyList = jdbcTemplate.queryForList("SELECT year, works_count AS worksCount, oa_works_count AS oaWorksCount, cited_by_count AS citedByCount FROM openalex_yearly_stats WHERE institution_id = ? ORDER BY year DESC", toNVarChar(PTIT_INSTITUTION_ID));
        res.put("yearlyStats", new JSONArray(yearlyList));

        return res;
    }

    public JSONObject getAuthorStats(int limit, String search, String sortBy) {
        JSONObject res = new JSONObject();
        
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM openalex_authors", Integer.class);
        if (count == null || count == 0) {
            syncOpenAlexData();
        }

        String validSortBy = "cited_by_count";
        if ("works_count".equalsIgnoreCase(sortBy)) validSortBy = "works_count";
        else if ("h_index".equalsIgnoreCase(sortBy)) validSortBy = "h_index";

        int maxLimit = limit > 0 ? Math.min(limit, 500) : 50;

        String sql = "SELECT TOP " + maxLimit + " author_id AS authorId, display_name AS displayName, orcid, works_count AS worksCount, " +
                "cited_by_count AS citedByCount, h_index AS hIndex, i10_index AS i10Index, mean_citedness_2yr AS meanCitedness2yr, " +
                "last_known_institution AS lastKnownInstitution, works_api_url AS worksApiUrl FROM openalex_authors ";

        List<Map<String, Object>> authors;
        if (search != null && !search.trim().isEmpty()) {
            sql += "WHERE display_name LIKE ? ORDER BY " + validSortBy + " DESC";
            authors = jdbcTemplate.queryForList(sql, toNVarChar("%" + search.trim() + "%"));
        } else {
            sql += "ORDER BY " + validSortBy + " DESC";
            authors = jdbcTemplate.queryForList(sql);
        }

        res.put("status", "SUCCESS");
        res.put("total", authors.size());
        res.put("sortBy", validSortBy);
        res.put("data", new JSONArray(authors));
        return res;
    }

    public JSONObject getTopicStats() {
        JSONObject res = new JSONObject();
        List<Map<String, Object>> topics = jdbcTemplate.queryForList("SELECT topic_id AS topicId, display_name AS displayName, works_count AS worksCount, subfield, field, domain FROM openalex_topics WHERE institution_id = ? ORDER BY works_count DESC", toNVarChar(PTIT_INSTITUTION_ID));
        res.put("status", "SUCCESS");
        res.put("total", topics.size());
        res.put("data", new JSONArray(topics));
        return res;
    }
}
