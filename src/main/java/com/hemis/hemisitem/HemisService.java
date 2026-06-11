package com.hemis.hemisitem;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.session.SessionService;
import com.session.struct_session;

@RestController("hemisServiceV2")
@RequestMapping("/hemis_v2")
public class HemisService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private HemisExtend hemisExtend;

    @PostMapping("/children_item")
    public String childrenItemAPI(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int item_id = jin.getInt("item_id");
            jout.put("children", fn_get_children(item_id));
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800}";
        }
        return jout.toString();
    }

    public JSONArray fn_get_children(int parent_item_id) {
        JSONArray result_arr = new JSONArray();
        String sql = "select * from dbo.tbl_hemis_item where ParentID=? and (IsDeleted is null or IsDeleted='0')";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parent_item_id);
        for (Map<String, Object> row : rows) {
            JSONObject c = new JSONObject();
            c.put("id", row.get("ID"));
            c.put("label", row.get("NAME"));
            c.put("name", row.get("NAME"));
            c.put("item_level", row.get("ITEM_LEVEL"));
            result_arr.put(c);
        }
        return result_arr;
    }

    @PostMapping("/item_tree")
    public String getItemTreeAPI(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            if (sessionService.getSessionInfo(jin.getString("session_id")) == null) return "{\"code\":700}";

            JSONArray hemisItemArr = new JSONArray();
            fn_loop_hemis_item_all(0, hemisItemArr);
            jout.put("item_tree", hemisItemArr);
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800}";
        }
        return jout.toString();
    }

    private void fn_loop_hemis_item_all(int root_item_id, JSONArray result_arr) {
        String sql = "select * from dbo.tbl_hemis_item where ParentID=? and (IsDeleted is null or IsDeleted='0')";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_item_id);
        for (Map<String, Object> row : rows) {
            JSONObject c = new JSONObject();
            int item_id = (int) row.get("ID");
            c.put("id", item_id);
            c.put("label", row.get("NAME"));
            c.put("name", row.get("NAME"));
            c.put("item_level", row.get("ITEM_LEVEL"));
            
            JSONArray sarr = new JSONArray();
            fn_loop_hemis_item_all(item_id, sarr);
            c.put("children", sarr);
            result_arr.put(c);
        }
    }

    @PostMapping("/item_tree_flat")
    public String getItemTreeFlatAPI(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            int item_id = jin.optInt("item_id", 0);
            JSONArray hemisItemArr = new JSONArray();
            fn_loop_hemis_item_all_flat(item_id, hemisItemArr);
            jout.put("item_tree", hemisItemArr);
            jout.put("code", 200);
        } catch (JSONException e) {
            return "{\"code\":800}";
        }
        return jout.toString();
    }

    private void fn_loop_hemis_item_all_flat(int root_item_id, JSONArray result_arr) {
        String sql = "select a.*, c.STATUS, d.NAME as status_name , b.org_id, e.Name  as org_name  from dbo.tbl_hemis_item a "
                + " left join TBL_HEMIS_ASSIGNMENT b on b.ITEM_ID = a.ID "
                + " left join TBL_HEMIS_STATUS c on c.ASSIGNMENT_ID = b.ID "
                + " left join DEF_HEMIS_STATUS d on d.ID = c.STATUS "
                + " left join TBL_ORG e on e.ID = b.ORG_ID "
                + " where a.ParentID = ? and (a.IsDeleted is null or a.IsDeleted = 0)";
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_item_id);
        for (Map<String, Object> row : rows) {
            JSONObject c = new JSONObject();
            int item_id = (int) row.get("ID");
            c.put("id", item_id);
            c.put("parent_id", root_item_id);
            c.put("label", row.get("NAME"));
            c.put("name", row.get("NAME"));
            c.put("item_level", row.get("ITEM_LEVEL"));
            
            if (row.get("org_id") != null) {
                c.put("org_id", row.get("org_id"));
                c.put("org_name", row.get("org_name"));
            }
            if (row.get("STATUS") != null) {
                c.put("status", row.get("STATUS"));
                c.put("status_name", row.get("status_name"));
            }
            result_arr.put(c);
            fn_loop_hemis_item_all_flat(item_id, result_arr);
        }
    }

    @PostMapping("/assign")
    public String assignFillingFormApi(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700}";

            hemisExtend.assignFillingForm(jin.getInt("item_id"), jin.getInt("org_id"), sst.UserID);
            return new JSONObject().put("code", 200).put("description", "Thành công").toString();
        } catch (JSONException e) {
            return "{\"code\":800}";
        }
    }
}
