package com.employees;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/employees")
public class employeesServices {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
	
	private static final String EXCLUDE_ROOT_ID = "66a308ce8068e53428da2033"; // Học viện - Nam
	private static final String VP_HOC_VIEN_ID = "66a308ce8068e53428da2035"; // Văn phòng Học viện (level 2, treated as level 3)
	private static final String LD_HOC_VIEN_ID = "66a308ce8068e53428da202c"; // Lãnh đạo Học viện - Bắc (level 2, treated as level 3)
	private static final String LD_HOC_VIEN_NAM_ID = "66a308ce8068e53428da202d"; // Lãnh đạo Học viện - Nam (level 2, treated as level 3)

	@PostMapping("/list")
	public String getEmployeesList(@RequestBody String sReq) {
		System.out.println("-------getEmployeesList:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
			if (sessionId == null || sessionService.getSessionInfo(sessionId) == null) {
				jout.put("code", 700);
				jout.put("description", "Chưa đăng nhập");
				return jout.toString();
			}

			String sql = 
				"SELECT p.id AS uCode, p.fullname AS uName, p.emailCanBo AS uEmail, " +
				"COALESCE(o3.ten, oChinh.ten, N'N/A') AS uUnit " +
				"FROM personnel p " +
				"LEFT JOIN orgs o3 ON o3.id = p.donViL3Id " +
				"LEFT JOIN orgs oChinh ON oChinh.id = p.donViChinhId " +
				"LEFT JOIN orgs p2 ON p2.id = o3.donViChaId " +
				"WHERE p.isDeleted = 0 AND (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '') " +
				"  AND (p2.donViChaId IS NULL OR p2.donViChaId <> ?) " +
				"ORDER BY p.fullname ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, EXCLUDE_ROOT_ID);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", JSONObject.NULL);
				obj.put("updateType", "personnel_api");
				ja.put(obj);
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("employees", ja);
		} catch (JSONException e) {
			e.printStackTrace();
			jout.put("code", 400);
			jout.put("description", "Lỗi định dạng dữ liệu (JSON error): " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		// System.out.println("RES(getEmployeesList):" + jout.toString());
		return jout.toString();
	}

	@GetMapping("/list")
	public String getEmployeesListGet(@RequestParam(value = "session_id", required = false) String sessionId) {
		System.out.println("-------getEmployeesListGet:" + sessionId);
		JSONObject jout = new JSONObject();
		try {
			if (sessionId != null) {
				struct_session sst = sessionService.getSessionInfo(sessionId);
				if (sst == null) {
					jout.put("code", 700);
					jout.put("description", "Chưa đăng nhập");
					return jout.toString();
				}
			}

			String sql = 
				"SELECT p.id AS uCode, p.fullname AS uName, p.emailCanBo AS uEmail, " +
				"COALESCE(o3.ten, oChinh.ten, N'N/A') AS uUnit " +
				"FROM personnel p " +
				"LEFT JOIN orgs o3 ON o3.id = p.donViL3Id " +
				"LEFT JOIN orgs oChinh ON oChinh.id = p.donViChinhId " +
				"LEFT JOIN orgs p2 ON p2.id = o3.donViChaId " +
				"WHERE p.isDeleted = 0 AND (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '') " +
				"  AND (p2.donViChaId IS NULL OR p2.donViChaId <> ?) " +
				"ORDER BY p.fullname ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, EXCLUDE_ROOT_ID);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", JSONObject.NULL);
				obj.put("updateType", "personnel_api");
				ja.put(obj);
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("employees", ja);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		// System.out.println("RES(getEmployeesListGet):" + jout.toString());
		return jout.toString();
	}

	private static JSONArray cachedDepartments = null;
	private static long lastDeptCacheTime = 0;
	private static final long DEPT_CACHE_TTL = 60000; // 60 seconds TTL

	public synchronized JSONArray getCachedDepartments() {
		long now = System.currentTimeMillis();
		if (cachedDepartments != null && (now - lastDeptCacheTime) < DEPT_CACHE_TTL) {
			return cachedDepartments;
		}
		try {
			String sql = 
				"SELECT o.id AS dept_id, o.maDonVi AS dept_code, o.ten AS dept_name, o.leaderName AS leader_name " +
				"FROM orgs o WITH (NOLOCK) " +
				"LEFT JOIN orgs p2 WITH (NOLOCK) ON p2.id = o.donViChaId " +
				"WHERE (o.level = 3 OR o.id IN (?, ?, ?)) AND ISNULL(o.isDeleted, 0) = 0 " +
				"  AND ISNULL(p2.donViChaId, '') <> ? " +
				"ORDER BY o.ten ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, VP_HOC_VIEN_ID, LD_HOC_VIEN_ID, LD_HOC_VIEN_NAM_ID, EXCLUDE_ROOT_ID);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("id", row.get("dept_id"));
				obj.put("code", row.get("dept_code") != null ? row.get("dept_code") : "");
				obj.put("name", row.get("dept_name"));
				obj.put("leader_name", row.get("leader_name") != null ? row.get("leader_name") : "");
				ja.put(obj);
			}
			cachedDepartments = ja;
			lastDeptCacheTime = now;
			return ja;
		} catch (Exception e) {
			e.printStackTrace();
			return cachedDepartments != null ? cachedDepartments : new JSONArray();
		}
	}

	public static void invalidateDepartmentsCache() {
		cachedDepartments = null;
		lastDeptCacheTime = 0;
	}

	@PostMapping("/departments")
	public String getDepartmentsList(@RequestBody String sReq) {
		System.out.println("-------getDepartmentsList:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
			if (sessionId == null || sessionService.getSessionInfo(sessionId) == null) {
				jout.put("code", 700);
				jout.put("description", "Chưa đăng nhập");
				return jout.toString();
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("departments", getCachedDepartments());
		} catch (JSONException e) {
			e.printStackTrace();
			jout.put("code", 400);
			jout.put("description", "Lỗi định dạng dữ liệu (JSON error): " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		//System.out.println("RES(getDepartmentsList):" + jout.toString());
		return jout.toString();
	}

	@GetMapping("/departments")
	public String getDepartmentsListGet(@RequestParam(value = "session_id", required = false) String sessionId) {
		System.out.println("-------getDepartmentsListGet:" + sessionId);
		JSONObject jout = new JSONObject();
		try {
			if (sessionId != null) {
				struct_session sst = sessionService.getSessionInfo(sessionId);
				if (sst == null) {
					jout.put("code", 700);
					jout.put("description", "Chưa đăng nhập");
					return jout.toString();
				}
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("departments", getCachedDepartments());
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		// System.out.println("RES(getDepartmentsListGet):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/by-department")
	public String getEmployeesByDepartment(@RequestBody String sReq) {
		System.out.println("-------getEmployeesByDepartment:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
			if (sessionId == null || sessionService.getSessionInfo(sessionId) == null) {
				jout.put("code", 700);
				jout.put("description", "Chưa đăng nhập");
				return jout.toString();
			}

			String department = jin.has("department") ? jin.getString("department") : null;
			if (department == null || department.trim().isEmpty()) {
				jout.put("code", 400);
				jout.put("description", "Thiếu tham số department");
				return jout.toString();
			}

			String deptParam = department.trim();
			String sql = 
				"SELECT p.id AS uCode, p.fullname AS uName, p.emailCanBo AS uEmail, " +
				"COALESCE(o3.ten, oChinh.ten, N'N/A') AS uUnit " +
				"FROM personnel p " +
				"LEFT JOIN orgs o3 ON o3.id = p.donViL3Id " +
				"LEFT JOIN orgs oChinh ON oChinh.id = p.donViChinhId " +
				"LEFT JOIN orgs p2 ON p2.id = o3.donViChaId " +
				"WHERE p.isDeleted = 0 AND (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '') " +
				"  AND (p2.donViChaId IS NULL OR p2.donViChaId <> ?) " +
				"  AND (p.donViL3Id = ? OR o3.id = ? OR o3.ten = ? OR p.donViChinhId = ? OR oChinh.ten = ?) " +
				"ORDER BY p.fullname ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, EXCLUDE_ROOT_ID, deptParam, deptParam, deptParam, deptParam, deptParam);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", JSONObject.NULL);
				obj.put("updateType", "personnel_api");
				ja.put(obj);
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("employees", ja);
		} catch (JSONException e) {
			e.printStackTrace();
			jout.put("code", 400);
			jout.put("description", "Lỗi định dạng dữ liệu (JSON error): " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		// System.out.println("RES(getEmployeesByDepartment):" + jout.toString());
		return jout.toString();
	}

	@GetMapping("/by-department")
	public String getEmployeesByDepartmentGet(
			@RequestParam("department") String department,
			@RequestParam(value = "session_id", required = false) String sessionId) {
		System.out.println("-------getEmployeesByDepartmentGet: department=" + department + ", session_id=" + sessionId);
		JSONObject jout = new JSONObject();
		try {
			if (sessionId != null) {
				struct_session sst = sessionService.getSessionInfo(sessionId);
				if (sst == null) {
					jout.put("code", 700);
					jout.put("description", "Chưa đăng nhập");
					return jout.toString();
				}
			}

			if (department == null || department.trim().isEmpty()) {
				jout.put("code", 400);
				jout.put("description", "Thiếu tham số department");
				return jout.toString();
			}

			String deptParam = department.trim();
			String sql = 
				"SELECT p.id AS uCode, p.fullname AS uName, p.emailCanBo AS uEmail, " +
				"COALESCE(o3.ten, oChinh.ten, N'N/A') AS uUnit " +
				"FROM personnel p " +
				"LEFT JOIN orgs o3 ON o3.id = p.donViL3Id " +
				"LEFT JOIN orgs oChinh ON oChinh.id = p.donViChinhId " +
				"LEFT JOIN orgs p2 ON p2.id = o3.donViChaId " +
				"WHERE p.isDeleted = 0 AND (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '') " +
				"  AND (p2.donViChaId IS NULL OR p2.donViChaId <> ?) " +
				"  AND (p.donViL3Id = ? OR o3.id = ? OR o3.ten = ? OR p.donViChinhId = ? OR oChinh.ten = ?) " +
				"ORDER BY p.fullname ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, EXCLUDE_ROOT_ID, deptParam, deptParam, deptParam, deptParam, deptParam);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", JSONObject.NULL);
				obj.put("updateType", "personnel_api");
				ja.put(obj);
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("employees", ja);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		// System.out.println("RES(getEmployeesByDepartmentGet):" + jout.toString());
		return jout.toString();
	}
}