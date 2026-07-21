package com.tdg;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class TdgExtend {
    public final String host = Config.host;

    @Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public JSONArray listTdg(int kd_id, int kd_scope, int status, String doituong_kd) {
        JSONArray jsaTdgs = new JSONArray();
        String sql = "SELECT a.*, c.Fullname as Creator FROM TBL_Tudanhgia a "
                + " INNER JOIN TBL_USER c on c.ID = a.CreatedBy "
                + " where a.kd_id = ? "
                + " and a.status = ? "
                + " and a.doituong_kd = ? "
                + " and (a.IsDeleted is null or a.IsDeleted=0)";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id, status, doituong_kd);
        for (Map<String, Object> row : rows) {
            JSONObject jo = new JSONObject();
            jo.put("id", row.get("ID"));
            jo.put("ten", row.get("ten"));
            jo.put("ghi_chu", row.get("ghi_chu"));
            jo.put("created_time", row.get("CreatedTime"));
            jo.put("creator", row.get("Creator"));
            jo.put("path", row.get("path") == null ? "" : host + row.get("path"));
            jo.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
            jo.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh"));
            jsaTdgs.put(jo);
        }
        return jsaTdgs;
    }

    public boolean isTdgExisted(String ten_tdg) {
        String sql = "select count(*) from TBL_Tudanhgia where ten = ? and (IsDeleted is null or IsDeleted=0)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ten_tdg);
        return count != null && count > 0;
    }

    public int updateTdg(int tdg_id, String so_vb, String ngay_bh, String ghi_chu, int created_by) {
        String sql = "update TBL_Tudanhgia set so_vb = ?, "
                + " ngay_bh = " + (ngay_bh == null ? "NULL" : "CONVERT(DATETIME, ?, 102)") + ", "
                + " ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
        
        if (ngay_bh == null) {
            return jdbcTemplate.update(sql, so_vb, ghi_chu, created_by, tdg_id);
        } else {
            return jdbcTemplate.update(sql, so_vb, ngay_bh, ghi_chu, created_by, tdg_id);
        }
    }

    public int deleteTdg(int id) {
        return jdbcTemplate.update("update TBL_Tudanhgia set IsDeleted = 1 where ID = ?", id);
    }
}
