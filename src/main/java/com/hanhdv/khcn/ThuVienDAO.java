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
public class ThuVienDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insertMemSach(String hoTen, String chucDanh, int thuocHocVien, int laChuTri, int bookId) {
        jdbcTemplate.update("Insert into tbl_sach_tacgia(ho_ten, chuc_danh, thuoc_hoc_vien, la_chu_tri, book_id) values(?,?,?,?,?)",
                hoTen, chucDanh, thuocHocVien, laChuTri, bookId);
    }

    public int getIDFromInsertSach(String isbn, String tenSach, int loaiSachId, int namXuatBan, int soLuong) {
        String insertSQL = "Insert into TBL_SACH(isbn,ten_sach,loai_sach_id,nam_xuat_ban, so_luong) values(?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, isbn);
            ps.setString(2, tenSach);
            ps.setInt(3, loaiSachId);
            ps.setInt(4, namXuatBan);
            ps.setInt(5, soLuong);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public String createNewBook(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int sachId = getIDFromInsertSach(jo.getString("isbn"), jo.getString("name"), jo.getJSONObject("loai_sach").getInt("id"), jo.getInt("publish_year"), jo.getInt("so_luong"));

            JSONArray users = jo.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                JSONObject json = users.getJSONObject(i);
                insertMemSach(json.getString("name"), json.getString("chuc_danh"), json.getInt("thuoc_hoc_vien"), json.getInt("la_chu_tri"), sachId);
            }
            return "{\"code\":200, \"description\":\"OK\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListBook() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select a.*, b.name from tbl_sach a, def_loai_sach b where a.loai_sach_id=b.id");
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                int bookId = (int) row.get("id");
                jo.put("id", bookId);
                jo.put("name", row.get("ten_sach"));
                jo.put("isbn", row.get("isbn"));
                jo.put("publish_year", row.get("nam_xuat_ban"));
                jo.put("so_luong", row.get("so_luong"));
                jo.put("phan_loai_sach", row.get("name"));
                jo.put("loai_sach_id", row.get("loai_sach_id"));

                List<Map<String, Object>> members = jdbcTemplate.queryForList("Select * from tbl_sach_tacgia where book_id=?", bookId);
                JSONArray ja1 = new JSONArray();
                for (Map<String, Object> m : members) {
                    JSONObject json1 = new JSONObject();
                    json1.put("id", m.get("id"));
                    json1.put("ho_ten", m.get("ho_ten"));
                    json1.put("chuc_danh", m.get("chuc_danh"));
                    json1.put("thuoc_hoc_vien", m.get("thuoc_hoc_vien"));
                    json1.put("la_chu_tri", m.get("la_chu_tri"));
                    ja1.put(json1);
                }
                jo.put("users", ja1);
                ja.put(jo);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String createDauSach(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            JSONArray lstTacgia = jo.getJSONArray("tac_gia");
            StringBuilder tacGia = new StringBuilder();
            for (int i = 0; i < lstTacgia.length(); i++) {
                tacGia.append(lstTacgia.getJSONObject(i).getString("text")).append(",");
            }
            jdbcTemplate.update("Insert into tbl_dau_sach(dau_sach, so_ban_sach,phan_loai,khoi_nganh_id,tac_gia) values(?,?,?,?,?)",
                    jo.getString("ten_dau_sach"), jo.getInt("so_ban_sach"), jo.getString("phan_loai"), jo.getJSONObject("khoi_nganh").getInt("id"), tacGia.toString());
            return "{\"code\":200, \"description\":\"OK\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListDauSach() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select a.*, b.name from tbl_dau_sach a, tbl_dau_sach_khoi_nganh b where a.khoi_nganh_id=b.id");
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                jo.put("id", row.get("id"));
                jo.put("ten_dau_sach", row.get("dau_sach"));
                jo.put("so_ban_sach", row.get("so_ban_sach"));
                jo.put("phan_loai", row.get("phan_loai"));
                jo.put("khoi_nganh", row.get("name"));
                jo.put("khoi_nganh_id", row.get("khoi_nganh_id"));
                ja.put(jo);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateBook(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_sach set isbn=?, ten_sach=?, loai_sach_id=?, nam_xuat_ban=?, so_luong=? where id=?",
                    jo.getString("isbn"), jo.getString("name"), jo.getInt("loai_sach_id"), jo.getInt("publish_year"), jo.getInt("so_luong"), jo.getInt("id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateDauSach(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_dau_sach set dau_sach=?, so_ban_sach=?, khoi_nganh_id=?, phan_loai=? where id=?",
                    jo.getString("ten_dau_sach"), jo.getInt("so_ban_sach"), jo.getInt("khoi_nganh_id"), jo.getString("phan_loai"), jo.getInt("id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteBook(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int id = jo.getInt("id");
            jdbcTemplate.update("Delete from tbl_sach where id=?", id);
            jdbcTemplate.update("Delete from tbl_sach_tacgia where book_id=?", id);
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteDauSach(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_dau_sach where id=?", jo.getInt("id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
