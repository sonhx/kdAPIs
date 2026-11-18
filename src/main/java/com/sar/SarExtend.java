package com.sar;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class SarExtend {

    public final String host = Config.host;

    @Autowired
    private JdbcTemplate jdbcTemplate; // IQA database (personnel table)

    @Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate evidenceJdbcTemplate; // kiemdinh database (TBL_Tudanhgia)

    private Map<String, String> getCreatorNames(Set<String> createdByIds) {
        Map<String, String> resultMap = new HashMap<>();
        if (createdByIds == null || createdByIds.isEmpty()) return resultMap;

        List<String> idList = new ArrayList<>(createdByIds);
        String inSql = String.join(",", Collections.nCopies(idList.size(), "?"));

        try {
            String sql = "SELECT CAST(id AS VARCHAR(100)) as id, fullname FROM personnel WHERE CAST(id AS VARCHAR(100)) IN (" + inSql + ") AND isDeleted = 0";
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, idList.toArray());
            for (Map<String, Object> r : list) {
                if (r.get("id") != null && r.get("fullname") != null) {
                    resultMap.put(r.get("id").toString().trim(), r.get("fullname").toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    public JSONArray listSar(int kd_id, String doituong_kd) {
        JSONArray jsaSars = new JSONArray();
        String sql = "SELECT a.* FROM TBL_Tudanhgia a "
                + " WHERE a.kd_id = ? "
                + " AND a.doituong_kd = ? "
                + " AND (a.IsDeleted IS NULL OR a.IsDeleted = 0) "
                + " ORDER BY a.ID DESC";

        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, kd_id, doituong_kd);

        Set<String> createdByIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object createdByObj = row.get("CreatedBy");
            if (createdByObj != null) {
                String cbStr = createdByObj.toString().trim();
                if (!cbStr.isEmpty()) {
                    createdByIds.add(cbStr);
                }
            }
        }

        Map<String, String> creatorMap = getCreatorNames(createdByIds);

        for (Map<String, Object> row : rows) {
            JSONObject jo = new JSONObject();
            int id = ((Number) row.get("ID")).intValue();
            jo.put("id", id);
            jo.put("ten", row.get("ten") == null ? "" : row.get("ten"));
            jo.put("ghi_chu", row.get("ghi_chu") == null ? "" : row.get("ghi_chu"));
            jo.put("created_time", row.get("CreatedTime") == null ? "" : row.get("CreatedTime").toString());

            Object createdByObj = row.get("CreatedBy");
            String cbStr = createdByObj != null ? createdByObj.toString().trim() : "";
            String creator = creatorMap.getOrDefault(cbStr, "");
            jo.put("created_by", cbStr);
            jo.put("creator", creator);

            String path = row.get("path") == null ? "" : row.get("path").toString();
            jo.put("path", path);

            // Document key for ONLYOFFICE session
            String docKey = row.get("document_key") == null ? "" : row.get("document_key").toString().trim();
            if (docKey.isEmpty()) {
                docKey = "sar_" + id + "_" + System.currentTimeMillis();
                updateDocumentKey(id, docKey);
            }
            jo.put("document_key", docKey);

            int status = row.get("status") == null ? 0 : ((Number) row.get("status")).intValue();
            jo.put("status", status);

            jo.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
            jo.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh").toString());

            jsaSars.put(jo);
        }
        return jsaSars;
    }

    public Map<String, Object> getSarById(int id) {
        String sql = "SELECT * FROM TBL_Tudanhgia WHERE ID = ? AND (IsDeleted IS NULL OR IsDeleted = 0)";
        List<Map<String, Object>> list = evidenceJdbcTemplate.queryForList(sql, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public int updateDocumentKey(int id, String documentKey) {
        String sql = "UPDATE TBL_Tudanhgia SET document_key = ? WHERE ID = ?";
        return evidenceJdbcTemplate.update(sql, documentKey, id);
    }

    public int updateStatus(int id, int status) {
        String sql = "UPDATE TBL_Tudanhgia SET status = ? WHERE ID = ?";
        return evidenceJdbcTemplate.update(sql, status, id);
    }

    public int createSar(String ten, String ghiChu, String fullPath, int createdBy, int kdId, String doituongKd, String documentKey) {
        String sql = "INSERT INTO TBL_Tudanhgia (ten, ghi_chu, path, CreatedTime, CreatedBy, kd_id, doituong_kd, status, document_key, IsDeleted) "
                   + " VALUES (?, ?, ?, GETDATE(), ?, ?, ?, 0, ?, 0)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        evidenceJdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, ten);
            ps.setString(2, ghiChu == null ? "" : ghiChu);
            ps.setString(3, fullPath);
            ps.setInt(4, createdBy);
            ps.setInt(5, kdId);
            ps.setString(6, doituongKd);
            ps.setString(7, documentKey);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.intValue() : 0;
    }

    public int deleteSar(int id) {
        String sql = "UPDATE TBL_Tudanhgia SET IsDeleted = 1 WHERE ID = ?";
        return evidenceJdbcTemplate.update(sql, id);
    }
}

