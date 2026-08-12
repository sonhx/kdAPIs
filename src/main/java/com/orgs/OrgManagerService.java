package com.orgs;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Types;
import java.util.*;

@Service
public class OrgManagerService {

    private static final Logger log = LoggerFactory.getLogger(OrgManagerService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

	/*@PostConstruct
	public void initDatabaseTable() {
	    try {
	        log.info("Initializing 'orgs' database table...");
	        String createTableSql = 
	            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'orgs') " +
	            "BEGIN " +
	            "    CREATE TABLE orgs ( " +
	            "        id VARCHAR(100) PRIMARY KEY, " +
	            "        ten NVARCHAR(500) NOT NULL, " +
	            "        maDonVi NVARCHAR(100) NULL, " +
	            "        donViChaId VARCHAR(100) NULL, " +
	            "        tenVietTat NVARCHAR(200) NULL, " +
	            "        level INT NOT NULL DEFAULT 1, " +
	            "        isDeleted INT NOT NULL DEFAULT 0, " +
	            "        createdAt DATETIME2 DEFAULT GETDATE(), " +
	            "        updatedAt DATETIME2 DEFAULT GETDATE() " +
	            "    ); " +
	            "    CREATE INDEX IX_orgs_donViChaId ON orgs(donViChaId); " +
	            "    CREATE INDEX IX_orgs_isDeleted ON orgs(isDeleted); " +
	            "END";
	        jdbcTemplate.execute(createTableSql);
	
	        // Migration to add 'level' column if it didn't exist in older table versions
	        String addLevelColumnSql = 
	            "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('orgs') AND name = 'level') " +
	            "BEGIN " +
	            "    ALTER TABLE orgs ADD level INT NOT NULL DEFAULT 1; " +
	            "END";
	        jdbcTemplate.execute(addLevelColumnSql);
	
	        // Migration to add 'leaderId' and 'leaderName' columns if missing
	        try {
	            String addLeaderColsSql = 
	                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('orgs') AND name = 'leaderId') " +
	                "BEGIN " +
	                "    ALTER TABLE orgs ADD leaderId VARCHAR(100) NULL; " +
	                "    ALTER TABLE orgs ADD leaderName NVARCHAR(500) NULL; " +
	                "END";
	            jdbcTemplate.execute(addLeaderColsSql);
	        } catch (Exception ex) {
	            log.debug("Notice migration leader columns: {}", ex.getMessage());
	        }
	
	        log.info("'orgs' table initialized successfully with 'level' and 'leader' columns.");
	
	        // Check if initial seeding or re-sync is required
	        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orgs", Integer.class);
	        if (count == null || count == 0) {
	            log.info("Table 'orgs' is empty. Seeding initial data from orgs.txt...");
	            syncInitialLocalFile();
	        } else {
	            // Perform level re-calculation for existing database records if level column was just added
	            log.info("Triggering level re-sync from orgs.txt to populate hierarchy levels...");
	            syncInitialLocalFile();
	        }
	    } catch (Exception e) {
	        log.error("Failed to initialize or seed 'orgs' table", e);
	    }
	}*/

    /**
     * Sync initial data from orgs.txt on local filesystem.
     */
    public JSONObject syncInitialLocalFile() {
        try {
            Path path = Paths.get("orgs.txt");
            if (!Files.exists(path)) {
                path = Paths.get("e:/eclipse-workspace/kdAPIs/orgs.txt");
            }
            if (Files.exists(path)) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                return syncOrgsFromJsonString(content);
            } else {
                log.warn("Local file orgs.txt not found for initial seeding.");
                JSONObject err = new JSONObject();
                err.put("status", "ERROR");
                err.put("message", "File orgs.txt not found");
                return err;
            }
        } catch (Exception e) {
            log.error("Error during initial seeding from orgs.txt", e);
            JSONObject err = new JSONObject();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return err;
        }
    }

    /**
     * Compute hierarchy level for every node in the uploaded dataset.
     * Roots (no parent or missing parent in dataset) = Level 1.
     * Children = Parent Level + 1.
     */
    private Map<String, Integer> calculateNodeLevels(JSONArray dataArray) {
        Map<String, JSONObject> itemMap = new HashMap<>();
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject item = dataArray.getJSONObject(i);
            String id = item.has("_id") ? item.optString("_id") : item.optString("id");
            if (id != null && !id.isBlank()) {
                itemMap.put(id, item);
            }
        }

        Map<String, Integer> levelMap = new HashMap<>();
        for (String id : itemMap.keySet()) {
            computeLevelRecursive(id, itemMap, levelMap, new HashSet<>());
        }
        return levelMap;
    }

    private int computeLevelRecursive(String id, Map<String, JSONObject> itemMap, Map<String, Integer> levelMap, Set<String> visited) {
        if (levelMap.containsKey(id)) {
            return levelMap.get(id);
        }
        if (visited.contains(id)) {
            levelMap.put(id, 1);
            return 1;
        }

        JSONObject item = itemMap.get(id);
        if (item == null) {
            return 1;
        }

        String parentId = item.has("donViChaId") && !item.isNull("donViChaId") ? item.optString("donViChaId") : null;
        if (parentId == null || parentId.isBlank() || "null".equalsIgnoreCase(parentId) || !itemMap.containsKey(parentId)) {
            levelMap.put(id, 1);
            return 1;
        }

        visited.add(id);
        int parentLevel = computeLevelRecursive(parentId, itemMap, levelMap, visited);
        int nodeLevel = parentLevel + 1;
        levelMap.put(id, nodeLevel);
        return nodeLevel;
    }

    /**
     * Upsert and soft-delete sync based on uploaded JSON string (orgs.json).
     * Calculates 'level' automatically for every node.
     */
    public JSONObject syncOrgsFromJsonString(String jsonContent) {
        JSONObject response = new JSONObject();
        try {
            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                response.put("status", "ERROR");
                response.put("message", "Nội dung JSON rỗng.");
                return response;
            }

            JSONArray dataArray;
            String trimmed = jsonContent.trim();
            if (trimmed.startsWith("[")) {
                dataArray = new JSONArray(trimmed);
            } else {
                JSONObject rootObj = new JSONObject(trimmed);
                if (rootObj.has("data")) {
                    dataArray = rootObj.getJSONArray("data");
                } else {
                    dataArray = new JSONArray();
                    dataArray.put(rootObj);
                }
            }

            // Calculate level for every node in dataset
            Map<String, Integer> levelMap = calculateNodeLevels(dataArray);

            // Fetch all existing IDs in DB
            List<String> existingDbIdList = jdbcTemplate.queryForList("SELECT id FROM orgs", String.class);
            Set<String> existingDbIds = new HashSet<>(existingDbIdList);

            Set<String> newFileIds = new HashSet<>();
            int inserted = 0;
            int updated = 0;
            int softDeleted = 0;

            String updateSql = "UPDATE orgs SET ten = ?, maDonVi = ?, donViChaId = ?, tenVietTat = ?, level = ?, isDeleted = 0, updatedAt = GETDATE() WHERE id = ?";
            String insertSql = "INSERT INTO orgs (id, ten, maDonVi, donViChaId, tenVietTat, level, isDeleted, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, 0, GETDATE(), GETDATE())";

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);
                
                String id = item.has("_id") ? item.optString("_id") : item.optString("id");
                if (id == null || id.isBlank()) continue;

                newFileIds.add(id);

                String ten = item.optString("ten", "");
                String maDonVi = item.has("maDonVi") && !item.isNull("maDonVi") ? item.optString("maDonVi") : null;
                String donViChaId = item.has("donViChaId") && !item.isNull("donViChaId") ? item.optString("donViChaId") : null;
                if (donViChaId != null && (donViChaId.isBlank() || "null".equalsIgnoreCase(donViChaId))) {
                    donViChaId = null;
                }
                String tenVietTat = item.has("tenVietTat") && !item.isNull("tenVietTat") ? item.optString("tenVietTat") : null;
                int nodeLevel = levelMap.getOrDefault(id, 1);

                final String fTen = ten;
                final String fMaDonVi = maDonVi;
                final String fDonViChaId = donViChaId;
                final String fTenVietTat = tenVietTat;
                final int fLevel = nodeLevel;
                final String fId = id;

                if (existingDbIds.contains(id)) {
                    jdbcTemplate.update(updateSql, ps -> {
                        ps.setNString(1, fTen);
                        if (fMaDonVi != null) ps.setNString(2, fMaDonVi); else ps.setNull(2, Types.NVARCHAR);
                        if (fDonViChaId != null) ps.setString(3, fDonViChaId); else ps.setNull(3, Types.VARCHAR);
                        if (fTenVietTat != null) ps.setNString(4, fTenVietTat); else ps.setNull(4, Types.NVARCHAR);
                        ps.setInt(5, fLevel);
                        ps.setString(6, fId);
                    });
                    updated++;
                } else {
                    jdbcTemplate.update(insertSql, ps -> {
                        ps.setString(1, fId);
                        ps.setNString(2, fTen);
                        if (fMaDonVi != null) ps.setNString(3, fMaDonVi); else ps.setNull(3, Types.NVARCHAR);
                        if (fDonViChaId != null) ps.setString(4, fDonViChaId); else ps.setNull(4, Types.VARCHAR);
                        if (fTenVietTat != null) ps.setNString(5, fTenVietTat); else ps.setNull(5, Types.NVARCHAR);
                        ps.setInt(6, fLevel);
                    });
                    inserted++;
                }
            }

            // Soft-delete nodes present in DB but missing from newly uploaded file
            String softDeleteSql = "UPDATE orgs SET isDeleted = 1, updatedAt = GETDATE() WHERE id = ? AND isDeleted = 0";
            for (String dbId : existingDbIds) {
                if (!newFileIds.contains(dbId)) {
                    int rows = jdbcTemplate.update(softDeleteSql, dbId);
                    if (rows > 0) {
                        softDeleted++;
                    }
                }
            }

            log.info("Org Sync Completed. Total in file: {}, Inserted: {}, Updated: {}, Soft-deleted: {}",
                    newFileIds.size(), inserted, updated, softDeleted);

            response.put("status", "SUCCESS");
            response.put("message", "Đồng bộ danh sách đơn vị thành công.");
            response.put("totalInFile", newFileIds.size());
            response.put("inserted", inserted);
            response.put("updated", updated);
            response.put("softDeleted", softDeleted);
            return response;

        } catch (Exception e) {
            log.error("Error synchronizing orgs from JSON", e);
            response.put("status", "ERROR");
            response.put("message", "Lỗi đồng bộ dữ liệu: " + e.getMessage());
            return response;
        }
    }

    /**
     * Set org leader ("Phụ trách đơn vị").
     */
    public JSONObject setOrgLeader(String orgId, String leaderId, String leaderName) {
        JSONObject response = new JSONObject();
        try {
            int updated = jdbcTemplate.update(
                "UPDATE orgs SET leaderId = ?, leaderName = ?, updatedAt = GETDATE() WHERE id = ?",
                ps -> {
                    if (leaderId != null) ps.setString(1, leaderId); else ps.setNull(1, Types.VARCHAR);
                    if (leaderName != null) ps.setNString(2, leaderName); else ps.setNull(2, Types.NVARCHAR);
                    ps.setString(3, orgId);
                }
            );
            if (updated > 0) {
                response.put("status", "SUCCESS");
                response.put("message", "Cập nhật phụ trách đơn vị thành công.");
            } else {
                response.put("status", "ERROR");
                response.put("message", "Không tìm thấy đơn vị.");
            }
        } catch (Exception e) {
            log.error("Error setting org leader", e);
            response.put("status", "ERROR");
            response.put("message", "Lỗi cập nhật: " + e.getMessage());
        }
        return response;
    }

    /**
     * Get list of orgs flat structure with level, leaderId, leaderName columns.
     */
    public List<Map<String, Object>> getOrgsList(boolean includeDeleted, String search) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, ten, maDonVi, donViChaId, tenVietTat, level, leaderId, leaderName, isDeleted, createdAt, updatedAt " +
            "FROM orgs WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (!includeDeleted) {
            sql.append("AND isDeleted = 0 ");
        }

        if (search != null && !search.isBlank()) {
            sql.append("AND (ten LIKE ? OR maDonVi LIKE ? OR tenVietTat LIKE ?) ");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        sql.append("ORDER BY level ASC, ten ASC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Get orgs hierarchical tree structure including level, leaderId, leaderName attributes.
     */
    public JSONObject getOrgsTree(boolean includeDeleted, String search) {
        JSONObject result = new JSONObject();
        List<Map<String, Object>> rows = getOrgsList(includeDeleted, search);

        Map<String, JSONObject> nodesMap = new LinkedHashMap<>();
        JSONArray allNodesArray = new JSONArray();

        // Convert rows to JSONObjects
        for (Map<String, Object> row : rows) {
            JSONObject node = new JSONObject();
            String id = (String) row.get("id");
            node.put("id", id);
            node.put("ten", row.get("ten"));
            node.put("maDonVi", row.get("maDonVi") != null ? row.get("maDonVi") : "");
            node.put("donViChaId", row.get("donViChaId") != null ? row.get("donViChaId") : "");
            node.put("tenVietTat", row.get("tenVietTat") != null ? row.get("tenVietTat") : "");
            node.put("level", row.get("level") != null ? row.get("level") : 1);
            node.put("leaderId", row.get("leaderId") != null ? row.get("leaderId") : "");
            node.put("leaderName", row.get("leaderName") != null ? row.get("leaderName") : "");
            node.put("isDeleted", row.get("isDeleted") != null ? row.get("isDeleted") : 0);
            node.put("children", new JSONArray());

            nodesMap.put(id, node);
            allNodesArray.put(node);
        }

        // If searching, return flat array in tree wrapper for easy display
        if (search != null && !search.isBlank()) {
            result.put("status", "SUCCESS");
            result.put("tree", allNodesArray);
            result.put("totalCount", rows.size());
            result.put("isSearchMode", true);
            return result;
        }

        // Build hierarchy tree
        JSONArray rootNodes = new JSONArray();
        for (JSONObject node : nodesMap.values()) {
            String parentId = node.optString("donViChaId", "");
            if (parentId.isEmpty() || !nodesMap.containsKey(parentId)) {
                rootNodes.put(node);
            } else {
                JSONObject parentNode = nodesMap.get(parentId);
                parentNode.getJSONArray("children").put(node);
            }
        }

        result.put("status", "SUCCESS");
        result.put("tree", rootNodes);
        result.put("totalCount", rows.size());
        result.put("isSearchMode", false);
        return result;
    }
}
