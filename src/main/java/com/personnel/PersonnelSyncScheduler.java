package com.personnel;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.sql.Types;
import java.util.*;

@Service
public class PersonnelSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PersonnelSyncScheduler.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${slink.api-key:4sUgLOsrtFOkjQ84ar9kvVwjByhAiQPs}")
    private String slinkApiKey;

	/*@PostConstruct
	public void init() {
	    try {
	        log.info("Initializing 'personnel' database table...");
	        String createTableSql = 
	            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'personnel') " +
	            "BEGIN " +
	            "    CREATE TABLE personnel ( " +
	            "        id VARCHAR(100) PRIMARY KEY, " +
	            "        maCanBo NVARCHAR(200) NULL, " +
	            "        emailCanBo NVARCHAR(500) NULL, " +
	            "        hoDem NVARCHAR(500) NULL, " +
	            "        ten NVARCHAR(500) NULL, " +
	            "        fullname NVARCHAR(1000) NULL, " +
	            "        trangThai NVARCHAR(200) NULL, " +
	            "        hocHam NVARCHAR(200) NULL, " +
	            "        trinhDoDaoTao NVARCHAR(200) NULL, " +
	            "        email NVARCHAR(500) NULL, " +
	            "        sdtCaNhan NVARCHAR(500) NULL, " +
	            "        donViChinhId VARCHAR(100) NULL, " +
	            "        donViL3Id VARCHAR(100) NULL, " +
	            "        donViViTri NVARCHAR(MAX) NULL, " +
	            "        tenChucVu NVARCHAR(1000) NULL, " +
	            "        isDeleted INT NOT NULL DEFAULT 0, " +
	            "        createdAt DATETIME2 DEFAULT GETDATE(), " +
	            "        updatedAt DATETIME2 DEFAULT GETDATE() " +
	            "    ); " +
	            "    CREATE INDEX IX_personnel_maCanBo ON personnel(maCanBo); " +
	            "    CREATE INDEX IX_personnel_emailCanBo ON personnel(emailCanBo); " +
	            "    CREATE INDEX IX_personnel_donViChinhId ON personnel(donViChinhId); " +
	            "    CREATE INDEX IX_personnel_donViL3Id ON personnel(donViL3Id); " +
	            "END";
	        jdbcTemplate.execute(createTableSql);
	
	        // Migration to add 'donViL3Id' column if missing
	        try {
	            String addL3Col = 
	                "IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('personnel') AND name = 'donViL3Id') " +
	                "BEGIN " +
	                "    ALTER TABLE personnel ADD donViL3Id VARCHAR(100) NULL; " +
	                "    CREATE INDEX IX_personnel_donViL3Id ON personnel(donViL3Id); " +
	                "END";
	            jdbcTemplate.execute(addL3Col);
	
	            jdbcTemplate.execute("ALTER TABLE personnel ALTER COLUMN sdtCaNhan NVARCHAR(500) NULL");
	            jdbcTemplate.execute("ALTER TABLE personnel ALTER COLUMN fullname NVARCHAR(1000) NULL");
	            jdbcTemplate.execute("ALTER TABLE personnel ALTER COLUMN emailCanBo NVARCHAR(500) NULL");
	            jdbcTemplate.execute("ALTER TABLE personnel ALTER COLUMN email NVARCHAR(500) NULL");
	            jdbcTemplate.execute("ALTER TABLE personnel ALTER COLUMN tenChucVu NVARCHAR(1000) NULL");
	        } catch (Exception ex) {
	            log.debug("Column alter notice: {}", ex.getMessage());
	        }
	
	        log.info("'personnel' table initialized successfully.");
	
	        // Startup personnel sync disabled per request
	        // java.util.concurrent.CompletableFuture.runAsync(() -> {
	        //     try {
	        //         Thread.sleep(3000);
	        //         log.info("Triggering initial personnel sync on startup...");
	        //         syncPersonnel();
	        //     } catch (Exception e) {
	        //         log.error("Error running initial personnel sync on startup", e);
	        //     }
	        // });
	
	    } catch (Exception e) {
	        log.error("Failed to initialize 'personnel' database table", e);
	    }
	}
	*/
    /**
     * Compute Level 3 Organization ID by tracing up parent hierarchy in 'orgs' table.
     */
    private static final String VP_HOC_VIEN_ID = "66a308ce8068e53428da2035"; // Văn phòng Học viện (level 2, treated as level 3)
    private static final String LD_HOC_VIEN_ID = "66a308ce8068e53428da202c"; // Lãnh đạo Học viện - Bắc (level 2, treated as level 3)
    private static final String LD_HOC_VIEN_NAM_ID = "66a308ce8068e53428da202d"; // Lãnh đạo Học viện - Nam (level 2, treated as level 3)

    private String computeDonViL3Id(String donViChinhId, Map<String, Map<String, Object>> orgMap) {
        if (donViChinhId == null || donViChinhId.isBlank()) {
            return null;
        }
        String currId = donViChinhId.trim();
        String l3Id = null;
        Set<String> visited = new HashSet<>();

        while (currId != null && !currId.isBlank() && orgMap.containsKey(currId) && !visited.contains(currId)) {
            visited.add(currId);
            Map<String, Object> orgNode = orgMap.get(currId);
            int level = orgNode.get("level") != null ? ((Number) orgNode.get("level")).intValue() : 1;
            if (level == 3 || currId.equals(VP_HOC_VIEN_ID) || currId.equals(LD_HOC_VIEN_ID) || currId.equals(LD_HOC_VIEN_NAM_ID)) {
                l3Id = currId;
            }
            currId = (String) orgNode.get("donViChaId");
        }

        if (l3Id == null && orgMap.containsKey(donViChinhId)) {
            l3Id = donViChinhId;
        }

        return l3Id;
    }

    /**
     * Scheduled Monthly Sync (At 00:00 on 1st of every month).
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void scheduledMonthlySync() {
        log.info("Executing scheduled monthly personnel synchronization...");
        syncPersonnel();
    }

    /**
     * Synchronize personnel from TCNS API: https://gw.aisoftech.vn/ptit/tcns/internal/tcns/sap-xep-nhan-su
     */
    public JSONObject syncPersonnel() {
        JSONObject result = new JSONObject();
        try {
            log.info("Starting personnel sync from TCNS API...");
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(15000);
            requestFactory.setReadTimeout(30000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            String url = "https://gw.aisoftech.vn/ptit/tcns/internal/tcns/sap-xep-nhan-su";
            String apiKey = slinkApiKey != null ? slinkApiKey.replace("\"", "").trim() : "4sUgLOsrtFOkjQ84ar9kvVwjByhAiQPs";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                result.put("status", "ERROR");
                result.put("message", "API response error: " + response.getStatusCode());
                return result;
            }

            JSONObject jsonResponse = new JSONObject(response.getBody());
            JSONArray dataArray = jsonResponse.optJSONArray("data");

            if (dataArray == null) {
                log.warn("No 'data' array found in TCNS API response.");
                result.put("status", "ERROR");
                result.put("message", "API response missing data array");
                return result;
            }

            log.info("Fetched {} personnel records from TCNS API.", dataArray.length());

            // Build org map for calculating donViL3Id
            List<Map<String, Object>> orgRows = jdbcTemplate.queryForList("SELECT id, donViChaId, level FROM orgs");
            Map<String, Map<String, Object>> orgMap = new HashMap<>();
            for (Map<String, Object> r : orgRows) {
                orgMap.put((String) r.get("id"), r);
            }

            // Fetch existing IDs from personnel table
            List<String> existingDbIdList = jdbcTemplate.queryForList("SELECT id FROM personnel", String.class);
            Set<String> existingDbIds = new HashSet<>(existingDbIdList);

            Set<String> newApiIds = new HashSet<>();
            int inserted = 0;
            int updated = 0;
            int softDeleted = 0;

            String updateSql = 
                "UPDATE personnel SET maCanBo = ?, emailCanBo = ?, hoDem = ?, ten = ?, fullname = ?, " +
                "trangThai = ?, hocHam = ?, trinhDoDaoTao = ?, email = ?, sdtCaNhan = ?, donViChinhId = ?, " +
                "donViL3Id = ?, donViViTri = ?, tenChucVu = ?, isDeleted = 0, updatedAt = GETDATE() WHERE id = ?";

            String insertSql = 
                "INSERT INTO personnel (id, maCanBo, emailCanBo, hoDem, ten, fullname, trangThai, hocHam, " +
                "trinhDoDaoTao, email, sdtCaNhan, donViChinhId, donViL3Id, donViViTri, tenChucVu, isDeleted, createdAt, updatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, GETDATE(), GETDATE())";

            for (int i = 0; i < dataArray.length(); i++) {
                JSONObject item = dataArray.getJSONObject(i);

                String id = item.has("_id") ? item.optString("_id") : item.optString("id");
                if (id == null || id.isBlank()) continue;

                newApiIds.add(id);

                String maCanBo = item.has("maCanBo") && !item.isNull("maCanBo") ? item.optString("maCanBo") : null;
                String emailCanBo = item.has("emailCanBo") && !item.isNull("emailCanBo") ? item.optString("emailCanBo") : null;
                String hoDem = item.has("hoDem") && !item.isNull("hoDem") ? item.optString("hoDem") : null;
                String ten = item.has("ten") && !item.isNull("ten") ? item.optString("ten") : null;

                // Combine hoDem + " " + ten for fullname
                String fullname = null;
                String hStr = hoDem != null ? hoDem.trim() : "";
                String tStr = ten != null ? ten.trim() : "";
                String combined = (hStr + " " + tStr).trim();
                if (!combined.isEmpty()) {
                    fullname = combined;
                }

                String trangThai = item.has("trangThai") && !item.isNull("trangThai") ? item.optString("trangThai") : null;
                String hocHam = item.has("hocHam") && !item.isNull("hocHam") ? item.optString("hocHam") : null;
                String trinhDoDaoTao = item.has("trinhDoDaoTao") && !item.isNull("trinhDoDaoTao") ? item.optString("trinhDoDaoTao") : null;
                String email = item.has("email") && !item.isNull("email") ? item.optString("email") : null;
                String sdtCaNhan = item.has("sdtCaNhan") && !item.isNull("sdtCaNhan") ? item.optString("sdtCaNhan") : null;
                String donViChinhId = item.has("donViChinhId") && !item.isNull("donViChinhId") ? item.optString("donViChinhId") : null;

                // Calculate level 3 org ID
                String donViL3Id = computeDonViL3Id(donViChinhId, orgMap);

                // donViViTri as JSON string representation
                String donViViTri = null;
                if (item.has("donViViTri") && !item.isNull("donViViTri")) {
                    donViViTri = item.get("donViViTri").toString();
                }

                // tenChucVu extraction
                String tenChucVu = null;
                if (item.has("tenChucVu") && !item.isNull("tenChucVu")) {
                    tenChucVu = item.optString("tenChucVu");
                } else if (item.has("donViViTri") && !item.isNull("donViViTri") && item.optJSONObject("donViViTri") != null) {
                    tenChucVu = item.optJSONObject("donViViTri").optString("tenChucVu", null);
                }

                final String fId = id;
                final String fMaCanBo = maCanBo;
                final String fEmailCanBo = emailCanBo;
                final String fHoDem = hoDem;
                final String fTen = ten;
                final String fFullname = fullname;
                final String fTrangThai = trangThai;
                final String fHocHam = hocHam;
                final String fTrinhDoDaoTao = trinhDoDaoTao;
                final String fEmail = email;
                final String fSdtCaNhan = sdtCaNhan;
                final String fDonViChinhId = donViChinhId;
                final String fDonViL3Id = donViL3Id;
                final String fDonViViTri = donViViTri;
                final String fTenChucVu = tenChucVu;

                if (existingDbIds.contains(id)) {
                    jdbcTemplate.update(updateSql, ps -> {
                        if (fMaCanBo != null) ps.setNString(1, fMaCanBo); else ps.setNull(1, Types.NVARCHAR);
                        if (fEmailCanBo != null) ps.setNString(2, fEmailCanBo); else ps.setNull(2, Types.NVARCHAR);
                        if (fHoDem != null) ps.setNString(3, fHoDem); else ps.setNull(3, Types.NVARCHAR);
                        if (fTen != null) ps.setNString(4, fTen); else ps.setNull(4, Types.NVARCHAR);
                        if (fFullname != null) ps.setNString(5, fFullname); else ps.setNull(5, Types.NVARCHAR);
                        if (fTrangThai != null) ps.setNString(6, fTrangThai); else ps.setNull(6, Types.NVARCHAR);
                        if (fHocHam != null) ps.setNString(7, fHocHam); else ps.setNull(7, Types.NVARCHAR);
                        if (fTrinhDoDaoTao != null) ps.setNString(8, fTrinhDoDaoTao); else ps.setNull(8, Types.NVARCHAR);
                        if (fEmail != null) ps.setNString(9, fEmail); else ps.setNull(9, Types.NVARCHAR);
                        if (fSdtCaNhan != null) ps.setNString(10, fSdtCaNhan); else ps.setNull(10, Types.NVARCHAR);
                        if (fDonViChinhId != null) ps.setString(11, fDonViChinhId); else ps.setNull(11, Types.VARCHAR);
                        if (fDonViL3Id != null) ps.setString(12, fDonViL3Id); else ps.setNull(12, Types.VARCHAR);
                        if (fDonViViTri != null) ps.setNString(13, fDonViViTri); else ps.setNull(13, Types.NVARCHAR);
                        if (fTenChucVu != null) ps.setNString(14, fTenChucVu); else ps.setNull(14, Types.NVARCHAR);
                        ps.setString(15, fId);
                    });
                    updated++;
                } else {
                    jdbcTemplate.update(insertSql, ps -> {
                        ps.setString(1, fId);
                        if (fMaCanBo != null) ps.setNString(2, fMaCanBo); else ps.setNull(2, Types.NVARCHAR);
                        if (fEmailCanBo != null) ps.setNString(3, fEmailCanBo); else ps.setNull(3, Types.NVARCHAR);
                        if (fHoDem != null) ps.setNString(4, fHoDem); else ps.setNull(4, Types.NVARCHAR);
                        if (fTen != null) ps.setNString(5, fTen); else ps.setNull(5, Types.NVARCHAR);
                        if (fFullname != null) ps.setNString(6, fFullname); else ps.setNull(6, Types.NVARCHAR);
                        if (fTrangThai != null) ps.setNString(7, fTrangThai); else ps.setNull(7, Types.NVARCHAR);
                        if (fHocHam != null) ps.setNString(8, fHocHam); else ps.setNull(8, Types.NVARCHAR);
                        if (fTrinhDoDaoTao != null) ps.setNString(9, fTrinhDoDaoTao); else ps.setNull(9, Types.NVARCHAR);
                        if (fEmail != null) ps.setNString(10, fEmail); else ps.setNull(10, Types.NVARCHAR);
                        if (fSdtCaNhan != null) ps.setNString(11, fSdtCaNhan); else ps.setNull(11, Types.NVARCHAR);
                        if (fDonViChinhId != null) ps.setString(12, fDonViChinhId); else ps.setNull(12, Types.VARCHAR);
                        if (fDonViL3Id != null) ps.setString(13, fDonViL3Id); else ps.setNull(13, Types.VARCHAR);
                        if (fDonViViTri != null) ps.setNString(14, fDonViViTri); else ps.setNull(14, Types.NVARCHAR);
                        if (fTenChucVu != null) ps.setNString(15, fTenChucVu); else ps.setNull(15, Types.NVARCHAR);
                    });
                    inserted++;
                }
            }

            // Soft-delete missing records
            String softDeleteSql = "UPDATE personnel SET isDeleted = 1, updatedAt = GETDATE() WHERE id = ? AND isDeleted = 0";
            for (String dbId : existingDbIds) {
                if (!newApiIds.contains(dbId)) {
                    int rows = jdbcTemplate.update(softDeleteSql, dbId);
                    if (rows > 0) {
                        softDeleted++;
                    }
                }
            }

            log.info("Personnel Sync Completed. Total API records: {}, Inserted: {}, Updated: {}, Soft-deleted: {}",
                    newApiIds.size(), inserted, updated, softDeleted);

            // Sync users ID using personnel data matching by email
            try {
                int syncedIds = jdbcTemplate.update(
                    "UPDATE u SET u.ID = p.id " +
                    "FROM users u " +
                    "JOIN personnel p ON (p.emailCanBo = u.Email OR p.email = u.Email) " +
                    "WHERE p.id IS NOT NULL AND p.id <> '' AND u.ID <> p.id " +
                    "  AND (u.IsDeleted IS NULL OR u.IsDeleted = '0') AND p.isDeleted = 0"
                );
                if (syncedIds > 0) {
                    log.info("Synced {} user IDs in users using personnel table.", syncedIds);
                }
            } catch (Exception ex) {
                log.error("Error syncing user IDs in users", ex);
            }

            result.put("status", "SUCCESS");
            result.put("message", "Đồng bộ nhân sự (personnel) từ TCNS thành công.");
            result.put("totalApiRecords", newApiIds.size());
            result.put("inserted", inserted);
            result.put("updated", updated);
            result.put("softDeleted", softDeleted);
            return result;

        } catch (Exception e) {
            log.error("Error during personnel synchronization", e);
            result.put("status", "ERROR");
            result.put("message", "Lỗi đồng bộ nhân sự: " + e.getMessage());
            return result;
        }
    }

    /**
     * Fetch personnel list with search and pagination/filter support.
     */
    public List<Map<String, Object>> getPersonnelList(boolean includeDeleted, String search, String donViChinhId, String donViL3Id) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, maCanBo, emailCanBo, hoDem, ten, fullname, trangThai, hocHam, trinhDoDaoTao, " +
            "email, sdtCaNhan, donViChinhId, donViL3Id, donViViTri, tenChucVu, isDeleted, createdAt, updatedAt " +
            "FROM personnel WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (!includeDeleted) {
            sql.append("AND isDeleted = 0 ");
        }

        if (donViChinhId != null && !donViChinhId.isBlank()) {
            sql.append("AND donViChinhId = ? ");
            params.add(donViChinhId.trim());
        }

        if (donViL3Id != null && !donViL3Id.isBlank()) {
            sql.append("AND donViL3Id = ? ");
            params.add(donViL3Id.trim());
        }

        if (search != null && !search.isBlank()) {
            sql.append("AND (fullname LIKE ? OR maCanBo LIKE ? OR emailCanBo LIKE ? OR tenChucVu LIKE ?) ");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        sql.append("ORDER BY fullname ASC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Return total number of lecturers in personnel table.
     * Lecturer is defined as a person with tenChucVu LIKE '%Giảng viên%' and trangThai = 'Đang làm việc'.
     */
    public int getLecturerCount(boolean includeDeleted, String donViChinhId, String donViL3Id) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM personnel WHERE tenChucVu LIKE N'%Giảng viên%' AND trangThai = N'Đang làm việc' "
        );
        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            sql.append("AND (isDeleted = 0 OR isDeleted IS NULL) ");
        }

        if (donViChinhId != null && !donViChinhId.isBlank()) {
            sql.append("AND donViChinhId = ? ");
            params.add(donViChinhId.trim());
        }

        if (donViL3Id != null && !donViL3Id.isBlank()) {
            sql.append("AND donViL3Id = ? ");
            params.add(donViL3Id.trim());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public int getLecturerCount(boolean includeDeleted) {
        return getLecturerCount(includeDeleted, null, null);
    }

    public int getLecturerCount() {
        return getLecturerCount(false, null, null);
    }

    /**
     * Fetch list of lecturers in personnel table.
     * Lecturer is defined as a person with tenChucVu LIKE '%Giảng viên%' and trangThai = 'Đang làm việc'.
     */
    public List<Map<String, Object>> getLecturerList(boolean includeDeleted, String search, String donViChinhId, String donViL3Id) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, maCanBo, emailCanBo, hoDem, ten, fullname, trangThai, hocHam, trinhDoDaoTao, " +
            "email, sdtCaNhan, donViChinhId, donViL3Id, donViViTri, tenChucVu, isDeleted, createdAt, updatedAt " +
            "FROM personnel WHERE tenChucVu LIKE N'%Giảng viên%' AND trangThai = N'Đang làm việc' "
        );
        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            sql.append("AND (isDeleted = 0 OR isDeleted IS NULL) ");
        }

        if (donViChinhId != null && !donViChinhId.isBlank()) {
            sql.append("AND donViChinhId = ? ");
            params.add(donViChinhId.trim());
        }

        if (donViL3Id != null && !donViL3Id.isBlank()) {
            sql.append("AND donViL3Id = ? ");
            params.add(donViL3Id.trim());
        }

        if (search != null && !search.isBlank()) {
            sql.append("AND (fullname LIKE ? OR maCanBo LIKE ? OR emailCanBo LIKE ? OR tenChucVu LIKE ?) ");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        sql.append("ORDER BY fullname ASC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getLecturerList(boolean includeDeleted) {
        return getLecturerList(includeDeleted, null, null, null);
    }

    public List<Map<String, Object>> getLecturerList() {
        return getLecturerList(false, null, null, null);
    }

    /**
     * Return total number of PhD lecturers in personnel table.
     * PhD lecturer is a lecturer whose trinhDoDaoTao or hocHam contains 'Tiến sĩ', 'Tiến sỹ', 'Ph.D', 'PhD', or 'TS'.
     */
    public int getPhdLecturerCount(boolean includeDeleted, String donViChinhId, String donViL3Id) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM personnel WHERE tenChucVu LIKE N'%Giảng viên%' AND trangThai = N'Đang làm việc' " +
            "AND (trinhDoDaoTao LIKE N'%Tiến sĩ%' OR trinhDoDaoTao LIKE N'%Tiến sỹ%' OR trinhDoDaoTao LIKE N'%Ph.D%' OR trinhDoDaoTao LIKE N'%PhD%' OR trinhDoDaoTao LIKE N'%TS%' OR hocHam LIKE N'%Tiến sĩ%' OR hocHam LIKE N'%Tiến sỹ%') "
        );
        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            sql.append("AND (isDeleted = 0 OR isDeleted IS NULL) ");
        }

        if (donViChinhId != null && !donViChinhId.isBlank()) {
            sql.append("AND donViChinhId = ? ");
            params.add(donViChinhId.trim());
        }

        if (donViL3Id != null && !donViL3Id.isBlank()) {
            sql.append("AND donViL3Id = ? ");
            params.add(donViL3Id.trim());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public int getPhdLecturerCount(boolean includeDeleted) {
        return getPhdLecturerCount(includeDeleted, null, null);
    }

    public int getPhdLecturerCount() {
        return getPhdLecturerCount(false, null, null);
    }

    /**
     * Fetch list of PhD lecturers in personnel table.
     */
    public List<Map<String, Object>> getPhdLecturerList(boolean includeDeleted, String search, String donViChinhId, String donViL3Id) {
        StringBuilder sql = new StringBuilder(
            "SELECT id, maCanBo, emailCanBo, hoDem, ten, fullname, trangThai, hocHam, trinhDoDaoTao, " +
            "email, sdtCaNhan, donViChinhId, donViL3Id, donViViTri, tenChucVu, isDeleted, createdAt, updatedAt " +
            "FROM personnel WHERE tenChucVu LIKE N'%Giảng viên%' AND trangThai = N'Đang làm việc' " +
            "AND (trinhDoDaoTao LIKE N'%Tiến sĩ%' OR trinhDoDaoTao LIKE N'%Tiến sỹ%' OR trinhDoDaoTao LIKE N'%Ph.D%' OR trinhDoDaoTao LIKE N'%PhD%' OR trinhDoDaoTao LIKE N'%TS%' OR hocHam LIKE N'%Tiến sĩ%' OR hocHam LIKE N'%Tiến sỹ%') "
        );
        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            sql.append("AND (isDeleted = 0 OR isDeleted IS NULL) ");
        }

        if (donViChinhId != null && !donViChinhId.isBlank()) {
            sql.append("AND donViChinhId = ? ");
            params.add(donViChinhId.trim());
        }

        if (donViL3Id != null && !donViL3Id.isBlank()) {
            sql.append("AND donViL3Id = ? ");
            params.add(donViL3Id.trim());
        }

        if (search != null && !search.isBlank()) {
            sql.append("AND (fullname LIKE ? OR maCanBo LIKE ? OR emailCanBo LIKE ? OR tenChucVu LIKE ?) ");
            String term = "%" + search.trim() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        sql.append("ORDER BY fullname ASC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getPhdLecturerList(boolean includeDeleted) {
        return getPhdLecturerList(includeDeleted, null, null, null);
    }

    public List<Map<String, Object>> getPhdLecturerList() {
        return getPhdLecturerList(false, null, null, null);
    }
}
