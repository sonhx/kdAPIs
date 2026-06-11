package com.khcn;

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
@RequestMapping("/khcn")
public class KhcnServiceB {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private KhcnExtend kce;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @PostMapping("/listloaidetai")
    public String listLoaiDeTai(@RequestBody String sReq) {
        return new JSONObject().put("loaidetai_list", kce.listLoaiDeTai()).put("code", 200).toString();
    }

    @PostMapping("/listloaitapchi")
    public String listLoaiTapChi(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from DEF_LOAITAPCHI where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("loaitapchi_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listdanhmuctapchi")
    public String listDanhMucTapChi(@RequestBody String sReq) {
        JSONObject jin = new JSONObject(sReq);
        int loaitapchi = jin.has("loaitapchi") ? jin.getInt("loaitapchi") : 0;
        return new JSONObject().put("danhmuctapchi_list", kce.listDanhMucTapChi(loaitapchi)).put("code", 200).toString();
    }

    @PostMapping("/listcapquanlykh")
    public String listCapQuanLyKH(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Name as name, Code as code from DEF_CAPQUANLYKH where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("project_manager_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listchuongtrinhkh")
    public String listChuongTrinhKH(@RequestBody String sReq) {
        JSONObject jin = new JSONObject(sReq);
        int id_capquanly = jin.getInt("id_capquanly");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Name as name, Code as code from DEF_CHUONGTRINHKH where CapQuanLyKH=? and (IsDeleted is null or IsDeleted='0')", id_capquanly);
        return new JSONObject().put("research_program_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listchitieudetai")
    public String listChitieuDetai(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int year = jin.has("year") ? jin.getInt("year") : 0;
            int id_cap = jin.has("id_capquanlykh") ? jin.getInt("id_capquanlykh") : 0;

            JSONObject jout = new JSONObject();
            jout.put("chitieu_detai_list", kce.listChitieuDetai(year, id_cap));
            jout.put("code", 200);
            return jout.toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/listdetai")
    public String listDetai(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int year = jin.has("year") ? jin.getInt("year") : 0;
            int capId = jin.has("id_capquanlykh") ? jin.getInt("id_capquanlykh") : 0;
            int pId = jin.has("id_chuongtrinhkh") ? jin.getInt("id_chuongtrinhkh") : 0;

            String sql = "select * from tbl_detai where (IsDeleted=0 or IsDeleted Is NULL)";
            List<Object> params = new ArrayList<>();
            if (year > 0) { sql += " and NamBatDau=?"; params.add(year); }
            if (capId > 0) { sql += " and CapQuanLyKH=?"; params.add(capId); }
            if (pId > 0) { sql += " and ChuongTrinhKH=?"; params.add(pId); }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            JSONArray ja = new JSONArray();
            long totalKinhPhi = 0;
            for (Map<String, Object> row : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", row.get("ID"));
                obj.put("tendetai", row.get("TenDeTai"));
                obj.put("chutri", row.get("ChuTri"));
                obj.put("nambatdau", row.get("NamBatDau"));
                obj.put("namketthuc", row.get("NamKetThuc"));
                obj.put("tencapquanlykh", kce.getCapQuanLyName((int) row.get("CapQuanLyKH")));
                obj.put("tenchuongtrinhkh", kce.getChuongTrinhName((int) row.get("ChuongTrinhKH")));
                obj.put("thoigianthuchien", row.get("ThoiGianThucHien"));
                obj.put("kinhphi", row.get("KinhPhi"));
                totalKinhPhi += (long) (int) row.get("KinhPhi");
                ja.put(obj);
            }

            return new JSONObject().put("danhmuc_detai_list", ja).put("tong_doanh_thu", totalKinhPhi).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/gettangtruongkhcn")
    public String getTangTruongKHCN(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            return kce.getTangTruongKHCN().put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/listbaibao")
    public String listBaiBao(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int year = jin.has("year") ? jin.getInt("year") : 0;
            int type = jin.has("loaitapchi") ? jin.getInt("loaitapchi") : 0;
            int journalId = jin.has("tapchi") ? jin.getInt("tapchi") : 0;
            String author = jin.has("tentacgia") ? jin.getString("tentacgia") : "";

            String sql = "select * from tbl_baibao where (IsDeleted=0 or IsDeleted Is NULL)";
            List<Object> params = new ArrayList<>();
            if (type > 0) { sql += " and LoaiTapChi=?"; params.add(type); }
            if (year > 0) { sql += " and NamXuatBan=?"; params.add(year); }
            if (journalId > 0) { sql += " and TapChi=?"; params.add(journalId); }
            if (!author.isEmpty()) { sql += " and UPPER(TacGiaPTIT) LIKE UPPER(?)"; params.add("%" + author + "%"); }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            JSONArray ja = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", row.get("ID"));
                obj.put("namxuatban", row.get("NamXuatBan"));
                obj.put("tentapchi", kce.getDanhMucTapChi((int) row.get("TapChi"))); // Simplified for now
                obj.put("tenbaibao", row.get("TenBaiBao"));
                obj.put("tacgiaptit", row.get("TacGiaPTIT"));
                obj.put("kinhphihotro", row.get("KinhPhiHoTro"));
                ja.put(obj);
            }
            return new JSONObject().put("baibao_list", ja).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
