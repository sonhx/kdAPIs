package com.khcn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KhcnExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Helper methods moved from Service
    public String getCapQuanLyName(int id) {
        try { return jdbcTemplate.queryForObject("select Name from def_capquanlykh where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    public String getChuongTrinhName(int id) {
        try { return jdbcTemplate.queryForObject("select Name from def_chuongtrinhkh where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    public String getOrgName(int id) {
        try { return jdbcTemplate.queryForObject("select Name from TBL_ORG where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    public String getProductTypeName(int id) {
        try { return jdbcTemplate.queryForObject("select Name from DEF_LOAISANPHAMKHCN where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    public String getSectorName(int id) {
        try { return jdbcTemplate.queryForObject("select Ten from DEF_NGANHHOC where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    // Business Methods
    public JSONArray listLoaiDeTai() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from DEF_LOAIDETAI where (IsDeleted is null or IsDeleted='0')");
        JSONArray ja = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("value", row.get("Value"));
            obj.put("name", row.get("Name"));
            ja.put(obj);
        }
        return ja;
    }

    public JSONArray listDanhMucTapChi(int type) {
        String sql = "select * from DEF_DANHMUCTAPCHI where (IsDeleted is null or IsDeleted=0)";
        List<Object> params = new ArrayList<>();
        if (type > 0) { sql += " and Type=?"; params.add(type); }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        JSONArray ja = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("type", row.get("Type"));
            obj.put("name", row.get("Name"));
            ja.put(obj);
        }
        return ja;
    }

    public String getDanhMucTapChi(int id) {
        try { return jdbcTemplate.queryForObject("select Name from DEF_DANHMUCTAPCHI where ID=?", String.class, id); } catch (Exception e) { return "unknown"; }
    }

    public JSONArray listChitieuDetai(int year, int capId) {
        String sql = "select * from tbl_detai_chitieu where (IsDeleted=0 or IsDeleted Is NULL)";
        List<Object> params = new ArrayList<>();
        if (year > 0) { sql += " and Nam=?"; params.add(year); }
        if (capId > 0) { sql += " and CapQuanLyKH=?"; params.add(capId); }
        sql += " order by Nam ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        JSONArray ja = new JSONArray();
        for (Map<String, Object> row : rows) {
            JSONObject obj = new JSONObject();
            obj.put("id", row.get("ID"));
            obj.put("nam", row.get("Nam"));
            obj.put("tencapquanlykh", getCapQuanLyName((int) row.get("CapQuanLyKH")));
            obj.put("doanhthukhsan", row.get("DoanhthuKHSan"));
            obj.put("doanhthukhtran", row.get("DoanhThuKHTran"));
            ja.put(obj);
        }
        return ja;
    }

    public JSONObject getTangTruongKHCN() {
        List<Map<String, Object>> types = jdbcTemplate.queryForList("Select * from DEF_LOAISANPHAMKHCN where (IsDeleted=0 or IsDeleted is NULL)");
        List<Integer> years = jdbcTemplate.queryForList("Select distinct year from TBL_CGCN order by year asc", Integer.class);
        
        JSONArray jar = new JSONArray();
        for (Integer year : years) {
            JSONObject yObj = new JSONObject();
            yObj.put("year", year);
            JSONArray productArr = new JSONArray();
            for (Map<String, Object> type : types) {
                int typeId = (int) type.get("ID");
                JSONObject pObj = new JSONObject();
                pObj.put("id", typeId);
                pObj.put("name", type.get("Name"));
                pObj.put("code", type.get("Code"));
                
                int count = jdbcTemplate.queryForObject("Select count(*) from TBL_CGCN where Year=? and ProductTypeID=?", Integer.class, year, typeId);
                pObj.put("counter", count);
                productArr.put(pObj);
            }
            yObj.put("product_counter", productArr);
            jar.put(yObj);
        }
        
        JSONObject res = new JSONObject();
        res.put("growth_list", jar);
        return res;
    }
}
