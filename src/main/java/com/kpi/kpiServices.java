package com.kpi;

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
@RequestMapping("/kpi")
public class kpiServices {

	@Autowired
	private kpiExtend kpiExtend;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	// TODO change table name departments as it does not exist in the database
	private Integer resolveKpiId(String kpiCode) {
		if (kpiCode == null || kpiCode.isEmpty())
			return null;
		try {
			return jdbcTemplate.queryForObject(
					"SELECT kpi_id FROM kpi_definitions WHERE kpi_code = ? AND (is_deleted = 0 OR is_deleted IS NULL)",
					Integer.class, kpiCode.trim());
		} catch (Exception e) {
			System.err.println("Could not resolve kpi_id for code: " + kpiCode);
		}
		return null;
	}

	private Integer resolveDepartmentId(String name) {
		if (name == null || name.trim().isEmpty())
			return null;
		try {
			return jdbcTemplate.queryForObject(
					"SELECT TOP 1 ID FROM TBL_ORG WHERE Name = ? AND (IsDeleted is null or IsDeleted='0')",
					Integer.class, name.trim());
		} catch (Exception e) {
			try {
				return jdbcTemplate.queryForObject(
						"SELECT TOP 1 ID FROM TBL_ORG WHERE Name LIKE ? AND (IsDeleted is null or IsDeleted='0') ORDER BY ID ASC",
						Integer.class, "%" + name.trim() + "%");
			} catch (Exception ex) {
				String lowerName = name.toLowerCase().trim();
				if (lowerName.contains("khảo thí") || lowerName.contains("đbclgd") || lowerName.contains("đảm bảo chất lượng")
						|| lowerName.contains("kt&dbclgd")) {
					return 1;
				} else if (lowerName.contains("đào tạo") || lowerName.contains("khoa") || lowerName.contains("viện")
						|| lowerName.contains("cơ bản")) {
					return 2;
				} else if (lowerName.contains("ctsv") || lowerName.contains("sinh viên") || lowerName.contains("chính trị")) {
					return 3;
				} else if (lowerName.contains("văn phòng học viện") || lowerName.contains("vp học viện")) {
					return 4;
				}
				return 4;
			}
		}
	}

	/**
	 * GET /kpi/definitions - Get all KPI definitions
	 * 
	 * Response JSON format:
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "definitions": [
	 * {
	 * "kpi_id": 4,
	 * "kpi_code": "T1.01",
	 * "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
	 * "category": "T – Đào tạo & Người học",
	 * "unit": "%",
	 * "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
	 * "data_source": "P. Đào tạo",
	 * "frequency": "Năm học"
	 * }
	 * ]
	 * }
	 */
	@GetMapping("/definitions")
	public String getKpiDefinitions() {
		System.out.println("-------getKpiDefinitions");
		JSONObject jout = new JSONObject();
		try {
			JSONArray definitions = kpiExtend.getKpiDefinitions();
			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("definitions", definitions);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Server error: " + e.getMessage());
		}
		System.out.println("RES(getKpiDefinitions):" + jout.toString());
		return jout.toString();
	}

	/**
	 * GET /kpi/definitions/{kpiId} - Get specific KPI definition by ID
	 * 
	 * Response JSON format:
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "kpi_id": 4,
	 * "kpi_code": "T1.01",
	 * "kpi_name": "Tỷ lệ sinh viên nhập học/ tuyển sinh theo kế hoạch",
	 * "category": "T – Đào tạo & Người học",
	 * "unit": "%",
	 * "formula": "Số SV nhập học thực tế/ chỉ tiêu tuyển sinh × 100%",
	 * "data_source": "P. Đào tạo",
	 * "frequency": "Năm học"
	 * }
	 */
	@GetMapping("/definitions/{kpiId}")
	public String getKpiDefinitionById(@PathVariable Integer kpiId) {
		System.out.println("-------getKpiDefinitionById:" + kpiId);
		JSONObject jout = new JSONObject();
		try {
			JSONObject definition = kpiExtend.getKpiDefinitionById(kpiId);

			if (definition.length() == 0) {
				jout.put("code", 404);
				jout.put("description", "KPI not found");
			} else {
				jout.put("code", 200);
				jout.put("description", "Thành công");
				jout.put("kpi_id", definition.get("kpi_id"));
				jout.put("kpi_code", definition.get("kpi_code"));
				jout.put("kpi_name", definition.get("kpi_name"));
				jout.put("category", definition.get("category"));
				jout.put("unit", definition.get("unit"));
				jout.put("measurement", definition.get("measurement"));
				jout.put("source", definition.get("source"));
				jout.put("cycle", definition.get("cycle"));
			}
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Server error: " + e.getMessage());
		}
		System.out.println("RES(getKpiDefinitionById):" + jout.toString());
		return jout.toString();
	}

	/**
	 * GET /kpi/definitions/category?category=T - Get KPI definitions by category
	 * 
	 * Query Parameters:
	 * - category (optional): Filter by category
	 * 
	 * Response JSON format:
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "category": "T – Đào tạo & Người học",
	 * "definitions": [...]
	 * }
	 */
	@GetMapping("/definitions/by-category")
	public String getKpiDefinitionsByCategory(@RequestParam String category) {
		System.out.println("-------getKpiDefinitionsByCategory:" + category);
		JSONObject jout = new JSONObject();
		try {
			JSONArray definitions = kpiExtend.getKpiDefinitionsByCategory(category);
			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("category", category);
			jout.put("definitions", definitions);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Server error: " + e.getMessage());
		}
		System.out.println("RES(getKpiDefinitionsByCategory):" + jout.toString());
		return jout.toString();
	}

	/**
	 * GET /kpi/vertex-data/by-category - Get vertex data (KPI definitions, assignments,
	 * data points, and normalized values) filtered by a specific category.
	 * 
	 * Response JSON format:
	 * {
	 *   "code": 200,
	 *   "description": "Thành công",
	 *   "category": "T – Đào tạo & Người học (Training)",
	 *   "kpis": [...]
	 * }
	 */
	@GetMapping("/vertex-data/by-category")
	public String getVertexDataByCategory(@RequestParam String category) {
		System.out.println("-------getVertexDataByCategory:" + category);
		JSONObject jout = new JSONObject();
		try {
			JSONArray kpis = kpiExtend.getVertexDataByCategory(category);
			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("category", category);
			jout.put("kpis", kpis);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Server error: " + e.getMessage());
		}
		System.out.println("RES(getVertexDataByCategory):" + jout.toString());
		return jout.toString();
	}


	/**
	 * POST /kpi/definition/add - Add a new KPI definition
	 * 
	 * Request JSON format:
	 * {
	 * "code": "T1.11",
	 * "name": "Tỷ lệ sinh viên hài lòng về chương trình đào tạo",
	 * "category": "T – Đào tạo & Người học (Training)",
	 * "unit": "%",
	 * "formula": "SV hài lòng/ tổng SV khảo sát × 100%",
	 * "data_source": "Khảo sát trực tuyến",
	 * "frequency": "Năm học"
	 * }
	 * 
	 * Response JSON format (Success):
	 * {
	 * "code": 201,
	 * "description": "Thành công",
	 * "kpi_id": 65,
	 * "kpi_code": "T1.11"
	 * }
	 * 
	 * Response JSON format (Conflict - code already exists):
	 * {
	 * "code": 409,
	 * "description": "KPI code already exists"
	 * }
	 * 
	 * Response JSON format (Bad Request - missing fields):
	 * {
	 * "code": 400,
	 * "description": "KPI code is required"
	 * }
	 */
	@PostMapping("/definition/add")
	public String addKpiDefinition(@RequestBody String sReq) {
		System.out.println("-------addKpiDefinition:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);

			// Extract parameters
			String code = jin.has("code") ? jin.getString("code").trim() : null;
			String name = jin.has("name") ? jin.getString("name").trim() : null;
			String category = jin.has("category") ? jin.getString("category").trim() : null;
			String unit = jin.has("unit") ? jin.getString("unit").trim() : null;
			String formula = jin.has("measurement") ? jin.getString("measurement").trim() : null;
			String dataSource = jin.has("source") ? jin.getString("source").trim() : null;
			String frequency = jin.has("cycle") ? jin.getString("cycle").trim() : null;

			// Call service to add KPI definition
			JSONObject addResult = kpiExtend.addKpiDefinition(code, name, category, unit, formula, dataSource,
					frequency);

			jout.put("code", addResult.getInt("code"));
			jout.put("description", addResult.getString("description"));

			// Add kpi_id and kpi_code if present in response (on success)
			if (addResult.has("kpi_id")) {
				jout.put("kpi_id", addResult.getInt("kpi_id"));
			}
			if (addResult.has("kpi_code")) {
				jout.put("kpi_code", addResult.getString("kpi_code"));
			}

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"" + "Server error: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(addKpiDefinition):" + jout.toString());
		return jout.toString();
	}

	/**
	 * DELETE /kpi/definition/{kpiId} - Delete (soft-delete) a KPI definition
	 * 
	 * Path Parameters:
	 * - kpiId: The ID of the KPI to delete
	 * 
	 * Response JSON format (Success):
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "kpi_id": 65
	 * }
	 * 
	 * Response JSON format (Not Found):
	 * {
	 * "code": 404,
	 * "description": "KPI not found or already deleted"
	 * }
	 * 
	 * Response JSON format (Bad Request):
	 * {
	 * "code": 400,
	 * "description": "KPI ID is required and must be greater than 0"
	 * }
	 */
	@DeleteMapping("/definition/{kpiId}")
	public String deleteKpiDefinition(@PathVariable Integer kpiId) {
		System.out.println("-------deleteKpiDefinition:" + kpiId);
		JSONObject jout = new JSONObject();
		try {
			// Call service to delete KPI definition
			JSONObject deleteResult = kpiExtend.deleteKpiDefinition(kpiId);

			jout.put("code", deleteResult.getInt("code"));
			jout.put("description", deleteResult.getString("description"));

			// Add kpi_id if present in response
			if (deleteResult.has("kpi_id")) {
				jout.put("kpi_id", deleteResult.getInt("kpi_id"));
			}

		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"" + "Server error: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(deleteKpiDefinition):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/definition/edit")
	public String editKpiDefinition(@RequestBody String sReq) {
		System.out.println("-------editKpiDefinition:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);

			// Extract parameters
			int kpiId = jin.has("kpi_id") ? jin.getInt("kpi_id") : 0;
			String code = jin.has("code") ? jin.getString("code").trim() : null;
			String name = jin.has("name") ? jin.getString("name").trim() : null;
			String category = jin.has("category") ? jin.getString("category").trim() : null;
			String unit = jin.has("unit") ? jin.getString("unit").trim() : null;
			String formula = jin.has("formula") ? jin.getString("formula").trim() : null;
			String dataSource = jin.has("data_source") ? jin.getString("data_source").trim() : null;
			String frequency = jin.has("frequency") ? jin.getString("frequency").trim() : null;

			// Call service to add KPI definition
			JSONObject editResult = kpiExtend.editKpiDefinition(kpiId, code, name, category, unit, formula, dataSource,
					frequency);

			jout.put("code", editResult.getInt("code"));
			jout.put("description", editResult.getString("description"));

		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"" + "Server error: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(editKpiDefinition):" + jout.toString());
		return jout.toString();
	}

	/**
	 * POST /kpi/assign - Save a KPI assignment to database
	 * 
	 * Request JSON format:
	 * {
	 * "session_id": "user_session_id",
	 * "kpi_id": 1,
	 * "department_id": 5,
	 * "role": "A"
	 * }
	 * 
	 * Response JSON format:
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "assignment_id": 123
	 * }
	 */
	@PostMapping("/assign")
	public String assignKpi(@RequestBody String sReq) {
		System.out.println("-------assignKpi:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);

			// Validate session
			/*
			 * String session_id = jin.getString("session_id");
			 * struct_session sst = sessionService.getSessionInfo(session_id);
			 * if (sst == null) {
			 * return "{\"code\":" + 700 + ", \"description\":\"" + "Chưa đăng nhập" +
			 * "\"}";
			 * }
			 */

			// Extract parameters
			JSONArray jsAssignments = jin.has("data") ? jin.getJSONArray("data") : null;
			if (jsAssignments == null || jsAssignments.length() == 0) {
				return "{\"code\":" + 400 + ", \"description\":\"" + "Dữ liệu phân công KPI không hợp lệ" + "\"}";
			}

			System.out.println("Received " + jsAssignments.length() + " assignments to save.");
			System.out.println(jsAssignments.toString());
			for (int i = 0; i < jsAssignments.length(); i++) {
				JSONObject assignment = jsAssignments.getJSONObject(i);

				System.out.println(assignment.toString());

				Integer kpiId = null;
				if (assignment.has("kpiCode")) {
					kpiId = resolveKpiId(assignment.getString("kpiCode"));
				}
				if (kpiId == null && assignment.has("kpi_id")) {
					kpiId = assignment.getInt("kpi_id");
				}

				Integer departmentId = null;
				if (assignment.has("departmentName")) {
					departmentId = resolveDepartmentId(assignment.getString("departmentName"));
				}
				if (departmentId == null && assignment.has("department_id")) {
					departmentId = assignment.getInt("department_id");
				}

				String role = assignment.has("role") ? assignment.getString("role") : "A";
				Integer assignedBy = 10000000;

				if (kpiId != null) {
					System.out.println("Saving/Deleting assignment: kpiId=" + kpiId + ", departmentId=" + departmentId
							+ ", role=" + role + ", assignedBy=" + assignedBy);
					kpiExtend.saveAssignment(kpiId, departmentId, role, assignedBy);
				} else {
					System.err.println("Skipping assignment due to unresolved KPI ID: kpiId=" + kpiId);
				}
			}

			jout.put("code", 200);
			jout.put("description", "Thành công");
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\":" + 800 + ", \"description\":\"" + "JSON error: Thiếu tham số?" + e + "\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\":" + 500 + ", \"description\":\"" + "Server error: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(assignKpi):" + jout.toString());
		return jout.toString();
	}

	/**
	 * GET /kpi/assignments/{departmentId} - Get all KPI assignments for a
	 * department
	 * 
	 * Path Parameters:
	 * - departmentId: The ID of the department/organization
	 * 
	 * Response JSON format:
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "department_id": 5,
	 * "assignments": [
	 * {
	 * "assignment_id": 1,
	 * "kpi_id": 4,
	 * "department_id": 5,
	 * "role": "A",
	 * "assigned_date": "2026-06-10T10:30:00",
	 * "assigned_by": 1
	 * }
	 * ]
	 * }
	 */
	@GetMapping("/assignments/{departmentId}")
	public String getAssignmentsByDepartment(@PathVariable Integer departmentId) {
		System.out.println("-------getAssignmentsByDepartment:" + departmentId);
		JSONObject jout = new JSONObject();
		try {
			if (departmentId == null || departmentId <= 0) {
				jout.put("code", 400);
				jout.put("description", "Department ID is required and must be greater than 0");
				return jout.toString();
			}

			JSONArray assignments = kpiExtend.getAssignmentsByDepartment(departmentId);
			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("department_id", departmentId);
			jout.put("assignments", assignments);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Server error: " + e.getMessage());
		}
		System.out.println("RES(getAssignmentsByDepartment):" + jout.toString());
		return jout.toString();
	}

	/**
	 * GET /kpi/assignments/{departmentId}/details - Get KPI assignments for a
	 * department with KPI details
	 * 
	 * Path Parameters:
	 * - departmentId: The ID of the department/organization
	 * 
	 * Response JSON format:
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "department_id": 5,
	 * "assignments": [
	 * {
	 * "assignment_id": 1,
	 * "kpi_id": 4,
	 * "kpi_code": "T1.01",
	 * "kpi_name": "Tỷ lệ sinh viên nhập học",
	 * "category": "T – Đào tạo & Người học",
	 * "department_id": 5,
	 * "role": "A",
	 * "assigned_date": "2026-06-10T10:30:00",
	 * "assigned_by": 1
	 * }
	 * ]
	 * }
	 */
	@GetMapping("/assignments/{departmentId}/details")
	public String getAssignmentsWithDetailsByDepartment(@PathVariable Integer departmentId) {
		System.out.println("-------getAssignmentsWithDetailsByDepartment:" + departmentId);
		JSONObject jout = new JSONObject();
		try {
			if (departmentId == null || departmentId <= 0) {
				jout.put("code", 400);
				jout.put("description", "Department ID is required and must be greater than 0");
				return jout.toString();
			}

			JSONArray assignments = kpiExtend.getAssignmentsWithDetailsForDepartment(departmentId);
			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("department_id", departmentId);
			jout.put("assignments", assignments);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Server error: " + e.getMessage());
		}
		System.out.println("RES(getAssignmentsWithDetailsByDepartment):" + jout.toString());
		return jout.toString();
	}

	/**
	 * GET /kpi/definitions/with-assignments - Get all KPIs with their assignments
	 * and data points.
	 * If a KPI has no assignments, it is marked as unassigned.
	 * 
	 * Response JSON format:
	 * {
	 * "code": 200,
	 * "description": "Thành công",
	 * "kpis": [
	 * {
	 * "kpi_id": 5,
	 * "kpi_code": "T1.02",
	 * "kpi_name": "Tỷ lệ duy trì sinh viên qua các năm học",
	 * "category": "T – Đào tạo & Người học",
	 * "unit": "%",
	 * "measurement": "...",
	 * "source": "...",
	 * "cycle": "...",
	 * "assignment_status": "assigned",
	 * "assignments": [
	 * {
	 * "assignment_id": 4,
	 * "department_id": 1,
	 * "role": "A",
	 * "assigned_date": "2026-06-10 11:25:46.337",
	 * "assigned_by": 10000000
	 * }
	 * ],
	 * "data_points": [
	 * {
	 * "data_id": 12,
	 * "period": "2025-2026",
	 * "target_value": 95.0,
	 * "actual_value": 94.2,
	 * "status": "Completed",
	 * "updated_at": "2026-06-10 12:00:00"
	 * }
	 * ]
	 * }
	 * ]
	 * }
	 */
	@GetMapping({"/definitions/with-assignments", "/with-assignments"})
	public String getKpiDefinitionsWithAssignments() {
		System.out.println("-------getKpiDefinitionsWithAssignments");
		JSONObject jout = new JSONObject();
		try {
			JSONArray kpis = kpiExtend.getKpisWithAssignments();
			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("kpis", kpis);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Server error: " + e.getMessage());
		}
		System.out.println("RES(getKpiDefinitionsWithAssignments):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/update-weights")
	public String updateKpiWeights(@RequestBody String sReq) {
		System.out.println("-------updateKpiWeights:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			JSONArray weightsArray = jin.getJSONArray("weights");
			
			JSONObject result = kpiExtend.updateKpiWeights(weightsArray);
			jout.put("code", result.getInt("code"));
			jout.put("description", result.getString("description"));
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\": 400, \"description\": \"JSON error: Invalid payload format.\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\": 500, \"description\": \"Server error: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(updateKpiWeights):" + jout.toString());
		return jout.toString();
	}

	@GetMapping("/deadlines/config")
	public String getDeadlinesConfig() {
		System.out.println("-------getDeadlinesConfig");
		JSONObject result = kpiExtend.getDeadlinesConfig();
		System.out.println("RES(getDeadlinesConfig):" + result.toString());
		return result.toString();
	}

	@PostMapping("/deadlines/save-defaults")
	public String saveCycleDefaults(@RequestBody String sReq) {
		System.out.println("-------saveCycleDefaults:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			JSONArray cycles = jin.getJSONArray("cycles");
			JSONObject result = kpiExtend.saveCycleDefaults(cycles);
			jout.put("code", result.getInt("code"));
			jout.put("description", result.getString("description"));
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\": 400, \"description\": \"JSON error: Invalid payload format.\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\": 500, \"description\": \"Server error: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(saveCycleDefaults):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/deadlines/save-kpi-override")
	public String saveKpiDeadlineOverride(@RequestBody String sReq) {
		System.out.println("-------saveKpiDeadlineOverride:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			int kpiId = jin.getInt("kpi_id");
			Boolean hasOverride = jin.getBoolean("has_override");
			Integer offsetDays = jin.has("override_deadline_offset_days") && !jin.isNull("override_deadline_offset_days") ? jin.getInt("override_deadline_offset_days") : 0;
			Integer offsetWeeks = jin.has("override_deadline_offset_weeks") && !jin.isNull("override_deadline_offset_weeks") ? jin.getInt("override_deadline_offset_weeks") : 0;
			String absoluteDate = jin.has("override_absolute_deadline_date") && !jin.isNull("override_absolute_deadline_date") ? jin.getString("override_absolute_deadline_date") : null;

			JSONObject result = kpiExtend.saveKpiDeadlineOverride(kpiId, hasOverride, offsetDays, offsetWeeks, absoluteDate);
			jout.put("code", result.getInt("code"));
			jout.put("description", result.getString("description"));
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"code\": 400, \"description\": \"JSON error: Invalid payload format.\"}";
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"code\": 500, \"description\": \"Server error: " + e.getMessage() + "\"}";
		}
		System.out.println("RES(saveKpiDeadlineOverride):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/upload-evidence")
	public String uploadEvidence(
			@RequestParam("file") MultipartFile file,
			@RequestParam("session_id") String sessionId,
			@RequestParam("kpi_code") String kpiCode) {
		System.out.println("-------uploadEvidence:" + kpiCode);
		JSONObject jout = new JSONObject();
		try {
			struct_session sst = sessionService.getSessionInfo(sessionId);
			if (sst == null) {
				jout.put("code", 401);
				jout.put("description", "Phiên đăng nhập hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.");
				return jout.toString();
			}

			if (file.isEmpty()) {
				jout.put("code", 400);
				jout.put("description", "Không nhận được tệp tải lên.");
				return jout.toString();
			}

			String originalName = file.getOriginalFilename();
			String safeKpiCode = kpiCode.replaceAll("[^a-zA-Z0-9_-]", "");
			String uploadDir = Config.homeDir + "/kpi/evidence/" + safeKpiCode;
			File destFolder = new File(uploadDir);
			if (!destFolder.exists()) {
				destFolder.mkdirs();
			}

			File destFile = new File(destFolder, originalName);
			file.transferTo(destFile);

			String virtualUrl = "/kpi/evidence/" + safeKpiCode + "/" + originalName;
			long bytes = file.getSize();
			String formattedSize;
			if (bytes >= 1024 * 1024) {
				formattedSize = String.format("%.1f MB", (double) bytes / (1024 * 1024));
			} else {
				formattedSize = String.format("%.1f KB", (double) bytes / 1024);
			}

			jout.put("code", 200);
			jout.put("description", "Tải tệp minh chứng thành công");
			jout.put("file_url", virtualUrl);
			jout.put("file_name", originalName);
			jout.put("file_size", formattedSize);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi tải tệp: " + e.getMessage());
		}
		System.out.println("RES(uploadEvidence):" + jout.toString());
		return jout.toString();
	}

	@PostMapping("/value/save")
	public String saveKpiValue(@RequestBody String sReq) {
		System.out.println("-------saveKpiValue:" + sReq);
		JSONObject jout = new JSONObject();
		try {
			JSONObject jin = new JSONObject(sReq);
			String sessionId = jin.getString("session_id");
			int kpiId = jin.getInt("kpi_id");
			Integer deptId = jin.has("department_id") && !jin.isNull("department_id") ? jin.getInt("department_id") : null;
			double actualValue = jin.getDouble("actual_value");
			String notes = jin.has("notes") && !jin.isNull("notes") ? jin.getString("notes") : "";
			String evidenceLink = jin.has("evidence_link") && !jin.isNull("evidence_link") ? jin.getString("evidence_link") : "";
			String fileName = jin.has("evidence_file_name") && !jin.isNull("evidence_file_name") ? jin.getString("evidence_file_name") : "";
			String fileSize = jin.has("evidence_file_size") && !jin.isNull("evidence_file_size") ? jin.getString("evidence_file_size") : "";
			
			String testDateStr = jin.has("test_date") && !jin.isNull("test_date") ? jin.getString("test_date") : null;
			Date referenceDate = null;
			if (testDateStr != null && !testDateStr.trim().isEmpty()) {
				try {
					referenceDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(testDateStr.trim());
				} catch (Exception ex) {
					System.err.println("Invalid test_date format: " + testDateStr);
				}
			}

			struct_session sst = sessionService.getSessionInfo(sessionId);
			if (sst == null) {
				jout.put("code", 401);
				jout.put("description", "Phiên đăng nhập hết hạn hoặc không hợp lệ. Vui lòng đăng nhập lại.");
				return jout.toString();
			}

			// Check admin status
			boolean isAdmin = false;
			try {
				List<Map<String, Object>> userRows = jdbcTemplate.queryForList("SELECT IsAdmin, Email FROM tbl_user WHERE ID = ?", sst.UserID);
				if (!userRows.isEmpty()) {
					Map<String, Object> uRow = userRows.get(0);
					int adminVal = uRow.get("IsAdmin") != null ? ((Number) uRow.get("IsAdmin")).intValue() : 0;
					String email = uRow.get("Email") != null ? uRow.get("Email").toString() : "";
					if (adminVal == 1 || "admin@ptit.edu.vn".equalsIgnoreCase(email)) {
						isAdmin = true;
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			JSONObject result = kpiExtend.saveKpiValue(kpiId, deptId, actualValue, notes, evidenceLink, fileName, fileSize, sst.UserID, isAdmin, referenceDate);
			jout.put("code", result.getInt("code"));
			jout.put("description", result.getString("description"));
			if (result.has("data_id")) jout.put("data_id", result.get("data_id"));
			if (result.has("period_id")) jout.put("period_id", result.get("period_id"));
			if (result.has("version_number")) jout.put("version_number", result.get("version_number"));
		} catch (JSONException e) {
			e.printStackTrace();
			jout.put("code", 400);
			jout.put("description", "Lỗi định dạng dữ liệu (JSON error): " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		System.out.println("RES(saveKpiValue):" + jout.toString());
		return jout.toString();
	}

	@GetMapping("/value/history")
	public String getKpiValueHistory(
			@RequestParam("kpi_code") String kpiCode,
			@RequestParam(value = "department_id", required = false) Integer departmentId) {
		System.out.println("-------getKpiValueHistory:" + kpiCode + ", dept:" + departmentId);
		JSONObject jout = new JSONObject();
		try {
			Integer kpiId = resolveKpiId(kpiCode);
			if (kpiId == null) {
				jout.put("code", 404);
				jout.put("description", "Không tìm thấy chỉ số KPI.");
				return jout.toString();
			}

			JSONArray history = kpiExtend.getKpiValueHistory(kpiId, departmentId);
			jout.put("code", 200);
			jout.put("description", "Thành công");
			jout.put("history", history);
		} catch (Exception e) {
			e.printStackTrace();
			jout.put("code", 500);
			jout.put("description", "Lỗi máy chủ: " + e.getMessage());
		}
		System.out.println("RES(getKpiValueHistory):" + jout.toString());
		return jout.toString();
	}
}