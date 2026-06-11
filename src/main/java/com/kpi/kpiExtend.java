package com.kpi;

import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.db.dbconnect;

@Service
public class kpiExtend {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * Save a KPI assignment to the kpi_assignments table
	 * 
	 * @param kpiId The KPI ID to assign
	 * @param departmentId The department ID to assign to
	 * @param role The role associated with the assignment (char(1))
	 * @param assignedBy The user ID who is making the assignment
	 * @return JSONObject with response code and assignment ID
	 */
	@Transactional
	public JSONObject saveAssignment(Integer kpiId, Integer departmentId, String role, Integer assignedBy) {
		JSONObject response = new JSONObject();
		try {
			// Validate inputs
			if (kpiId == null || kpiId <= 0) {
				response.put("code", 400);
				response.put("description", "KPI ID is required and must be greater than 0");
				return response;
			}
			if (departmentId == null || departmentId <= 0) {
				response.put("code", 400);
				response.put("description", "Department ID is required and must be greater than 0");
				return response;
			}
			if (role == null || role.isEmpty()) {
				response.put("code", 400);
				response.put("description", "Role is required");
				return response;
			}
			if (assignedBy == null || assignedBy <= 0) {
				response.put("code", 400);
				response.put("description", "Assigned By user ID is required and must be greater than 0");
				return response;
			}

			// Insert into kpi_assignments table
			String sql = "INSERT INTO kpi_assignments (kpi_id, department_id, role, assigned_date, assigned_by) "
					+ "VALUES (?, ?, ?, GETDATE(), ?)";
			
			int rowsAffected = jdbcTemplate.update(sql, kpiId, departmentId, role, assignedBy);
			
			if (rowsAffected > 0) {
				// Get the inserted assignment_id
				String selectSql = "SELECT IDENT_CURRENT('kpi_assignments') as assignment_id";
				Integer assignmentId = jdbcTemplate.queryForObject(selectSql, Integer.class);
				
				response.put("code", 200);
				response.put("description", "Thành công");
				response.put("assignment_id", assignmentId);
			} else {
				response.put("code", 500);
				response.put("description", "Failed to insert assignment");
			}
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}

	/**
	 * Get list of all KPI definitions (excluding deleted ones)
	 * 
	 * @return JSONArray containing all KPI definitions
	 */
	public JSONArray getKpiDefinitions() {
		JSONArray jsaDefinitions = new JSONArray();
		try {
			// Query KPI definitions from database, excluding deleted KPIs
			String sql = "SELECT kpi_id, kpi_code, name as kpi_name, " +
					"category, unit, measurement, source, cycle " +
					"FROM kpi_definitions " +
					"WHERE (is_deleted = 0 OR is_deleted IS NULL) " +
					"ORDER BY kpi_id ASC";
			
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
			for (Map<String, Object> row : rows) {
				JSONObject joKpi = new JSONObject();
				joKpi.put("kpi_id", row.get("kpi_id"));
				joKpi.put("kpi_code", row.get("kpi_code"));
				joKpi.put("kpi_name", row.get("kpi_name"));
				joKpi.put("category", row.get("category"));
				joKpi.put("unit", row.get("unit"));
				joKpi.put("measurement", row.get("measurement"));
				joKpi.put("source", row.get("source"));
				joKpi.put("cycle", row.get("cycle"));
				jsaDefinitions.put(joKpi);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsaDefinitions;
	}

	/**
	 * Get KPI definition by ID (excluding deleted ones)
	 * 
	 * @param kpiId The ID of the KPI
	 * @return JSONObject containing KPI definition or empty if not found or deleted
	 */
	public JSONObject getKpiDefinitionById(Integer kpiId) {
		JSONObject joKpi = new JSONObject();
		try {
			String sql = "SELECT kpi_id, kpi_code, name as kpi_name, " +
					"category, unit, measurement, source, cycle " +
					"FROM kpi_definitions " +
					"WHERE kpi_id = ? AND (is_deleted = 0 OR is_deleted IS NULL)";
			
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kpiId);
			if (!rows.isEmpty()) {
				Map<String, Object> row = rows.get(0);
				joKpi.put("kpi_id", row.get("kpi_id"));
				joKpi.put("kpi_code", row.get("kpi_code"));
				joKpi.put("kpi_name", row.get("kpi_name"));
				joKpi.put("category", row.get("category"));
				joKpi.put("unit", row.get("unit"));
				joKpi.put("measurement", row.get("measurement"));
				joKpi.put("source", row.get("source"));
				joKpi.put("cycle", row.get("cycle"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return joKpi;
	}

	/**
	 * Get KPI definitions by category (excluding deleted ones)
	 * 
	 * @param category The category to filter by
	 * @return JSONArray containing KPI definitions for the category
	 */
	public JSONArray getKpiDefinitionsByCategory(String category) {
		JSONArray jsaDefinitions = new JSONArray();
		try {
			String sql = "SELECT kpi_id, kpi_code, name as kpi_name, " +
					"category, unit, measurement, source, cycle " +
					"FROM kpi_definitions " +
					"WHERE category = ? AND (is_deleted = 0 OR is_deleted IS NULL) " +
					"ORDER BY kpi_id ASC";
			
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, category);
			for (Map<String, Object> row : rows) {
				JSONObject joKpi = new JSONObject();
				joKpi.put("kpi_id", row.get("kpi_id"));
				joKpi.put("kpi_code", row.get("kpi_code"));
				joKpi.put("kpi_name", row.get("kpi_name"));
				joKpi.put("category", row.get("category"));
				joKpi.put("unit", row.get("unit"));
				joKpi.put("measurement", row.get("measurement"));
				joKpi.put("source", row.get("source"));
				joKpi.put("cycle", row.get("cycle"));
				jsaDefinitions.put(joKpi);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsaDefinitions;
	}

	/**
	 * Add a new KPI definition to the kpi_definitions table
	 * 
	 * @param code KPI code (e.g., T1.01)
	 * @param name KPI name/description
	 * @param category KPI category
	 * @param unit Unit of measurement
	 * @param measurement Calculation formula
	 * @param source Source of data collection
	 * @param cycle Reporting frequency
	 * @return JSONObject with response code and KPI ID
	 */
	@Transactional
	public JSONObject addKpiDefinition(String code, String name, String category, String unit, 
										String measurement, String source, String cycle) {
		JSONObject response = new JSONObject();
		try {
			// Validate inputs
			if (code == null || code.trim().isEmpty()) {
				response.put("code", 400);
				response.put("description", "KPI code is required");
				return response;
			}
			if (name == null || name.trim().isEmpty()) {
				response.put("code", 400);
				response.put("description", "KPI name is required");
				return response;
			}
			if (category == null || category.trim().isEmpty()) {
				response.put("code", 400);
				response.put("description", "Category is required");
				return response;
			}
			if (unit == null || unit.trim().isEmpty()) {
				response.put("code", 400);
				response.put("description", "Unit is required");
				return response;
			}
			if (measurement == null || measurement.trim().isEmpty()) {
				response.put("code", 400);
				response.put("description", "Formula is required");
				return response;
			}
			if (source == null || source.trim().isEmpty()) {
				response.put("code", 400);
				response.put("description", "Data source is required");
				return response;
			}
			if (cycle == null || cycle.trim().isEmpty()) {
				response.put("code", 400);
				response.put("description", "Frequency is required");
				return response;
			}

			// Check if code already exists
			String checkSql = "SELECT COUNT(*) as cnt FROM kpi_definitions WHERE kpi_code = ?";
			Integer count = jdbcTemplate.queryForObject(checkSql, new Object[]{code}, Integer.class);
			if (count != null && count > 0) {
				response.put("code", 409);
				response.put("description", "KPI code already exists");
				return response;
			}

			// Insert into kpi_definitions table
			String sql = "INSERT INTO kpi_definitions (kpi_code, name, category, unit, measurement, source, cycle) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?)";
			
			int rowsAffected = jdbcTemplate.update(sql, code, name, category, unit, measurement, source, cycle);
			
			if (rowsAffected > 0) {
				// Get the inserted kpi_id
				String selectSql = "SELECT IDENT_CURRENT('kpi_definitions') as kpi_id";
				Integer kpiId = jdbcTemplate.queryForObject(selectSql, Integer.class);
				
				response.put("code", 201);
				response.put("description", "Thành công");
				response.put("kpi_id", kpiId);
				response.put("kpi_code", code);
			} else {
				response.put("code", 500);
				response.put("description", "Failed to insert KPI definition");
			}
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}
	
	
	@Transactional
	public JSONObject editKpiDefinition(int kpiId, String code, String name, String category, String unit, 
										String formula, String dataSource, String frequency) {
		JSONObject response = new JSONObject();
		try {
			
			// validate kpi_id
			if (kpiId<=0) {
				response.put("code", 400);
				response.put("description", "KPI ID is required and must be greater than 0");
				return response;
			}
						
			// Check if code already exists
			String checkSql = "SELECT COUNT(*) as cnt FROM kpi_definitions WHERE code = ?";
			Integer count = jdbcTemplate.queryForObject(checkSql, new Object[]{code}, Integer.class);
			if (count != null && count > 0) {
				response.put("code", 409);
				response.put("description", "KPI code already exists");
				return response;
			}

			// Insert into kpi_definitions table
			// Build the SQL string
			StringBuilder sql = new StringBuilder("UPDATE kpi_definitions SET ");
			List<Object> parameters = new ArrayList<>();

			if (code != null) { sql.append("code = ?, "); parameters.add(code); }
			if (name != null) { sql.append("name = ?, "); parameters.add(name); }
			if (category != null) { sql.append("category = ?, "); parameters.add(category); }
			if (unit != null) { sql.append("unit = ?, "); parameters.add(unit); }
			if (formula != null) { sql.append("formula = ?, "); parameters.add(formula); }
			if (dataSource != null) { sql.append("data_source = ?, "); parameters.add(dataSource); }
			if (frequency != null) { sql.append("frequency = ?, "); parameters.add(frequency); }

			// Remove the trailing comma and space if at least one field is being updated
			if (parameters.isEmpty()) {
			    throw new IllegalArgumentException("No fields provided for update.");
			}
			sql.setLength(sql.length() - 2); 

			// Append the WHERE clause
			sql.append(" WHERE kpi_id = ?");
			parameters.add(kpiId);
			
			// Execute the query
			try (Connection conn = dbconnect.conn; // Your database connection logic
			     PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
			    
			    // Dynamically bind parameters based on the collected list
			    for (int i = 0; i < parameters.size(); i++) {
			        pstmt.setObject(i + 1, parameters.get(i));
			    }
			    
			    int rowsAffected = pstmt.executeUpdate();
			    if (rowsAffected > 0) {
					response.put("code", 201);
					response.put("description", "Thành công");
				} else {
					response.put("code", 500);
					response.put("description", "Failed to insert KPI definition");
				}
			    
			} catch (SQLException e) {
			    e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}


	/**
	 * Soft-delete a KPI definition by marking it as deleted
	 * 
	 * @param kpiId The ID of the KPI to delete
	 * @return JSONObject with response code
	 */
	@Transactional
	public JSONObject deleteKpiDefinition(Integer kpiId) {
		JSONObject response = new JSONObject();
		try {
			// Validate input
			if (kpiId == null || kpiId <= 0) {
				response.put("code", 400);
				response.put("description", "KPI ID is required and must be greater than 0");
				return response;
			}

			// Check if KPI exists and is not already deleted
			String checkSql = "SELECT COUNT(*) as cnt FROM kpi_definitions WHERE kpi_id = ? AND (is_deleted = 0 OR is_deleted IS NULL)";
			Integer count = jdbcTemplate.queryForObject(checkSql, new Object[]{kpiId}, Integer.class);
			if (count == null || count == 0) {
				response.put("code", 404);
				response.put("description", "KPI not found or already deleted");
				return response;
			}

			// Soft-delete the KPI by setting is_deleted = 1
			String sql = "UPDATE kpi_definitions SET is_deleted = 1 WHERE kpi_id = ?";
			int rowsAffected = jdbcTemplate.update(sql, kpiId);

			if (rowsAffected > 0) {
				response.put("code", 200);
				response.put("description", "Thành công");
				response.put("kpi_id", kpiId);
			} else {
				response.put("code", 500);
				response.put("description", "Failed to delete KPI");
			}
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}

	/**
	 * Get all KPI assignments for a specific department
	 * 
	 * @param departmentId The department ID
	 * @return JSONArray containing all assignments for the department
	 */
	public JSONArray getAssignmentsByDepartment(Integer departmentId) {
		JSONArray jsaAssignments = new JSONArray();
		try {
			if (departmentId == null || departmentId <= 0) {
				return jsaAssignments; // Return empty array for invalid input
			}

			// Query assignments for the department
			String sql = "SELECT assignment_id, kpi_id, department_id, role, assigned_date, assigned_by " +
					"FROM kpi_assignments " +
					"WHERE department_id = ? " +
					"ORDER BY assignment_id DESC";
			
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, departmentId);
			for (Map<String, Object> row : rows) {
				JSONObject joAssignment = new JSONObject();
				joAssignment.put("assignment_id", row.get("assignment_id"));
				joAssignment.put("kpi_id", row.get("kpi_id"));
				joAssignment.put("department_id", row.get("department_id"));
				joAssignment.put("role", row.get("role"));
				joAssignment.put("assigned_date", row.get("assigned_date"));
				joAssignment.put("assigned_by", row.get("assigned_by"));
				jsaAssignments.put(joAssignment);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsaAssignments;
	}

	/**
	 * Get KPI assignments for a department with KPI details (join query)
	 * 
	 * @param departmentId The department ID
	 * @return JSONArray containing assignments with KPI details
	 */
	public JSONArray getAssignmentsWithDetailsForDepartment(Integer departmentId) {
		JSONArray jsaAssignments = new JSONArray();
		try {
			if (departmentId == null || departmentId <= 0) {
				return jsaAssignments;
			}

			// Try to join with kpi_definitions if it exists; if not, return basic assignments
			String sql = "SELECT a.assignment_id, a.kpi_id, a.department_id, a.role, a.assigned_date, a.assigned_by, " +
					"ISNULL(k.kpi_code, '') as kpi_code, ISNULL(k.name, '') as kpi_name, ISNULL(k.category, '') as category " +
					"FROM kpi_assignments a " +
					"LEFT JOIN kpi_definitions k ON a.kpi_id = k.kpi_id " +
					"WHERE a.department_id = ? " +
					"ORDER BY a.assignment_id DESC";
			
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, departmentId);
			for (Map<String, Object> row : rows) {
				JSONObject joAssignment = new JSONObject();
				joAssignment.put("assignment_id", row.get("assignment_id"));
				joAssignment.put("kpi_id", row.get("kpi_id"));
				joAssignment.put("kpi_code", row.get("kpi_code"));
				joAssignment.put("kpi_name", row.get("kpi_name"));
				joAssignment.put("category", row.get("category"));
				joAssignment.put("department_id", row.get("department_id"));
				joAssignment.put("role", row.get("role"));
				joAssignment.put("assigned_date", row.get("assigned_date"));
				joAssignment.put("assigned_by", row.get("assigned_by"));
				jsaAssignments.put(joAssignment);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsaAssignments;
	}

	/**
	 * Get all KPI definitions with assignment status for a department.
	 * If a KPI is not assigned to the department, assigned=false and assignment fields are null.
	 * 
	 * @param departmentId The department to check assignments for (can be null to indicate no department)
	 * @return JSONArray of KPIs with assignment status
	 */
	public JSONArray getKpisWithAssignmentStatus(Integer departmentId) {
		JSONArray jsa = new JSONArray();
		try {
			String sql;
			List<Map<String, Object>> rows;
			if (departmentId == null || departmentId <= 0) {
				// No department specified: return all KPIs with assigned=false
				sql = "SELECT k.kpi_id, k.kpi_code, k.name as kpi_name, k.category, k.unit, k.measurement, k.source, k.cycle, "
					+ "NULL as assignment_id, NULL as role, NULL as assigned_date, NULL as assigned_by "
					+ "FROM kpi_definitions k "
					+ "WHERE (k.is_deleted = 0 OR k.is_deleted IS NULL) "
					+ "ORDER BY k.kpi_id ASC";
				rows = jdbcTemplate.queryForList(sql);
			} else {
				// Join to assignments for the given department (take any matching assignment)
				sql = "SELECT k.kpi_id, k.kpi_code, k.name as kpi_name, k.category, k.unit, k.measurement, k.source, k.cycle, "
					+ "a.assignment_id, a.role, a.assigned_date, a.assigned_by "
					+ "FROM kpi_definitions k "
					+ "LEFT JOIN (SELECT assignment_id, kpi_id, role, assigned_date, assigned_by FROM kpi_assignments WHERE department_id = ?) a "
					+ "ON a.kpi_id = k.kpi_id "
					+ "WHERE (k.is_deleted = 0 OR k.is_deleted IS NULL) "
					+ "ORDER BY k.kpi_id ASC";
				rows = jdbcTemplate.queryForList(sql, departmentId);
			}

			for (Map<String, Object> row : rows) {
				JSONObject jo = new JSONObject();
				jo.put("kpi_id", row.get("kpi_id"));
				jo.put("kpi_code", row.get("kpi_code"));
				jo.put("kpi_name", row.get("kpi_name"));
				jo.put("category", row.get("category"));
				jo.put("unit", row.get("unit"));
				jo.put("measurement", row.get("measurement"));
				jo.put("source", row.get("source"));
				jo.put("cycle", row.get("cycle"));

				Object assignmentId = row.get("assignment_id");
				if (assignmentId == null) {
					jo.put("assigned", false);
					jo.put("assignment_id", JSONObject.NULL);
					jo.put("role", JSONObject.NULL);
					jo.put("assigned_date", JSONObject.NULL);
					jo.put("assigned_by", JSONObject.NULL);
				} else {
					jo.put("assigned", true);
					jo.put("assignment_id", assignmentId);
					jo.put("role", row.get("role"));
					jo.put("assigned_date", row.get("assigned_date"));
					jo.put("assigned_by", row.get("assigned_by"));
				}
				jsa.put(jo);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsa;
	}

	/**
	 * Get all KPI definitions along with their assignments and data points.
	 * If a KPI has no assignments, it is marked as unassigned.
	 * 
	 * @return JSONArray of all KPIs with assignments and data points
	 */
	public JSONArray getKpisWithAssignments() {
		JSONArray jsaResult = new JSONArray();
		try {
			// 1. Fetch all active KPI definitions
			String kpiSql = "SELECT kpi_id, kpi_code, name as kpi_name, category, unit, measurement, source, cycle " +
							"FROM kpi_definitions " +
							"WHERE (is_deleted = 0 OR is_deleted IS NULL) " +
							"ORDER BY kpi_id ASC";
			List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(kpiSql);

			// 2. Fetch all KPI assignments
			String assignSql = "SELECT assignment_id, kpi_id, department_id, role, assigned_date, assigned_by " +
							   "FROM kpi_assignments";
			List<Map<String, Object>> assignRows = jdbcTemplate.queryForList(assignSql);

			// Group assignments by kpi_id
			java.util.Map<Integer, List<JSONObject>> assignmentsMap = new java.util.HashMap<>();
			for (Map<String, Object> row : assignRows) {
				Integer kpiId = (Integer) row.get("kpi_id");
				if (kpiId != null) {
					JSONObject joAssign = new JSONObject();
					joAssign.put("assignment_id", row.get("assignment_id"));
					joAssign.put("department_id", row.get("department_id"));
					joAssign.put("role", row.get("role") != null ? row.get("role").toString().trim() : JSONObject.NULL);
					joAssign.put("assigned_date", row.get("assigned_date") != null ? row.get("assigned_date").toString() : JSONObject.NULL);
					joAssign.put("assigned_by", row.get("assigned_by"));
					
					assignmentsMap.computeIfAbsent(kpiId, k -> new ArrayList<>()).add(joAssign);
				}
			}

			// 3. Fetch all KPI data points
			String dpSql = "SELECT data_id, kpi_id, period, target_value, actual_value, status, updated_at " +
						   "FROM kpi_data_points";
			List<Map<String, Object>> dpRows = jdbcTemplate.queryForList(dpSql);

			// Group data points by kpi_id
			java.util.Map<Integer, List<JSONObject>> dpMap = new java.util.HashMap<>();
			for (Map<String, Object> row : dpRows) {
				Integer kpiId = (Integer) row.get("kpi_id");
				if (kpiId != null) {
					JSONObject joDp = new JSONObject();
					joDp.put("data_id", row.get("data_id"));
					joDp.put("period", row.get("period"));
					joDp.put("target_value", row.get("target_value"));
					joDp.put("actual_value", row.get("actual_value"));
					joDp.put("status", row.get("status"));
					joDp.put("updated_at", row.get("updated_at") != null ? row.get("updated_at").toString() : JSONObject.NULL);

					dpMap.computeIfAbsent(kpiId, k -> new ArrayList<>()).add(joDp);
				}
			}

			// 4. Assemble the final result
			for (Map<String, Object> kpiRow : kpiRows) {
				Integer kpiId = (Integer) kpiRow.get("kpi_id");
				JSONObject joKpi = new JSONObject();
				joKpi.put("kpi_id", kpiId);
				joKpi.put("kpi_code", kpiRow.get("kpi_code"));
				joKpi.put("kpi_name", kpiRow.get("kpi_name"));
				joKpi.put("category", kpiRow.get("category"));
				joKpi.put("unit", kpiRow.get("unit"));
				joKpi.put("measurement", kpiRow.get("measurement"));
				joKpi.put("source", kpiRow.get("source"));
				joKpi.put("cycle", kpiRow.get("cycle"));

				List<JSONObject> assignments = assignmentsMap.get(kpiId);
				if (assignments == null || assignments.isEmpty()) {
					joKpi.put("assignment_status", "unassigned");
					joKpi.put("assignments", new JSONArray());
				} else {
					joKpi.put("assignment_status", "assigned");
					JSONArray jsaAssigns = new JSONArray();
					for (JSONObject joAssign : assignments) {
						jsaAssigns.put(joAssign);
					}
					joKpi.put("assignments", jsaAssigns);
				}

				List<JSONObject> dataPoints = dpMap.get(kpiId);
				JSONArray jsaDps = new JSONArray();
				if (dataPoints != null) {
					for (JSONObject joDp : dataPoints) {
						jsaDps.put(joDp);
					}
				}
				joKpi.put("data_points", jsaDps);

				jsaResult.put(joKpi);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsaResult;
	}
}