package com.hanhdv.khcn;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GetDataStatistic {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String getThongKeDeTai() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeDeTai(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("ten_loai_de_tai", row.get("ten_loai_de_tai"));
                json.put("nam", row.get(String.valueOf(currentYear)));
                json.put("bon", row.get(String.valueOf(currentYear - 4)));
                json.put("ba", row.get(String.valueOf(currentYear - 3)));
                json.put("hai", row.get(String.valueOf(currentYear - 2)));
                json.put("mot", row.get(String.valueOf(currentYear - 1)));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getThongKeBaiBao() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeSoLuongBaiBao(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("ten_loai_de_tai", row.get("ten_loai_bai_bao"));
                json.put("nam", row.get(String.valueOf(currentYear)));
                json.put("bon", row.get(String.valueOf(currentYear - 4)));
                json.put("ba", row.get(String.valueOf(currentYear - 3)));
                json.put("hai", row.get(String.valueOf(currentYear - 2)));
                json.put("mot", row.get(String.valueOf(currentYear - 1)));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getThongKeSinhVienGiaiThuong() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeSinhVienNghienCuuKH(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("ten_thanh_tich", row.get("ten_thanh_tich"));
                json.put("nam", row.get(String.valueOf(currentYear)));
                json.put("bon", row.get(String.valueOf(currentYear - 4)));
                json.put("ba", row.get(String.valueOf(currentYear - 3)));
                json.put("hai", row.get(String.valueOf(currentYear - 2)));
                json.put("mot", row.get(String.valueOf(currentYear - 1)));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getThongKeHoiThao() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeBaoCaoHN(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("ten_loai_hoi_thao", row.get("ten_loai_hoi_thao"));
                json.put("nam", row.get(String.valueOf(currentYear)));
                json.put("bon", row.get(String.valueOf(currentYear - 4)));
                json.put("ba", row.get(String.valueOf(currentYear - 3)));
                json.put("hai", row.get(String.valueOf(currentYear - 2)));
                json.put("mot", row.get(String.valueOf(currentYear - 1)));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getThongKeDoanhThuNCKH() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spDoanhThuNckh(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("nam_doanh_thu", row.get("nam_doanh_thu"));
                json.put("doanh_thu", row.get("doanh_thu"));
                json.put("ti_le_vs_tong", row.get("ti_le_vs_tong"));
                json.put("ti_le_vs_can_bo", row.get("ti_le_vs_can_bo"));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String thongKeCanBoDeTai_5nam() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            JSONArray jaYear = new JSONArray();
            for (int year = currentYear - 4; year < currentYear + 1; year++) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeCanBo_DeTai(?)}", currentYear);
                JSONArray ja = new JSONArray();
                for (Map<String, Object> row : rows) {
                    JSONObject json = new JSONObject();
                    json.put("title", row.get("title"));
                    json.put("so_luong_cap_nn", row.get("1"));
                    json.put("so_luong_cap_bo", row.get("2"));
                    json.put("so_luong_cap_truong", row.get("3"));
                    ja.put(json);
                }
                JSONObject jo = new JSONObject();
                jo.put("year", year);
                jo.put("data", ja);
                jaYear.put(jo);
            }
            return new JSONObject().put("data", jaYear).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String thongKeSinhVienDeTai() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeSinhVien_DeTai(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("title", row.get("title"));
                json.put("so_luong_cap_nn", row.get("1"));
                json.put("so_luong_cap_bo", row.get("2"));
                json.put("so_luong_cap_truong", row.get("3"));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String thongKeCanBoBaiBao() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeCanBo_BaiBao(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("title", row.get("title"));
                json.put("quoc_te", row.get("QuocTe"));
                json.put("trong_nuoc", row.get("TrongNuoc"));
                json.put("cap_truong", row.get("CapTruong"));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String thongKeCanBoHoiThao() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeCanBo_HoiThao(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("title", row.get("title"));
                json.put("quoc_te", row.get("QuocTe"));
                json.put("trong_nuoc", row.get("TrongNuoc"));
                json.put("cap_truong", row.get("CapTruong"));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    public String getThongKeSangChe() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("{CALL spThongKeBangSangChe(?)}", currentYear);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("nam_cap", row.get("nam_cap"));
                json.put("so_luong", row.get("so_luong"));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
