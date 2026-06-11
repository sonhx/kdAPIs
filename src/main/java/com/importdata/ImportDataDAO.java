package com.importdata;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.session.SessionService;
import com.session.struct_session;

@Service
public class ImportDataDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionService sessionService;

    public String clearTable_1(String req) {
        try {
            JSONObject jin = new JSONObject(req);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Unauthorized\"}";

            jdbcTemplate.execute("TRUNCATE TABLE [TBL_CBCNV]");
            return "Deleted";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed";
        }
    }

    public String getImportData_1(String req) {
        JSONObject jout = new JSONObject();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from TBL_CBCNV");
            JSONArray ja = new JSONArray();

            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("ten", row.get("tendaydu"));
                json.put("email", row.get("email"));
                json.put("ngay_sinh", row.get("ngaysinh"));
                json.put("cccd", row.get("cccd"));
                json.put("sdt", row.get("mobile"));

                int val_hoc_ham = row.get("hocham") != null ? (int) row.get("hocham") : 0;
                int val_hoc_vi = row.get("trinhdo") != null ? (int) row.get("trinhdo") : 0;
                int val_gioi_tinh = row.get("gioitinh") != null ? (int) row.get("gioitinh") : 0;
                int val_chuc_danh = row.get("chucdanh") != null ? (int) row.get("chucdanh") : 0;
                String val_org = (String) row.get("orgcode");

                json.put("hoc_ham", getNameFromDef("def_hocham", val_hoc_ham));
                json.put("hoc_vi", getNameFromDef("def_trinhdo", val_hoc_vi));
                json.put("gioi_tinh", getNameFromDef("def_gioitinh", val_gioi_tinh));
                json.put("chuc_danh", getNameFromDef("def_chucdanh", val_chuc_danh));
                json.put("org", getOrgNameByCode(val_org));

                ja.put(json);
            }
            jout.put("data", ja);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    public String getImportData_2(String req) {
        JSONObject jout = new JSONObject();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from TBL_CBCNV where ChucDanh < 4");
            JSONArray ja = new JSONArray();

            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("ten", row.get("tendaydu"));
                json.put("email", row.get("email"));
                json.put("ngay_sinh", row.get("ngaysinh"));
                json.put("cccd", row.get("cccd"));
                json.put("sdt", row.get("mobile"));

                int val_hoc_ham = row.get("hocham") != null ? (int) row.get("hocham") : 0;
                int val_hoc_vi = row.get("trinhdo") != null ? (int) row.get("trinhdo") : 0;
                int val_gioi_tinh = row.get("gioitinh") != null ? (int) row.get("gioitinh") : 0;
                int val_chuc_danh = row.get("chucdanh") != null ? (int) row.get("chucdanh") : 0;
                String val_org = (String) row.get("orgcode");

                json.put("hoc_ham", getNameFromDef("def_hocham", val_hoc_ham));
                json.put("hoc_vi", getNameFromDef("def_trinhdo", val_hoc_vi));
                json.put("gioi_tinh", getNameFromDef("def_gioitinh", val_gioi_tinh));
                json.put("chuc_danh", getNameFromDef("def_chucdanh", val_chuc_danh));
                json.put("org", getOrgNameByCode(val_org));

                ja.put(json);
            }
            jout.put("data", ja);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    public String getImportData_3(String req) {
        JSONObject jout = new JSONObject();
        try {
            List<Map<String, Object>> orgRows = jdbcTemplate.queryForList("Select * from TBL_ORG where parentid = 2");
            JSONArray ja = new JSONArray();

            for (Map<String, Object> orgRow : orgRows) {
                JSONObject json = new JSONObject();
                int orgId = (int) orgRow.get("id");
                json.put("org_id", orgId);
                json.put("khoa_vien", orgRow.get("name"));

                Integer nganhId = jdbcTemplate.queryForObject("select top 1 NganhHoc from TBL_ORG_NGANHHOC where orgID=?", Integer.class, orgId);
                if (nganhId != null) {
                    Integer trinhDoId = jdbcTemplate.queryForObject("select TrinhDo from DEF_NGANHHOC where id=?", Integer.class, nganhId);
                    if (trinhDoId != null) {
                        if (trinhDoId == 3) json.put("trinh_do", "Đại học");
                        else if (trinhDoId == 4 || trinhDoId == 5 || trinhDoId == 7) json.put("trinh_do", "Sau Đại học");
                        else json.put("trinh_do", "Khác");
                    }
                    json.put("so_hoc_vien", jdbcTemplate.queryForObject("select count(id) from TBL_HOCVIEN where nganhhoc=?", Integer.class, nganhId));
                }
                json.put("so_ctdt", jdbcTemplate.queryForObject("select count(nganhhoc) from TBL_ORG_NGANHHOC where orgID=?", Integer.class, orgId));
                ja.put(json);
            }
            jout.put("data", ja);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    public String getImportData_4(String req) {
        JSONObject jout = new JSONObject();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from TBL_ORG where id = 8 or ( id > 39 and id < 45 )");
            JSONArray ja = new JSONArray();

            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                int orgId = (int) row.get("id");
                String orgCode = (String) row.get("code");
                json.put("org_id", orgId);
                json.put("don_vi_tt", row.get("name"));

                Integer nganhId = jdbcTemplate.queryForObject("select top 1 NganhHoc from TBL_ORG_NGANHHOC where orgID=?", Integer.class, orgId);
                if (nganhId != null) {
                    Integer trinhDoId = jdbcTemplate.queryForObject("select TrinhDo from DEF_NGANHHOC where id=?", Integer.class, nganhId);
                    if (trinhDoId != null) {
                        if (trinhDoId == 3) json.put("trinh_do", "Đại học");
                        else if (trinhDoId == 4 || trinhDoId == 5 || trinhDoId == 7) json.put("trinh_do", "Sau Đại học");
                        else json.put("trinh_do", "Khác");
                    }
                }
                json.put("so_nghien_cuu_vien", jdbcTemplate.queryForObject("select COUNT(id) from TBL_CBCNV where OrgCode = ? and chucdanh = 53", Integer.class, orgCode));
                json.put("so_can_bo", jdbcTemplate.queryForObject("select COUNT(id) from TBL_CBCNV where OrgCode = ? and chucdanh != 53", Integer.class, orgCode));
                ja.put(json);
            }
            jout.put("data", ja);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":500, \"description\":\"" + e.getMessage() + "\"}";
        }
        return jout.toString();
    }

    public String getImportData_CSVC_2(String req) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from TBL_BOOK_STORE_STAT");
            return new JSONObject().put("data", new JSONArray(rows)).toString();
        } catch (Exception e) {
            return "{\"code\":500}";
        }
    }

    public String getImportData_CSVC_3(String req) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from TBL_CAMPUS_AREA where Loai not in (1,2,3,4,5,26)");
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject(row);
                json.put("owner_type", ((int)row.get("ownertype") == 1) ? "Sở hữu" : "");
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":500}";
        }
    }

    public String getImportData_CSVC_4(String req) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("Select * from TBL_CAMPUS_AREA_ASSET");
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject json = new JSONObject();
                json.put("id", row.get("id"));
                json.put("ten_thiet_bi", row.get("ten"));
                json.put("so_luong", row.get("soluong"));
                json.put("ten_phong", jdbcTemplate.queryForObject("select Ten from TBL_CAMPUS_AREA where id=?", String.class, row.get("CampusAreaID")));
                json.put("don_vi_su_dung", jdbcTemplate.queryForObject("select Name from TBL_ORG where id=?", String.class, row.get("DonViSuDung")));
                ja.put(json);
            }
            return new JSONObject().put("data", ja).toString();
        } catch (Exception e) {
            return "{\"code\":500}";
        }
    }

    private String getNameFromDef(String table, int value) {
        if (value <= 0) return "";
        try {
            return jdbcTemplate.queryForObject("select name from " + table + " where value = ?", String.class, value);
        } catch (Exception e) {
            return "";
        }
    }

    private String getOrgNameByCode(String code) {
        if (code == null || code.isEmpty()) return "";
        try {
            return jdbcTemplate.queryForObject("select name from TBL_ORG where code = ?", String.class, code);
        } catch (Exception e) {
            return "";
        }
    }
}
