package com.org;

import java.util.List;
import java.util.Map;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrgExtend {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public void fn_loop_org_all(int root_org_id, JSONArray result_arr, JSONArray result_leaves) {
		try {
			String sql = "select * from dbo.tbl_org where ParentID=? and (IsDeleted is null or IsDeleted='0') order by OrderID asc";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_org_id);

			for (Map<String, Object> row : rows) {
				JSONObject c = new JSONObject();
				int org_id = (int) row.get("ID");

				c.put("id", org_id);
				c.put("order_id", row.get("OrderID"));
				c.put("code", row.get("Code"));
				c.put("label", row.get("Name"));
				c.put("url", row.get("Url"));
				c.put("logo", row.get("Logo"));
				c.put("hotline", row.get("Hotline"));
				c.put("email", row.get("Email"));

				JSONArray sarr = new JSONArray();
				fn_loop_org_all(org_id, sarr, result_leaves);
				c.put("children", sarr);

				if (sarr.length() == 0) {
					c.put("is_leaf", true);
					result_leaves.put(c);
				} else {
					c.put("is_leaf", false);
				}
				result_arr.put(c);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public int addMember(int user_id, int org_id) {
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String sql = "insert into TBL_ORG_MEMBER (MEMBER_ID, ORG_ID, Created_Time, IsDeleted) values (?, ?, ?, 0)";
		try {
			return jdbcTemplate.update(sql, user_id, org_id, dateFormat.format(date));
		} catch (Exception e) {
			e.printStackTrace();
			return -2;
		}
	}

	public JSONObject memberOrg(int member_id) {
		JSONObject joOrg = new JSONObject();
		String sql = "SELECT o.* FROM [dbo].[TBL_ORG_MEMBER] m INNER JOIN TBL_ORG o ON o.ID = m.ORG_ID WHERE m.MEMBER_ID = ? AND (m.IsDeleted is null or m.IsDeleted =0)";
		try {
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, member_id);
			if (!rows.isEmpty()) {
				Map<String, Object> row = rows.get(0);
				joOrg.put("org_id", row.get("ID"));
				joOrg.put("org_name", row.get("Name"));
				joOrg.put("type", row.get("Type"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return joOrg;
	}

	public JSONArray OrgUsers(int org_id) {
		JSONArray jsaUsers = new JSONArray();
		String sql = "SELECT a.Created_Time, b.ID, b.Fullname, b.LoginName, b.Email FROM TBL_ORG_MEMBER a INNER JOIN TBL_USER b on b.ID = a.MEMBER_ID where a.ORG_ID = ? and (a.IsDeleted is null or a.IsDeleted = 0) and (b.IsDeleted is null or b.IsDeleted = 0)";
		try {
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, org_id);
			for (Map<String, Object> row : rows) {
				JSONObject jo = new JSONObject();
				jo.put("id", row.get("ID"));
				jo.put("created_time", row.get("Created_Time"));
				jo.put("full_name", row.get("Fullname"));
				jo.put("login_name", row.get("LoginName"));
				jo.put("email", row.get("Email"));
				jsaUsers.put(jo);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return jsaUsers;
	}
}
