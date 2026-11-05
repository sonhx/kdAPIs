package com.kpi;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Types;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KpiD806Controller - REST endpoints for KPI D8.06
 * "Mức độ an toàn dữ liệu QA"
 * Thang Mức độ Trưởng thành An toàn Dữ liệu (Data Security Maturity Scale 1–5).
 * The KPI level is the highest level possible when all items in that level are checked.
 */
@RestController
@RequestMapping({"/api/kpi/d806", "/kpi/d806"})
@CrossOrigin(origins = "*")
public class KpiD806Controller {

    private static final Logger log = LoggerFactory.getLogger(KpiD806Controller.class);

    private static final String UPLOAD_SUBDIR = "d806_evidence";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTable() {
        // Tables dbo.kpi_d806_checklist_items, dbo.kpi_d806_assessments, dbo.kpi_d806_assessment_items
        // and KPI definitions are established in database migrations.
        // Startup DDL execution bypassed to prevent database lock contention.
    }

    /**
     * Standardize period string to ANNUAL_YYYY (e.g. ANNUAL_2025)
     */
    private String normalizePeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            return "ANNUAL_2025";
        }
        String clean = period.trim();
        Matcher m = Pattern.compile("(20\\d\\d)").matcher(clean);
        if (m.find()) {
            return "ANNUAL_" + m.group(1);
        }
        return clean;
    }

    private String getLevelTitle(int level) {
        switch (level) {
            case 1: return "Mức 1 – Sơ khởi (Initial / Ad-hoc)";
            case 2: return "Mức 2 – Bắt đầu quản lý (Repeatable / managed)";
            case 3: return "Mức 3 – Đã xác định & chuẩn hóa (Defined)";
            case 4: return "Mức 4 – Được định lượng & giám sát (Quantitatively managed)";
            case 5: return "Mức 5 – Tối ưu hóa liên tục (Optimizing)";
            default: return "Chưa đạt Mức 1";
        }
    }

    // -------------------------------------------------------------------------
    // 1. GET /kpi/d806/checklist
    //    Get master list of 13 criteria grouped by Level 1 to 5
    // -------------------------------------------------------------------------
    @GetMapping(value = "/checklist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getChecklist() {
        JSONObject result = new JSONObject();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, level_number, level_name, level_name_en, item_code, content, standard_reference, sort_order " +
                "FROM dbo.kpi_d806_checklist_items WHERE is_active = 1 " +
                "ORDER BY level_number ASC, sort_order ASC"
            );

            JSONArray levels = buildGroupedLevels(rows, Collections.emptyMap());

            result.put("status", "SUCCESS");
            result.put("total_items", rows.size());
            result.put("levels", levels);
            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error retrieving D8.06 checklist", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 2. GET /kpi/d806/assessment
    //    Get assessment data for a given period (or blank template if none exists)
    // -------------------------------------------------------------------------
    @GetMapping(value = "/assessment", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAssessment(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "raw_period", required = false) String rawPeriod) {

        JSONObject result = new JSONObject();
        try {
            String targetPeriod = normalizePeriod(period != null ? period : rawPeriod);

            // 1. Fetch all master criteria
            List<Map<String, Object>> masterItems = jdbcTemplate.queryForList(
                "SELECT id, level_number, level_name, level_name_en, item_code, content, standard_reference, sort_order " +
                "FROM dbo.kpi_d806_checklist_items WHERE is_active = 1 " +
                "ORDER BY level_number ASC, sort_order ASC"
            );

            // 2. Fetch latest assessment record for this period
            List<Map<String, Object>> assessmentRows = jdbcTemplate.queryForList(
                "SELECT TOP 1 id, period_code, achieved_level, total_items, checked_items_count, level_summary_json, " +
                "       assessor_id, assessor_name, assessed_at, notes, batch_id, updated_at " +
                "FROM dbo.kpi_d806_assessments " +
                "WHERE period_code = ? OR period_code = ? " +
                "ORDER BY updated_at DESC",
                targetPeriod, period
            );

            Map<Integer, Map<String, Object>> itemResponses = new HashMap<>();
            Long assessmentId = null;
            int achievedLevel = 0;
            String assessorName = null;
            String assessedAtStr = null;
            String notes = null;

            if (!assessmentRows.isEmpty()) {
                Map<String, Object> header = assessmentRows.get(0);
                assessmentId = ((Number) header.get("id")).longValue();
                achievedLevel = header.get("achieved_level") != null ? ((Number) header.get("achieved_level")).intValue() : 0;
                assessorName = (String) header.get("assessor_name");
                assessedAtStr = header.get("assessed_at") != null ? header.get("assessed_at").toString() : null;
                notes = (String) header.get("notes");

                List<Map<String, Object>> detailRows = jdbcTemplate.queryForList(
                    "SELECT checklist_item_id, is_checked, evidence_text, evidence_file_name, evidence_file_url, evidence_file_size " +
                    "FROM dbo.kpi_d806_assessment_items WHERE assessment_id = ?",
                    assessmentId
                );

                for (Map<String, Object> d : detailRows) {
                    Integer itemId = ((Number) d.get("checklist_item_id")).intValue();
                    itemResponses.put(itemId, d);
                }
            }

            JSONArray levels = buildGroupedLevels(masterItems, itemResponses);

            // Re-calculate highest level possible when all items in that level are checked
            CalculationResult calc = calculateAchievedLevel(masterItems, itemResponses);

            result.put("status", "SUCCESS");
            result.put("period_code", targetPeriod);
            result.put("assessment_id", assessmentId != null ? assessmentId : JSONObject.NULL);
            result.put("achieved_level", calc.achievedLevel);
            result.put("achieved_level_name", getLevelTitle(calc.achievedLevel));
            result.put("total_items", masterItems.size());
            result.put("checked_items_count", calc.totalChecked);
            result.put("assessor_name", assessorName != null ? assessorName : "");
            result.put("assessed_at", assessedAtStr != null ? assessedAtStr : "");
            result.put("notes", notes != null ? notes : "");
            result.put("levels", levels);
            result.put("level_summary", calc.levelSummaryJson);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error retrieving D8.06 assessment", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 3. POST /kpi/d806/calculate
    //    Calculate the highest level given a list of checked item IDs (preview only)
    // -------------------------------------------------------------------------
    @PostMapping(value = "/calculate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> calculatePreview(@RequestBody String payload) {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(payload);
            JSONArray checkedIdsArr = json.optJSONArray("checkedItemIds");
            Set<Integer> checkedIds = new HashSet<>();
            if (checkedIdsArr != null) {
                for (int i = 0; i < checkedIdsArr.length(); i++) {
                    checkedIds.add(checkedIdsArr.getInt(i));
                }
            }

            List<Map<String, Object>> masterItems = jdbcTemplate.queryForList(
                "SELECT id, level_number, level_name, item_code FROM dbo.kpi_d806_checklist_items WHERE is_active = 1 ORDER BY level_number, sort_order"
            );

            Map<Integer, Map<String, Object>> itemResponses = new HashMap<>();
            for (Integer cid : checkedIds) {
                Map<String, Object> map = new HashMap<>();
                map.put("is_checked", Boolean.TRUE);
                itemResponses.put(cid, map);
            }

            CalculationResult calc = calculateAchievedLevel(masterItems, itemResponses);

            result.put("status", "SUCCESS");
            result.put("achieved_level", calc.achievedLevel);
            result.put("achieved_level_name", getLevelTitle(calc.achievedLevel));
            result.put("total_items", masterItems.size());
            result.put("checked_items_count", calc.totalChecked);
            result.put("level_summary", calc.levelSummaryJson);

            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            log.error("Error calculating D8.06 level preview", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 4. POST /kpi/d806/save
    //    Save or update checklist evaluation for a period
    // -------------------------------------------------------------------------
    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saveAssessment(@RequestBody String payload) {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(payload);
            String rawPeriod = json.optString("period_code", "ANNUAL_2025");
            String normalizedPeriod = normalizePeriod(rawPeriod);
            String notes = json.optString("notes", null);
            String assessorName = json.optString("assessor_name", "QA Officer");
            Integer assessorId = json.has("assessor_id") && !json.isNull("assessor_id") ? json.getInt("assessor_id") : null;
            boolean syncToKpi = json.optBoolean("sync_to_kpi", true);

            JSONArray itemsArr = json.optJSONArray("items");
            if (itemsArr == null) {
                itemsArr = new JSONArray();
            }

            // Load master items
            List<Map<String, Object>> masterItems = jdbcTemplate.queryForList(
                "SELECT id, level_number, level_name, item_code FROM dbo.kpi_d806_checklist_items WHERE is_active = 1 ORDER BY level_number, sort_order"
            );

            // Parse client responses
            Map<Integer, Map<String, Object>> itemResponses = new HashMap<>();
            for (int i = 0; i < itemsArr.length(); i++) {
                JSONObject it = itemsArr.getJSONObject(i);
                int itemId = it.getInt("checklist_item_id");
                boolean isChecked = it.optBoolean("is_checked", false);
                String evidenceText = it.optString("evidence_text", null);
                String evidenceFileName = it.optString("evidence_file_name", null);
                String evidenceFileUrl = it.optString("evidence_file_url", null);
                String evidenceFileSize = it.optString("evidence_file_size", null);

                Map<String, Object> map = new HashMap<>();
                map.put("is_checked", isChecked);
                map.put("evidence_text", evidenceText);
                map.put("evidence_file_name", evidenceFileName);
                map.put("evidence_file_url", evidenceFileUrl);
                map.put("evidence_file_size", evidenceFileSize);
                itemResponses.put(itemId, map);
            }

            // Calculate achieved level
            CalculationResult calc = calculateAchievedLevel(masterItems, itemResponses);
            int achievedLevel = calc.achievedLevel;
            String batchId = UUID.randomUUID().toString();

            // Check if assessment already exists for this period
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM dbo.kpi_d806_assessments WHERE period_code = ? OR period_code = ?",
                normalizedPeriod, rawPeriod
            );

            long assessmentId;
            if (!existing.isEmpty()) {
                assessmentId = ((Number) existing.get(0).get("id")).longValue();
                jdbcTemplate.update(
                    "UPDATE dbo.kpi_d806_assessments SET " +
                    "  period_code = ?, achieved_level = ?, total_items = ?, checked_items_count = ?, " +
                    "  level_summary_json = ?, assessor_id = ?, assessor_name = ?, notes = ?, batch_id = ?, updated_at = SYSDATETIME() " +
                    "WHERE id = ?",
                    normalizedPeriod, achievedLevel, masterItems.size(), calc.totalChecked,
                    calc.levelSummaryJson.toString(), assessorId, assessorName, notes, batchId, assessmentId
                );
                // Clear old items
                jdbcTemplate.update("DELETE FROM dbo.kpi_d806_assessment_items WHERE assessment_id = ?", assessmentId);
            } else {
                // Insert header
                jdbcTemplate.update(
                    "INSERT INTO dbo.kpi_d806_assessments " +
                    "(period_code, achieved_level, total_items, checked_items_count, level_summary_json, assessor_id, assessor_name, notes, batch_id, assessed_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATETIME(), SYSDATETIME())",
                    normalizedPeriod, achievedLevel, masterItems.size(), calc.totalChecked,
                    calc.levelSummaryJson.toString(), assessorId, assessorName, notes, batchId
                );
                assessmentId = jdbcTemplate.queryForObject("SELECT @@IDENTITY", Long.class);
            }

            // Insert details for each master item
            String insertItemSql = "INSERT INTO dbo.kpi_d806_assessment_items " +
                "(assessment_id, checklist_item_id, is_checked, evidence_text, evidence_file_name, evidence_file_url, evidence_file_size, evaluated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, SYSDATETIME())";

            for (Map<String, Object> mi : masterItems) {
                int itemId = ((Number) mi.get("id")).intValue();
                Map<String, Object> resp = itemResponses.get(itemId);
                boolean isChecked = resp != null && Boolean.TRUE.equals(resp.get("is_checked"));
                String evText = resp != null ? (String) resp.get("evidence_text") : null;
                String evFileName = resp != null ? (String) resp.get("evidence_file_name") : null;
                String evFileUrl = resp != null ? (String) resp.get("evidence_file_url") : null;
                String evFileSize = resp != null ? (String) resp.get("evidence_file_size") : null;

                jdbcTemplate.update(insertItemSql, assessmentId, itemId, isChecked ? 1 : 0, evText, evFileName, evFileUrl, evFileSize);
            }

            // Sync to kpi_data_points if requested
            if (syncToKpi && achievedLevel > 0) {
                syncToKpiDataPoints(normalizedPeriod, achievedLevel, calc.totalChecked, masterItems.size());
            }

            result.put("status", "SUCCESS");
            result.put("message", "Lưu đánh giá checklist thành công.");
            result.put("assessment_id", assessmentId);
            result.put("period_code", normalizedPeriod);
            result.put("achieved_level", achievedLevel);
            result.put("achieved_level_name", getLevelTitle(achievedLevel));
            result.put("checked_items_count", calc.totalChecked);
            result.put("total_items", masterItems.size());
            result.put("level_summary", calc.levelSummaryJson);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error saving D8.06 assessment", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi lưu bảng đánh giá: " + e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 5. POST /kpi/d806/upload-evidence
    //    Upload optional evidence file for a checklist criterion
    // -------------------------------------------------------------------------
    @PostMapping(value = "/upload-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadEvidence(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "item_code", required = false) String itemCode,
            @RequestParam(value = "period", required = false) String period) {

        JSONObject result = new JSONObject();
        if (file.isEmpty()) {
            result.put("status", "ERROR");
            result.put("message", "Tệp tải lên rỗng.");
            return ResponseEntity.badRequest().body(result.toString());
        }

        try {
            String originalName = file.getOriginalFilename();
            if (originalName == null) originalName = "evidence_" + System.currentTimeMillis();

            // Safe file extension
            String ext = "";
            int dotIdx = originalName.lastIndexOf(".");
            if (dotIdx >= 0) ext = originalName.substring(dotIdx);

            String safeBaseName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storedFileName = (itemCode != null ? itemCode.replace(".", "_") + "_" : "") +
                System.currentTimeMillis() + "_" + safeBaseName;

            // Target directory: ptitioc_upload/d806_evidence or fallback to user temp dir
            String baseUploadDir = System.getProperty("user.dir") + File.separator + "public" + File.separator + "uploads" + File.separator + UPLOAD_SUBDIR;
            File dir = new File(baseUploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File targetFile = new File(dir, storedFileName);
            try (InputStream in = file.getInputStream(); FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            long sizeBytes = file.getSize();
            String formattedSize = sizeBytes > 1024 * 1024
                ? String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                : String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0);

            String fileUrl = "/uploads/" + UPLOAD_SUBDIR + "/" + storedFileName;

            result.put("status", "SUCCESS");
            result.put("original_name", originalName);
            result.put("file_name", storedFileName);
            result.put("file_url", fileUrl);
            result.put("file_size", formattedSize);
            result.put("message", "Tải tệp minh chứng thành công.");

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error uploading evidence for D8.06", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi upload file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 6. POST /kpi/d806/sync-kpi
    //    Explicit sync of calculated level to kpi_data_points
    // -------------------------------------------------------------------------
    @PostMapping(value = "/sync-kpi", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> syncKpiData(@RequestBody String payload) {
        JSONObject result = new JSONObject();
        try {
            JSONObject json = new JSONObject(payload);
            String rawPeriod = json.optString("period_code", "ANNUAL_2025");
            String normalizedPeriod = normalizePeriod(rawPeriod);
            int level = json.optInt("achieved_level", 0);

            if (level <= 0) {
                // Fetch from assessment table
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT achieved_level, checked_items_count, total_items FROM dbo.kpi_d806_assessments WHERE period_code = ?",
                    normalizedPeriod
                );
                if (!rows.isEmpty()) {
                    level = ((Number) rows.get(0).get("achieved_level")).intValue();
                }
            }

            if (level <= 0) {
                result.put("status", "ERROR");
                result.put("message", "Không tìm thấy mức đánh giá hợp lệ để đồng bộ.");
                return ResponseEntity.badRequest().body(result.toString());
            }

            syncToKpiDataPoints(normalizedPeriod, level, 0, 13);

            result.put("status", "SUCCESS");
            result.put("message", "Đã đồng bộ giá trị KPI D8.06 = Mức " + level + " vào hệ thống.");
            result.put("achieved_level", level);
            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error syncing D8.06 to kpi_data_points", e);
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Calculation Helpers
    // -------------------------------------------------------------------------
    private static class CalculationResult {
        int achievedLevel;
        int totalChecked;
        JSONObject levelSummaryJson;
    }

    /**
     * "The KPI level is the highest level possible when all items in the level is checked."
     */
    private CalculationResult calculateAchievedLevel(
            List<Map<String, Object>> masterItems,
            Map<Integer, Map<String, Object>> responses) {

        CalculationResult res = new CalculationResult();
        JSONObject summary = new JSONObject();

        Map<Integer, Integer> totalPerLevel = new HashMap<>();
        Map<Integer, Integer> checkedPerLevel = new HashMap<>();

        for (int lvl = 1; lvl <= 5; lvl++) {
            totalPerLevel.put(lvl, 0);
            checkedPerLevel.put(lvl, 0);
        }

        int totalCheckedOverall = 0;

        for (Map<String, Object> mi : masterItems) {
            int lvl = ((Number) mi.get("level_number")).intValue();
            int id = ((Number) mi.get("id")).intValue();

            totalPerLevel.put(lvl, totalPerLevel.getOrDefault(lvl, 0) + 1);

            Map<String, Object> resp = responses.get(id);
            if (resp != null && Boolean.TRUE.equals(resp.get("is_checked"))) {
                checkedPerLevel.put(lvl, checkedPerLevel.getOrDefault(lvl, 0) + 1);
                totalCheckedOverall++;
            }
        }

        int highestLevel = 0;
        for (int lvl = 1; lvl <= 5; lvl++) {
            int tot = totalPerLevel.get(lvl);
            int chk = checkedPerLevel.get(lvl);
            boolean isComplete = (tot > 0 && chk == tot);

            JSONObject lvlObj = new JSONObject();
            lvlObj.put("levelNumber", lvl);
            lvlObj.put("totalItems", tot);
            lvlObj.put("checkedCount", chk);
            lvlObj.put("isComplete", isComplete);
            summary.put(String.valueOf(lvl), lvlObj);

            if (isComplete && lvl > highestLevel) {
                highestLevel = lvl;
            }
        }

        // If no full level completed yet, but items are checked, baseline is 0 or 1
        res.achievedLevel = highestLevel;
        res.totalChecked = totalCheckedOverall;
        res.levelSummaryJson = summary;
        return res;
    }

    private JSONArray buildGroupedLevels(
            List<Map<String, Object>> masterItems,
            Map<Integer, Map<String, Object>> responses) {

        JSONArray levelsArr = new JSONArray();
        Map<Integer, List<JSONObject>> grouped = new TreeMap<>();

        for (Map<String, Object> mi : masterItems) {
            int lvl = ((Number) mi.get("level_number")).intValue();
            int id = ((Number) mi.get("id")).intValue();

            JSONObject itemJson = new JSONObject();
            itemJson.put("id", id);
            itemJson.put("itemCode", mi.get("item_code"));
            itemJson.put("levelNumber", lvl);
            itemJson.put("content", mi.get("content"));
            itemJson.put("standardReference", mi.get("standard_reference") != null ? mi.get("standard_reference") : "");
            itemJson.put("sortOrder", mi.get("sort_order") != null ? ((Number) mi.get("sort_order")).intValue() : 1);

            Map<String, Object> resp = responses.get(id);
            boolean isChecked = resp != null && Boolean.TRUE.equals(resp.get("is_checked"));
            itemJson.put("isChecked", isChecked);
            itemJson.put("evidenceText", resp != null && resp.get("evidence_text") != null ? resp.get("evidence_text") : "");
            itemJson.put("evidenceFileName", resp != null && resp.get("evidence_file_name") != null ? resp.get("evidence_file_name") : "");
            itemJson.put("evidenceFileUrl", resp != null && resp.get("evidence_file_url") != null ? resp.get("evidence_file_url") : "");
            itemJson.put("evidenceFileSize", resp != null && resp.get("evidence_file_size") != null ? resp.get("evidence_file_size") : "");

            grouped.computeIfAbsent(lvl, k -> new ArrayList<>()).add(itemJson);
        }

        for (Map.Entry<Integer, List<JSONObject>> entry : grouped.entrySet()) {
            int lvl = entry.getKey();
            List<JSONObject> items = entry.getValue();

            int checkedCount = 0;
            JSONArray itArr = new JSONArray();
            for (JSONObject it : items) {
                if (it.getBoolean("isChecked")) checkedCount++;
                itArr.put(it);
            }

            JSONObject levelObj = new JSONObject();
            levelObj.put("levelNumber", lvl);
            levelObj.put("levelName", getLevelTitle(lvl));
            levelObj.put("totalItems", items.size());
            levelObj.put("checkedCount", checkedCount);
            levelObj.put("isComplete", items.size() > 0 && checkedCount == items.size());
            levelObj.put("items", itArr);

            levelsArr.put(levelObj);
        }

        return levelsArr;
    }

    private void syncToKpiDataPoints(String periodCode, int level, int checkedCount, int totalItems) {
        try {
            List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(
                "SELECT id, cycle FROM kpi_definitions WHERE kpi_code = 'D8.06' AND (is_deleted = 0 OR is_deleted IS NULL)"
            );
            if (kpiRows.isEmpty()) {
                return;
            }

            int kpiId = ((Number) kpiRows.get(0).get("id")).intValue();

            // Find period_id from period_definitions or cycle if available
            List<Map<String, Object>> periodRows = jdbcTemplate.queryForList(
                "SELECT period_id FROM period_definitions WHERE period_code = ? OR period_name LIKE ?",
                periodCode, "%" + periodCode + "%"
            );

            Integer periodId = null;
            if (!periodRows.isEmpty()) {
                periodId = ((Number) periodRows.get(0).get("period_id")).intValue();
            }

            String note = String.format(
                "[Checklist An toàn dữ liệu QA]: Đạt Mức %d/5 (%s). Hoàn thành 100%% tiêu chí Mức %d.",
                level, getLevelTitle(level), level
            );

            // Upsert into kpi_data_points
            String checkExistSql = periodId != null
                ? "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ?"
                : "SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? ORDER BY data_id DESC";

            List<Map<String, Object>> existingDp = periodId != null
                ? jdbcTemplate.queryForList(checkExistSql, kpiId, periodId)
                : jdbcTemplate.queryForList(checkExistSql, kpiId);

            if (!existingDp.isEmpty()) {
                long dataId = ((Number) existingDp.get(0).get("data_id")).longValue();
                jdbcTemplate.update(
                    "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), notes = ? WHERE data_id = ?",
                    (double) level, note, dataId
                );
                log.info("Synced KPI D8.06 (Level {}) into existing kpi_data_points id: {}", level, dataId);
            } else {
                jdbcTemplate.update(
                    "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, target, status_id, notes, updated_at) " +
                    "VALUES (?, ?, ?, 4.0, 1, ?, GETDATE())",
                    kpiId, periodId, (double) level, note
                );
                log.info("Inserted new kpi_data_points row for KPI D8.06 (Level {})", level);
            }
        } catch (Exception e) {
            log.warn("Notice syncing D8.06 to kpi_data_points: {}", e.getMessage());
        }
    }
}
