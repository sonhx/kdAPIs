package com.profile;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;
import com.user.UserExtend;
import org.springframework.security.crypto.bcrypt.BCrypt;

@RestController("userProfileController")
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserExtend userExtend;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------- GET MY PROFILE ----------------
    @PostMapping(value = "/getmyprofile", produces = "application/json; charset=UTF-8")
    public String getMyProfile(@RequestBody String sReq) {
        System.out.println("getmyprofile:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String sessionId = jsonobjReq.getString("session_id");

            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Người sử dụng chưa đăng nhập\"}";
            }

            String sql = "select * from dbo.tbl_user where ID=?";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, sst.UserID);
            if (users.isEmpty()) {
                jout.put("code", 710);
                jout.put("description", "No user");
                System.out.println("RES(getmyprofile):" + jout.toString());
                return jout.toString();
            }

            Map<String, Object> user = users.get(0);
            jout.put("full_name", user.get("FullName"));
            jout.put("email", user.get("Email"));
            jout.put("mobile", user.get("Mobile"));
            jout.put("dataimg", user.get("Avatar"));
            jout.put("id", sst.UserID);

            Object orgIdObj = user.get("OrgID");
            int orgId = orgIdObj != null ? (int) orgIdObj : 0;
            jout.put("org_id", orgId);
            String orgName = (orgId != 0) ? userExtend.fn_org_name(orgId) : null;
            if (orgName != null) jout.put("org_name", orgName);

            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        System.out.println("RES(getmyprofile):" + jout.toString());
        return jout.toString();
    }

    // ---------------- UPDATE ACCOUNT ----------------
    @PostMapping(value = "/updateaccount", produces = "application/json; charset=UTF-8")
    public String updateAccount(@RequestBody String sReq) {
        System.out.println("updateaccount:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jo = new JSONObject(sReq);
            int userId = jo.getInt("user_id");

            String fullname = jo.optString("full_name", "");
            String avatar = jo.optString("avatar", "");
            String email = jo.optString("email", "");
            String mobile = jo.optString("mobile", "");
            String code = jo.optString("code", "");
            int preferredRoleId = jo.optInt("preferred_role_id", -1);

            if (!fullname.isEmpty()) {
                jdbcTemplate.update("Update tbl_User set fullName=? where id=?", fullname, userId);
            }
            if (!avatar.isEmpty()) {
                jdbcTemplate.update("Update tbl_user set Avatar=? where id=?", avatar, userId);
            }
            if (!email.isEmpty()) {
                jdbcTemplate.update("Update tbl_User set Email=? where id=?", email, userId);
            }
            if (!mobile.isEmpty()) {
                jdbcTemplate.update("Update tbl_User set Mobile=? where id=?", mobile, userId);
            }
            if (!code.isEmpty()) {
                jdbcTemplate.update("Update tbl_User set Code=? where id=?", code, userId);
            }

            if (preferredRoleId >= 0) {
                String sql = "UPDATE TBL_USER_TYPE SET IsPreferred = CASE WHEN Type = ? THEN 1 ELSE 0 END WHERE UserID =?";
                jdbcTemplate.update(sql, preferredRoleId, userId);
            }

            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        System.out.println("RES(updateaccount):" + jout.toString());
        return jout.toString();
    }

    // ---------------- UPDATE PROFILE (ADMIN) ----------------
    @PostMapping(value = "/updateprofile", produces = "application/json; charset=UTF-8")
    public String updateProfile(@RequestBody String sReq) {
        System.out.println("updateprofile:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String sessionId = jsonobjReq.getString("session_id");
            int userId = jsonobjReq.getInt("user_id");

            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Người sử dụng chưa đăng nhập\"}";
            }
            if (!userExtend.typeExisted(2, sst.UserID)) {
                return "{\"code\":709, \"description\":\"Người dùng không có quyền thực hiện tác vụ này\"}";
            }

            String fullName = jsonobjReq.optString("full_name", "").trim();
            String mobile = jsonobjReq.optString("mobile", null);
            String email = jsonobjReq.optString("email", null);
            String code = jsonobjReq.optString("code", "").trim();
            String dob = jsonobjReq.optString("dob", null);

            List<String> setClauses = new java.util.ArrayList<>();
            List<Object> params = new java.util.ArrayList<>();

            if (!fullName.isEmpty()) {
                setClauses.add("Fullname = ?");
                params.add(fullName);
                if (fullName.lastIndexOf(" ") > 0) {
                    String ten = fullName.substring(fullName.lastIndexOf(" ") + 1);
                    String ho = fullName.substring(0, fullName.lastIndexOf(" "));
                    setClauses.add("FirstName = ?");
                    params.add(ten);
                    setClauses.add("LastName = ?");
                    params.add(ho);
                }
            }
            if (mobile != null) {
                setClauses.add("Mobile = ?");
                params.add(mobile);
            }
            if (email != null) {
                setClauses.add("Email = ?");
                params.add(email);
            }
            if (!code.isEmpty()) {
                setClauses.add("Code = ?");
                params.add(code);
            }
            if (dob != null) {
                setClauses.add("DoB = ?");
                params.add(dob);
            }

            if (!setClauses.isEmpty()) {
                String sql = "update tbl_user set " + String.join(", ", setClauses) + " where ID = ?";
                params.add(userId);
                jdbcTemplate.update(sql, params.toArray());
            }

            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        System.out.println("RES(updateprofile):" + jout.toString());
        return jout.toString();
    }

    // ---------------- CHANGE PASSWORD ----------------
    @PostMapping(value = "/changepassword", produces = "application/json; charset=UTF-8")
    public String changePassword(@RequestBody String sReq) {
        System.out.println("changepassword:" + sReq);
        JSONObject jout = new JSONObject();
        try {
            JSONObject jsonobjReq = new JSONObject(sReq);
            String sessionId = jsonobjReq.getString("session_id");
            String currentPassword = jsonobjReq.has("current_password") ? jsonobjReq.getString("current_password") : 
                                    (jsonobjReq.has("old_password") ? jsonobjReq.getString("old_password") : "");
            String newPassword = jsonobjReq.has("new_password") ? jsonobjReq.getString("new_password") : 
                                 (jsonobjReq.has("password") ? jsonobjReq.getString("password") : "");

            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Người sử dụng chưa đăng nhập\"}";
            }

            String sql = "select * from dbo.tbl_user where ID=?";
            List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, sst.UserID);
            if (users.isEmpty()) {
                jout.put("code", 710);
                jout.put("description", "No user");
                System.out.println("RES(changepassword):" + jout.toString());
                return jout.toString();
            }

            Map<String, Object> user = users.get(0);
            String localHash = (String) user.get("Hash");

            boolean isCurrentPasswordCorrect = false;
            if (localHash != null && !localHash.isEmpty()) {
                try {
                    isCurrentPasswordCorrect = BCrypt.checkpw(currentPassword, localHash);
                } catch (Exception e) {
                    System.out.println("BCrypt check failed: " + e.getMessage());
                }
            }

            if (!isCurrentPasswordCorrect) {
                jout.put("code", 740);
                jout.put("description", "Mật khẩu hiện tại không đúng");
                System.out.println("RES(changepassword):" + jout.toString());
                return jout.toString();
            }

            String hashedNewPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            jdbcTemplate.update("update dbo.tbl_user set Hash = ? where ID = ?", hashedNewPassword, sst.UserID);

            jout.put("code", 200);
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON parse error\"}";
        }
        System.out.println("RES(changepassword):" + jout.toString());
        return jout.toString();
    }
}
