package com.employees;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.session.SessionService;
import com.session.struct_session;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.config.Config;

@RestController
@RequestMapping("/employees")
public class employeesServices {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
	
	
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

			String sql = "SELECT uCode, uName, uImage, uEmail, uUnit, uGender, updateType, "
					+ "FORMAT(createdAt, 'yyyy-MM-dd HH:mm:ss') as createdAt, "
					+ "FORMAT(updatedAt, 'yyyy-MM-dd HH:mm:ss') as updatedAt "
					+ "FROM employees WHERE uEmail IS NOT NULL AND uEmail <> '' ORDER BY uName ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", row.get("uImage") != null ? row.get("uImage").toString() : JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", row.get("uGender") != null ? row.get("uGender").toString() : JSONObject.NULL);
				obj.put("updateType", row.get("updateType") != null ? row.get("updateType").toString() : JSONObject.NULL);
				obj.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : JSONObject.NULL);
				obj.put("updatedAt", row.get("updatedAt") != null ? row.get("updatedAt").toString() : JSONObject.NULL);
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
		System.out.println("RES(getEmployeesList):" + jout.toString());
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

			String sql = "SELECT uCode, uName, uImage, uEmail, uUnit, uGender, updateType, "
					+ "FORMAT(createdAt, 'yyyy-MM-dd HH:mm:ss') as createdAt, "
					+ "FORMAT(updatedAt, 'yyyy-MM-dd HH:mm:ss') as updatedAt "
					+ "FROM employees WHERE uEmail IS NOT NULL AND uEmail <> '' ORDER BY uName ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", row.get("uImage") != null ? row.get("uImage").toString() : JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", row.get("uGender") != null ? row.get("uGender").toString() : JSONObject.NULL);
				obj.put("updateType", row.get("updateType") != null ? row.get("updateType").toString() : JSONObject.NULL);
				obj.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : JSONObject.NULL);
				obj.put("updatedAt", row.get("updatedAt") != null ? row.get("updatedAt").toString() : JSONObject.NULL);
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
		System.out.println("RES(getEmployeesListGet):" + jout.toString());
		return jout.toString();
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

			String sql = "SELECT dept_id, dept_code, dept_name FROM departments ORDER BY dept_name ASC";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("id", row.get("dept_id"));
				obj.put("code", row.get("dept_code"));
				obj.put("name", row.get("dept_name"));
				ja.put(obj);
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("departments", ja);
		} catch (JSONException e) {
			e.printStackTrace();
			jout.put("code", 400);
			jout.put("description", "Lỗi định dạng dữ liệu (JSON error): " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		System.out.println("RES(getDepartmentsList):" + jout.toString());
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

			String sql = "SELECT dept_id, dept_code, dept_name FROM departments ORDER BY dept_name ASC";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("id", row.get("dept_id"));
				obj.put("code", row.get("dept_code"));
				obj.put("name", row.get("dept_name"));
				ja.put(obj);
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("departments", ja);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		System.out.println("RES(getDepartmentsListGet):" + jout.toString());
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

			String sql = "SELECT uCode, uName, uImage, uEmail, uUnit, uGender, updateType, "
					+ "FORMAT(createdAt, 'yyyy-MM-dd HH:mm:ss') as createdAt, "
					+ "FORMAT(updatedAt, 'yyyy-MM-dd HH:mm:ss') as updatedAt "
					+ "FROM employees WHERE uUnit = ? AND uEmail IS NOT NULL AND uEmail <> '' ORDER BY uName ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, department.trim());
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", row.get("uImage") != null ? row.get("uImage").toString() : JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", row.get("uGender") != null ? row.get("uGender").toString() : JSONObject.NULL);
				obj.put("updateType", row.get("updateType") != null ? row.get("updateType").toString() : JSONObject.NULL);
				obj.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : JSONObject.NULL);
				obj.put("updatedAt", row.get("updatedAt") != null ? row.get("updatedAt").toString() : JSONObject.NULL);
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
		System.out.println("RES(getEmployeesByDepartment):" + jout.toString());
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

			String sql = "SELECT uCode, uName, uImage, uEmail, uUnit, uGender, updateType, "
					+ "FORMAT(createdAt, 'yyyy-MM-dd HH:mm:ss') as createdAt, "
					+ "FORMAT(updatedAt, 'yyyy-MM-dd HH:mm:ss') as updatedAt "
					+ "FROM employees WHERE uUnit = ? AND uEmail IS NOT NULL AND uEmail <> '' ORDER BY uName ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, department.trim());
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", row.get("uImage") != null ? row.get("uImage").toString() : JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", row.get("uGender") != null ? row.get("uGender").toString() : JSONObject.NULL);
				obj.put("updateType", row.get("updateType") != null ? row.get("updateType").toString() : JSONObject.NULL);
				obj.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : JSONObject.NULL);
				obj.put("updatedAt", row.get("updatedAt") != null ? row.get("updatedAt").toString() : JSONObject.NULL);
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
		System.out.println("RES(getEmployeesByDepartmentGet):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/by-email")
	public String getEmployeeByEmail(@RequestBody String sReq) {
		System.out.println("-------getEmployeeByEmail:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
			if (sessionId == null || sessionService.getSessionInfo(sessionId) == null) {
				jout.put("code", 700);
				jout.put("description", "Chưa đăng nhập");
				return jout.toString();
			}

			String email = jin.has("email") ? jin.getString("email") : null;
			if (email == null || email.trim().isEmpty()) {
				jout.put("code", 400);
				jout.put("description", "Thiếu tham số email");
				return jout.toString();
			}

			String sql = "SELECT uCode, uName, uImage, uEmail, uUnit, uGender, updateType, "
					+ "FORMAT(createdAt, 'yyyy-MM-dd HH:mm:ss') as createdAt, "
					+ "FORMAT(updatedAt, 'yyyy-MM-dd HH:mm:ss') as updatedAt "
					+ "FROM employees WHERE uEmail = ?";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, email.trim());
			if (rows.isEmpty()) {
				jout.put("code", 404);
				jout.put("description", "Không tìm thấy nhân viên");
				return jout.toString();
			}

			Map<String, Object> row = rows.get(0);
			JSONObject obj = new JSONObject();
			obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
			obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
			obj.put("uImage", row.get("uImage") != null ? row.get("uImage").toString() : JSONObject.NULL);
			obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
			obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
			obj.put("uGender", row.get("uGender") != null ? row.get("uGender").toString() : JSONObject.NULL);
			obj.put("updateType", row.get("updateType") != null ? row.get("updateType").toString() : JSONObject.NULL);
			obj.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : JSONObject.NULL);
			obj.put("updatedAt", row.get("updatedAt") != null ? row.get("updatedAt").toString() : JSONObject.NULL);

			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("employee", obj);
		} catch (JSONException e) {
			e.printStackTrace();
			jout.put("code", 400);
			jout.put("description", "Lỗi định dạng dữ liệu (JSON error): " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		System.out.println("RES(getEmployeeByEmail):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/search")
	public String searchEmployees(@RequestBody String sReq) {
		System.out.println("-------searchEmployees:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
			if (sessionId == null || sessionService.getSessionInfo(sessionId) == null) {
				jout.put("code", 700);
				jout.put("description", "Chưa đăng nhập");
				return jout.toString();
			}

			String keyword = jin.has("keyword") ? jin.getString("keyword") : null;
			if (keyword == null || keyword.trim().isEmpty()) {
				jout.put("code", 400);
				jout.put("description", "Thiếu tham số keyword");
				return jout.toString();
			}

			String searchPattern = "%" + keyword.trim() + "%";
			String sql = "SELECT uCode, uName, uImage, uEmail, uUnit, uGender, updateType, "
					+ "FORMAT(createdAt, 'yyyy-MM-dd HH:mm:ss') as createdAt, "
					+ "FORMAT(updatedAt, 'yyyy-MM-dd HH:mm:ss') as updatedAt "
					+ "FROM employees WHERE (uName LIKE ? OR uEmail LIKE ?) AND uEmail IS NOT NULL AND uEmail <> '' ORDER BY uName ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, searchPattern, searchPattern);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", row.get("uImage") != null ? row.get("uImage").toString() : JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", row.get("uGender") != null ? row.get("uGender").toString() : JSONObject.NULL);
				obj.put("updateType", row.get("updateType") != null ? row.get("updateType").toString() : JSONObject.NULL);
				obj.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : JSONObject.NULL);
				obj.put("updatedAt", row.get("updatedAt") != null ? row.get("updatedAt").toString() : JSONObject.NULL);
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
		System.out.println("RES(searchEmployees):" + jout.toString());
		return jout.toString();
	}

	@GetMapping("/search")
	public String searchEmployeesGet(
			@RequestParam("keyword") String keyword,
			@RequestParam(value = "session_id", required = false) String sessionId) {
		System.out.println("-------searchEmployeesGet: keyword=" + keyword + ", session_id=" + sessionId);
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

			if (keyword == null || keyword.trim().isEmpty()) {
				jout.put("code", 400);
				jout.put("description", "Thiếu tham số keyword");
				return jout.toString();
			}

			String searchPattern = "%" + keyword.trim() + "%";
			String sql = "SELECT uCode, uName, uImage, uEmail, uUnit, uGender, updateType, "
					+ "FORMAT(createdAt, 'yyyy-MM-dd HH:mm:ss') as createdAt, "
					+ "FORMAT(updatedAt, 'yyyy-MM-dd HH:mm:ss') as updatedAt "
					+ "FROM employees WHERE (uName LIKE ? OR uEmail LIKE ?) AND uEmail IS NOT NULL AND uEmail <> '' ORDER BY uName ASC";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, searchPattern, searchPattern);
			JSONArray ja = new JSONArray();
			for (Map<String, Object> row : rows) {
				JSONObject obj = new JSONObject();
				obj.put("uCode", row.get("uCode") != null ? row.get("uCode").toString() : JSONObject.NULL);
				obj.put("uName", row.get("uName") != null ? row.get("uName").toString() : JSONObject.NULL);
				obj.put("uImage", row.get("uImage") != null ? row.get("uImage").toString() : JSONObject.NULL);
				obj.put("uEmail", row.get("uEmail") != null ? row.get("uEmail").toString() : JSONObject.NULL);
				obj.put("uUnit", row.get("uUnit") != null ? row.get("uUnit").toString() : JSONObject.NULL);
				obj.put("uGender", row.get("uGender") != null ? row.get("uGender").toString() : JSONObject.NULL);
				obj.put("updateType", row.get("updateType") != null ? row.get("updateType").toString() : JSONObject.NULL);
				obj.put("createdAt", row.get("createdAt") != null ? row.get("createdAt").toString() : JSONObject.NULL);
				obj.put("updatedAt", row.get("updatedAt") != null ? row.get("updatedAt").toString() : JSONObject.NULL);
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
		System.out.println("RES(searchEmployeesGet):" + jout.toString());
		return jout.toString();
	}
}