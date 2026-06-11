package com.ct;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class CtExtend {
    public final String host = Config.host;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public int createNganhDT(String ten, String abbr, int iUserID) {
        String sql = "INSERT into TBL_Nganh_daotao (ten, abbr, CreatedBy, CreatedTime, IsDeleted) VALUES (?, ?, ?, GETDATE(), 0)";
        return jdbcTemplate.update(sql, ten, abbr, iUserID);
    }

    public JSONArray listNganhDT() {
        JSONArray jsaCTKds = new JSONArray();
        String sql = "SELECT a.*, b.Fullname as Creator FROM TBL_Nganh_daotao a "
                + " inner join TBL_USER b on b.ID = a.CreatedBy "
                + " where (a.IsDeleted is null or a.IsDeleted=0)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        for (Map<String, Object> row : rows) {
            JSONObject jo = new JSONObject();
            jo.put("id", row.get("ID"));
            jo.put("ten", row.get("ten"));
            jo.put("abbr", row.get("abbr"));
            jo.put("creator", row.get("Creator"));
            jo.put("created_time", row.get("CreatedTime").toString());
            jsaCTKds.put(jo);
        }
        return jsaCTKds;
    }

    public boolean isCtExisted(String ten) {
        String sql = "select count(*) from TBL_Nganh_daotao where ten = ? and (IsDeleted is null or IsDeleted=0)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ten);
        return count != null && count > 0;
    }

    public int updateCt(int id, String ten, String abbr, int created_by) {
        String sql = "update TBL_Nganh_daotao set ten = ?, abbr = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
        return jdbcTemplate.update(sql, ten, abbr, created_by, id);
    }

    public int deleteCt(int id) {
        return jdbcTemplate.update("update TBL_Nganh_daotao set IsDeleted = 1 where ID = ?", id);
    }
}
