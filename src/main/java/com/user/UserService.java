package com.user;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.org.OrgExtend;
import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/user")
public class UserService {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private OrgExtend orgExtend;

	@Autowired
	private UserExtend userExtend;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// =====================LOGIN===============================
	@PostMapping("/login")
	public String Login(@RequestBody String sReq) {
		String loginname, userpass, session;
		System.out.println("LOGIN:" + sReq);
		JSONObject jout = new JSONObject();

		try {
			JSONObject jsologin = new JSONObject(sReq);
			loginname = jsologin.getString("user_name");
			userpass = jsologin.getString("user_password");

			String sql = "select * from dbo.tbl_user where email=? and (IsDeleted IS NULL or IsDeleted='0')";
			List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, loginname);

			if (users.isEmpty()) {
				jout.put("code", 710);
				jout.put("description", "No user");
				System.out.println("LOGIN error:" + "No user");
				return jout.toString();
			}

			Map<String, Object> user = users.get(0);
			int user_id = (int) user.get("ID");
			String local_password = (String) user.get("Password");

			// --------------check pw
			if (!userpass.equals(local_password)) {
				jout.put("code", 740);
				jout.put("description", "Invalid password");
				System.out.println("LOGIN error:" + "Invalid password");
				return jout.toString();
			}

			// create new session
			session = sessionService.createSession(user_id);

			if (session != null) {
				jout.put("session_id", session);
				jout.put("user_id", user_id);
				jout.put("full_name", user.get("FullName"));
				jout.put("mobile", user.get("Mobile"));
				jout.put("status", user.get("Status"));
				jout.put("is_admin", user.get("IsAdmin"));
				jout.put("lock_doc", user.get("LockDoc"));
				jout.put("lock_user", user.get("LockUser"));
				jout.put("avatar", user.get("Avatar"));

				int type = (int) user.get("type");
				jout.put("type", type);

				if (type == 0) {
					JSONObject joOrg = orgExtend.memberOrg(user_id);
					if (joOrg.length() == 0) {
						return "{\"code\":" + 802 + ", \"description\":\"" + "Membership not found!" + "\"}";
					}

					jout.put("org_id", joOrg.getInt("org_id"));
					jout.put("org_name", joOrg.getString("org_name"));
					jout.put("org_type", joOrg.getInt("type"));
				} else {
					jout.put("org_id", -1);
					jout.put("org_name", "Kiểm định ngoài");
					jout.put("org_type", 0);
				}

				jout.put("code", 200);
			} else {
				jout.put("code", 500);
				jout.put("description", "Internal error");
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}

		System.out.println("LOGIN response:" + jout.toString());
		return jout.toString();
	}

	// ========================LOG OUT===============================
	@PostMapping("/logout")
	public String logOut(@RequestBody String sReq) {
		System.out.println("Logout:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);

			if (sst == null) {
				return "{\"code\":" + 200 + ", \"description\":\"" + "Logout khi ở trạng thái chưa login" + "\"}";
			}

			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
			Date date = new Date();
			jdbcTemplate.update("update dbo.tbl_session set isdeleted=1, DeleteTime=? where sessionid=?", 
					dateFormat.format(date), session_id);
			
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("Logout response:" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/listuser")
	public String listUser(@RequestBody String sReq) {
		System.out.println("----------listUser:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			String sql = "select a.*, c.ID as org_id, c.Code as org_code, c.Name as org_name from TBL_USER a "
					+ " INNER JOIN TBL_ORG_MEMBER b on b.MEMBER_ID = a.ID "
					+ " LEFT JOIN TBL_ORG c on c.ID = b.ORG_ID "
					+ " where (a.IsDeleted is null or a.IsDeleted='0')"
					+ " order by STATUS desc";
			
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

			for (Map<String, Object> row : rows) {
				int type = (int) row.get("Type");
				if (type == 2) continue;

				JSONObject obj = new JSONObject();
				obj.put("id", row.get("ID"));
				obj.put("full_name", row.get("Fullname"));
				obj.put("email", row.get("Email"));
				obj.put("mobile", row.get("Mobile"));
				obj.put("avatar", row.get("Avatar"));
				obj.put("is_admin", row.get("IsAdmin"));
				obj.put("lock_doc", row.get("LockDoc"));
				obj.put("lock_user", row.get("LockUser"));
				obj.put("type", type);

				if (type == 0) {
					obj.put("type_name", "HV");
					obj.put("org_id", row.get("org_id"));
					obj.put("org_name", row.get("org_name"));
				} else if (type == 1) {
					obj.put("type_name", "Tổ chức ngoài");
					obj.put("org_id", -1);
					obj.put("org_name", "Tổ chức ngoài");
				}
				obj.put("status", String.valueOf(row.get("Status")));
				jaout.put(obj);
			}

			jout.put("user_list", jaout);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listUser):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/listagent")
	public String listAgent(@RequestBody String sReq) {
		System.out.println("----------listAgent:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			String sql = "select * from TBL_USER where Type=0 and (IsDeleted is null or IsDeleted='0')";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				int id = (int) row.get("ID");
				int orgId = (int) row.get("OrgID");
				obj.put("id", id);
				obj.put("full_name", row.get("Fullname"));
				obj.put("org_id", orgId);
				obj.put("org_name", userExtend.fn_org_name(orgId));
				jaout.put(obj);
			}
			jout.put("user_list", jaout);
			jout.put("code", 200);

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listAgent):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/createuser")
	public String CreateUser(@RequestBody String sReq) {
		System.out.println("CreateUser:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String full_name = jsonobjReq.getString("full_name");
			String email = jsonobjReq.getString("email");
			String password = jsonobjReq.getString("password");
			int type = jsonobjReq.getInt("type");
			int org_id = type == 0 ? jsonobjReq.getInt("org_id") : -1;
			String mobile = jsonobjReq.has("mobile") ? jsonobjReq.getString("mobile") : "";

			int user_id = userExtend.RegisterUser(full_name, email, password, mobile, type);
			System.out.println("user_id = " + user_id);
			if (user_id == -1) {
				return "{\"code\":" + 9999 + ", \"description\":\"" + "Error while registering user" + "\"}";
			}
			if (orgExtend.addMember(user_id, org_id) < 0) {
				return "{\"code\":" + 9999 + ", \"description\":\"" + "Error while registering member" + "\"}";
			}

			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(CreateUser):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/deleteuser")
	public String DeleteUser(@RequestBody String sReq) {
		System.out.println("DeleteUser:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int deleted_user_id = jsonobjReq.getInt("user_id");
			jdbcTemplate.update("update dbo.tbl_user set IsDeleted=1 where ID=?", deleted_user_id);

			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(DeleteUser):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/deactivate")
	public String DeactivateUser(@RequestBody String sReq) {
		System.out.println("DeactivateUser:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int user_id = jsonobjReq.getInt("user_id");
			jdbcTemplate.update("update dbo.tbl_user set Status = 0 where ID=?", user_id);

			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(DeactivateUser):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/reactivate")
	public String ReactivateUser(@RequestBody String sReq) {
		System.out.println("ReactivateUser:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			int user_id = jsonobjReq.getInt("user_id");
			jdbcTemplate.update("update dbo.tbl_user set Status = 1 where ID=?", user_id);

			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(ReactivateUser):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/lisalltuser")
	public String listAllUser(@RequestBody String sReq) {
		System.out.println("----------listAllUser:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";

			String sql = "select * from TBL_USER where (IsDeleted is null or IsDeleted='0')";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

			for (Map<String, Object> row : rows) {
				int type = (int) row.get("Type");
				JSONObject obj = new JSONObject();
				obj.put("id", row.get("ID"));
				obj.put("full_name", row.get("Fullname"));
				obj.put("email", row.get("Email"));
				obj.put("mobile", row.get("Mobile"));
				obj.put("avatar", row.get("Avatar"));
				obj.put("type", type);

				if (type == 0) obj.put("type_name", "Nhân viên");
				else if (type == 1) obj.put("type_name", "Admin");
				else if (type == 2) obj.put("type_name", "System Admin");
				else if (type == 3) obj.put("type_name", "Giám sát");
				else obj.put("type_name", "Unknown(" + type + ")");

				int orgId = (int) row.get("OrgID");
				obj.put("org_id", orgId);
				obj.put("org_name", userExtend.fn_org_name(orgId));
				jaout.put(obj);
			}

			jout.put("user_list", jaout);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(listAllUser):" + jout.toString());
		return jout.toString();
	}
}

