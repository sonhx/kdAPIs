package com.tckt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TCKTExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String importTaiChinh(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            JSONArray ja = jo.getJSONArray("data");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject json = ja.getJSONObject(i);
                insertChiTieuTaiChinh(json.getInt("nam_hoc"), json.getString("hoat_dong"), json.getDouble("gia_tri"));
            }
            return "{\"code\":200, \"description\":\"Thêm mới thành công\"}";
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
    }

    public void insertChiTieuTaiChinh(int nam_hoc, String hoat_dong, double gia_tri) {
        jdbcTemplate.update("Delete from tbl_hoat_dong_tai_chinh where nam_hoc=? and hoat_dong=?", nam_hoc, hoat_dong);
        jdbcTemplate.update("Insert into tbl_hoat_dong_tai_chinh(nam_hoc, hoat_dong, gia_tri) values(?, ?, ?)", nam_hoc, hoat_dong, gia_tri);
    }
}
