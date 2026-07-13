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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;

import com.org.OrgExtend;
import com.session.SessionService;
import com.session.struct_session;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/user")
public class UserService {

	public static Map<String, JSONObject> slinkTokensMap = new ConcurrentHashMap<>();

	@Autowired
	private SessionService sessionService;

	@Value("${slink.api-key}")
	private String slinkApiKey;

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

			String sql = "select u.*, o.ID as org_id, o.Name as org_name "
					+ "from dbo.tbl_user u "
					+ "left join dbo.tbl_org o on o.ID = u.OrgID and (o.IsDeleted IS NULL or o.IsDeleted='0') "
					+ "where u.email=? and (u.IsDeleted IS NULL or u.IsDeleted='0')";
			List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, loginname);
			
			System.out.println("Found " + users.size() + " user(s) with email: " + loginname);
			System.out.println(users.toString());

			if (users.isEmpty()) {
				jout.put("code", 710);
				jout.put("description", "No user");
				System.out.println("LOGIN error:" + "No user");
				return jout.toString();
			}

			// --------------check pw
			Map<String, Object> user = users.get(0);
			int user_id = (int) user.get("ID");
			String local_hash = (String) user.get("Hash");

			boolean isPasswordCorrect = false;
			if (local_hash != null && !local_hash.isEmpty()) {
				try {
					isPasswordCorrect = BCrypt.checkpw(userpass, local_hash);
				} catch (Exception e) {
					System.out.println("BCrypt check failed: " + e.getMessage());
				}
			}

			if (!isPasswordCorrect) {
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

				int type = (int) user.get("Type");
				jout.put("type", type);

				if (type == 0) {//admin
					//TODO: get org info of admin user
				}
				
				JSONObject DpInfo = userExtend.getUserDepartmentInfo(user_id);
				Object finalDpId = DpInfo.has("dept_id") ? DpInfo.get("dept_id") : null;
				Object finalDpName = DpInfo.has("dept_name") ? DpInfo.get("dept_name") : null;
				Object finalDpCode = DpInfo.has("dept_code") ? DpInfo.get("dept_code") : null;

				if (type != 0 && finalDpId == null) {
					return "{\"code\":" + 802 + ", \"description\":\"" + "Membership not found!" + "\"}";
				}

				jout.put("dept_id", finalDpId != null ? finalDpId : JSONObject.NULL);
				jout.put("dept_name", finalDpName != null ? finalDpName : JSONObject.NULL);
				jout.put("dept_code", finalDpCode != null ? finalDpCode : JSONObject.NULL);

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
			String session_id 	= jsonobjReq.getString("session_id");
			struct_session sst 	= sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			String full_name 	= jsonobjReq.getString("full_name");
			String email 		= jsonobjReq.getString("email");
			String password 	= jsonobjReq.getString("password");
			int type 			= jsonobjReq.getInt("type");
			int org_id 			= type == 0 ? jsonobjReq.getInt("org_id") : -1;
			String mobile 		= jsonobjReq.has("mobile") ? jsonobjReq.getString("mobile") : "";

			int user_id 		= userExtend.RegisterUser(full_name, email, password, mobile, type);
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

	@PostMapping("/updaterole")
	public String UpdateUserRole(@RequestBody String sReq) {
		System.out.println("UpdateUserRole:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			// Check admin rights (Type 1: Admin, Type 2: System Admin)
			String adminCheckSql = "SELECT Type FROM TBL_USER WHERE ID=?";
			Integer adminType = null;
			try {
				adminType = jdbcTemplate.queryForObject(adminCheckSql, Integer.class, sst.UserID);
			} catch (Exception e) {
				// User not found or error
			}
			
			if (adminType == null || (adminType != 1 && adminType != 2)) {
				return "{\"code\":" + 403 + ", \"description\":\"" + "Bạn không có quyền thực hiện tác vụ này" + "\"}";
			}

			int target_user_id = jsonobjReq.getInt("user_id");
			int new_type = jsonobjReq.getInt("type");

			jdbcTemplate.update("UPDATE dbo.tbl_user SET Type = ? WHERE ID = ?", new_type, target_user_id);

			jout.put("code", 200);
			jout.put("description", "Thành công");
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"" + "Lỗi máy chủ: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(UpdateUserRole):" + jout.toString());
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
				Object typeVal = row.get("Type");
				int type = typeVal != null ? ((Number) typeVal).intValue() : 4;
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

				int uId = ((Number) row.get("ID")).intValue();
				JSONObject deptInfo = userExtend.getUserDepartmentInfo(uId);
				
				System.out.println("User ID: " + uId + ", Department Info: " + deptInfo.toString());
				
				if (deptInfo.has("dept_id")) {
					obj.put("org_id", deptInfo.get("dept_id"));
					obj.put("org_name", deptInfo.get("dept_name"));
					obj.put("dept_id", deptInfo.get("dept_id"));
					obj.put("dept_name", deptInfo.get("dept_name"));
					if (deptInfo.has("dept_code")) {
						obj.put("dept_code", deptInfo.get("dept_code"));
					}
				} else {
					Object orgIdVal = row.get("OrgID");
					int orgId = orgIdVal != null ? ((Number) orgIdVal).intValue() : -1;
					obj.put("org_id", orgId);
					obj.put("org_name", orgId != -1 ? userExtend.fn_org_name(orgId) : "N/A");
				}
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
	
	@PostMapping("/adduser")
	public String AddUser(@RequestBody String sReq) {
		System.out.println("AddUser:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" + "\"}";

			// check admin right (Type 1: Admin, Type 2: System Admin)
			String adminCheckSql = "SELECT Type FROM TBL_USER WHERE ID=?";
			Integer adminType = null;
			try {
				adminType = jdbcTemplate.queryForObject(adminCheckSql, Integer.class, sst.UserID);
			} catch (Exception e) {
				// User not found or error
			}
			
			if (adminType == null || (adminType != 1 && adminType != 2)) {
				return "{\"code\":" + 403 + ", \"description\":\"" + "Bạn không có quyền thêm user" + "\"}";
			}

			String email = jsonobjReq.getString("email");

			// Check if user already exists
			String checkUserSql = "SELECT COUNT(*) FROM TBL_USER WHERE Email = ? AND (IsDeleted IS NULL OR IsDeleted='0')";
			Integer existingCount = jdbcTemplate.queryForObject(checkUserSql, Integer.class, email);
			if (existingCount != null && existingCount > 0) {
				return "{\"code\":" + 409 + ", \"description\":\"" + "Người dùng với email này đã tồn tại" + "\"}";
			}

			// Query employee and department
			String sql = "SELECT TOP 1 e.uName as full_name, d.dept_id " +
					"FROM employees e " +
					"LEFT JOIN departments d ON e.uUnit = d.dept_name " +
					"WHERE e.uEmail = ?";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, email);

			if (rows.isEmpty()) {
				return "{\"code\":" + 404 + ", \"description\":\"" + "Không tìm thấy nhân viên với email này" + "\"}";
			}

			Map<String, Object> empRow = rows.get(0);
			String fullName = (String) empRow.get("full_name");
			Integer deptId = (Integer) empRow.get("dept_id");
			int org_id = (deptId != null) ? deptId : -1;

			// Register User
			String defaultPassword = "123456";
			int defaultType = 4;
			String mobile = "";

			int user_id = userExtend.RegisterUser(fullName, email, defaultPassword, mobile, defaultType);
			if (user_id == -1) {
				return "{\"code\":" + 9999 + ", \"description\":\"" + "Lỗi khi đăng ký người dùng" + "\"}";
			}
			
			// Map user to department in TBL_USER and org_member
			/*if (org_id != -1) {
				try {
					jdbcTemplate.update("UPDATE TBL_USER SET OrgID = ? WHERE ID = ?", org_id, user_id);
				} catch (Exception e) {
					System.err.println("Warning: Could not update OrgID in TBL_USER: " + e.getMessage());
				}
				if (orgExtend.addMember(user_id, org_id) < 0) {
					System.err.println("Warning: Could not add member to org " + org_id);
				}
			}*/

			jout.put("code", 200);
			jout.put("description", "Thành công");
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"" + "Lỗi máy chủ: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(AddUser):" + jout.toString());
		return jout.toString();
	}
	
	@PostMapping("/get-department")
	public String getDepartmentByUserId(@RequestBody String sReq) {
		System.out.println("----------getDepartmentByUserId:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.has("session_id") ? jsonobjReq.getString("session_id") : null;
			if (session_id == null || sessionService.getSessionInfo(session_id) == null) {
				return "{\"code\":" + 700 + ", \"description\":\"" + "Người sử dụng chưa đăng nhập" + "\"}";
			}

			int target_user_id = jsonobjReq.getInt("user_id");
			JSONObject memberOrg = userExtend.getUserDepartmentInfo(target_user_id);
			
			if (memberOrg.has("dept_id")) {
				jout.put("org_id", memberOrg.get("dept_id"));
				jout.put("org_name", memberOrg.get("dept_name"));
				jout.put("dept_id", memberOrg.get("dept_id"));
				jout.put("dept_name", memberOrg.get("dept_name"));
				if (memberOrg.has("dept_code")) {
					jout.put("dept_code", memberOrg.get("dept_code"));
				}
				jout.put("code", 200);
				jout.put("description", "Thành công");
			} else {
				jout.put("code", 404);
				jout.put("description", "Không tìm thấy phòng ban cho user này");
			}
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"" + "Lỗi máy chủ: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(getDepartmentByUserId):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/slink-login")
	public String slinkLogin(@RequestBody String sReq) {
		System.out.println("SLINK-LOGIN:" + sReq);
		JSONObject jout = new JSONObject();

		try {
			JSONObject jReq = new JSONObject(sReq);
			String code = jReq.getString("code");

			String cleanApiKey = slinkApiKey;
			if (cleanApiKey != null) {
				cleanApiKey = cleanApiKey.replace("\"", "").trim();
			}

			// Call token endpoint to exchange code for access_token
			RestTemplate restTemplate = new RestTemplate();
			String tokenUrl = "https://gwdu.ptit.edu.vn/sso/realms/ptit/protocol/openid-connect/token";
			
			org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
			headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
			headers.set("x-api-key", cleanApiKey);

			org.springframework.util.MultiValueMap<String, String> map = new org.springframework.util.LinkedMultiValueMap<>();
			map.add("client_id", "ptit-cdit");
			map.add("grant_type", "authorization_code");
			map.add("code", code);
			map.add("redirect_uri", "https://dbclgd.ptit.edu.vn/qa-ims/dashboard");

			org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> request = new org.springframework.http.HttpEntity<>(map, headers);
			org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
			
			JSONObject tokenResponse = new JSONObject(response.getBody());
			String accessToken = tokenResponse.getString("access_token");
			String refreshToken = tokenResponse.optString("refresh_token");
			String idToken = tokenResponse.optString("id_token");


			// Call UserInfo endpoint to get email
			String userInfoUrl = "https://gwdu.ptit.edu.vn/sso/realms/ptit/protocol/openid-connect/userinfo";
			org.springframework.http.HttpHeaders uiHeaders = new org.springframework.http.HttpHeaders();
			uiHeaders.set("Authorization", "Bearer " + accessToken);
			uiHeaders.set("x-api-key", cleanApiKey);

			org.springframework.http.HttpEntity<String> uiRequest = new org.springframework.http.HttpEntity<>(uiHeaders);
			org.springframework.http.ResponseEntity<String> uiResponse = restTemplate.exchange(userInfoUrl, org.springframework.http.HttpMethod.GET, uiRequest, String.class);
			
			JSONObject userInfo = new JSONObject(uiResponse.getBody());
			String email = userInfo.getString("email");

			// Query user with email
			String sql = "select u.*, o.ID as org_id, o.Name as org_name "
					+ "from dbo.tbl_user u "
					+ "left join dbo.tbl_org o on o.ID = u.OrgID and (o.IsDeleted IS NULL or o.IsDeleted='0') "
					+ "where u.email=? and (u.IsDeleted IS NULL or u.IsDeleted='0')";
			List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, email);
			
			Map<String, Object> user;
			int user_id;

			if (users.isEmpty()) {
				// User not in TBL_USER. Auto-provision if exists in employees
				String checkEmpSql = "SELECT TOP 1 e.uName as full_name, d.dept_id " +
						"FROM employees e " +
						"LEFT JOIN departments d ON e.uUnit = d.dept_name " +
						"WHERE e.uEmail = ?";
				List<Map<String, Object>> empRows = jdbcTemplate.queryForList(checkEmpSql, email);

				if (empRows.isEmpty()) {
					jout.put("code", 710);
					jout.put("description", "Tài khoản SSO này chưa được phân quyền trên hệ thống.");
					return jout.toString();
				}

				Map<String, Object> empRow = empRows.get(0);
				String fullName = (String) empRow.get("full_name");
				Integer deptId = (Integer) empRow.get("dept_id");
				int org_id = (deptId != null) ? deptId : -1;

				// Register default user
				user_id = userExtend.RegisterUser(fullName, email, "123456", "", 4);
				if (user_id == -1) {
					jout.put("code", 500);
					jout.put("description", "Không thể tạo tài khoản người dùng tự động.");
					return jout.toString();
				}

				// Fetch newly registered user
				users = jdbcTemplate.queryForList(sql, email);
				if (users.isEmpty()) {
					jout.put("code", 500);
					jout.put("description", "Lỗi đồng bộ tài khoản.");
					return jout.toString();
				}
			}

			user = users.get(0);
			user_id = (int) user.get("ID");

			// create new session
			String session = sessionService.createSession(user_id);

			if (session != null) {
				JSONObject tokens = new JSONObject();
				tokens.put("access_token", accessToken);
				tokens.put("refresh_token", refreshToken);
				tokens.put("id_token", idToken);
				slinkTokensMap.put(session, tokens);

				jout.put("session_id", session);
				jout.put("user_id", user_id);
				jout.put("full_name", user.get("FullName"));
				jout.put("mobile", user.get("Mobile"));
				jout.put("status", user.get("Status"));
				jout.put("is_admin", user.get("IsAdmin"));
				jout.put("lock_doc", user.get("LockDoc"));
				jout.put("lock_user", user.get("LockUser"));
				jout.put("avatar", user.get("Avatar"));

				int type = (int) user.get("Type");
				jout.put("type", type);

				JSONObject DpInfo = userExtend.getUserDepartmentInfo(user_id);
				Object finalDpId = DpInfo.has("dept_id") ? DpInfo.get("dept_id") : null;
				Object finalDpName = DpInfo.has("dept_name") ? DpInfo.get("dept_name") : null;
				Object finalDpCode = DpInfo.has("dept_code") ? DpInfo.get("dept_code") : null;

				jout.put("dept_id", finalDpId != null ? finalDpId : JSONObject.NULL);
				jout.put("dept_name", finalDpName != null ? finalDpName : JSONObject.NULL);
				jout.put("dept_code", finalDpCode != null ? finalDpCode : JSONObject.NULL);

				jout.put("code", 200);
			} else {
				jout.put("code", 500);
				jout.put("description", "Internal error creating session");
			}
		} catch (Exception e) {
			e.printStackTrace();
			try {
				jout.put("code", 500);
				jout.put("description", "Lỗi đăng nhập SSO: " + e.getMessage());
			} catch (Exception ex) {}
		}

		System.out.println("SLINK-LOGIN response:" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/loginfromslink")
	public String loginFromSlink(@RequestBody String sReq) {
		System.out.println("LOGIN_AFTER_SLINK:" + sReq);
		JSONObject jout = new JSONObject();

		try {
			JSONObject jReq = new JSONObject(sReq);
			String email = jReq.getString("email");
			String scrambled = jReq.getString("scrambled");

			// Decode scrambled
			String decodedBase64 = new String(java.util.Base64.getDecoder().decode(scrambled), java.nio.charset.StandardCharsets.UTF_8);
			String reversedStr = new StringBuilder(decodedBase64).reverse().toString();
			
			String key = "ptit-secret-2026";
			StringBuilder unscrambled = new StringBuilder();
			for (int i = 0; i < reversedStr.length(); i++) {
				unscrambled.append((char) (reversedStr.charAt(i) ^ key.charAt(i % key.length())));
			}
			
			String decodedStr = unscrambled.toString();
			String[] parts = decodedStr.split("\\|\\|\\|");
			if (parts.length != 2) {
				jout.put("code", 400);
				jout.put("description", "Invalid scrambled format");
				return jout.toString();
			}
			
			String decodedEmail = parts[0];
			if (!decodedEmail.equals(email)) {
				jout.put("code", 401);
				jout.put("description", "Xác thực SSO không hợp lệ");
				return jout.toString();
			}

			// Query user with email
			String sql = "select u.*, o.ID as org_id, o.Name as org_name "
					+ "from dbo.tbl_user u "
					+ "left join dbo.tbl_org o on o.ID = u.OrgID and (o.IsDeleted IS NULL or o.IsDeleted='0') "
					+ "where u.email=? and (u.IsDeleted IS NULL or u.IsDeleted='0')";
			List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, email);
			
			Map<String, Object> user;
			int user_id;

			if (users.isEmpty()) {
				// User not in TBL_USER. Auto-provision if exists in employees
				String checkEmpSql = "SELECT TOP 1 e.uName as full_name, d.dept_id " +
						"FROM employees e " +
						"LEFT JOIN departments d ON e.uUnit = d.dept_name " +
						"WHERE e.uEmail = ?";
				List<Map<String, Object>> empRows = jdbcTemplate.queryForList(checkEmpSql, email);

				if (empRows.isEmpty()) {
					jout.put("code", 710);
					jout.put("description", "Tài khoản SSO này chưa được phân quyền trên hệ thống.");
					return jout.toString();
				}

				Map<String, Object> empRow = empRows.get(0);
				String fullName = (String) empRow.get("full_name");
				Integer deptId = (Integer) empRow.get("dept_id");
				int org_id = (deptId != null) ? deptId : -1;

				// Register default user
				user_id = userExtend.RegisterUser(fullName, email, "123456", "", 4);
				if (user_id == -1) {
					jout.put("code", 500);
					jout.put("description", "Không thể tạo tài khoản người dùng tự động.");
					return jout.toString();
				}

				// Fetch newly registered user
				users = jdbcTemplate.queryForList(sql, email);
				if (users.isEmpty()) {
					jout.put("code", 500);
					jout.put("description", "Lỗi đồng bộ tài khoản.");
					return jout.toString();
				}
			}

			user = users.get(0);
			user_id = (int) user.get("ID");

			// create new session
			String session = sessionService.createSession(user_id);

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

				int type = (int) user.get("Type");
				jout.put("type", type);

				JSONObject DpInfo = userExtend.getUserDepartmentInfo(user_id);
				Object finalDpId = DpInfo.has("dept_id") ? DpInfo.get("dept_id") : null;
				Object finalDpName = DpInfo.has("dept_name") ? DpInfo.get("dept_name") : null;
				Object finalDpCode = DpInfo.has("dept_code") ? DpInfo.get("dept_code") : null;

				jout.put("dept_id", finalDpId != null ? finalDpId : JSONObject.NULL);
				jout.put("dept_name", finalDpName != null ? finalDpName : JSONObject.NULL);
				jout.put("dept_code", finalDpCode != null ? finalDpCode : JSONObject.NULL);

				jout.put("code", 200);
			} else {
				jout.put("code", 500);
				jout.put("description", "Internal error creating session");
			}
		} catch (Exception e) {
			e.printStackTrace();
			try {
				jout.put("code", 500);
				jout.put("description", "Lỗi đăng nhập SSO: " + e.getMessage());
			} catch (Exception ex) {}
		}

		System.out.println("LOGIN_AFTER_SLINK response:" + jout.toString());
		return jout.toString();
	}
}

