package com.hanhdv.khcn;

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
import java.sql.PreparedStatement;
import java.sql.Statement;

@Service
public class BaiBaoDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insertMemBaiBao(String hoTen, String chucDanh, int thuocHocVien, int laChuTri, int baiBaoId) {
        String sql = "Insert into TBL_BAIBAO_THANHVIEN(ho_ten, chuc_danh, thuoc_hoc_vien, la_chu_tri, bai_bao_id) values(?,?,?,?,?)";
        jdbcTemplate.update(sql, hoTen, chucDanh, thuocHocVien, laChuTri, baiBaoId);
    }

    public String createNewBaiBao(String req) {
        try {
            JSONObject json = new JSONObject(req);
            String issn = json.has("issn") ? json.getString("issn") : "";
            String tenBaiBao = json.getString("ten_bai_bao");
            int loaiTapChiId = json.getJSONObject("loai_tap_chi").getInt("id");
            String namXuatBan = String.valueOf(json.getInt("nam_xuat_ban"));
            String tenTapChi = json.getString("ten_tap_chi");
            String thongTinThem = json.has("thong_tin_them") ? json.getString("thong_tin_them") : "";

            int baiBaoId = getIDFromInsertBaiBao(issn, tenBaiBao, loaiTapChiId, tenTapChi, namXuatBan, thongTinThem);

            JSONArray users = json.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.getJSONObject(i);
                insertMemBaiBao(user.getString("ho_ten"), user.getString("chuc_danh"), user.getInt("thuoc_hoc_vien"), user.getInt("la_chu_tri"), baiBaoId);
            }
            return "{\"code\":200, \"description\":\"Thêm mới bài báo thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteBaiBao(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int id = jo.getInt("id");
            jdbcTemplate.update("Delete from tbl_baibao where id=?", id);
            jdbcTemplate.update("Delete from TBL_BAIBAO_THANHVIEN where bai_bao_id=?", id);
            return "{\"code\":200, \"description\":\"OK!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public int getIDFromInsertBaiBao(String issn, String tenBaiBao, int loaiTapChi, String tenTapChi, String namXuatBan, String thongTinThem) {
        String insertSQL = "Insert into TBL_BAIBAO(TenBaiBao,LoaiTapChi,TenTapChi,NamXuatBan, ISSN,ChiTiet,createdTime) values(?,?,?,?,?,?,getdate())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, tenBaiBao);
            ps.setInt(2, loaiTapChi);
            ps.setString(3, tenTapChi);
            ps.setString(4, namXuatBan);
            ps.setString(5, issn);
            ps.setString(6, thongTinThem);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public String getListBaiBao(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int id = jo.getInt("id");
            int parentId = jo.getInt("parentId");
            int childrenSize = jo.getInt("childrenSize");

            String sql = "Select a.*, b.name from tbl_baibao a , tbl_loai_tap_chi b where a.LoaiTapChi=b.id";
            List<Object> params = new ArrayList<>();
            if (parentId == 0 && childrenSize > 0) {
                sql += " and b.parent_id=?";
                params.add(id);
            } else {
                sql += " and a.LoaiTapChi=?";
                params.add(id);
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                int baiBaoId = (int) row.get("ID");
                json.put("id", baiBaoId);
                json.put("issn", row.get("issn"));
                json.put("nam_xuat_ban", row.get("NamXuatBan"));
                json.put("ten_bai_bao", row.get("TenBaiBao"));
                json.put("ten_tap_chi", row.get("TenTapChi"));
                json.put("loai_tap_chi_id", row.get("LoaiTapChi"));
                json.put("loai_tap_chi", row.get("name"));
                json.put("thong_tin_them", row.get("ChiTiet"));

                List<Map<String, Object>> members = jdbcTemplate.queryForList("Select * from tbl_baibao_thanhvien where bai_bao_id=?", baiBaoId);
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
                json.put("thanh_vien_list", ja1);
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListLoaiTapChi() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select id, name as text, parent_id as parentId from tbl_loai_tap_chi");
            return new JSONObject().put("data", new JSONArray(rows)).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateBaiBaoMember(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_baibao_thanhvien set ho_ten=?, chuc_danh=?, la_chu_tri=?, thuoc_hoc_vien=? where id=?",
                    jo.getString("ho_ten"), jo.getString("chuc_danh"), jo.get("la_chu_tri"), jo.get("thuoc_hoc_vien"), jo.getInt("id"));
            return "{\"code\":200, \"description\":\"OK!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteMember(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_baibao_thanhvien where id=?", jo.getInt("id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String addNewMem(String req) {
        try {
            JSONObject json = new JSONObject(req);
            insertMemBaiBao(json.getString("ho_ten"), json.getString("chuc_danh"), json.getInt("thuoc_hoc_vien"), json.getInt("la_chu_tri"), json.getInt("bai_bao_id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListMemberByBaiBaoId(String req) {
        try {
            JSONObject json = new JSONObject(req);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from tbl_baibao_thanhvien where bai_bao_id=?", json.getInt("bai_bao_id"));
            return new JSONObject().put("data", new JSONArray(rows)).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateBaiBao(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_baibao set TenBaiBao=?, LoaiTapChi=?, NamXuatBan=?, issn=?, TenTapChi=?, ChiTiet=? where id=?",
                    jo.getString("ten_bai_bao"), jo.getInt("loai_tap_chi_id"), jo.getInt("nam_xuat_ban"), jo.getString("issn"), jo.getString("ten_tap_chi"), jo.has("thong_tin_them") ? jo.getString("thong_tin_them") : "", jo.getInt("id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
