package com.org;

import java.text.SimpleDateFormat;
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
@RequestMapping("/org")
public class OrgService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private OrgExtend orgExtend;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    @PostMapping("/getorgtree")
    public String getOrgTree(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int parentId = jin.getInt("parent_id");
            JSONArray orgArr = new JSONArray();
            JSONArray orgLeaves = new JSONArray();
            orgExtend.fn_loop_org_all(parentId, orgArr, orgLeaves);

            return new JSONObject().put("org_tree", orgArr).put("org_leaves", orgLeaves).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":800, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/deleteorg")
    public String deleteOrg(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int orgId = jin.getInt("org_id");
            fn_del_sub_org(orgId);
            jdbcTemplate.update("update tbl_org set IsDeleted=1 where ID=?", orgId);

            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    private void fn_del_sub_org(int parentId) {
        List<Integer> childIds = jdbcTemplate.queryForList("select ID from tbl_org where ParentID =?", Integer.class, parentId);
        for (Integer childId : childIds) {
            fn_del_sub_org(childId);
            jdbcTemplate.update("update tbl_org set IsDeleted=1 where ID=?", childId);
        }
    }

    @PostMapping("/updateorg")
    public String updateOrg(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            int orgId = jin.getInt("org_id");

            if (jin.has("org_name")) jdbcTemplate.update("Update tbl_org set Name=? where ID=?", jin.getString("org_name"), orgId);
            if (jin.has("org_url")) jdbcTemplate.update("Update tbl_org set Url=? where ID=?", jin.getString("org_url"), orgId);
            if (jin.has("org_logo")) jdbcTemplate.update("Update tbl_org set Logo=? where ID=?", jin.getString("org_logo"), orgId);
            if (jin.has("org_hotline")) jdbcTemplate.update("update TBL_ORG set Hotline=? where ID=?", jin.getString("org_hotline"), orgId);
            if (jin.has("org_email")) jdbcTemplate.update("update TBL_ORG set Email=? where ID=?", jin.getString("org_email"), orgId);
            if (jin.has("order_id")) jdbcTemplate.update("update TBL_ORG set OrderID=? where ID=?", jin.getInt("order_id"), orgId);

            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
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

    @PostMapping("/createorg")
    public String createOrg(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int parentId = jin.has("parent_org_id") ? jin.getInt("parent_org_id") : 0;
            String name = jin.getString("org_name");
            String time = dateFormat.format(new Date());

            jdbcTemplate.update("insert into tbl_org (Name,CreatedBy,UrbanID,CreatedTime,ParentID) values(?,?,?,?,?)",
                    name, sst.UserID, sst.UrbanID, time, parentId);

            int orgId = jdbcTemplate.queryForObject("select max(ID) from TBL_ORG", Integer.class);

            if (jin.has("org_url")) jdbcTemplate.update("update TBL_ORG set Url=? where ID=?", jin.getString("org_url"), orgId);
            if (jin.has("org_logo")) jdbcTemplate.update("update TBL_ORG set Logo=? where ID=?", jin.getString("org_logo"), orgId);
            if (jin.has("org_hotline")) jdbcTemplate.update("update TBL_ORG set Hotline=? where ID=?", jin.getString("org_hotline"), orgId);
            if (jin.has("org_email")) jdbcTemplate.update("update TBL_ORG set Email=? where ID=?", jin.getString("org_email"), orgId);
            if (jin.has("order_id")) jdbcTemplate.update("update TBL_ORG set OrderID=? where ID=?", jin.getInt("order_id"), orgId);

            return "{\"code\":200}";
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }

    @PostMapping("/org_users")
    public String orgUsers(@RequestBody String sReq) {
        try {
            JSONObject jin = new JSONObject(sReq);
            struct_session sst = sessionService.getSessionInfo(jin.getString("session_id"));
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int orgId = jin.getInt("org_id");
            return new JSONObject().put("org_users", orgExtend.OrgUsers(orgId)).put("code", 200).toString();
        } catch (Exception e) {
            return "{\"code\":801, \"description\":\"Error\"}";
        }
    }
}
