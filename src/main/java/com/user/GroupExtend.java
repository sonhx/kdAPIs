package com.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GroupExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public JSONArray myGroups(int user_id, int kd_id) {
        JSONArray jsGroups = new JSONArray();
        String sql = "SELECT b.GROUP_ID, b.IS_LEADER, c.group_name, e.id as kd_id "
                   + " FROM TBL_GROUP_MEMBER b "
                   + " INNER JOIN TBL_GROUP c ON c.ID = b.GROUP_ID "
                   + " INNER JOIN TBL_Kiemdinh e on e.ID = c.KD_ID "
                   + " WHERE b.MEMBER_ID = ? and (b.IsDeleted is null or b.IsDeleted = 0)";
        List<Object> params = new ArrayList<>();
        params.add(user_id);
        if (kd_id != -1) {
            sql += " AND c.KD_ID = ?";
            params.add(kd_id);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            for (Map<String, Object> row : rows) {
                JSONObject joGroup = new JSONObject();
                joGroup.put("group_id", row.get("GROUP_ID"));
                joGroup.put("group_name", row.get("group_name"));
                joGroup.put("kd_id", row.get("kd_id"));
                joGroup.put("is_leader", row.get("IS_LEADER"));
                jsGroups.put(joGroup);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsGroups;
    }

    /**
     * Check if a user is a leader of a specific group
     */
    public boolean isLeaderOf(int user_id, int group_id) {
        try {
            String sql = "SELECT COUNT(*) FROM TBL_GROUP_MEMBER "
                       + "WHERE MEMBER_ID = ? AND GROUP_ID = ? AND IS_LEADER = 1 "
                       + "AND (IsDeleted IS NULL OR IsDeleted = 0)";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, user_id, group_id);
            return count != null && count > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get groups where user is a leader
     */
    public JSONArray myLeaderGroups(int user_id, int kd_id) {
        JSONArray jsGroups = new JSONArray();
        String sql = "SELECT b.GROUP_ID, c.group_name, e.id as kd_id "
                   + " FROM TBL_GROUP_MEMBER b "
                   + " INNER JOIN TBL_GROUP c ON c.ID = b.GROUP_ID "
                   + " INNER JOIN TBL_Kiemdinh e on e.ID = c.KD_ID "
                   + " WHERE b.MEMBER_ID = ? AND b.IS_LEADER = 1 "
                   + " AND (b.IsDeleted IS NULL OR b.IsDeleted = 0)";
        List<Object> params = new ArrayList<>();
        params.add(user_id);
        if (kd_id != -1) {
            sql += " AND c.KD_ID = ?";
            params.add(kd_id);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            for (Map<String, Object> row : rows) {
                JSONObject joGroup = new JSONObject();
                joGroup.put("group_id", row.get("GROUP_ID"));
                joGroup.put("group_name", row.get("group_name"));
                joGroup.put("kd_id", row.get("kd_id"));
                jsGroups.put(joGroup);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsGroups;
    }

    /**
     * Get the group_id for a member (user_id) in a given kd_id context.
     * Returns -1 if not a member of any group.
     */
    public int memberGroupId(int user_id, int kd_id) {
        try {
            String sql = "SELECT TOP 1 b.GROUP_ID FROM TBL_GROUP_MEMBER b "
                       + "INNER JOIN TBL_GROUP c ON c.ID = b.GROUP_ID "
                       + "WHERE b.MEMBER_ID = ? AND c.KD_ID = ? "
                       + "AND (b.IsDeleted IS NULL OR b.IsDeleted = 0)";
            List<Integer> ids = jdbcTemplate.queryForList(sql, Integer.class, user_id, kd_id);
            return ids.isEmpty() ? -1 : ids.get(0);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
