package com.system;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void addNationCodes(JSONArray jarr) {
        for (int i = 0; i < jarr.length(); i++) {
            JSONObject obj = jarr.getJSONObject(i);
            if (!obj.has("ten_viet_tat") || !obj.has("ten_quoc_gia")) continue;
            
            String code = obj.getString("ten_viet_tat");
            String name = obj.getString("ten_quoc_gia");
            int stt = obj.has("stt") ? obj.getInt("stt") : 0;

            String checkSql = "select count(*) from dbo.DEF_QUOCTICH where Code=?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, code);
            if (count != null && count > 0) continue;

            String sql = "insert into DEF_QUOCTICH (Code, Value, Name) values (?, ?, ?)";
            jdbcTemplate.update(sql, code, stt, name);
        }
    }
}
