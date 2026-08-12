package com.user;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.session.SessionService;
import com.session.struct_session;
import com.minhchung.MCExtend;

@RestController
@RequestMapping("/group")
public class GroupController {

    @Autowired
    private SessionService sessionService;
    
    @Autowired
    @Qualifier("jdbcTemplate")
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate evidenceJdbcTemplate;

    @Autowired
    private UserExtend userExtend;

    @Autowired
    private MCExtend mcExtend;

    private static boolean isNumericStr(String str) {
        if (str == null || str.isBlank()) return false;
        try {
            Long.parseLong(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String getOrCreateUserId(JSONObject mObj) {
        if (mObj.has("email") && !mObj.getString("email").trim().isEmpty()) {
            String email = mObj.getString("email").trim();
            String name = mObj.optString("ten_day_du", "");
            if (name.isEmpty() && mObj.has("FullName")) {
                name = mObj.getString("FullName");
            }
            if (name.isEmpty() && mObj.has("uName")) {
                name = mObj.getString("uName");
            }
            if (name.isEmpty()) {
                name = email.split("@")[0];
            }

            try {
                List<String> ids = jdbcTemplate.queryForList(
                    "SELECT CAST(ID AS VARCHAR(100)) FROM users WHERE Email = ? AND (IsDeleted IS NULL OR IsDeleted = '0')",
                    String.class,
                    email
                );
                if (!ids.isEmpty()) {
                    String existingId = ids.get(0);
                    if (isNumericStr(existingId)) {
                        return existingId.trim();
                    } else {
                        // Existing user in users table has non-numeric ID (e.g. 'Bùi Anh Tu?n').
                        // Register/generate a valid numeric ID and repair users.ID.
                        String newId = userExtend.RegisterUser(null, email, "123456", "", 4);
                        if (newId != null && isNumericStr(newId)) {
                            jdbcTemplate.update("UPDATE users SET ID = ? WHERE Email = ?", newId, email);
                            return newId;
                        }
                    }
                } else {
                    String newId = userExtend.RegisterUser(name, email, "123456", "", 4);
                    return newId;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mObj.has("id")) {
            String idStr = mObj.get("id").toString().trim();
            if (isNumericStr(idStr)) {
                return idStr;
            }
        }
        return null;
    }

    @PostMapping("/listgroups")
    public String listGroups(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int kd_id = jin.has("kd_id") ? jin.getInt("kd_id") : -1;
            int user_type = sst.UserType;
            int user_id = sst.UserID;

            String sql = "SELECT a.ID, a.group_name, a.kd_id, a.CreatedBy " +
                         "FROM TBL_GROUP a " +
                         "WHERE (a.IsDeleted IS NULL OR a.IsDeleted = 0)";
            
            List<Object> params = new java.util.ArrayList<>();
            if (kd_id != -1) {
                sql += " AND a.kd_id = ?";
                params.add(kd_id);
            }

            if (user_type == 4) {
                // Non-admin: only show groups where this user is a leader
                sql += " AND a.ID IN (SELECT GROUP_ID FROM TBL_GROUP_MEMBER WHERE MEMBER_ID = ? AND IS_LEADER = 1 AND (IsDeleted IS NULL OR IsDeleted = 0))";
                params.add(user_id);
            }

            List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, params.toArray());

            // Collect creator IDs to fetch creator names from Core DB
            List<Object> creatorIds = rows.stream()
                                          .map(row -> row.get("CreatedBy"))
                                          .filter(java.util.Objects::nonNull)
                                          .distinct()
                                          .collect(Collectors.toList());

            Map<String, String> creatorNameMap = new java.util.HashMap<>();
            if (!creatorIds.isEmpty()) {
                List<String> creatorIdStrs = creatorIds.stream()
                                                       .map(String::valueOf)
                                                       .collect(Collectors.toList());
                String sqlUsers = "SELECT CAST(p.ID AS VARCHAR(100)) as user_id, p.fullname as creator_name " +
                                  "FROM personnel p " +
                                  "WHERE p.isDeleted = 0 " +
                                  "AND CAST(p.ID AS VARCHAR(100)) IN (" +
                                  creatorIdStrs.stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
                List<Map<String, Object>> userRows = jdbcTemplate.queryForList(sqlUsers, creatorIdStrs.toArray());
                for (Map<String, Object> uRow : userRows) {
                    if (uRow.get("user_id") != null && uRow.get("creator_name") != null) {
                        creatorNameMap.put(String.valueOf(uRow.get("user_id")), (String) uRow.get("creator_name"));
                    }
                }
            }

            JSONArray jaGroups = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                jo.put("id", row.get("ID"));
                jo.put("group_name", row.get("group_name"));
                jo.put("kd_id", row.get("kd_id"));
                Object creatorId = row.get("CreatedBy");
                String cKey = creatorId != null ? String.valueOf(creatorId) : "";
                jo.put("creator", creatorNameMap.getOrDefault(cKey, ""));
                jaGroups.put(jo);
            }

            jout.put("group_list", jaGroups);
            jout.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

	/*@PostMapping("/members")
	public String listGroupMembers(@RequestBody String sReq) {
	    JSONObject jout = new JSONObject();
	    try {
	        JSONObject jin = new JSONObject(sReq);
	        String session_id = jin.getString("session_id");
	        struct_session sst = sessionService.getSessionInfo(session_id);
	        if (sst == null) {
	            return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
	        }
	
	        int group_id = jin.getInt("group_id");
	        String sql = "SELECT b.ID as member_id, b.Fullname as ten_day_du, b.Email, b.Mobile, a.IS_LEADER " +
	                     "FROM TBL_GROUP_MEMBER a " +
	                     "INNER JOIN users b ON b.ID = a.MEMBER_ID " +
	                     "WHERE a.GROUP_ID = ? AND (a.IsDeleted IS NULL OR a.IsDeleted = 0)";
	        
	        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, group_id);
	        JSONArray jaMembers = new JSONArray();
	        for (Map<String, Object> row : rows) {
	            JSONObject jo = new JSONObject();
	            jo.put("member_id", row.get("member_id"));
	            jo.put("ten_day_du", row.get("ten_day_du"));
	            jo.put("email", row.get("Email"));
	            jo.put("mobile", row.get("Mobile"));
	            jo.put("is_leader", row.get("IS_LEADER"));
	            jaMembers.put(jo);
	        }
	
	        jout.put("members", jaMembers);
	        jout.put("code", 200);
	    } catch (Exception e) {
	        e.printStackTrace();
	        return "{\"code\":800, \"description\":\"JSON/DB error\"}";
	    }
	    return jout.toString();
	}
	*/
    
    @PostMapping("/members")
    public String listGroupMembers(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("group_id");

            // Step 1: Query group members from evidence DB
            String sqlMembers = "SELECT MEMBER_ID, IS_LEADER " +
                                "FROM TBL_GROUP_MEMBER " +
                                "WHERE GROUP_ID = ? AND (IsDeleted IS NULL OR IsDeleted = 0)";
            List<Map<String, Object>> memberRows = evidenceJdbcTemplate.queryForList(sqlMembers, group_id);

            if (memberRows.isEmpty()) {
                jout.put("members", new JSONArray());
                jout.put("code", 200);
                return jout.toString();
            }

            // Step 2: Collect member IDs as Strings
            List<String> memberIds = memberRows.stream()
                                               .map(row -> String.valueOf(row.get("MEMBER_ID")))
                                               .filter(id -> id != null && !id.equals("null"))
                                               .distinct()
                                               .collect(Collectors.toList());

            // Step 3: Query user info from core DB using CAST to VARCHAR(100)
            String sqlUsers = "SELECT CAST(u.ID AS VARCHAR(100)) as member_id, p.fullname as ten_day_du, u.Email, p.sdtCaNhan as Mobile " +
                              "FROM users u " +
                              "LEFT JOIN personnel p ON (p.emailCanBo = u.Email OR p.email = u.Email) AND p.isDeleted = 0 " +
                              "WHERE CAST(u.ID AS VARCHAR(100)) IN (" +
                              memberIds.stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
            List<Map<String, Object>> userRows = jdbcTemplate.queryForList(sqlUsers, memberIds.toArray());

            // Step 4: Build a lookup map for user info
            Map<String, Map<String, Object>> userMap = userRows.stream()
                    .collect(Collectors.toMap(
                            row -> String.valueOf(row.get("member_id")),
                            row -> row,
                            (existing, replacement) -> existing
                    ));

            // Step 5: Merge results
            JSONArray jaMembers = new JSONArray();
            for (Map<String, Object> memberRow : memberRows) {
                String memberId = String.valueOf(memberRow.get("MEMBER_ID"));
                Map<String, Object> userInfo = userMap.get(memberId);

                JSONObject jo = new JSONObject();
                jo.put("member_id", memberId);
                if (userInfo != null) {
                    jo.put("ten_day_du", userInfo.get("ten_day_du") != null ? userInfo.get("ten_day_du") : "");
                    jo.put("email", userInfo.get("Email") != null ? userInfo.get("Email") : "");
                    jo.put("mobile", userInfo.get("Mobile") != null ? userInfo.get("Mobile") : "");
                }
                jo.put("is_leader", memberRow.get("IS_LEADER"));
                jaMembers.put(jo);
            }

            jout.put("members", jaMembers);
            jout.put("code", 200);

        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    
    @PostMapping("/create")
    public String createGroup(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int kd_id = jin.getInt("kd_id");
            String group_name = jin.getString("group_name");
            int group_leader = jin.getInt("group_leader");
            JSONArray members = jin.getJSONArray("members");

            // Insert into TBL_GROUP
            org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
            final String insertGroupSql = "INSERT INTO TBL_GROUP (group_name, kd_id, CreatedBy, CREATEDTIME, IsDeleted) VALUES (?, ?, ?, GETDATE(), 0)";
            final int creatorId = sst.UserID;
            evidenceJdbcTemplate.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(insertGroupSql, java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setNString(1, group_name);
                ps.setInt(2, kd_id);
                ps.setInt(3, creatorId);
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key == null) {
                return "{\"code\":800, \"description\":\"Failed to insert group\"}";
            }
            int groupId = key.intValue();

            // Insert initial KD relation
            if (kd_id > 0) {
                evidenceJdbcTemplate.update("INSERT INTO TBL_GROUP_KD (GROUP_ID, KD_ID, ISDELETED) VALUES (?, ?, 0)", groupId, kd_id);
            }

            // Insert members
            String insertMemberSql = "INSERT INTO TBL_GROUP_MEMBER (GROUP_ID, MEMBER_ID, IS_LEADER, CreatedBy, CREATEDTIME, IsDeleted) VALUES (?, ?, ?, ?, GETDATE(), 0)";
            for (int i = 0; i < members.length(); i++) {
                JSONObject mObj = members.getJSONObject(i);
                String memberId = getOrCreateUserId(mObj);
                if (memberId == null || memberId.isBlank()) {
                    continue;
                }
                int isLeader = mObj.optInt("is_leader", 0);
                evidenceJdbcTemplate.update(insertMemberSql, groupId, memberId, isLeader, creatorId);
            }

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/update")
    public String updateGroup(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("group_id");
            String group_name = jin.getString("group_name");
            int group_leader = jin.getInt("group_leader");

            // Update group name using setNString for unicode support
            evidenceJdbcTemplate.update(
                connection -> {
                    java.sql.PreparedStatement ps = connection.prepareStatement("UPDATE TBL_GROUP SET group_name = ? WHERE ID = ?");
                    ps.setNString(1, group_name);
                    ps.setInt(2, group_id);
                    return ps;
                }
            );

            // Reset leaders for the group
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_MEMBER SET IS_LEADER = 0 WHERE GROUP_ID = ?", group_id);
            
            // Set new leader
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_MEMBER SET IS_LEADER = 1 WHERE GROUP_ID = ? AND MEMBER_ID = ?", group_id, group_leader);

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/change_leader")
    public String changeLeader(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("group_id");
            int group_leader = jin.getInt("group_leader");

            // Reset leaders
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_MEMBER SET IS_LEADER = 0 WHERE GROUP_ID = ?", group_id);
            
            // Set new leader
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_MEMBER SET IS_LEADER = 1 WHERE GROUP_ID = ? AND MEMBER_ID = ?", group_id, group_leader);

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/delete")
    public String deleteGroup(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("id");

            // Mark group and members deleted
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP SET IsDeleted = 1 WHERE ID = ?", group_id);
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_MEMBER SET IsDeleted = 1 WHERE GROUP_ID = ?", group_id);
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_KD SET ISDELETED = 1 WHERE GROUP_ID = ?", group_id);

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/remove_member")
    public String removeMember(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("group_id");
            int member_id = jin.getInt("member_id");

            // Remove member from TBL_GROUP_MEMBER
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_MEMBER SET IsDeleted = 1 WHERE GROUP_ID = ? AND MEMBER_ID = ?", group_id, member_id);
            
            // Try removing from TBL_GROUP_KD (using the member_id parameter as TBL_GROUP_KD primary key ID)
            evidenceJdbcTemplate.update("UPDATE TBL_GROUP_KD SET ISDELETED = 1 WHERE GROUP_ID = ? AND ID = ?", group_id, member_id);

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/add_members")
    public String addMembers(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("group_id");
            JSONArray members = jin.getJSONArray("members");
            int creatorId = sst.UserID;

            for (int i = 0; i < members.length(); i++) {
                JSONObject mObj = members.getJSONObject(i);
                String memberId = getOrCreateUserId(mObj);
                if (memberId == null || memberId.isBlank()) {
                    continue;
                }

                // Check if already in group (even if deleted)
                String checkSql = "SELECT COUNT(*) FROM TBL_GROUP_MEMBER WHERE GROUP_ID = ? AND MEMBER_ID = ?";
                Integer count = evidenceJdbcTemplate.queryForObject(checkSql, Integer.class, group_id, memberId);
                if (count != null && count > 0) {
                    evidenceJdbcTemplate.update("UPDATE TBL_GROUP_MEMBER SET IsDeleted = 0, IS_LEADER = 0, UpdatedBy = ?, UPDATEDTIME = GETDATE() WHERE GROUP_ID = ? AND MEMBER_ID = ?", creatorId, group_id, memberId);
                } else {
                    evidenceJdbcTemplate.update("INSERT INTO TBL_GROUP_MEMBER (GROUP_ID, MEMBER_ID, IS_LEADER, CreatedBy, CREATEDTIME, IsDeleted) VALUES (?, ?, 0, ?, GETDATE(), 0)", group_id, memberId, creatorId);
                }
            }

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/group_kds")
    public String listGroupKds(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("group_id");
            String sql = "SELECT "
            		+ "	a.ID AS member_id, "
            		+ "	a.KD_ID, "
            		+ "	c.ten AS ten_ct, "
            		+ "	d.ten AS loai_hinh, "
            		+ "	b.cycle, "
            		+ "	b.FromYear, "
            		+ "	b.ToYear,"
            		+ " e.ten as  ten_tc  "
            		+ "FROM "
            		+ "	TBL_GROUP_KD a "
            		+ "	INNER JOIN TBL_Kiemdinh b ON b.ID = a.KD_ID "
            		+ "	INNER JOIN TBL_Nganh_daotao c ON c.ID = b.CT_ID "
            		+ "	INNER JOIN DEF_LOAIHINH_KD d ON d.ID = b.loai_hinh_id  "
            		+ " INNER JOIN TBL_Standard e on e.ID= b.standard_id "
            		+ "WHERE "
            		+ "	a.GROUP_ID = ?  "
            		+ "	AND ( a.ISDELETED IS NULL OR a.ISDELETED = 0 )";
//            System.out.println("SQL: " + sql);

            List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, group_id);
            JSONArray jaKDs = new JSONArray();
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                jo.put("member_id", row.get("member_id"));
                jo.put("kd_id", row.get("KD_ID"));
                jo.put("ten_ct", row.get("ten_ct"));
                jo.put("loai_hinh", row.get("loai_hinh"));
                jo.put("ten_tc", row.get("ten_tc"));
                jo.put("cycle", row.get("cycle"));
                jo.put("FromYear", row.get("FromYear"));
                jo.put("ToYear", row.get("ToYear"));
                jaKDs.put(jo);
            }

            jout.put("list_group_kd", jaKDs);
            jout.put("code", 200);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    @PostMapping("/assign_kd")
    public String assignKd(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) {
                return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
            }

            int group_id = jin.getInt("group_id");
            int kd_id = jin.getInt("kd_id");

            // Check if already assigned (even if deleted)
            String checkSql = "SELECT COUNT(*) FROM TBL_GROUP_KD WHERE GROUP_ID = ? AND KD_ID = ?";
            Integer count = evidenceJdbcTemplate.queryForObject(checkSql, Integer.class, group_id, kd_id);
            if (count != null && count > 0) {
                evidenceJdbcTemplate.update("UPDATE TBL_GROUP_KD SET ISDELETED = 0 WHERE GROUP_ID = ? AND KD_ID = ?", group_id, kd_id);
            } else {
                evidenceJdbcTemplate.update("INSERT INTO TBL_GROUP_KD (GROUP_ID, KD_ID, ISDELETED) VALUES (?, ?, 0)", group_id, kd_id);
            }

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    /**
     * Admin assigns a frame branch to a group.
     */
    @PostMapping("/assign_frame")
    public String assignFrame(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            // Only admin (UserType != 4) can assign to group
            if (sst.UserType == 4) {
                return "{\"code\":403, \"description\":\"Bạn không có quyền phân công cho nhóm. Chỉ Admin mới có quyền này.\"}";
            }

            int f_id = jin.getInt("f_id");
            int group_id = jin.getInt("group_id");
            String deadline = jin.getString("deadline");

            // Admin assigns to group only — clears any previous member assignment
            mcExtend.assignFrame2Group(f_id, group_id, -1, deadline);
            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }

    /**
     * Group leader assigns a branch or single proof to a group member.
     */
    @PostMapping("/assign_to_member")
    public String assignToMember(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String session_id = jin.getString("session_id");
            struct_session sst = sessionService.getSessionInfo(session_id);
            if (sst == null) return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";

            int member_id = jin.getInt("member_id");
            String deadline = jin.has("deadline") ? jin.getString("deadline") : null;

            int mc_id = jin.has("mc_id") ? jin.getInt("mc_id") : -1;
            int f_id = jin.has("f_id") ? jin.getInt("f_id") : -1;

            if (mc_id > 0) {
                mcExtend.assignMC2Member(mc_id, member_id, deadline, sst.UserID);
            } else if (f_id > 0) {
                mcExtend.assignFrameProofs2Member(f_id, member_id, deadline);
            } else {
                return "{\"code\":801, \"description\":\"Thiếu f_id hoặc mc_id\"}";
            }

            jout.put("code", 200);
            jout.put("description", "Thành công");
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"code\":800, \"description\":\"JSON/DB error\"}";
        }
        return jout.toString();
    }
}
