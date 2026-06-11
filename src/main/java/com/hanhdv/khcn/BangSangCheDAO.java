package com.hanhdv.khcn;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BangSangCheDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String createBangSangChe(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            String sql = "Insert into tbl_bang_sang_che(ten_sang_che,noi_cap,ngay_cap,nguoi_dc_cap,createdAt) values(?,?,?,?,getdate())";
            jdbcTemplate.update(sql, jo.getString("ten_bang_sang_che"), jo.getString("noi_cap"), jo.getString("ngay_cap"), jo.getString("nguoi_duoc_cap"));
            return "{\"code\":200, \"description\":\"OK\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListBangSangChe() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from tbl_bang_sang_che");
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                jo.put("id", row.get("id"));
                jo.put("ten_bang_sang_che", row.get("ten_sang_che"));
                jo.put("noi_cap", row.get("noi_cap"));
                jo.put("ngay_cap", row.get("ngay_cap"));
                jo.put("nguoi_duoc_cap", row.get("nguoi_dc_cap"));
                ja.put(jo);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateBangSangChe(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            String sql = "Update tbl_bang_sang_che set ten_sang_che=?, noi_cap=?, ngay_cap=?, nguoi_dc_cap=? where id=?";
            jdbcTemplate.update(sql, jo.getString("ten_bang_sang_che"), jo.getString("noi_cap"), jo.getString("ngay_cap"), jo.getString("nguoi_duoc_cap"), jo.getInt("id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteBangSangChe(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_bang_sang_che where id=?", jo.getInt("id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
