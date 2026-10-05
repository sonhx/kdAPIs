package com.user;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
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
	private UserExtend userExtend;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// =====================LOGIN===============================
	@PostMapping(value = "/login", produces = "application/json; charset=UTF-8")
	public String Login(@RequestBody String sReq) {
		String loginname, userpass, session;
		System.out.println("LOGIN:" + sReq);
		JSONObject jout = new JSONObject();

		try {
			JSONObject jsologin = new JSONObject(sReq);
			loginname = jsologin.has("user_name") ? String.valueOf(jsologin.get("user_name")) : "";
			userpass = jsologin.has("user_password") ? String.valueOf(jsologin.get("user_password")) : "";
			
			System.out.println("loginname = " + loginname);
			/*String server = jdbcTemplate.queryForObject(
				    "SELECT @@SERVERNAME",
				    String.class
				);
			
				String db = jdbcTemplate.queryForObject(
				    "SELECT DB_NAME()",
				    String.class
				);
			
				System.out.println("SERVER = " + server);
				System.out.println("DATABASE = " + db);
				
			*/	
			
			// check user's existence by Email, ID, or maCanBo (fast indexed lookup with NOLOCK)
			String sqlByEmail = "SELECT TOP 1 u.ID, u.Email, u.Hash, u.Status, u.role_code, u.IsAdmin, u.LockDoc, u.LockUser "
					+ "FROM dbo.users u WITH (NOLOCK) "
					+ "WHERE (u.Email = ? OR u.ID = ?) AND (u.IsDeleted IS NULL OR u.IsDeleted = '0')";
			
			List<Map<String, Object>> users = jdbcTemplate.queryForList(sqlByEmail, loginname, loginname);

			if (users.isEmpty()) {
				String sqlByPersonnel = "SELECT TOP 1 u.ID, u.Email, u.Hash, u.Status, u.role_code, u.IsAdmin, u.LockDoc, u.LockUser "
						+ "FROM dbo.personnel p WITH (NOLOCK) "
						+ "INNER JOIN dbo.users u WITH (NOLOCK) ON (u.ID = p.id OR (u.Email IS NOT NULL AND u.Email <> '' AND (u.Email = p.emailCanBo OR u.Email = p.email))) AND (u.IsDeleted IS NULL OR u.IsDeleted = '0') "
						+ "WHERE (p.maCanBo = ? OR p.emailCanBo = ? OR p.email = ?) AND p.isDeleted = 0";
				users = jdbcTemplate.queryForList(sqlByPersonnel, loginname, loginname, loginname);
			}

			if (users.isEmpty()) {
				jout.put("code", 710);
				jout.put("description", "No user");
				System.out.println("LOGIN error:" + "No user");
				return jout.toString();
			}

			// --------------check pw
			Map<String, Object> user = users.get(0);
			Object user_id = user.get("ID");
			String userEmail = user.get("Email") != null ? user.get("Email").toString() : "";
			String local_hash = (String) user.get("Hash");

			boolean isPasswordCorrect = false;
			if (local_hash != null && !local_hash.isEmpty()) {
				try {
					isPasswordCorrect = BCrypt.checkpw(userpass, local_hash);
					if (!isPasswordCorrect && userpass != null) {
						isPasswordCorrect = BCrypt.checkpw(userpass.toLowerCase().trim(), local_hash);
					}
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

			// Fetch personnel details for display (fast separate lookup with NOLOCK)
			String fullName = "";
			String mobile = "";
			try {
				String pSql = "SELECT TOP 1 p.fullname AS FullName, p.sdtCaNhan AS Mobile "
						+ "FROM dbo.personnel p WITH (NOLOCK) "
						+ "WHERE p.isDeleted = 0 AND (p.id = ? OR (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '' AND p.emailCanBo = ?) OR (p.email IS NOT NULL AND p.email <> '' AND p.email = ?))";
				List<Map<String, Object>> pRows = jdbcTemplate.queryForList(pSql, 
						user_id != null ? user_id.toString() : "", userEmail, userEmail);
				if (!pRows.isEmpty()) {
					fullName = pRows.get(0).get("FullName") != null ? pRows.get(0).get("FullName").toString() : "";
					mobile = pRows.get(0).get("Mobile") != null ? pRows.get(0).get("Mobile").toString() : "";
				}
			} catch (Exception ex) {}

			// create new session
			session = sessionService.createSession(user_id);

			if (session != null) {
				jout.put("session_id", session);
				jout.put("user_id", user_id != null ? user_id : "");
				jout.put("full_name", fullName);
				jout.put("mobile", mobile);
				jout.put("status", user.get("Status") != null ? user.get("Status") : 1);
				jout.put("is_admin", user.get("IsAdmin") != null ? user.get("IsAdmin") : 0);
				jout.put("lock_doc", user.get("LockDoc") != null ? user.get("LockDoc") : 0);
				jout.put("lock_user", user.get("LockUser") != null ? user.get("LockUser") : 0);
				jout.put("avatar", "");

				Object roleCodeObj = user.get("role_code");
				String roleCode = roleCodeObj != null ? roleCodeObj.toString() : "CHUYEN_VIEN";
				jout.put("role_code", roleCode);
				int type = "ADMIN".equalsIgnoreCase(roleCode) ? 1 : ("LANH_DAO_HV".equalsIgnoreCase(roleCode) ? 2 : ("TRUONG_DON_VI".equalsIgnoreCase(roleCode) ? 3 : 0));
				jout.put("type", type);

				if ("ADMIN".equalsIgnoreCase(roleCode)) {//admin
					//TODO: get org info of admin user
				}
				
				JSONObject DpInfo = userExtend.getUserDepartmentInfo(user_id);
				Object finalDpId = (DpInfo != null && DpInfo.has("dept_id")) ? DpInfo.get("dept_id") : null;
				Object finalDpName = (DpInfo != null && DpInfo.has("dept_name")) ? DpInfo.get("dept_name") : null;
				Object finalDpCode = (DpInfo != null && DpInfo.has("dept_code")) ? DpInfo.get("dept_code") : null;

				if (type != 0 && type != 1 && type != 2 && (finalDpId == null || finalDpId.toString().trim().isEmpty())) {
					// Membership not mandatory for login
				}

				jout.put("dept_id", finalDpId != null ? finalDpId : JSONObject.NULL);
				jout.put("dept_name", finalDpName != null ? finalDpName : JSONObject.NULL);
				jout.put("dept_code", finalDpCode != null ? finalDpCode : JSONObject.NULL);

				// Check if this user is Lãnh đạo Học viện (belongs to LD_HOC_VIEN_ID or LD_HOC_VIEN_NAM_ID)
				boolean isLanhDaoHocVien = false;
				try {
					String ldCheckSql =
						"SELECT COUNT(*) FROM personnel p WITH (NOLOCK) WHERE (p.id = ? OR (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '' AND p.emailCanBo = ?) OR (p.email IS NOT NULL AND p.email <> '' AND p.email = ?)) " +
						"AND (p.donViChinhId IN ('66a308ce8068e53428da202c', '66a308ce8068e53428da202d') " +
						"  OR p.donViL3Id   IN ('66a308ce8068e53428da202c', '66a308ce8068e53428da202d'))";
					Integer cnt = jdbcTemplate.queryForObject(ldCheckSql, Integer.class, 
							user_id != null ? user_id.toString() : "", userEmail, userEmail);
					if (cnt != null && cnt > 0) {
						isLanhDaoHocVien = true;
					}
				} catch (Exception ldEx) {
					System.err.println("Warning: could not check Lãnh đạo Học viện status: " + ldEx.getMessage());
				}
				jout.put("is_lanh_dao_hoc_vien", isLanhDaoHocVien ? 1 : 0);

				// Check if this user is an org leader (their personnel.id is in orgs.leaderId)
				boolean isOrgLeader = false;
				String leaderOfDeptId = null;
				String leaderOfDeptName = null;
				try {
					String leaderCheckSql =
						"SELECT TOP 1 o.id AS orgId, o.ten AS orgName " +
						"FROM orgs o WITH (NOLOCK) " +
						"INNER JOIN personnel p WITH (NOLOCK) ON o.leaderId = p.id " +
						"WHERE (p.id = ? OR (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '' AND p.emailCanBo = ?) OR (p.email IS NOT NULL AND p.email <> '' AND p.email = ?)) AND (o.isDeleted IS NULL OR o.isDeleted = 0)";
					List<Map<String, Object>> leaderRows = jdbcTemplate.queryForList(
						leaderCheckSql, user_id != null ? user_id.toString() : "", userEmail, userEmail
					);
					if (!leaderRows.isEmpty()) {
						isOrgLeader = true;
						leaderOfDeptId   = leaderRows.get(0).get("orgId")   != null ? leaderRows.get(0).get("orgId").toString()   : null;
						leaderOfDeptName = leaderRows.get(0).get("orgName") != null ? leaderRows.get(0).get("orgName").toString() : null;
					}
				} catch (Exception leaderEx) {
					System.err.println("Warning: could not check org leader status: " + leaderEx.getMessage());
				}
				jout.put("is_org_leader", isOrgLeader ? 1 : 0);
				jout.put("leader_of_dept_id",   leaderOfDeptId   != null ? leaderOfDeptId   : JSONObject.NULL);
				jout.put("leader_of_dept_name", leaderOfDeptName != null ? leaderOfDeptName : JSONObject.NULL);

				jout.put("code", 200);
			} else {
				jout.put("code", 500);
				jout.put("description", "Internal error");
			}
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"Lỗi xử lý hệ thống: " + e.getMessage() + "\"}";
		}

		//System.out.println("LOGIN response:" + jout.toString());
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

	@PostMapping(value = "/listuser", produces = "application/json; charset=UTF-8")
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

			String sql = "select a.*, p.fullname as Fullname, p.sdtCaNhan as Mobile, c.ID as org_id, c.Code as org_code, c.Name as org_name from users a "
					+ " LEFT JOIN personnel p ON (p.emailCanBo = a.Email OR p.email = a.Email) AND p.isDeleted = 0 "
					+ " INNER JOIN TBL_ORG_MEMBER b on b.MEMBER_ID = a.ID "
					+ " LEFT JOIN TBL_ORG c on c.ID = b.ORG_ID "
					+ " where (a.IsDeleted is null or a.IsDeleted='0')"
					+ " order by STATUS desc";
			
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

			for (Map<String, Object> row : rows) {
				Object roleCodeObj = row.get("role_code");
				String roleCode = roleCodeObj != null ? roleCodeObj.toString() : "CHUYEN_VIEN";
				int type = "ADMIN".equalsIgnoreCase(roleCode) ? 1 : ("LANH_DAO_HV".equalsIgnoreCase(roleCode) ? 2 : ("TRUONG_DON_VI".equalsIgnoreCase(roleCode) ? 3 : 0));

				JSONObject obj = new JSONObject();
				obj.put("id", row.get("ID"));
				obj.put("full_name", row.get("Fullname"));
				obj.put("email", row.get("Email"));
				obj.put("mobile", row.get("Mobile") != null ? row.get("Mobile") : "");
				obj.put("avatar", "");
				obj.put("is_admin", row.get("IsAdmin"));
				obj.put("lock_doc", row.get("LockDoc"));
				obj.put("lock_user", row.get("LockUser"));
				obj.put("role_code", roleCode);
				obj.put("type", type);

				obj.put("type_name", userExtend.fn_user_type_name(roleCode));
				obj.put("org_id", row.get("org_id") != null ? row.get("org_id") : -1);
				obj.put("org_name", row.get("org_name") != null ? row.get("org_name") : "HV");
				obj.put("status", String.valueOf(row.get("Status")));
				jaout.put(obj);
			}

			jout.put("user_list", jaout);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		//System.out.println("RES(listUser):" + jout.toString());
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

			String sql = "select u.*, p.fullname as Fullname from users u "
					+ "LEFT JOIN personnel p ON (p.emailCanBo = u.Email OR p.email = u.Email) AND p.isDeleted = 0 "
					+ "where (u.role_code IS NULL OR u.role_code = 'CHUYEN_VIEN') and (u.IsDeleted is null or u.IsDeleted='0')";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				Object id = row.get("ID");
				int orgId = -1;
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
		//System.out.println("RES(listAgent):" + jout.toString());
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
			String roleCode = jsonobjReq.has("role_code") ? jsonobjReq.getString("role_code") : null;
			if (roleCode == null && jsonobjReq.has("type")) {
				int type = jsonobjReq.getInt("type");
				roleCode = type == 1 ? "ADMIN" : (type == 2 ? "LANH_DAO_HV" : (type == 3 ? "TRUONG_DON_VI" : "CHUYEN_VIEN"));
			}
			if (roleCode == null) roleCode = "CHUYEN_VIEN";
			int org_id = jsonobjReq.has("org_id") ? jsonobjReq.getInt("org_id") : -1;
			String mobile = jsonobjReq.has("mobile") ? jsonobjReq.getString("mobile") : "";

			String user_id = userExtend.RegisterUser(full_name, email, password, mobile, roleCode);
			//System.out.println("user_id = " + user_id);
			if (user_id == null) {
				return "{\"code\":" + 9999 + ", \"description\":\"" + "Error while registering user" + "\"}";
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
			String session_id = (jsonobjReq.has("session_id") && !jsonobjReq.isNull("session_id")) ? jsonobjReq.getString("session_id") : null;
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại." + "\"}";

			Object deleted_user_id = jsonobjReq.get("user_id");
			String updaterId = (sst.sUserId != null && !sst.sUserId.isEmpty()) ? sst.sUserId : String.valueOf(sst.UserID);
			jdbcTemplate.update("update dbo.users set IsDeleted=1, UpdatedBy=?, UpdatedTime=GETDATE() where ID=?", updaterId, deleted_user_id.toString());

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
			String session_id = (jsonobjReq.has("session_id") && !jsonobjReq.isNull("session_id")) ? jsonobjReq.getString("session_id") : null;
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại." + "\"}";

			String currentUserId = (sst.sUserId != null && !sst.sUserId.trim().isEmpty()) ? sst.sUserId.trim() : String.valueOf(sst.UserID);

			// Check admin/leader rights
			boolean isAuthorized = false;
			String adminCheckSql = "SELECT TOP 1 role_code, Type, Email FROM users WHERE CAST(ID AS VARCHAR(100)) = ? OR ID = ?";
			try {
				List<Map<String, Object>> uRows = jdbcTemplate.queryForList(adminCheckSql, currentUserId, currentUserId);
				if (!uRows.isEmpty()) {
					Map<String, Object> r = uRows.get(0);
					String adminRoleCode = r.get("role_code") != null ? r.get("role_code").toString().toUpperCase() : "";
					Number uTypeNum = r.get("Type") != null && (r.get("Type") instanceof Number) ? (Number) r.get("Type") : null;
					int userType = uTypeNum != null ? uTypeNum.intValue() : 4;
					String userEmail = r.get("Email") != null ? r.get("Email").toString().trim().toLowerCase() : "";

					if (userType == 1 || userType == 2 || userType == 3 ||
						"ADMIN".equals(adminRoleCode) || "LANH_DAO_HV".equals(adminRoleCode) || "TRUONG_DON_VI".equals(adminRoleCode) ||
						userEmail.equalsIgnoreCase("admin@ptit.edu.vn") || userEmail.equalsIgnoreCase("sonhx@ptit.edu.vn")) {
						isAuthorized = true;
					}
				}
			} catch (Exception e) {
				System.err.println("Error verifying admin permissions: " + e.getMessage());
			}

			// Fallback for demo session or root admin ID 1
			if (!isAuthorized && ("1".equals(currentUserId) || "demo-session".equalsIgnoreCase(session_id))) {
				isAuthorized = true;
			}

			if (!isAuthorized) {
				return "{\"code\":" + 403 + ", \"description\":\"" + "Bạn không có quyền thực hiện tác vụ này" + "\"}";
			}

			Object target_user_id = jsonobjReq.get("user_id");
			String newRoleCode = jsonobjReq.has("role_code") ? jsonobjReq.getString("role_code") : null;
			if (newRoleCode == null && jsonobjReq.has("type")) {
				int new_type = jsonobjReq.getInt("type");
				newRoleCode = new_type == 1 ? "ADMIN" : (new_type == 2 ? "LANH_DAO_HV" : (new_type == 3 ? "TRUONG_DON_VI" : "CHUYEN_VIEN"));
			}
			if (newRoleCode == null) newRoleCode = "CHUYEN_VIEN";

			String updaterId = (sst.sUserId != null && !sst.sUserId.isEmpty()) ? sst.sUserId : String.valueOf(sst.UserID);

			jdbcTemplate.update("UPDATE dbo.users SET role_code = ?, UpdatedBy = ?, UpdatedTime = GETDATE() WHERE ID = ?", newRoleCode, updaterId, target_user_id.toString());

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
			String session_id = (jsonobjReq.has("session_id") && !jsonobjReq.isNull("session_id")) ? jsonobjReq.getString("session_id") : null;
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại." + "\"}";

			Object user_id = jsonobjReq.get("user_id");
			String updaterId = (sst.sUserId != null && !sst.sUserId.isEmpty()) ? sst.sUserId : String.valueOf(sst.UserID);
			jdbcTemplate.update("update dbo.users set Status = 0, UpdatedBy = ?, UpdatedTime = GETDATE() where ID=?", updaterId, user_id.toString());

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
			String session_id = (jsonobjReq.has("session_id") && !jsonobjReq.isNull("session_id")) ? jsonobjReq.getString("session_id") : null;
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại." + "\"}";

			Object user_id = jsonobjReq.get("user_id");
			String updaterId = (sst.sUserId != null && !sst.sUserId.isEmpty()) ? sst.sUserId : String.valueOf(sst.UserID);
			jdbcTemplate.update("update dbo.users set Status = 1, UpdatedBy = ?, UpdatedTime = GETDATE() where ID=?", updaterId, user_id.toString());

			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		System.out.println("RES(ReactivateUser):" + jout.toString());
		return jout.toString();
	}

	@PostMapping(value = "/lisalltuser", produces = "application/json; charset=UTF-8")
	public String listAllUser(@RequestBody String sReq) {
		System.out.println("----------listAllUser:" + sReq);

		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();

		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = (jsonobjReq.has("session_id") && !jsonobjReq.isNull("session_id")) ? jsonobjReq.getString("session_id") : null;
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại." + "\"}";

			// Optional dept filter — org leaders pass their leader_of_dept_id here
			String filterDeptId = jsonobjReq.has("filter_dept_id") && !jsonobjReq.isNull("filter_dept_id")
				? jsonobjReq.getString("filter_dept_id") : null;

			String sql = filterDeptId != null ? """
				    SELECT * FROM (
				        SELECT
				            u.ID,
				            COALESCE(p.fullname, p.emailCanBo, p.email, u.Email) AS Fullname,
				            u.Email,
				            COALESCE(p.sdtCaNhan, '') AS Mobile,
				            u.role_code,
				            COALESCE(p.donViL3Id, p.donViChinhId) AS dept_id,
				            COALESCE(o.ten, oChinh.ten) AS dept_name,
				            COALESCE(o.maDonVi, oChinh.maDonVi, '') AS dept_code
				        FROM users u WITH (NOLOCK)
				        OUTER APPLY (
				            SELECT TOP 1 p.fullname, p.emailCanBo, p.email, p.sdtCaNhan, p.donViL3Id, p.donViChinhId
				            FROM personnel p WITH (NOLOCK)
				            WHERE p.isDeleted = 0 
				              AND (p.id = u.ID OR (u.Email IS NOT NULL AND u.Email <> '' AND (p.emailCanBo = u.Email OR p.email = u.Email)))
				            ORDER BY CASE WHEN p.emailCanBo = u.Email THEN 1 WHEN p.email = u.Email THEN 2 ELSE 3 END
				        ) p
				        LEFT JOIN orgs o WITH (NOLOCK) ON o.id = p.donViL3Id
				        LEFT JOIN orgs oChinh WITH (NOLOCK) ON oChinh.id = p.donViChinhId
				        WHERE (u.IsDeleted IS NULL OR u.IsDeleted = 0 OR u.IsDeleted = '0')
				    ) t
				    WHERE t.dept_id = ?
				    ORDER BY t.ID, t.Fullname ASC;
				    """ : """
				    SELECT
				        u.ID,
				        COALESCE(p.fullname, p.emailCanBo, p.email, u.Email) AS Fullname,
				        u.Email,
				        COALESCE(p.sdtCaNhan, '') AS Mobile,
				        u.role_code,
				        COALESCE(p.donViL3Id, p.donViChinhId) AS dept_id,
				        COALESCE(o.ten, oChinh.ten) AS dept_name,
				        COALESCE(o.maDonVi, oChinh.maDonVi, '') AS dept_code
				    FROM users u WITH (NOLOCK)
				    OUTER APPLY (
				        SELECT TOP 1 p.fullname, p.emailCanBo, p.email, p.sdtCaNhan, p.donViL3Id, p.donViChinhId
				        FROM personnel p WITH (NOLOCK)
				        WHERE p.isDeleted = 0 
				          AND (p.id = u.ID OR (u.Email IS NOT NULL AND u.Email <> '' AND (p.emailCanBo = u.Email OR p.email = u.Email)))
				        ORDER BY CASE WHEN p.emailCanBo = u.Email THEN 1 WHEN p.email = u.Email THEN 2 ELSE 3 END
				    ) p
				    LEFT JOIN orgs o WITH (NOLOCK) ON o.id = p.donViL3Id
				    LEFT JOIN orgs oChinh WITH (NOLOCK) ON oChinh.id = p.donViChinhId
				    WHERE (u.IsDeleted IS NULL OR u.IsDeleted = 0 OR u.IsDeleted = '0')
				    ORDER BY u.ID, Fullname ASC;
				    """;

			//System.out.println("listAllUser SQL: " + sql);

			List<Map<String, Object>> rows = filterDeptId != null
				? jdbcTemplate.queryForList(sql, filterDeptId)
				: jdbcTemplate.queryForList(sql);

			Set<Object> seenUserIds = new HashSet<>();
			for (Map<String, Object> row : rows) {
				Object userId = row.get("ID");
				if (userId != null && !seenUserIds.add(userId.toString())) {
					continue;
				}
				Object roleCodeObj = row.get("role_code");
				String roleCode = roleCodeObj != null ? roleCodeObj.toString() : "CHUYEN_VIEN";
				int type = "ADMIN".equalsIgnoreCase(roleCode) ? 1 : ("LANH_DAO_HV".equalsIgnoreCase(roleCode) ? 2 : ("TRUONG_DON_VI".equalsIgnoreCase(roleCode) ? 3 : 0));
				JSONObject obj = new JSONObject();
				obj.put("id", row.get("ID"));
				obj.put("full_name", row.get("Fullname"));
				String email = row.get("Email") != null ? row.get("Email").toString().trim() : "";
				obj.put("email", email);

				obj.put("mobile", row.get("Mobile") != null ? row.get("Mobile") : "");
				obj.put("avatar", "");
				obj.put("role_code", roleCode);
				obj.put("type", type);
				obj.put("type_name", userExtend.fn_user_type_name(roleCode));

				Object deptId = row.get("dept_id");
				Object deptName = row.get("dept_name");
				Object deptCode = row.get("dept_code");

				if (deptId != null && deptName != null) {
					obj.put("org_id", deptId);
					obj.put("org_name", deptName);
					obj.put("dept_id", deptId);
					obj.put("dept_name", deptName);
					if (deptCode != null && !deptCode.toString().isEmpty()) {
						obj.put("dept_code", deptCode);
					}
				} else {
					obj.put("org_id", -1);
					obj.put("org_name", "N/A");
				}
				jaout.put(obj);
			}

			jout.put("user_list", jaout);
			jout.put("code", 200);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON parse error" + "\"}";
		}
		//System.out.println("RES(listAllUser):" + jout.toString());
		return jout.toString();
	}
	
	@PostMapping("/adduser")
	public String AddUser(@RequestBody String sReq) {
		System.out.println("AddUser:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = (jsonobjReq.has("session_id") && !jsonobjReq.isNull("session_id")) ? jsonobjReq.getString("session_id") : null;
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null)
				return "{\"code\":" + 700 + ", \"description\":\"" + "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại." + "\"}";

			String currentUserId = (sst.sUserId != null && !sst.sUserId.trim().isEmpty()) ? sst.sUserId.trim() : String.valueOf(sst.UserID);

			// check admin right (role_code ADMIN or LANH_DAO_HV or TRUONG_DON_VI)
			boolean isAuthorized = false;
			String adminCheckSql = "SELECT TOP 1 role_code, Type, Email FROM users WHERE CAST(ID AS VARCHAR(100)) = ? OR ID = ?";
			try {
				List<Map<String, Object>> uRows = jdbcTemplate.queryForList(adminCheckSql, currentUserId, currentUserId);
				if (!uRows.isEmpty()) {
					Map<String, Object> r = uRows.get(0);
					String adminRoleCode = r.get("role_code") != null ? r.get("role_code").toString().toUpperCase() : "";
					Number uTypeNum = r.get("Type") != null && (r.get("Type") instanceof Number) ? (Number) r.get("Type") : null;
					int userType = uTypeNum != null ? uTypeNum.intValue() : 4;
					String userEmail = r.get("Email") != null ? r.get("Email").toString().trim().toLowerCase() : "";

					if (userType == 1 || userType == 2 || userType == 3 ||
						"ADMIN".equals(adminRoleCode) || "LANH_DAO_HV".equals(adminRoleCode) || "TRUONG_DON_VI".equals(adminRoleCode) ||
						userEmail.equalsIgnoreCase("admin@ptit.edu.vn") || userEmail.equalsIgnoreCase("sonhx@ptit.edu.vn")) {
						isAuthorized = true;
					}
				}
			} catch (Exception e) {
				System.err.println("Error verifying admin permissions: " + e.getMessage());
			}

			if (!isAuthorized && ("1".equals(currentUserId) || "demo-session".equalsIgnoreCase(session_id))) {
				isAuthorized = true;
			}

			if (!isAuthorized) {
				return "{\"code\":" + 403 + ", \"description\":\"" + "Bạn không có quyền thêm user" + "\"}";
			}

			String email = jsonobjReq.getString("email");

			// Check if user already exists
			String checkUserSql = "SELECT COUNT(*) FROM users WHERE Email = ? AND (IsDeleted IS NULL OR IsDeleted='0')";
			Integer existingCount = jdbcTemplate.queryForObject(checkUserSql, Integer.class, email);
			if (existingCount != null && existingCount > 0) {
				return "{\"code\":" + 409 + ", \"description\":\"" + "Người dùng với email này đã tồn tại" + "\"}";
			}

			// Query personnel and Level 3 department
			String sql = "SELECT TOP 1 p.fullname as full_name, p.donViL3Id as dept_id " +
					"FROM personnel p " +
					"WHERE (p.emailCanBo = ? OR p.email = ?) AND p.isDeleted = 0";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, email, email);

			if (rows.isEmpty()) {
				return "{\"code\":" + 404 + ", \"description\":\"" + "Không tìm thấy nhân viên với email này" + "\"}";
			}

			Map<String, Object> empRow = rows.get(0);
			String fullName = (String) empRow.get("full_name");
			Object deptId = empRow.get("dept_id");

			// Register User
			String defaultPassword = "123456";
			int defaultType = 4;
			String mobile = "";

			String user_id = userExtend.RegisterUser(fullName, email, defaultPassword, mobile, defaultType);
			if (user_id == null) {
				return "{\"code\":" + 9999 + ", \"description\":\"" + "Lỗi khi đăng ký người dùng" + "\"}";
			}
			
			// Map user to department in users and org_member
			/*if (org_id != -1) {
				try {
					jdbcTemplate.update("UPDATE users SET OrgID = ? WHERE ID = ?", org_id, user_id);
				} catch (Exception e) {
					System.err.println("Warning: Could not update OrgID in users: " + e.getMessage());
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
			String session_id = (jsonobjReq.has("session_id") && !jsonobjReq.isNull("session_id")) ? jsonobjReq.getString("session_id") : null;
			if (session_id == null || sessionService.getSessionInfo(session_id) == null) {
				return "{\"code\":" + 700 + ", \"description\":\"" + "Phiên đăng nhập đã hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại." + "\"}";
			}

			Object target_user_id = jsonobjReq.get("user_id");
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

	@PostMapping(value = "/slink-login", produces = "application/json; charset=UTF-8")
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
			String sql = "select u.*, p.fullname as Fullname from dbo.users u "
					+ "LEFT JOIN personnel p ON (p.emailCanBo = u.Email OR p.email = u.Email) AND p.isDeleted = 0 "
					+ "where u.email=? and (u.IsDeleted IS NULL or u.IsDeleted='0')";
			List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, email);
			
			Map<String, Object> user;
			Object user_id;

			if (users.isEmpty()) {
				// User not in users. Auto-provision if exists in personnel
				String checkEmpSql = "SELECT TOP 1 p.fullname as full_name, p.donViL3Id as dept_id " +
						"FROM personnel p " +
						"WHERE (p.emailCanBo = ? OR p.email = ?) AND p.isDeleted = 0";
				List<Map<String, Object>> empRows = jdbcTemplate.queryForList(checkEmpSql, email, email);

				if (empRows.isEmpty()) {
					jout.put("code", 710);
					jout.put("description", "Tài khoản SSO này chưa được phân quyền trên hệ thống.");
					return jout.toString();
				}

				Map<String, Object> empRow = empRows.get(0);
				String fullName = (String) empRow.get("full_name");

				// Register default user
				user_id = userExtend.RegisterUser(fullName, email, "123456", "", 4);
				if (user_id == null) {
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
			user_id = user.get("ID");

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
				jout.put("full_name", user.get("Fullname"));
				jout.put("mobile", user.get("Mobile"));
				jout.put("status", user.get("Status"));
				jout.put("is_admin", user.get("IsAdmin"));
				jout.put("lock_doc", user.get("LockDoc"));
				jout.put("lock_user", user.get("LockUser"));
				jout.put("avatar", user.get("Avatar"));

				Object roleCodeObj = user.get("role_code");
				String roleCode = roleCodeObj != null ? roleCodeObj.toString() : "CHUYEN_VIEN";
				jout.put("role_code", roleCode);
				int type = "ADMIN".equalsIgnoreCase(roleCode) ? 1 : ("LANH_DAO_HV".equalsIgnoreCase(roleCode) ? 2 : ("TRUONG_DON_VI".equalsIgnoreCase(roleCode) ? 3 : 0));
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

			// Query user with email (fast indexed query with NOLOCK)
			String sql = "SELECT TOP 1 u.ID, u.Email, u.Hash, u.Status, u.role_code, u.IsAdmin, u.LockDoc, u.LockUser, u.Avatar "
					+ "FROM dbo.users u WITH (NOLOCK) "
					+ "WHERE u.email=? AND (u.IsDeleted IS NULL OR u.IsDeleted='0')";
			List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, email);
			
			Map<String, Object> user;
			Object user_id;

			if (users.isEmpty()) {
				// User not in users. Auto-provision if exists in personnel
				String checkEmpSql = "SELECT TOP 1 p.fullname as full_name, p.donViL3Id as dept_id " +
						"FROM personnel p WITH (NOLOCK) " +
						"WHERE (p.emailCanBo = ? OR p.email = ?) AND p.isDeleted = 0";
				List<Map<String, Object>> empRows = jdbcTemplate.queryForList(checkEmpSql, email, email);

				if (empRows.isEmpty()) {
					jout.put("code", 710);
					jout.put("description", "Tài khoản SSO này chưa được phân quyền trên hệ thống.");
					return jout.toString();
				}

				Map<String, Object> empRow = empRows.get(0);
				String fullName = (String) empRow.get("full_name");

				// Register default user
				user_id = userExtend.RegisterUser(fullName, email, "123456", "", 4);
				if (user_id == null) {
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
			user_id = user.get("ID");
			String userEmail = user.get("Email") != null ? user.get("Email").toString() : email;

			// Fetch personnel details for display
			String fullName = "";
			try {
				String pSql = "SELECT TOP 1 p.fullname FROM personnel p WITH (NOLOCK) WHERE (p.emailCanBo = ? OR p.email = ?) AND p.isDeleted = 0";
				List<String> pNames = jdbcTemplate.queryForList(pSql, String.class, userEmail, userEmail);
				if (!pNames.isEmpty() && pNames.get(0) != null) {
					fullName = pNames.get(0);
				}
			} catch (Exception ex) {}

			// create new session
			String session = sessionService.createSession(user_id);

			if (session != null) {
				jout.put("session_id", session);
				jout.put("user_id", user_id != null ? user_id : "");
				jout.put("full_name", fullName);
				jout.put("mobile", user.get("Mobile") != null ? user.get("Mobile") : "");
				jout.put("status", user.get("Status") != null ? user.get("Status") : 1);
				jout.put("is_admin", user.get("IsAdmin") != null ? user.get("IsAdmin") : 0);
				jout.put("lock_doc", user.get("LockDoc") != null ? user.get("LockDoc") : 0);
				jout.put("lock_user", user.get("LockUser") != null ? user.get("LockUser") : 0);
				jout.put("avatar", user.get("Avatar") != null ? user.get("Avatar") : "");

				Object roleCodeObj = user.get("role_code");
				String roleCode = roleCodeObj != null ? roleCodeObj.toString() : "CHUYEN_VIEN";
				jout.put("role_code", roleCode);
				int type = "ADMIN".equalsIgnoreCase(roleCode) ? 1 : ("LANH_DAO_HV".equalsIgnoreCase(roleCode) ? 2 : ("TRUONG_DON_VI".equalsIgnoreCase(roleCode) ? 3 : 0));
				jout.put("type", type);

				JSONObject DpInfo = userExtend.getUserDepartmentInfo(user_id);
				Object finalDpId = (DpInfo != null && DpInfo.has("dept_id")) ? DpInfo.get("dept_id") : null;
				Object finalDpName = (DpInfo != null && DpInfo.has("dept_name")) ? DpInfo.get("dept_name") : null;
				Object finalDpCode = (DpInfo != null && DpInfo.has("dept_code")) ? DpInfo.get("dept_code") : null;

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

	@PostMapping("/listcbcnv")
	public String listCBCNV(@RequestBody String sReq) {
		System.out.println("----------listCBCNV:" + sReq);
		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.optString("session_id", "");
			if (!session_id.isEmpty()) {
				struct_session sst = sessionService.getSessionInfo(session_id);
				if (sst == null) {
					return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
				}
			}

			String keyword = jsonobjReq.has("keyword") ? jsonobjReq.getString("keyword").trim() : "";
			String sql;
			List<Map<String, Object>> rows;

			if (!keyword.isEmpty()) {
				sql = "SELECT p.id AS p_id, p.maCanBo, p.fullname, COALESCE(NULLIF(p.emailCanBo, ''), NULLIF(p.email, ''), u.Email, N'') AS email, " +
				      "p.sdtCaNhan, COALESCE(o3.ten, oChinh.ten, N'') AS org_name, u.ID AS user_id " +
				      "FROM personnel p WITH (NOLOCK) " +
				      "LEFT JOIN orgs o3 WITH (NOLOCK) ON o3.id = p.donViL3Id " +
				      "LEFT JOIN orgs oChinh WITH (NOLOCK) ON oChinh.id = p.donViChinhId " +
				      "LEFT JOIN ( " +
				      "    SELECT Email, MIN(CAST(ID AS VARCHAR(100))) as ID " +
				      "    FROM users WITH (NOLOCK) " +
				      "    WHERE (IsDeleted IS NULL OR IsDeleted = '0') AND ISNUMERIC(ID) = 1 AND Email IS NOT NULL AND Email <> '' " +
				      "    GROUP BY Email " +
				      ") u ON (u.Email = p.emailCanBo OR u.Email = p.email) " +
				      "WHERE (p.isDeleted IS NULL OR p.isDeleted = 0) " +
				      "  AND (p.fullname IS NOT NULL AND p.fullname <> '') " +
				      "  AND (p.fullname LIKE ? OR p.emailCanBo LIKE ? OR p.email LIKE ? OR p.maCanBo LIKE ?) " +
				      "ORDER BY p.fullname ASC";
				String pattern = "%" + keyword + "%";
				rows = jdbcTemplate.queryForList(sql, pattern, pattern, pattern, pattern);
			} else {
				sql = "SELECT p.id AS p_id, p.maCanBo, p.fullname, COALESCE(NULLIF(p.emailCanBo, ''), NULLIF(p.email, ''), u.Email, N'') AS email, " +
				      "p.sdtCaNhan, COALESCE(o3.ten, oChinh.ten, N'') AS org_name, u.ID AS user_id " +
				      "FROM personnel p WITH (NOLOCK) " +
				      "LEFT JOIN orgs o3 WITH (NOLOCK) ON o3.id = p.donViL3Id " +
				      "LEFT JOIN orgs oChinh WITH (NOLOCK) ON oChinh.id = p.donViChinhId " +
				      "LEFT JOIN ( " +
				      "    SELECT Email, MIN(CAST(ID AS VARCHAR(100))) as ID " +
				      "    FROM users WITH (NOLOCK) " +
				      "    WHERE (IsDeleted IS NULL OR IsDeleted = '0') AND ISNUMERIC(ID) = 1 AND Email IS NOT NULL AND Email <> '' " +
				      "    GROUP BY Email " +
				      ") u ON (u.Email = p.emailCanBo OR u.Email = p.email) " +
				      "WHERE (p.isDeleted IS NULL OR p.isDeleted = 0) " +
				      "  AND (p.fullname IS NOT NULL AND p.fullname <> '') " +
				      "ORDER BY p.fullname ASC";
				rows = jdbcTemplate.queryForList(sql);
			}

			for (Map<String, Object> row : rows) {
				Object emailObj = row.get("email");
				Object nameObj = row.get("fullname");
				Object userIdObj = row.get("user_id");
				Object pIdObj = row.get("p_id");
				String userId = (userIdObj != null && !userIdObj.toString().isEmpty()) 
				    ? userIdObj.toString() 
				    : (pIdObj != null ? pIdObj.toString() : "");

				JSONObject obj = new JSONObject();
				obj.put("id", userId);
				obj.put("ten_day_du", nameObj != null ? nameObj.toString() : "");
				obj.put("email", emailObj != null ? emailObj.toString() : "");
				obj.put("mobile", row.get("sdtCaNhan") != null ? row.get("sdtCaNhan").toString() : "");
				obj.put("org_name", row.get("org_name") != null ? row.get("org_name").toString() : "");
				obj.put("ma_cb", row.get("maCanBo") != null ? row.get("maCanBo").toString() : "");
				obj.put("nam_sinh", "");
				jaout.put(obj);
			}

			jout.put("user_list", jaout);
			jout.put("code", 200);
		} catch (Exception e) {
			System.err.println("Error in listCBCNV: " + e.getMessage());
			e.printStackTrace();
			return "{\"code\":800, \"description\":\"JSON/DB error: " + e.getMessage() + "\"}";
		}
		return jout.toString();
	}

	@PostMapping("/searchuser")
	public String searchUser(@RequestBody String sReq) {
		System.out.println("----------searchUser:" + sReq);
		JSONObject jout = new JSONObject();
		JSONArray jaout = new JSONArray();
		try {
			JSONObject jsonobjReq = new JSONObject(sReq);
			String session_id = jsonobjReq.getString("session_id");
			struct_session sst = sessionService.getSessionInfo(session_id);
			if (sst == null) {
				return "{\"code\":700, \"description\":\"Chưa đăng nhập\"}";
			}

			String search_key = jsonobjReq.getString("search_key");
			String sql = "SELECT a.ID, p.fullname as Fullname, a.Email, p.sdtCaNhan as Mobile, c.Name as org_name " +
					"FROM users a " +
					"LEFT JOIN personnel p ON (p.emailCanBo = a.Email OR p.email = a.Email) AND p.isDeleted = 0 " +
					"LEFT JOIN TBL_ORG_MEMBER b ON b.MEMBER_ID = a.ID " +
					"LEFT JOIN TBL_ORG c ON c.ID = b.ORG_ID " +
					"WHERE (a.IsDeleted IS NULL OR a.IsDeleted = '0') " +
					"AND (p.fullname LIKE ? OR a.Email LIKE ?)";

			String queryKey = "%" + search_key + "%";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, queryKey, queryKey);
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("id", row.get("ID"));
				obj.put("ten_day_du", row.get("Fullname"));
				obj.put("email", row.get("Email"));
				obj.put("mobile", row.get("Mobile") != null ? row.get("Mobile") : "");
				obj.put("org_name", row.get("org_name") != null ? row.get("org_name") : "");
				obj.put("ma_cb", "");
				obj.put("nam_sinh", "");
				jaout.put(obj);
			}
			jout.put("user_list", jaout);
			jout.put("code", 200);
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":800, \"description\":\"JSON/DB error\"}";
		}
		return jout.toString();
	}
}

