package com.hanhdv.khcn;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
public class SinhVienDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String createGiaiThuongSV(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            String tenGiaiThuong = jo.getString("ten_giai_thuong");
            int namThucHien = jo.getInt("nam_thuc_hien");
            int loaiGiaiThuong = jo.getJSONObject("loai_giai_thuong").getInt("id");

            int giaiThuongId = getIDFromInsertGiaiThuong(tenGiaiThuong, loaiGiaiThuong, namThucHien);

            JSONArray listSv = jo.getJSONArray("lst_sinh_vien");
            for (int i = 0; i < listSv.length(); i++) {
                JSONObject sv = listSv.getJSONObject(i);
                insertSinhVien(sv.getString("ho_ten"), sv.getString("ma_sinh_vien"), giaiThuongId);
            }
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public int getIDFromInsertGiaiThuong(String tenGiaiThuong, int loaiThanhTich, int namThucHien) {
        String insertSQL = "Insert into TBL_GIAI_THUONG(ten_giai_thuong, loai_thanh_tich, nam_thuc_hien, createdAt) values(?,?,?, getdate())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tenGiaiThuong);
            ps.setInt(2, loaiThanhTich);
            ps.setInt(3, namThucHien);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public void insertSinhVien(String hoTen, String maSv, int giaiThuongId) {
        jdbcTemplate.update("Insert into TBL_GIAITHUONG_SINHVIEN(ho_ten, ma_sinh_vien, giai_thuong_id) values(?,?,?)",
                hoTen, maSv, giaiThuongId);
    }

    public String getListGiaiThuong() {
        try {
            String sql = "Select a.*, b.name from tbl_giai_thuong a, def_loai_giaithuong b where a.loai_thanh_tich=b.id";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                int giaiThuongId = (int) row.get("id");
                jo.put("id", giaiThuongId);
                jo.put("ten_giai_thuong", row.get("ten_giai_thuong"));
                jo.put("loai_giai_thuong_id", row.get("loai_thanh_tich"));
                jo.put("nam_thuc_hien", row.get("nam_thuc_hien"));
                jo.put("loai_giai_thuong", row.get("name"));

                List<Map<String, Object>> students = jdbcTemplate.queryForList("Select * from tbl_giaithuong_sinhvien where giai_thuong_id=?", giaiThuongId);
                JSONArray ja1 = new JSONArray();
                for (Map<String, Object> s : students) {
                    JSONObject jo1 = new JSONObject();
                    jo1.put("id", s.get("id"));
                    jo1.put("ho_ten", s.get("ho_ten"));
                    jo1.put("ma_sinh_vien", s.get("ma_sinh_vien"));
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

    public String updateGiaiThuong(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_giai_thuong set ten_giai_thuong=?, loai_thanh_tich=?, nam_thuc_hien=? where id=?",
                    jo.getString("ten_giai_thuong"), jo.getInt("loai_giai_thuong_id"), jo.getInt("nam_thuc_hien"), jo.getInt("id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateSinhVien(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_giaithuong_sinhvien set ho_ten=?, ma_sinh_vien=? where id=?",
                    jo.getString("ho_ten"), jo.getString("ma_sinh_vien"), jo.getInt("id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteSinhVien(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_giaithuong_sinhvien where id=?", jo.getInt("id"));
            return "{\"code\":200, \"description\":\"OK\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteGiaiThuong(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_giai_thuong where id=?", jo.getInt("id"));
            return "{\"code\":200, \"description\":\"OK\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
