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
public class BaoCaoDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String getListBaoCao(String req) {
        try {
            JSONObject json = new JSONObject(req);
            int loaiHoiThaoId = json.getInt("loai_hoi_thao_id");
            int year = json.getInt("year");

            String sql = "Select a.*, b.name from tbl_bao_cao_hoi_thao a inner join DEF_LOAI_HOI_THAO b on a.loai_hoi_thao=b.id where a.nam_bao_cao=?";
            List<Object> params = new ArrayList<>();
            params.add(year);

            if (loaiHoiThaoId != 0) {
                sql += " and a.loai_hoi_thao=?";
                params.add(loaiHoiThaoId);
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                int baoCaoId = (int) row.get("id");
                jo.put("id", baoCaoId);
                jo.put("ten_bao_cao", row.get("ten_bao_cao"));
                jo.put("nam_bao_cao", row.get("nam_bao_cao"));
                jo.put("ten_hoi_thao", row.get("ten_hoi_thao"));
                jo.put("ten_loai_hoi_thao", row.get("name"));
                jo.put("loai_hoi_thao_id", row.get("loai_hoi_thao"));
                jo.put("thong_tin_them", row.get("thong_tin_them"));

                List<Map<String, Object>> members = jdbcTemplate.queryForList("Select * from tbl_baocao_thanhvien where bao_cao_id=?", baoCaoId);
                JSONArray ja1 = new JSONArray();
                for (Map<String, Object> m : members) {
                    JSONObject json1 = new JSONObject();
                    json1.put("id", m.get("id"));
                    json1.put("ho_ten", m.get("ho_ten"));
                    json1.put("chuc_danh", m.get("chuc_danh"));
                    json1.put("thuoc_hoc_vien", String.valueOf(m.get("thuoc_hoc_vien")));
                    json1.put("la_chu_tri", String.valueOf(m.get("la_chu_tri")));
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

    public String getListLoaiBaoCao() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select id, name from DEF_LOAI_HOI_THAO");
            return new JSONObject().put("data", new JSONArray(rows)).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String createNewBaoCao(String req) {
        try {
            JSONObject json = new JSONObject(req);
            String tenBaoCao = json.getString("ten_bao_cao");
            int namBaoCao = json.getInt("nam_bao_cao");
            int loaiHoiThao = json.getJSONObject("loai_hoi_thao").getInt("id");
            String tenHoiThao = json.getString("ten_hoi_thao");
            String thongTinThem = json.has("thong_tin_them") ? json.getString("thong_tin_them") : "";

            int baoCaoId = getIDFromInsertBaoCao(tenBaoCao, tenHoiThao, loaiHoiThao, namBaoCao, thongTinThem);

            JSONArray users = json.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.getJSONObject(i);
                insertMemBaoCao(user.getString("ho_ten"), user.has("chuc_danh") ? user.getString("chuc_danh") : "", user.getInt("thuoc_hoc_vien"), user.getInt("la_chu_tri"), baoCaoId);
            }
            return "{\"code\":200, \"description\":\"OK\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public void insertMemBaoCao(String hoTen, String chucDanh, int thuocHocVien, int laChuTri, int baoCaoId) {
        jdbcTemplate.update("Insert into TBL_BAOCAO_THANHVIEN(ho_ten, chuc_danh, thuoc_hoc_vien, la_chu_tri, bao_cao_id) values(?,?,?,?,?)",
                hoTen, chucDanh, thuocHocVien, laChuTri, baoCaoId);
    }

    public int getIDFromInsertBaoCao(String tenBaoCao, String tenHoiThao, int loaiHoiThao, int namBaoCao, String thongTinThem) {
        String insertSQL = "Insert into [TBL_BAO_CAO_HOI_THAO](ten_bao_cao,ten_hoi_thao,loai_hoi_thao,nam_bao_cao, thong_tin_them,createdAt) values(?,?,?,?,?,getdate())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tenBaoCao);
            ps.setString(2, tenHoiThao);
            ps.setInt(3, loaiHoiThao);
            ps.setInt(4, namBaoCao);
            ps.setString(5, thongTinThem);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public String updateBaoCao(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_bao_cao_hoi_thao set ten_bao_cao=?, loai_hoi_thao=?, ngay_bao_cao=?, nguoi_bao_cao=? where id=?",
                    jo.getString("ten_bao_cao"), jo.getInt("loai_hoi_thao_id"), jo.getString("ngay_bao_cao"), jo.getString("nguoi_bao_cao"), jo.getInt("id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteBaoCao(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_bao_cao_hoi_thao where id=?", jo.getInt("id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateMemberBaoCao(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_baocao_thanhvien set ho_ten=?, chuc_danh=?, la_chu_tri=?, thuoc_hoc_vien=? where id=?",
                    jo.getString("ho_ten"), jo.has("chuc_danh") ? jo.getString("chuc_danh") : "", jo.get("la_chu_tri"), jo.get("thuoc_hoc_vien"), jo.getInt("id"));
            return "{\"code\":200, \"description\":\"OK!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListMemberByBaoCaoId(String req) {
        try {
            JSONObject json = new JSONObject(req);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from tbl_baocao_thanhvien where bao_cao_id=?", json.getInt("bao_cao_id"));
            return new JSONObject().put("data", new JSONArray(rows)).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteMember(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_baocao_thanhvien where id=?", jo.getInt("id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String addNewMem(String req) {
        try {
            JSONObject json = new JSONObject(req);
            insertMemBaoCao(json.getString("ho_ten"), json.has("chuc_danh") ? json.getString("chuc_danh") : "", json.getInt("thuoc_hoc_vien"), json.getInt("la_chu_tri"), json.getInt("bao_cao_id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
