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
public class KhcnDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insertMemDetaiNew(String hoTen, String chucDanh, int thuocHocVien, int laChuTri, int deTaiId, int laSinhVien) {
        String sql = "Insert into tbl_detai_thanhvien(ho_ten, chuc_danh, thuoc_hoc_vien, la_chu_tri, detai_id,la_sinh_vien) values(?,?,?,?,?,?)";
        jdbcTemplate.update(sql, hoTen, chucDanh, thuocHocVien, laChuTri, deTaiId, laSinhVien);
    }

    public String deleteMember(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Delete from tbl_detai_thanhvien where id=?", jo.getInt("id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String deleteDetai(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int id = jo.getInt("id");
            jdbcTemplate.update("Delete from tbl_detai where id=?", id);
            jdbcTemplate.update("Delete from tbl_detai_thanhvien where detai_id=?", id);
            return "{\"code\":200, \"description\":\"Xóa đề tài thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String addNewMem(String req) {
        try {
            JSONObject json = new JSONObject(req);
            insertMemDetaiNew(json.getString("ho_ten"), json.has("chuc_danh") ? json.getString("chuc_danh") : "", json.getInt("thuoc_hoc_vien"), json.getInt("la_chu_tri"), json.getInt("de_tai_id"), json.has("la_sinh_vien") ? json.getInt("la_sinh_vien") : 0);
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public int getIDFromInsertDeTai(String maDt, String tenDt, String tenCt, int capDt, String namBatDau, String namKetThuc, String kinhPhi, String dvThucHien, String nghiemThu) {
        String insertSQL = "Insert into TBL_DETAI(MaSo,TenDeTai,CapQuanLyKH,NamBatDau, NamKetThuc, KinhPhi, DonViChuTri,TenChuongTrinhKH,ThoiGianThucHien) values(?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, maDt);
            ps.setString(2, tenDt);
            ps.setInt(3, capDt);
            ps.setString(4, namBatDau);
            ps.setString(5, namKetThuc);
            ps.setString(6, kinhPhi);
            ps.setString(7, dvThucHien);
            ps.setString(8, tenCt);
            ps.setString(9, nghiemThu);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public String getListDetai(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int loaiDeTai = jo.getInt("loai_de_tai");

            String sql = "Select a.*, b.name from tbl_detai a inner join [DEF_CAPQUANLYKH] b on a.CapQuanLyKH=b.id";
            List<Object> params = new ArrayList<>();
            if (loaiDeTai != 0) {
                sql += " where a.CapQuanLyKH=?";
                params.add(loaiDeTai);
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                int detaiId = (int) row.get("id");
                json.put("id", detaiId);
                json.put("ma_so", row.get("MaSo"));
                json.put("ten_de_tai", row.get("TenDeTai"));
                json.put("cap_quan_ly_id", row.get("CapQuanLyKH"));
                json.put("nam_bat_dau", row.get("NamBatDau"));
                json.put("nam_ket_thuc", row.get("NamKetThuc"));
                json.put("don_vi_chu_tri", row.get("DonViChuTri"));
                json.put("kinh_phi", row.get("KinhPhi"));
                json.put("ten_ct_kh", row.get("TenChuongTrinhKH"));
                json.put("phan_loai", row.get("name"));
                json.put("nghiem_thu", row.get("ThoiGianThucHien"));

                List<Map<String, Object>> members = jdbcTemplate.queryForList("Select * from tbl_detai_thanhvien where detai_id=?", detaiId);
                JSONArray ja1 = new JSONArray();
                for (Map<String, Object> m : members) {
                    JSONObject json1 = new JSONObject();
                    json1.put("id", m.get("id"));
                    json1.put("ho_ten", m.get("ho_ten"));
                    json1.put("chuc_danh", m.get("chuc_danh"));
                    json1.put("thuoc_hoc_vien", String.valueOf(m.get("thuoc_hoc_vien")));
                    json1.put("la_chu_tri", String.valueOf(m.get("la_chu_tri")));
                    json1.put("la_sinh_vien", String.valueOf(m.get("la_sinh_vien")));
                    ja1.put(json1);
                }
                json.put("thanh_vien", ja1);
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
    }

    public String getListMemberDetaiByDetaiId(String req) {
        try {
            JSONObject json = new JSONObject(req);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from tbl_detai_thanhvien where detai_id=?", json.getInt("de_tai_id"));
            return new JSONObject().put("data", new JSONArray(rows)).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String addNewDetai(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            int detaiId = getIDFromInsertDeTai(jo.getString("ma_so"), jo.getString("ten_de_tai"), jo.getString("chuong_trinh_kh"), jo.getJSONObject("phan_loai_dt").getInt("id"), jo.getString("nam_bat_dau"), jo.getString("nam_ket_thuc"), jo.getString("kinh_phi"), jo.getString("don_vi_thuc_hien"), jo.getString("nghiem_thu"));

            JSONArray users = jo.getJSONArray("users");
            for (int i = 0; i < users.length(); i++) {
                JSONObject json = users.getJSONObject(i);
                insertMemDetaiNew(json.getString("name"), json.getString("chuc_danh"), json.getInt("thuoc_hoc_vien"), json.getInt("la_chu_tri"), detaiId, json.getInt("la_sinh_vien"));
            }
            return "";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateDeTai(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_detai set MaSo=?, TenDeTai=?, NamBatDau=?, NamKetThuc=?, TenChuongTrinhKH=?, capQuanLyKH=?, DonViChuTri=?, kinhPHi=?, ThoiGianThucHien=? where id=?",
                    jo.getString("ma_so"), jo.getString("ten_de_tai"), jo.getInt("nam_bat_dau"), jo.getInt("nam_ket_thuc"), jo.has("ten_ct_kh") ? jo.getString("ten_ct_kh") : "", jo.getInt("cap_quan_ly_id"), jo.getString("don_vi_chu_tri"), jo.getInt("kinh_phi"), jo.getString("nghiem_thu"), jo.getInt("id"));
            return "{\"code\":200, \"description\":\"Cập nhật thành công!\"}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String updateDetaiMember(String req) {
        try {
            JSONObject jo = new JSONObject(req);
            jdbcTemplate.update("Update tbl_detai_thanhvien set ho_ten=?, chuc_danh=?, la_chu_tri=?, thuoc_hoc_vien=?, la_sinh_vien=? where id=?",
                    jo.getString("ho_ten"), jo.getString("chuc_danh"), jo.get("la_chu_tri"), jo.get("thuoc_hoc_vien"), jo.get("la_sinh_vien"), jo.getInt("id"));
            return "";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListCanBo() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select id as ID, MaCB as MaCanBo, TenDayDu from TBL_CBCNV order by TenDayDu");
            return new JSONObject().put("data", new JSONArray(rows)).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getListCanBoNew() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select id, MaCB as ma_can_bo, TenDayDu as text, OrgCode as [group] from TBL_CBCNV order by OrgCode");
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("id", row.get("id"));
                json.put("ma_can_bo", row.get("ma_can_bo"));
                json.put("text", row.get("text"));
                json.put("group", row.get("group").toString().trim().toUpperCase());
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
