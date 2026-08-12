package com.ct;

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
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class CtExtend {
    public final String host = Config.host;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate evidenceJdbcTemplate;

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

    public int createNganhDT(String ten, String abbr, int iUserID) {
        String sql = "INSERT into TBL_Nganh_daotao (ten, abbr, CreatedBy, CreatedTime, IsDeleted) VALUES (?, ?, ?, GETDATE(), 0)";
        return evidenceJdbcTemplate.update(sql, ten, abbr, iUserID);
    }

    public JSONArray listNganhDT() {
        return listNganhDT(-1);
    }

    public JSONArray listNganhDT(int loaiHinhId) {
        JSONArray jsaCTKds = new JSONArray();
        String sql = "SELECT a.* FROM TBL_Nganh_daotao a "
                + " where (a.IsDeleted is null or a.IsDeleted=0)";
        List<Object> params = new ArrayList<>();
        if (loaiHinhId != -1) {
            if (loaiHinhId == 0) {
                sql += " and (a.ID in (SELECT DISTINCT ct.CT_ID FROM TBL_KIEMDINH_CT ct "
                    + " INNER JOIN TBL_KIEMDINH kd ON kd.ID = ct.KD_ID "
                    + " WHERE (ct.IsDeleted is null or ct.IsDeleted = 0) "
                    + " AND (kd.IsDeleted is null or kd.IsDeleted = 0) "
                    + " AND (kd.loai_hinh_id = 0 or kd.loai_hinh_id is null))"
                    + " or a.ID not in (SELECT DISTINCT ct.CT_ID FROM TBL_KIEMDINH_CT ct "
                    + " WHERE (ct.IsDeleted is null or ct.IsDeleted = 0)))";
            } else {
                sql += " and a.ID in (SELECT DISTINCT ct.CT_ID FROM TBL_KIEMDINH_CT ct "
                    + " INNER JOIN TBL_KIEMDINH kd ON kd.ID = ct.KD_ID "
                    + " WHERE (ct.IsDeleted is null or ct.IsDeleted = 0) "
                    + " AND (kd.IsDeleted is null or kd.IsDeleted = 0) "
                    + " AND kd.loai_hinh_id = ?)";
                params.add(loaiHinhId);
            }
        }

        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, params.toArray());

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            JSONObject jo = new JSONObject();
            jo.put("id", row.get("ID"));
            jo.put("ten", row.get("ten"));
            jo.put("abbr", row.get("abbr"));
            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            jo.put("creator", creatorNames.getOrDefault(cbStr, ""));
            jo.put("created_time", row.get("CreatedTime") != null ? row.get("CreatedTime").toString() : "");
            jsaCTKds.put(jo);
        }
        return jsaCTKds;
    }

    public boolean isCtExisted(String ten) {
        String sql = "select count(*) from TBL_Nganh_daotao where ten = ? and (IsDeleted is null or IsDeleted=0)";
        Integer count = evidenceJdbcTemplate.queryForObject(sql, Integer.class, ten);
        return count != null && count > 0;
    }

    public int updateCt(int id, String ten, String abbr, int created_by) {
        String sql = "update TBL_Nganh_daotao set ten = ?, abbr = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
        return evidenceJdbcTemplate.update(sql, ten, abbr, created_by, id);
    }

    public int deleteCt(int id) {
        return evidenceJdbcTemplate.update("update TBL_Nganh_daotao set IsDeleted = 1 where ID = ?", id);
    }
}
