package com.campusarea;

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
@RequestMapping("/campusarea")
public class CampusAreaService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private CampusAreaExtend cae;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/getcampusareatree")
    public String getCampusAreaTree(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            JSONObject jout = new JSONObject();
            jout.put("campus_area_tree", cae.getCampusAreaTree(0));
            jout.put("code", 200);
            return jout.toString();
        } catch (Exception e) {
            return "{\"code\":800, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/deletecampusarea")
    public String deleteCampusArea(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            cae.deleteCampusArea(jin.getInt("campus_area_id"));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/updatecampusarea")
    public String updateCampusArea(@RequestBody String sReq) {
        try {
            cae.updateCampusArea(new JSONObject(sReq));
            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"" + e.getMessage() + "\"}";
        }
    }

    @PostMapping("/listcampusarea")
    public String listCampusArea(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID, Ten as name, Url, Logo from TBL_CAMPUS_AREA where (IsDeleted is null or IsDeleted='0')");
            return new JSONObject().put("campus_area_list", new JSONArray(rows)).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/listcampusareafeedback")
    public String listCampusAreaFeedback(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int areaId = jin.has("campus_area_id") ? jin.getInt("campus_area_id") : 0;
            String start = jin.has("begin_date") ? jin.getString("begin_date") : null;
            String end = jin.has("end_date") ? jin.getString("end_date") : null;

            return new JSONObject().put("feedback_list", cae.getFeedbackList(areaId, start, end)).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/thongketaisan")
    public String thongKeTaiSan(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            int areaId = jin.getInt("campus_area_id");
            return new JSONObject().put("asset_stat_list", cae.getAssetStatList(areaId)).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/listloaicampusarea")
    public String listLoaiCampusArea(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Name as name from DEF_CAMPUS_AREA_TYPE where ID>2 and (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("campus_type_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listcampusownertype")
    public String listCampusOwnerType(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Name as name from DEF_CAMPUS_AREA_OWNERTYPE where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("campus_ownertype_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listassettype")
    public String listAssetType(@RequestBody String sReq) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select ID as id, Name as name, Value as value from DEF_CAMPUS_AREA_ASSET_TYPE where (IsDeleted is null or IsDeleted='0')");
        return new JSONObject().put("assettype_list", new JSONArray(rows)).put("code", 200).toString();
    }

    @PostMapping("/listtaisan")
    public String listTaisan(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            int areaId = jin.getInt("campus_area_id");
            int typeId = jin.getInt("asset_type_id");
            String sql = "select * from TBL_CAMPUS_AREA_ASSET where (IsDeleted is null or IsDeleted='0') and CampusAreaID=?";
            if (typeId != 0) sql += " and Loai=" + typeId;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, areaId);
            return new JSONObject().put("asset_list", new JSONArray(rows)).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
