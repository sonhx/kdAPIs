package com.hanhdv.khcn;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class SinhVienDeTaiDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insertSinhVien(String tenSinhVien, String maLop, int deTaiId) {
        jdbcTemplate.update("Insert into TBL_SINHVIENDETAI_THANHVIEN(ten_sinh_vien, ma_lop, de_tai_id) values(?,?,?)",
                tenSinhVien, maLop, deTaiId);
    }

    public int getIDFromInsertDeTaiSinhVien(String maDeTai, String tenDeTai, String khoaChuTri, String nguoiHuongDan, int xepLoaiId, int namThucHien) {
        String insertSQL = "Insert into TBL_SINHVIEN_DETAI(ten_de_tai,ma_de_tai,khoa_chu_tri, nguoi_huong_dan, xep_loai_id,createdAt,nam_thuc_hien) values(?,?,?,?,?,getdate(),?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tenDeTai);
            ps.setString(2, maDeTai);
            ps.setString(3, khoaChuTri);
            ps.setString(4, nguoiHuongDan);
            ps.setInt(5, xepLoaiId);
            ps.setInt(6, namThucHien);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public String getListDeTaiSinhVien() {
        try {
            String sql = "select a.*, b.loai_danh_gia from [dbo].[TBL_SINHVIEN_DETAI] a, DEF_DANH_GIA b where a.xep_loai_id= b.id";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                int deTaiId = (int) row.get("id");
                jo.put("id", deTaiId);
                jo.put("ma_de_tai", row.get("ma_de_tai"));
                jo.put("ten_de_tai", row.get("ten_de_tai"));
                jo.put("khoa_chu_tri", row.get("khoa_chu_tri"));
                jo.put("nguoi_huong_dan", row.get("nguoi_huong_dan"));
                jo.put("loai_danh_gia", row.get("loai_danh_gia"));
                jo.put("xep_loai_id", row.get("xep_loai_id"));
                jo.put("nam_thuc_hien", row.get("nam_thuc_hien"));

                List<Map<String, Object>> students = jdbcTemplate.queryForList("Select * from [TBL_SINHVIENDETAI_THANHVIEN] where de_tai_id=?", deTaiId);
                JSONArray ja1 = new JSONArray();
                for (Map<String, Object> s : students) {
                    JSONObject jo1 = new JSONObject();
                    jo1.put("id", s.get("id"));
                    jo1.put("ten_sinh_vien", s.get("ten_sinh_vien"));
                    jo1.put("ma_lop", s.get("ma_lop"));
                    ja1.put(jo1);
                }
                jo.put("lst_sinh_vien", ja1);
                ja.put(jo);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String createNewDeTaiSinhVien(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int deTaiId = getIDFromInsertDeTaiSinhVien(jo.getString("ma_de_tai"), jo.getString("ten_de_tai"), jo.getString("khoa_chu_tri"), jo.getString("nguoi_huong_dan"), jo.getJSONObject("xep_loai").getInt("id"), jo.getInt("nam_thuc_hien"));

            JSONArray jaSinhVien = jo.getJSONArray("lst_sinh_vien");
            for (int i = 0; i < jaSinhVien.length(); i++) {
                JSONObject sv = jaSinhVien.getJSONObject(i);
                insertSinhVien(sv.getString("ten_sinh_vien"), sv.getString("ma_lop"), deTaiId);
            }
            return "{\"code\":200, \"description\":\"Thêm mới thành công\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteDeTaiSinhVien(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int id = jo.getInt("id");
            jdbcTemplate.update("Delete from [TBL_SINHVIEN_DETAI] where id=?", id);
            jdbcTemplate.update("Delete from [TBL_SINHVIENDETAI_THANHVIEN] where de_tai_id=?", id);
            return "{\"code\":200, \"description\":\"Xóa thành công\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
