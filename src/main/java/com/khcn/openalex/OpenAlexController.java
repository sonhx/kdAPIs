package com.khcn.openalex;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/khcn/openalex", "/khcn/ptit-stats"})
public class OpenAlexController {

    @Autowired
    private OpenAlexService openAlexService;

    @Autowired
    private ScimagoIngestionService scimagoIngestionService;

    @Autowired
    private ScopusWosIngestionService scopusWosIngestionService;

    /**
     * Get PTIT overall institution research statistics and annual citation breakdown.
     */
    @RequestMapping(value = "/stats", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getInstitutionStats() {
        JSONObject stats = openAlexService.getInstitutionStats();
        return ResponseEntity.ok(stats.toString());
    }

    /**
     * Get statistics by research areas (Field, Subfield, Topic) matching Thong_ke_PTIT_Server.xlsx.
     */
    @RequestMapping(value = {"/area-stats", "/areas"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAreaStats() {
        JSONObject areaStats = openAlexService.getAreaStats();
        return ResponseEntity.ok(areaStats.toString());
    }

    /**
     * Get detailed list of cited papers with authors from PTIT matching Danh_sach_chi_tiet_tung_bai_bao.xlsx.
     * Params:
     * - page: page number (default: 1)
     * - size: page size (default: 50)
     * - year: filter by publication year
     * - search: search by title, authors, or journal
     * - sort: sort by 'citations' (default), 'year', or 'title'
     */
    @RequestMapping(value = "/works", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getWorks(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", defaultValue = "citations") String sort) {
        JSONObject works = openAlexService.getWorks(page, size, year, search, sort);
        return ResponseEntity.ok(works.toString());
    }

    /**
     * Get statistics on authors of PTIT.
     */
    @RequestMapping(value = "/authors", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAuthorStats(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", defaultValue = "cited_by_count") String sort) {
        JSONObject authors = openAlexService.getAuthorStats(limit, search, sort);
        return ResponseEntity.ok(authors.toString());
    }

    /**
     * Get top research topics and subfields of PTIT.
     */
    @RequestMapping(value = "/topics", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTopicStats() {
        JSONObject topics = openAlexService.getTopicStats();
        return ResponseEntity.ok(topics.toString());
    }

    /**
     * Sync Lite: Pulls list of new works or current year works only.
     */
    @RequestMapping(value = {"/sync-works-lite", "/sync-lite"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerWorksLiteSync() {
        JSONObject syncResult = openAlexService.syncOpenAlexWorks(false);
        return ResponseEntity.ok(syncResult.toString());
    }

    /**
     * Sync Full: Heavy operation that pulls ALL works from OpenAlex API.
     */
    @RequestMapping(value = {"/sync-works-full", "/sync-full"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerWorksFullSync() {
        JSONObject syncResult = openAlexService.syncOpenAlexWorks(true);
        return ResponseEntity.ok(syncResult.toString());
    }

    /**
     * Sync Institutional Metadata: Updates institution and author summary stats.
     */
    @RequestMapping(value = "/sync", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerSync() {
        JSONObject syncResult = openAlexService.syncOpenAlexData();
        return ResponseEntity.ok(syncResult.toString());
    }

    /**
     * Manually trigger SCImago Rankings CSV download and ingestion.
     * Params:
     * - year: optional target year to sync
     */
    @RequestMapping(value = "/sync-scimago", method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerScimagoSync(@RequestParam(value = "year", required = false) Integer year) {
        String message = scimagoIngestionService.executeManualIngestion(year);
        JSONObject result = new JSONObject();
        result.put("status", message.toLowerCase().contains("failed") || message.toLowerCase().contains("aborting") ? "ERROR" : "SUCCESS");
        result.put("message", message);
        return ResponseEntity.ok(result.toString());
    }

    /**
     * Manually trigger Scopus and Web of Science (WoS) journal dataset synchronization.
     */
    @RequestMapping(value = {"/sync-scopus-wos", "/sync-wos"}, method = {RequestMethod.GET, RequestMethod.POST}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> triggerScopusWosSync() {
        String message = scopusWosIngestionService.syncDatasetsFromDatabase();
        JSONObject result = new JSONObject();
        result.put("status", message.toLowerCase().contains("failed") ? "ERROR" : "SUCCESS");
        result.put("message", message);
        return ResponseEntity.ok(result.toString());
    }
}
