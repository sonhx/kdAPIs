package com.tccb;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/tccb")
public class TccbService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private TccbExtend tce;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private final SimpleDateFormat sdfIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    @PostMapping("/listperson")
    public String listPerson(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            String orgCode = jin.has("org_code") ? jin.getString("org_code") : "";
            int gioiTinh = jin.has("gioi_tinh_value") ? jin.getInt("gioi_tinh_value") : -1;
            int chucDanh = jin.has("chuc_danh_value") ? jin.getInt("chuc_danh_value") : 0;
            int trinhDo = jin.has("trinh_do_value") ? jin.getInt("trinh_do_value") : 0;
            int hocHam = jin.has("hoc_ham_value") ? jin.getInt("hoc_ham_value") : 0;
            int loaiHd = jin.has("loaihopdong") ? jin.getInt("loaihopdong") : 0;
            int ngachLuong = jin.has("ngachluong") ? jin.getInt("ngachluong") : 0;
            int tuoi = jin.has("tuoi") ? jin.getInt("tuoi") : 0;
            String startDateStr = jin.has("start_date") ? jin.getString("start_date") : "";

            if (startDateStr.isEmpty()) return "{\"code\":722, \"description\":\"Không có thời điểm kiểm tra\"}";
            Date dStart = sdfIso.parse(startDateStr);
            SimpleDateFormat sdfCheck = new SimpleDateFormat("yyyy-MM-dd");
            String strCheckDate = sdfCheck.format(dStart);

            String sql = "Select * from TBL_CBCNV where (IsDeleted =0 or IsDeleted is null)";
            List<Object> params = new ArrayList<>();
            if (!orgCode.isEmpty()) { sql += " and OrgCode=?"; params.add(orgCode); }
            if (gioiTinh >= 0) { sql += " and GioiTinh=?"; params.add(gioiTinh); }
            if (chucDanh > 0) { sql += " and ChucDanh=?"; params.add(chucDanh); }
            if (trinhDo > 0) { sql += " and TrinhDo=?"; params.add(trinhDo); }
            if (hocHam > 0) { sql += " and HocHam=?"; params.add(hocHam); }
            if (loaiHd > 0) { sql += " and LoaiHD=?"; params.add(loaiHd); }
            if (ngachLuong > 0) { sql += " and NgachLuong=?"; params.add(ngachLuong); }

            sql += " and ( NgayVaoHocVien < ? and (LastStateChangedTime Is null or LastStateChangedTime > ?) )";
            params.add(strCheckDate);
            params.add(strCheckDate);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            JSONArray ja = new JSONArray();
            int currentYear = new Date().getYear() + 1900;

            for (Map<String, Object> row : rows) {
                if (tuoi > 0) {
                    int namSinh = (int) row.get("NamSinh");
                    if ((currentYear - namSinh) != tuoi) continue;
                }
                JSONObject obj = new JSONObject();
                obj.put("id", row.get("ID"));
                obj.put("macb", row.get("MaCB"));
                obj.put("tendaydu", row.get("TenDayDu"));
                obj.put("ngaysinh", row.get("NgaySinh"));
                obj.put("gioitinh", row.get("GioiTinh"));
                obj.put("email", row.get("Email"));
                obj.put("mobile", row.get("Mobile"));
                obj.put("hocham", tce.getHocHamName((int) row.get("HocHam")));
                obj.put("trinhdo", tce.getTrinhDoName((int) row.get("Trinhdo")));
                obj.put("chucdanh", row.get("ChucDanh"));
                obj.put("org_code", row.get("OrgCode"));
                obj.put("place_code", row.get("PlaceCode"));
                obj.put("state_name", tce.getStateName((int) row.get("State")));
                ja.put(obj);
            }
            return new JSONObject().put("person_list", ja).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/listpersonlog")
    public String listPersonLog(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int personId = jin.getInt("person_id");
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from TBL_CBCNV_LOG where PersonID=? order by CreatedTime desc", personId);
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", row.get("ID"));
                obj.put("ten_cb", tce.getPersonName((int) row.get("PersonID")));
                obj.put("fieldname", row.get("FieldName"));
                obj.put("oldvalue", row.get("OldValue"));
                obj.put("newvalue", row.get("NewValue"));
                obj.put("modified_by_name", tce.getUserFullName((int) row.get("ChangedBy")));
                obj.put("created_time", row.get("CreatedTime"));
                ja.put(obj);
            }
            return new JSONObject().put("personlog_list", ja).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/listgioitinh")
    public String listGioiTinh(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_GIOITINH where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("gioitinh_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listhocham")
    public String listHocHam(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_HOCHAM where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("hocham_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listtrinhdo")
    public String listTrinhDo(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_TRINHDO where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("trinhdo_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listchinhtri")
    public String listChinhTri(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_CHINHTRI where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("chinhtri_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listdantoc")
    public String listDanToc(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_DANTOC where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("dantoc_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listtongiao")
    public String listTonGiao(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_TONGIAO where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("tongiao_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listchucdanh")
    public String listChucDanh(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_CHUCDANH where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("chucdanh_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listngachluong")
    public String listNgachLuong(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_NGACHLUONG where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("ngachluong_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listloaihopdong")
    public String listLoaiHopDong(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Value as value, Name as name from DEF_LOAIHOPDONG where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("loaihopdong_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listorg")
    public String listOrg(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from TBL_ORG where (IsDeleted is null or IsDeleted='0')");
            return new JSONObject().put("org_list", new JSONArray(rows)).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
