package com.kpi;

import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

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
			if (kpiId == null || kpiId <= 0) {
				response.put("code", 400);
				response.put("description", "KPI ID is required and must be greater than 0");
				return response;
			}
			if (departmentId == null || departmentId <= 0) {
				String deleteSql = "DELETE FROM kpi_assignments WHERE kpi_id = ?";
				jdbcTemplate.update(deleteSql, kpiId);
				response.put("code", 200);
				response.put("description", "Thành công (Đã xóa phân công)");
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

			// Check if KPI assignment already exists for the given kpiId
			String checkSql = "SELECT COUNT(*) FROM kpi_assignments WHERE kpi_id = ?";
			Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, kpiId);

			if (count != null && count > 0) {
				// Update existing assignment
				String updateSql = "UPDATE kpi_assignments SET department_id = ?, role = ?, assigned_date = GETDATE(), assigned_by = ? WHERE kpi_id = ?";
				int rowsAffected = jdbcTemplate.update(updateSql, departmentId, role, assignedBy, kpiId);
				
				if (rowsAffected > 0) {
					// Get the assignment_id of the updated row
					String selectSql = "SELECT TOP 1 assignment_id FROM kpi_assignments WHERE kpi_id = ? ORDER BY assignment_id DESC";
					Integer assignmentId = jdbcTemplate.queryForObject(selectSql, Integer.class, kpiId);
					
					response.put("code", 200);
					response.put("description", "Thành công");
					response.put("assignment_id", assignmentId);
				} else {
					response.put("code", 500);
					response.put("description", "Failed to update assignment");
				}
			} else {
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
			String sql = "SELECT k.kpi_id, k.kpi_code, k.name as kpi_name, " +
					"k.category, k.unit, k.measurement, k.source, k.cycle, ISNULL(m.weight, 1.0) as weight " +
					"FROM kpi_definitions k " +
					"LEFT JOIN vertex_members m ON k.kpi_id = m.kpi_id " +
					"WHERE (k.is_deleted = 0 OR k.is_deleted IS NULL) " +
					"ORDER BY k.kpi_id ASC";
			
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
				joKpi.put("weight", row.get("weight") != null ? ((Number) row.get("weight")).doubleValue() : 1.0);
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
			String sql = "SELECT k.kpi_id, k.kpi_code, k.name as kpi_name, " +
					"k.category, k.unit, k.measurement, k.source, k.cycle, ISNULL(m.weight, 1.0) as weight " +
					"FROM kpi_definitions k " +
					"LEFT JOIN vertex_members m ON k.kpi_id = m.kpi_id " +
					"WHERE kpi_id = ? AND (k.is_deleted = 0 OR k.is_deleted IS NULL)";
			
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
				joKpi.put("weight", row.get("weight") != null ? ((Number) row.get("weight")).doubleValue() : 1.0);
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
			String sql = "SELECT k.kpi_id, k.kpi_code, k.name as kpi_name, " +
					"k.category, k.unit, k.measurement, k.source, k.cycle, ISNULL(m.weight, 1.0) as weight " +
					"FROM kpi_definitions k " +
					"LEFT JOIN vertex_members m ON k.kpi_id = m.kpi_id " +
					"WHERE k.category = ? AND (k.is_deleted = 0 OR k.is_deleted IS NULL) " +
					"ORDER BY k.kpi_id ASC";
			
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
				joKpi.put("weight", row.get("weight") != null ? ((Number) row.get("weight")).doubleValue() : 1.0);
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
	 * @param target KPI target
	 * @return JSONObject with response code and KPI ID
	 */
	@Transactional
	public JSONObject addKpiDefinition(String code, String name, String category, String unit, 
										String measurement, String source, String cycle, String target) {
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
			String sql = "INSERT INTO kpi_definitions (kpi_code, name, category, unit, measurement, source, cycle, target) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
			
			int rowsAffected = jdbcTemplate.update(sql, code, name, category, unit, measurement, source, cycle, target);
			
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
			int rowsAffected = jdbcTemplate.update(sql.toString(), parameters.toArray());
			if (rowsAffected > 0) {
				response.put("code", 201);
				response.put("description", "Thành công");
			} else {
				response.put("code", 500);
				response.put("description", "Failed to update KPI definition");
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
			String kpiSql = "SELECT k.kpi_id, k.kpi_code, k.name as kpi_name, k.category, k.unit, k.measurement, k.source, k.cycle, ISNULL(m.weight, 1.0) as weight, " +
							"k.cycle_id, c.cycle_type, k.target, " +
							"k.override_deadline_offset_days, k.override_deadline_offset_weeks, k.override_absolute_deadline_date, " +
							"c.default_deadline_offset_days, c.default_deadline_offset_weeks, c.deadline_type, v.vertex_name " +
							"FROM kpi_definitions k " +
							"LEFT JOIN vertex_members m ON k.kpi_id = m.kpi_id " +
							"LEFT JOIN vertices_def v ON m.vertex_id = v.vertex_id " +
							"LEFT JOIN cycle_definitions c ON k.cycle_id = c.cycle_id " +
							"WHERE (k.is_deleted = 0 OR k.is_deleted IS NULL) " +
							"ORDER BY k.kpi_id ASC";
			List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(kpiSql);

			java.util.Map<Integer, java.sql.Date> cycleEndDates = new java.util.HashMap<>();
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy");
			java.util.Date refDate = new java.util.Date();

			// 2. Fetch all KPI assignments
			String assignSql = "SELECT a.assignment_id, a.kpi_id, a.department_id, a.role, a.assigned_date, a.assigned_by, o.dept_name as department_name " +
							   "FROM kpi_assignments a LEFT JOIN departments o ON a.department_id = o.dept_id";
			List<Map<String, Object>> assignRows = jdbcTemplate.queryForList(assignSql);

			// Group assignments by kpi_id
			java.util.Map<Integer, List<JSONObject>> assignmentsMap = new java.util.HashMap<>();
			for (Map<String, Object> row : assignRows) {
				Integer kpiId = (Integer) row.get("kpi_id");
				if (kpiId != null) {
					JSONObject joAssign = new JSONObject();
					joAssign.put("assignment_id", row.get("assignment_id"));
					joAssign.put("department_id", row.get("department_id"));
					joAssign.put("department_name", row.get("department_name") != null ? row.get("department_name").toString() : JSONObject.NULL);
					joAssign.put("role", row.get("role") != null ? row.get("role").toString().trim() : JSONObject.NULL);
					joAssign.put("assigned_date", row.get("assigned_date") != null ? row.get("assigned_date").toString() : JSONObject.NULL);
					joAssign.put("assigned_by", row.get("assigned_by"));
					assignmentsMap.computeIfAbsent(kpiId, k -> new ArrayList<>()).add(joAssign);
				}
			}

			// 3. Fetch all KPI data points
			String dpSql = "SELECT dp.data_id, dp.kpi_id, dp.period_id, dp.actual_value, k.target, dp.status_id, dp.updated_at, dp.department_id, dp.notes, dp.evidence_link, dp.evidence_file_name, dp.evidence_file_size, dp.evidence_file_uploaded_at, pi.period_code " +
						   "FROM kpi_data_points dp " +
						   "INNER JOIN kpi_definitions k ON dp.kpi_id = k.kpi_id " +
						   "LEFT JOIN period_instances pi ON dp.period_id = pi.period_id";
			List<Map<String, Object>> dpRows = jdbcTemplate.queryForList(dpSql);
 
			// Group data points by kpi_id
			java.util.Map<Integer, List<JSONObject>> dpMap = new java.util.HashMap<>();
			for (Map<String, Object> row : dpRows) {
				Integer kpiId = (Integer) row.get("kpi_id");
				if (kpiId != null) {
					JSONObject joDp = new JSONObject();
					joDp.put("data_id", row.get("data_id"));
					joDp.put("period_id", row.get("period_id"));
					joDp.put("period", row.get("period_code") != null ? row.get("period_code") : row.get("period_id"));
					
					Object targetObj = row.get("target");
					Object targetVal = JSONObject.NULL;
					if (targetObj != null) {
						try {
							targetVal = Double.parseDouble(targetObj.toString().trim());
						} catch (Exception e) {
							targetVal = targetObj.toString();
						}
					}
					joDp.put("target_value", targetVal);
					
					joDp.put("actual_value", row.get("actual_value"));
					joDp.put("status_id", row.get("status_id"));
					joDp.put("status", row.get("status_id"));
					joDp.put("updated_at", row.get("updated_at") != null ? row.get("updated_at").toString() : JSONObject.NULL);
					joDp.put("department_id", row.get("department_id") != null ? row.get("department_id") : JSONObject.NULL);
					joDp.put("notes", row.get("notes") != null ? row.get("notes").toString() : JSONObject.NULL);
					joDp.put("evidence_link", row.get("evidence_link") != null ? row.get("evidence_link").toString() : JSONObject.NULL);
					joDp.put("evidence_file_name", row.get("evidence_file_name") != null ? row.get("evidence_file_name").toString() : JSONObject.NULL);
					joDp.put("evidence_file_size", row.get("evidence_file_size") != null ? row.get("evidence_file_size").toString() : JSONObject.NULL);
					joDp.put("evidence_file_uploaded_at", row.get("evidence_file_uploaded_at") != null ? row.get("evidence_file_uploaded_at").toString() : JSONObject.NULL);

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
				joKpi.put("weight", kpiRow.get("weight") != null ? ((Number) kpiRow.get("weight")).doubleValue() : 1.0);
				joKpi.put("vertex_name", kpiRow.get("vertex_name") != null ? kpiRow.get("vertex_name") : JSONObject.NULL);
				
				Object targetObj = kpiRow.get("target");
				Object targetVal = JSONObject.NULL;
				if (targetObj != null) {
					try {
						targetVal = Double.parseDouble(targetObj.toString().trim());
					} catch (Exception e) {
						targetVal = targetObj.toString();
					}
				}
				joKpi.put("target", targetVal);

				Integer cycleId = kpiRow.get("cycle_id") != null ? ((Number) kpiRow.get("cycle_id")).intValue() : null;
				String cycleType = (String) kpiRow.get("cycle_type");
				java.util.Date currDeadline = null;

				if (cycleId != null && cycleType != null) {
					java.sql.Date currEndDate = cycleEndDates.get(cycleId);
					if (currEndDate == null) {
						int currPeriodId = getOrCreatePeriodInstance(cycleId, cycleType, refDate);
						List<Map<String, Object>> currPeriodDetail = jdbcTemplate.queryForList("SELECT end_date FROM period_instances WHERE period_id = ?", currPeriodId);
						if (!currPeriodDetail.isEmpty()) {
							currEndDate = (java.sql.Date) currPeriodDetail.get(0).get("end_date");
							cycleEndDates.put(cycleId, currEndDate);
						}
					}

					if (currEndDate != null) {
						if (kpiRow.get("override_absolute_deadline_date") != null) {
							currDeadline = (java.sql.Date) kpiRow.get("override_absolute_deadline_date");
						} else {
							int offsetDays = kpiRow.get("override_deadline_offset_days") != null ? ((Number) kpiRow.get("override_deadline_offset_days")).intValue() : 
								(kpiRow.get("default_deadline_offset_days") != null ? ((Number) kpiRow.get("default_deadline_offset_days")).intValue() : 0);
							int offsetWeeks = kpiRow.get("override_deadline_offset_weeks") != null ? ((Number) kpiRow.get("override_deadline_offset_weeks")).intValue() : 
								(kpiRow.get("default_deadline_offset_weeks") != null ? ((Number) kpiRow.get("default_deadline_offset_weeks")).intValue() : 0);
							String deadlineType = kpiRow.get("deadline_type") != null ? (String) kpiRow.get("deadline_type") : "CALENDAR";
							
							currDeadline = calculateDeadline(currEndDate, offsetDays, offsetWeeks, deadlineType);
						}
					}
				}
				
				joKpi.put("deadline", currDeadline != null ? sdf.format(currDeadline) : JSONObject.NULL);

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


	/**
	 * Get vertex data (KPI definitions, assignments, and integrated data points containing normalized scores)
	 * filtered by a specific category.
	 * * @param category the category to filter by
	 * @return JSONArray of KPIs belonging to the category, with their associated assignments and combined data points.
	 */
	public JSONArray getVertexDataByCategory(String category) {
	    JSONArray jsaResult = new JSONArray();
	    try {
	        // 1. Fetch KPI definitions matching category
	        String kpiSql = "SELECT k.kpi_id, k.kpi_code, k.name as kpi_name, k.category, k.unit, k.measurement, k.source, k.cycle, ISNULL(m.weight, 1.0) as weight, v.vertex_name " +
	                        "FROM kpi_definitions k " +
	                        "LEFT JOIN vertex_members m ON k.kpi_id = m.kpi_id " +
	                        "LEFT JOIN vertices_def v ON m.vertex_id = v.vertex_id " +
	                        "WHERE k.category = ? AND (k.is_deleted = 0 OR k.is_deleted IS NULL) " +
	                        "ORDER BY k.kpi_id ASC";
	        List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(kpiSql, category);

	        // Extract KPIs IDs to target sub-queries for optimized fetch performance
	        if (kpiRows.isEmpty()) {
	            return jsaResult;
	        }

	        // 2. Fetch KPI assignments only for the scoped category 
	        String assignSql = "SELECT a.assignment_id, a.kpi_id, a.department_id, a.role, a.assigned_date, a.assigned_by " +
	                           "FROM kpi_assignments a " +
	                           "INNER JOIN kpi_definitions k ON a.kpi_id = k.kpi_id " +
	                           "WHERE k.category = ? AND (k.is_deleted = 0 OR k.is_deleted IS NULL)";
	        List<Map<String, Object>> assignRows = jdbcTemplate.queryForList(assignSql, category);

	        // Group assignments by kpi_id
	        Map<Integer, List<JSONObject>> assignmentsMap = new HashMap<>();
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

	        // 3. Fetch integrated KPI data points (Now contains actual, target, and normalized_score combined)
	        String dpSql = "SELECT dp.data_id, dp.kpi_id, dp.period_id, dp.actual_value, dp.target_value, dp.normalized_score, " +
	                       "dp.status_id, dp.updated_at, dp.department_id, dp.notes, dp.evidence_link, " +
	                       "dp.evidence_file_name, dp.evidence_file_size, dp.evidence_file_uploaded_at, pi.period_code " +
	                       "FROM kpi_data_points dp " +
	                       "INNER JOIN kpi_definitions k ON dp.kpi_id = k.kpi_id " +
	                       "LEFT JOIN period_instances pi ON dp.period_id = pi.period_id " +
	                       "WHERE k.category = ? AND (k.is_deleted = 0 OR k.is_deleted IS NULL)";
	        List<Map<String, Object>> dpRows = jdbcTemplate.queryForList(dpSql, category);

	        // Group combined data points by kpi_id
	        Map<Integer, List<JSONObject>> dpMap = new HashMap<>();
	        for (Map<String, Object> row : dpRows) {
	            Integer kpiId = (Integer) row.get("kpi_id");
	            if (kpiId != null) {
	                JSONObject joDp = new JSONObject();
	                joDp.put("data_id", row.get("data_id"));
	                joDp.put("period_id", row.get("period_id"));
	                joDp.put("period", row.get("period_code") != null ? row.get("period_code") : row.get("period_id"));
	                joDp.put("actual_value", row.get("actual_value") != null ? ((Number) row.get("actual_value")).doubleValue() : JSONObject.NULL);
	                
	                Object targetObj = row.get("target");
	                Object targetVal = JSONObject.NULL;
	                if (targetObj != null) {
	                    try {
	                        targetVal = Double.parseDouble(targetObj.toString().trim());
	                    } catch (Exception e) {
	                        targetVal = targetObj.toString();
	                    }
	                }
	                joDp.put("target_value", targetVal);
	                
	                // Mapped from combined table layout
	                joDp.put("normalized_value", row.get("normalized_value") != null ? ((Number) row.get("normalized_value")).doubleValue() : JSONObject.NULL);
	                
	                joDp.put("status_id", row.get("status_id"));
	                joDp.put("status", row.get("status_id"));
	                joDp.put("updated_at", row.get("updated_at") != null ? row.get("updated_at").toString() : JSONObject.NULL);
	                joDp.put("department_id", row.get("department_id") != null ? row.get("department_id") : JSONObject.NULL);
	                joDp.put("notes", row.get("notes") != null ? row.get("notes").toString() : JSONObject.NULL);
	                joDp.put("evidence_link", row.get("evidence_link") != null ? row.get("evidence_link").toString() : JSONObject.NULL);
	                joDp.put("evidence_file_name", row.get("evidence_file_name") != null ? row.get("evidence_file_name").toString() : JSONObject.NULL);
	                joDp.put("evidence_file_size", row.get("evidence_file_size") != null ? row.get("evidence_file_size").toString() : JSONObject.NULL);
	                joDp.put("evidence_file_uploaded_at", row.get("evidence_file_uploaded_at") != null ? row.get("evidence_file_uploaded_at").toString() : JSONObject.NULL);

	                dpMap.computeIfAbsent(kpiId, k -> new ArrayList<>()).add(joDp);
	            }
	        }

	        // 4. Assemble clean structural object tree
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
	            joKpi.put("weight", kpiRow.get("weight") != null ? ((Number) kpiRow.get("weight")).doubleValue() : 1.0);
	            joKpi.put("vertex_name", kpiRow.get("vertex_name") != null ? kpiRow.get("vertex_name") : JSONObject.NULL);

	            // Populate Assignments Mapping
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

	            // Populate Optimized Data points node (which now natively holds normalized values!)
	            List<JSONObject> dataPoints = dpMap.get(kpiId);
	            JSONArray jsaDps = new JSONArray();
	            if (dataPoints != null) {
	                for (JSONObject joDp : dataPoints) {
	                    jsaDps.put(joDp);
	                }
	            }
	            joKpi.put("data_points", jsaDps);

	            // Backward Compatibility: Keeps frontend components from breaking if they look for the legacy node
	            joKpi.put("normalized_values", jsaDps); 

	            jsaResult.put(joKpi);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return jsaResult;
	}

	@Transactional
	public JSONObject updateKpiWeights(JSONArray weightsArray) {
		JSONObject response = new JSONObject();
		try {
			String getKpiInfo = "SELECT kpi_code FROM kpi_definitions WHERE kpi_id = ?";
			String getVertexId = "SELECT vertex_id FROM vertices_def WHERE vertex_code = ?";
			String checkExists = "SELECT COUNT(*) FROM vertex_members WHERE kpi_id = ?";
			String updateSql = "UPDATE vertex_members SET weight = ? WHERE kpi_id = ?";
			String insertSql = "INSERT INTO vertex_members (vertex_id, kpi_id, weight) VALUES (?, ?, ?)";
			
			for (int i = 0; i < weightsArray.length(); i++) {
				JSONObject item = weightsArray.getJSONObject(i);
				int kpiId = item.getInt("kpi_id");
				double weight = item.getDouble("weight");
				
				// 1. Resolve vertex_code from kpi_code
				String kpiCode = jdbcTemplate.queryForObject(getKpiInfo, String.class, kpiId);
				if (kpiCode == null || kpiCode.isEmpty()) {
					continue;
				}
				String vertexCode = kpiCode.substring(0, 1);
				
				// 2. Get vertex_id from vertices_def
				Integer vertexId = jdbcTemplate.queryForObject(getVertexId, Integer.class, vertexCode);
				if (vertexId == null) {
					continue;
				}
				
				// 3. Update or Insert
				Integer exists = jdbcTemplate.queryForObject(checkExists, Integer.class, kpiId);
				if (exists != null && exists > 0) {
					jdbcTemplate.update(updateSql, weight, kpiId);
				} else {
					jdbcTemplate.update(insertSql, vertexId, kpiId, weight);
				}
			}
			response.put("code", 200);
			response.put("description", "Thành công");
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}

	public JSONObject getDeadlinesConfig() {
		JSONObject response = new JSONObject();
		try {
			// Ensure defaults exist
			String countSql = "SELECT COUNT(*) FROM cycle_definitions";
			Integer count = 0;
			try {
				count = jdbcTemplate.queryForObject(countSql, Integer.class);
			} catch (Exception e) {
				System.err.println("Error checking cycle_definitions: " + e.getMessage());
			}
			if (count == null || count == 0) {
				try {
					jdbcTemplate.update("INSERT INTO cycle_definitions (cycle_type, start_month, start_day, duration_months, duration_weeks, default_deadline_offset_days, default_deadline_offset_weeks, deadline_type) VALUES ('GLOBAL_DEFAULT', 1, 1, 12, 0, 5, 0, 'BUSINESS')");
					jdbcTemplate.update("INSERT INTO cycle_definitions (cycle_type, start_month, start_day, duration_months, duration_weeks, default_deadline_offset_days, default_deadline_offset_weeks, deadline_type) VALUES ('monthly', 1, 1, 1, 0, 5, 0, 'CALENDAR')");
					jdbcTemplate.update("INSERT INTO cycle_definitions (cycle_type, start_month, start_day, duration_months, duration_weeks, default_deadline_offset_days, default_deadline_offset_weeks, deadline_type) VALUES ('quarterly', 1, 1, 3, 0, 15, 0, 'CALENDAR')");
					jdbcTemplate.update("INSERT INTO cycle_definitions (cycle_type, start_month, start_day, duration_months, duration_weeks, default_deadline_offset_days, default_deadline_offset_weeks, deadline_type) VALUES ('semester', 1, 1, 6, 0, 15, 0, 'CALENDAR')");
					jdbcTemplate.update("INSERT INTO cycle_definitions (cycle_type, start_month, start_day, duration_months, duration_weeks, default_deadline_offset_days, default_deadline_offset_weeks, deadline_type) VALUES ('yearly', 1, 1, 12, 0, 30, 0, 'CALENDAR')");
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			// 1. Fetch cycle defaults
			JSONArray jsaCycleDefaults = new JSONArray();
			JSONObject joGlobalDefault = null;

			String sqlCycles = "SELECT cycle_id, cycle_type, start_month, start_day, duration_months, duration_weeks, default_deadline_offset_days, default_deadline_offset_weeks, deadline_type FROM cycle_definitions";
			List<Map<String, Object>> cycleRows = jdbcTemplate.queryForList(sqlCycles);
			for (Map<String, Object> row : cycleRows) {
				JSONObject joCycle = new JSONObject();
				joCycle.put("cycle_id", row.get("cycle_id"));
				String cycleType = (String) row.get("cycle_type");
				joCycle.put("cycle_type", cycleType);
				joCycle.put("start_month", row.get("start_month"));
				joCycle.put("start_day", row.get("start_day"));
				joCycle.put("duration_months", row.get("duration_months"));
				joCycle.put("duration_weeks", row.get("duration_weeks"));
				joCycle.put("default_deadline_offset_days", row.get("default_deadline_offset_days"));
				joCycle.put("default_deadline_offset_weeks", row.get("default_deadline_offset_weeks"));
				joCycle.put("deadline_type", row.get("deadline_type"));

				if ("GLOBAL_DEFAULT".equalsIgnoreCase(cycleType)) {
					joGlobalDefault = joCycle;
				} else {
					jsaCycleDefaults.put(joCycle);
				}
			}

			if (joGlobalDefault == null) {
				joGlobalDefault = new JSONObject();
				joGlobalDefault.put("cycle_type", "GLOBAL_DEFAULT");
				joGlobalDefault.put("default_deadline_offset_days", 5);
				joGlobalDefault.put("default_deadline_offset_weeks", 0);
				joGlobalDefault.put("deadline_type", "BUSINESS");
			}

			response.put("globalDefault", joGlobalDefault);
			response.put("cycleDefaults", jsaCycleDefaults);

			// 2. Fetch KPIs and deadline settings
			JSONArray jsaKpis = new JSONArray();
			String sqlKpis = "SELECT k.kpi_id, k.kpi_code, k.name, k.category, k.cycle, k.cycle_id, " +
							 "k.override_deadline_offset_days, k.override_deadline_offset_weeks, k.override_absolute_deadline_date " +
							 "FROM kpi_definitions k " +
							 "WHERE (k.is_deleted = 0 OR k.is_deleted IS NULL) " +
							 "ORDER BY k.kpi_code ASC";
			List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(sqlKpis);
			for (Map<String, Object> row : kpiRows) {
				JSONObject joKpi = new JSONObject();
				joKpi.put("kpi_id", row.get("kpi_id"));
				joKpi.put("kpi_code", row.get("kpi_code"));
				joKpi.put("kpi_name", row.get("name"));
				joKpi.put("category", row.get("category"));
				joKpi.put("cycle", row.get("cycle"));
				joKpi.put("cycle_id", row.get("cycle_id") != null ? row.get("cycle_id") : JSONObject.NULL);
				joKpi.put("override_deadline_offset_days", row.get("override_deadline_offset_days") != null ? row.get("override_deadline_offset_days") : JSONObject.NULL);
				joKpi.put("override_deadline_offset_weeks", row.get("override_deadline_offset_weeks") != null ? row.get("override_deadline_offset_weeks") : JSONObject.NULL);
				joKpi.put("override_absolute_deadline_date", row.get("override_absolute_deadline_date") != null ? row.get("override_absolute_deadline_date").toString() : JSONObject.NULL);
				jsaKpis.put(joKpi);
			}
			response.put("kpis", jsaKpis);
			response.put("code", 200);
			response.put("description", "Thành công");
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}

	@Transactional
	public JSONObject saveCycleDefaults(JSONArray cycles) {
		JSONObject response = new JSONObject();
		try {
			String updateSql = "UPDATE cycle_definitions SET default_deadline_offset_days = ?, default_deadline_offset_weeks = ?, deadline_type = ? WHERE cycle_type = ?";
			String checkSql = "SELECT COUNT(*) FROM cycle_definitions WHERE cycle_type = ?";
			String insertSql = "INSERT INTO cycle_definitions (cycle_type, start_month, start_day, duration_months, duration_weeks, default_deadline_offset_days, default_deadline_offset_weeks, deadline_type) VALUES (?, 1, 1, 1, 0, ?, ?, ?)";

			for (int i = 0; i < cycles.length(); i++) {
				JSONObject c = cycles.getJSONObject(i);
				String cycleType = c.optString("cycle_type");
				if (cycleType.isEmpty()) {
					continue;
				}
				int offsetDays = c.optInt("default_deadline_offset_days", 0);
				int offsetWeeks = c.optInt("default_deadline_offset_weeks", 0);
				String deadlineType = c.optString("deadline_type", "CALENDAR");

				Integer exists = jdbcTemplate.queryForObject(checkSql, Integer.class, cycleType);
				if (exists != null && exists > 0) {
					jdbcTemplate.update(updateSql, offsetDays, offsetWeeks, deadlineType, cycleType);
				} else {
					jdbcTemplate.update(insertSql, cycleType, offsetDays, offsetWeeks, deadlineType);
				}
			}
			response.put("code", 200);
			response.put("description", "Thành công");
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}

	@Transactional
	public JSONObject saveKpiDeadlineOverride(int kpiId, Boolean hasOverride, Integer offsetDays, Integer offsetWeeks, String absoluteDate) {
		JSONObject response = new JSONObject();
		try {
			String sql = "UPDATE kpi_definitions SET override_deadline_offset_days = ?, override_deadline_offset_weeks = ?, override_absolute_deadline_date = ? WHERE kpi_id = ?";
			
			if (hasOverride == null || !hasOverride) {
				jdbcTemplate.update(sql, null, null, null, kpiId);
			} else {
				java.sql.Date dateVal = null;
				if (absoluteDate != null && !absoluteDate.trim().isEmpty()) {
					try {
						dateVal = java.sql.Date.valueOf(absoluteDate.trim());
					} catch (Exception ex) {
						System.err.println("Invalid date format: " + absoluteDate);
					}
				}
				jdbcTemplate.update(sql, offsetDays, offsetWeeks, dateVal, kpiId);
			}
			response.put("code", 200);
			response.put("description", "Thành công");
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Database error: " + e.getMessage());
		}
		return response;
	}

	public static class CalculatedPeriod {
		public String code;
		public java.sql.Date startDate;
		public java.sql.Date endDate;
	}

	public Date calculateDeadline(Date endDate, int offsetDays, int offsetWeeks, String deadlineType) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(endDate);
		
		if (offsetWeeks > 0) {
			cal.add(Calendar.WEEK_OF_YEAR, offsetWeeks);
		}
		
		boolean isBusiness = "BUSINESS".equalsIgnoreCase(deadlineType);
		if (offsetDays > 0) {
			int added = 0;
			while (added < offsetDays) {
				cal.add(Calendar.DAY_OF_YEAR, 1);
				if (isBusiness) {
					int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
					if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
						added++;
					}
				} else {
					added++;
				}
			}
		}
		
		cal.set(Calendar.HOUR_OF_DAY, 23);
		cal.set(Calendar.MINUTE, 59);
		cal.set(Calendar.SECOND, 59);
		cal.set(Calendar.MILLISECOND, 999);
		
		return cal.getTime();
	}

	public CalculatedPeriod calculatePeriod(int cycleId, String cycleType, Date date) {
		CalculatedPeriod cp = new CalculatedPeriod();
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1; // 1-indexed

		if ("term".equalsIgnoreCase(cycleType)) {
			if (month >= 8 || month == 1) {
				int startYear = (month == 1) ? year - 1 : year;
				cal.set(startYear, 7, 1, 0, 0, 0); // Aug 1
				cp.startDate = new java.sql.Date(cal.getTimeInMillis());
				cal.set(startYear + 1, 0, 31, 23, 59, 59); // Jan 31
				cp.endDate = new java.sql.Date(cal.getTimeInMillis());
				cp.code = startYear + "-HK1";
			} else if (month >= 2 && month <= 5) {
				cal.set(year, 1, 1, 0, 0, 0); // Feb 1
				cp.startDate = new java.sql.Date(cal.getTimeInMillis());
				cal.set(year, 5, 30, 23, 59, 59); // Jun 30
				cp.endDate = new java.sql.Date(cal.getTimeInMillis());
				cp.code = year + "-HK2";
			} else { // month 6 and 7
				cal.set(year, 5, 1, 0, 0, 0); // Jun 1
				cp.startDate = new java.sql.Date(cal.getTimeInMillis());
				cal.set(year, 7, 31, 23, 59, 59); // Aug 31
				cp.endDate = new java.sql.Date(cal.getTimeInMillis());
				cp.code = year + "-HK3";
			}
			return cp;
		}

		List<Map<String, Object>> cycleDefs = jdbcTemplate.queryForList(
			"SELECT start_month, start_day, duration_months, duration_weeks FROM cycle_definitions WHERE cycle_id = ?", cycleId);
		
		int startMonth = 1;
		int startDay = 1;
		int durationMonths = 1;
		int durationWeeks = 0;
		if (!cycleDefs.isEmpty()) {
			Map<String, Object> row = cycleDefs.get(0);
			if (row.get("start_month") != null) startMonth = ((Number) row.get("start_month")).intValue();
			if (row.get("start_day") != null) startDay = ((Number) row.get("start_day")).intValue();
			if (row.get("duration_months") != null) durationMonths = ((Number) row.get("duration_months")).intValue();
			if (row.get("duration_weeks") != null) durationWeeks = ((Number) row.get("duration_weeks")).intValue();
		}

		Calendar anchorCal = Calendar.getInstance();
		anchorCal.setTime(date);
		anchorCal.set(Calendar.HOUR_OF_DAY, 0);
		anchorCal.set(Calendar.MINUTE, 0);
		anchorCal.set(Calendar.SECOND, 0);
		anchorCal.set(Calendar.MILLISECOND, 0);
		
		anchorCal.set(Calendar.YEAR, year);
		anchorCal.set(Calendar.MONTH, startMonth - 1);
		anchorCal.set(Calendar.DAY_OF_MONTH, startDay);
		
		int anchorYear = year;
		if (date.before(anchorCal.getTime())) {
			anchorYear = year - 1;
			anchorCal.set(Calendar.YEAR, anchorYear);
		}

		Calendar periodStart = (Calendar) anchorCal.clone();
		Calendar periodEnd = (Calendar) anchorCal.clone();
		
		if (durationWeeks > 0) {
			while (true) {
				periodEnd = (Calendar) periodStart.clone();
				periodEnd.add(Calendar.WEEK_OF_YEAR, durationWeeks);
				if (!date.before(periodStart.getTime()) && date.before(periodEnd.getTime())) {
					break;
				}
				periodStart.add(Calendar.WEEK_OF_YEAR, durationWeeks);
			}
			periodEnd.add(Calendar.DAY_OF_YEAR, -1);
			cp.startDate = new java.sql.Date(periodStart.getTimeInMillis());
			cp.endDate = new java.sql.Date(periodEnd.getTimeInMillis());
			cp.code = anchorYear + "-W" + periodStart.get(Calendar.WEEK_OF_YEAR);
		} else {
			if (durationMonths <= 0) durationMonths = 1;
			while (true) {
				periodEnd = (Calendar) periodStart.clone();
				periodEnd.add(Calendar.MONTH, durationMonths);
				if (!date.before(periodStart.getTime()) && date.before(periodEnd.getTime())) {
					break;
				}
				periodStart.add(Calendar.MONTH, durationMonths);
			}
			periodEnd.add(Calendar.DAY_OF_YEAR, -1);
			cp.startDate = new java.sql.Date(periodStart.getTimeInMillis());
			cp.endDate = new java.sql.Date(periodEnd.getTimeInMillis());

			if (durationMonths == 12) {
				if (startMonth == 9) {
					cp.code = anchorYear + "-" + (anchorYear + 1);
				} else {
					cp.code = String.valueOf(anchorYear);
				}
			} else if (durationMonths == 6) {
				int sMonth = periodStart.get(Calendar.MONTH) + 1;
				if (sMonth >= 9 || sMonth < 2) {
					cp.code = anchorYear + "-S1";
				} else {
					cp.code = anchorYear + "-S2";
				}
			} else if (durationMonths == 3) {
				int sMonth = periodStart.get(Calendar.MONTH) + 1;
				int qIdx = 1;
				if (sMonth >= 12 || sMonth < 3) qIdx = 2;
				else if (sMonth >= 3 && sMonth < 6) qIdx = 3;
				else if (sMonth >= 6 && sMonth < 9) qIdx = 4;
				cp.code = anchorYear + "-Q" + qIdx;
			} else if (durationMonths == 1) {
				int pYear = periodStart.get(Calendar.YEAR);
				int pMonth = periodStart.get(Calendar.MONTH) + 1;
				cp.code = String.format("%d-%02d", pYear, pMonth);
			} else {
				cp.code = anchorYear + "-P" + (periodStart.get(Calendar.MONTH) / durationMonths + 1);
			}
		}

		return cp;
	}

	public int getOrCreatePeriodInstance(int cycleId, String cycleType, Date date) {
		String sql = "SELECT period_id FROM period_instances WHERE cycle_id = ? AND ? BETWEEN start_date AND end_date";
		List<Integer> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("period_id"), cycleId, new java.sql.Date(date.getTime()));
		if (!ids.isEmpty()) {
			return ids.get(0);
		}

		CalculatedPeriod cp = calculatePeriod(cycleId, cycleType, date);

		try {
			String insertSql = "INSERT INTO period_instances (cycle_id, period_code, start_date, end_date) VALUES (?, ?, ?, ?)";
			jdbcTemplate.update(insertSql, cycleId, cp.code, cp.startDate, cp.endDate);
			
			return jdbcTemplate.queryForObject("SELECT period_id FROM period_instances WHERE cycle_id = ? AND period_code = ? AND start_date = ?", 
				Integer.class, cycleId, cp.code, cp.startDate);
		} catch (Exception e) {
			try {
				return jdbcTemplate.queryForObject(sql, Integer.class, cycleId, new java.sql.Date(date.getTime()));
			} catch (Exception ex) {
				ex.printStackTrace();
				throw new RuntimeException("Error getting/creating period instance: " + e.getMessage());
			}
		}
	}

	@Transactional
	public JSONObject saveKpiValue(int kpiId, Integer deptId, Double actualValue, String notes, String evidenceLink, String fileName, String fileSize, int userId, boolean isAdmin, Date referenceDate) {
		JSONObject response = new JSONObject();
		try {
			String kpiQuery = "SELECT k.cycle_id, c.cycle_type FROM kpi_definitions k JOIN cycle_definitions c ON k.cycle_id = c.cycle_id WHERE k.kpi_id = ? AND (k.is_deleted = 0 OR k.is_deleted IS NULL)";
			System.out.println("Executing KPI Query: " + kpiQuery);
			List<Map<String, Object>> kpiRows = jdbcTemplate.queryForList(kpiQuery, kpiId);
			
			System.out.println("KPI Rows: " + kpiRows);
			
			if (kpiRows.isEmpty()) {
				response.put("code", 404);
				response.put("description", "Không tìm thấy chỉ số KPI.");
				return response;
			}
			int cycleId = ((Number) kpiRows.get(0).get("cycle_id")).intValue();
			String cycleType = (String) kpiRows.get(0).get("cycle_type");

			Date refDate = referenceDate != null ? referenceDate : new Date();
			int currPeriodId = getOrCreatePeriodInstance(cycleId, cycleType, refDate);
			
			List<Map<String, Object>> currPeriodInfo = jdbcTemplate.queryForList("SELECT start_date FROM period_instances WHERE period_id = ?", currPeriodId);
			java.sql.Date currStartDate = (java.sql.Date) currPeriodInfo.get(0).get("start_date");
			Calendar prevCal = Calendar.getInstance();
			prevCal.setTime(currStartDate);
			prevCal.add(Calendar.DAY_OF_YEAR, -1);
			int prevPeriodId = getOrCreatePeriodInstance(cycleId, cycleType, prevCal.getTime());

			List<Map<String, Object>> prevPeriodInfo = jdbcTemplate.queryForList("SELECT end_date FROM period_instances WHERE period_id = ?", prevPeriodId);
			java.sql.Date prevEndDate = (java.sql.Date) prevPeriodInfo.get(0).get("end_date");
			
			List<Map<String, Object>> deadlineConfig = jdbcTemplate.queryForList(
				"SELECT override_deadline_offset_days, override_deadline_offset_weeks, override_absolute_deadline_date, " +
				"default_deadline_offset_days, default_deadline_offset_weeks, deadline_type " +
				"FROM kpi_definitions k LEFT JOIN cycle_definitions c ON k.cycle_id = c.cycle_id WHERE k.kpi_id = ?", kpiId);
			
			Date prevDeadline = null;
			if (!deadlineConfig.isEmpty()) {
				Map<String, Object> conf = deadlineConfig.get(0);
				if (conf.get("override_absolute_deadline_date") != null) {
					prevDeadline = (java.sql.Date) conf.get("override_absolute_deadline_date");
				} else {
					int offsetDays = conf.get("override_deadline_offset_days") != null ? ((Number) conf.get("override_deadline_offset_days")).intValue() : 
						(conf.get("default_deadline_offset_days") != null ? ((Number) conf.get("default_deadline_offset_days")).intValue() : 0);
					int offsetWeeks = conf.get("override_deadline_offset_weeks") != null ? ((Number) conf.get("override_deadline_offset_weeks")).intValue() : 
						(conf.get("default_deadline_offset_weeks") != null ? ((Number) conf.get("default_deadline_offset_weeks")).intValue() : 0);
					String deadlineType = conf.get("deadline_type") != null ? (String) conf.get("deadline_type") : "CALENDAR";
					prevDeadline = calculateDeadline(prevEndDate, offsetDays, offsetWeeks, deadlineType);
				}
			}

			int targetPeriodId;
			boolean isLocked = false;
			if (prevDeadline != null && !refDate.after(prevDeadline)) {
				targetPeriodId = prevPeriodId;
			} else {
				targetPeriodId = currPeriodId;
				
				List<Map<String, Object>> currPeriodDetail = jdbcTemplate.queryForList("SELECT end_date FROM period_instances WHERE period_id = ?", currPeriodId);
				java.sql.Date currEndDate = (java.sql.Date) currPeriodDetail.get(0).get("end_date");
				Date currDeadline = null;
				if (!deadlineConfig.isEmpty()) {
					Map<String, Object> conf = deadlineConfig.get(0);
					if (conf.get("override_absolute_deadline_date") != null) {
						currDeadline = (java.sql.Date) conf.get("override_absolute_deadline_date");
					} else {
						int offsetDays = conf.get("override_deadline_offset_days") != null ? ((Number) conf.get("override_deadline_offset_days")).intValue() : 
							(conf.get("default_deadline_offset_days") != null ? ((Number) conf.get("default_deadline_offset_days")).intValue() : 0);
						int offsetWeeks = conf.get("override_deadline_offset_weeks") != null ? ((Number) conf.get("override_deadline_offset_weeks")).intValue() : 
							(conf.get("default_deadline_offset_weeks") != null ? ((Number) conf.get("default_deadline_offset_weeks")).intValue() : 0);
						String deadlineType = conf.get("deadline_type") != null ? (String) conf.get("deadline_type") : "CALENDAR";
						currDeadline = calculateDeadline(currEndDate, offsetDays, offsetWeeks, deadlineType);
					}
				}
				if (currDeadline != null && refDate.after(currDeadline)) {
					isLocked = true;
				}
			}

			if (targetPeriodId == prevPeriodId && prevDeadline != null && refDate.after(prevDeadline)) {
				isLocked = true;
			}

			if (isLocked && !isAdmin) {
				response.put("code", 403);
				response.put("description", "Chu kỳ báo cáo đã kết thúc và hết hạn cập nhật. Số liệu đã bị khóa.");
				return response;
			}

			String queryExist = "SELECT data_id, actual_value, notes, evidence_link, evidence_file_name, evidence_file_size, evidence_file_uploaded_at "
					+ " FROM kpi_data_points "
					+ " WHERE kpi_id = ? "
					+ " AND period_id = ? AND (department_id = ? "
					+ " OR (department_id IS NULL AND ? IS NULL))";
			List<Map<String, Object>> existList = jdbcTemplate.queryForList(queryExist, kpiId, targetPeriodId, deptId, deptId);
			
			System.out.println("Exist List: " + existList);
			
			int dataId;
			String changeType;
			String finalNotes = notes;
			String finalEvidenceLink = evidenceLink;
			String finalFileName = fileName;
			String finalFileSize = fileSize;
			String finalFileUploadedAt = fileName != null && !fileName.isEmpty() ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()) : null;

			if (!existList.isEmpty()) {
				Map<String, Object> existing = existList.get(0);
				dataId = ((Number) existing.get("data_id")).intValue();
				
				String dbNotes = existing.get("notes") != null ? existing.get("notes").toString() : "";
				String dbEvidenceLink = existing.get("evidence_link") != null ? existing.get("evidence_link").toString() : "";
				String dbFileName = existing.get("evidence_file_name") != null ? existing.get("evidence_file_name").toString() : "";
				String dbFileSize = existing.get("evidence_file_size") != null ? existing.get("evidence_file_size").toString() : "";
				String dbFileUploadedAt = existing.get("evidence_file_uploaded_at") != null ? existing.get("evidence_file_uploaded_at").toString() : null;

				if (notes == null || notes.trim().isEmpty()) {
					finalNotes = dbNotes;
				}
				if (evidenceLink == null || evidenceLink.trim().isEmpty()) {
					finalEvidenceLink = dbEvidenceLink;
				}
				if (fileName == null || fileName.trim().isEmpty()) {
					finalFileName = dbFileName;
					finalFileSize = dbFileSize;
					finalFileUploadedAt = dbFileUploadedAt;
				}

				String updateSql = "UPDATE kpi_data_points SET actual_value = ?, updated_at = GETDATE(), updated_by = ?, notes = ?, evidence_link = ?, evidence_file_name = ?, evidence_file_size = ?, evidence_file_uploaded_at = ? WHERE data_id = ?";
				jdbcTemplate.update(updateSql, actualValue, userId, finalNotes, finalEvidenceLink, finalFileName, finalFileSize, finalFileUploadedAt, dataId);
				changeType = "UPDATE";
			} else {
				String insertSql = "INSERT INTO kpi_data_points (kpi_id, period_id, actual_value, updated_at, status_id, updated_by, notes, evidence_link, evidence_file_name, evidence_file_size, evidence_file_uploaded_at, department_id) VALUES (?, ?, ?, GETDATE(), 5, ?, ?, ?, ?, ?, ?, ?)";
				jdbcTemplate.update(insertSql, kpiId, targetPeriodId, actualValue, userId, finalNotes, finalEvidenceLink, finalFileName, finalFileSize, finalFileUploadedAt, deptId);
				
				dataId = jdbcTemplate.queryForObject("SELECT TOP 1 data_id FROM kpi_data_points WHERE kpi_id = ? AND period_id = ? AND (department_id = ? OR (department_id IS NULL AND ? IS NULL)) ORDER BY data_id DESC", Integer.class, kpiId, targetPeriodId, deptId, deptId);
				changeType = "CREATE";
			}

			int nextVersionNum = jdbcTemplate.queryForObject("SELECT ISNULL(MAX(version_number), 0) + 1 FROM kpi_value_versions WHERE data_id = ?", Integer.class, dataId);
			String versionSql = "INSERT INTO kpi_value_versions (data_id, actual_value, notes, evidence_link, evidence_file_name, evidence_file_size, updated_at, updated_by, change_type, version_number, department_id) VALUES (?, ?, ?, ?, ?, ?, GETDATE(), ?, ?, ?, ?)";
			jdbcTemplate.update(versionSql, dataId, actualValue, finalNotes, finalEvidenceLink, finalFileName, finalFileSize, userId, changeType, nextVersionNum, deptId);

			response.put("code", 200);
			response.put("description", "Đã cập nhật số liệu KPI và lưu hồ sơ minh chứng thành công!");
			response.put("data_id", dataId);
			response.put("period_id", targetPeriodId);
			response.put("version_number", nextVersionNum);
		} catch (Exception e) {
			e.printStackTrace();
			response.put("code", 500);
			response.put("description", "Lỗi cơ sở dữ liệu: " + e.getMessage());
		}
		return response;
	}

	public JSONArray getKpiValueHistory(int kpiId, Integer deptId) {
		JSONArray jsa = new JSONArray();
		try {
			String sql = "SELECT v.version_id, v.data_id, v.actual_value, v.notes, v.evidence_link, v.evidence_file_name, v.evidence_file_size, v.updated_at, v.change_type, v.version_number, u.Fullname as updated_by_name " +
						 "FROM kpi_value_versions v " +
						 "JOIN kpi_data_points d ON v.data_id = d.data_id " +
						 "LEFT JOIN tbl_user u ON v.updated_by = u.ID " +
						 "WHERE d.kpi_id = ? AND (v.department_id = ? OR (v.department_id IS NULL AND ? IS NULL)) " +
						 "ORDER BY v.version_number DESC";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kpiId, deptId, deptId);
			for (Map<String, Object> row : rows) {
				JSONObject jo = new JSONObject();
				jo.put("version_id", row.get("version_id"));
				jo.put("data_id", row.get("data_id"));
				jo.put("actual_value", row.get("actual_value"));
				jo.put("notes", row.get("notes") != null ? row.get("notes").toString() : JSONObject.NULL);
				jo.put("evidence_link", row.get("evidence_link") != null ? row.get("evidence_link").toString() : JSONObject.NULL);
				jo.put("evidence_file_name", row.get("evidence_file_name") != null ? row.get("evidence_file_name").toString() : JSONObject.NULL);
				jo.put("evidence_file_size", row.get("evidence_file_size") != null ? row.get("evidence_file_size").toString() : JSONObject.NULL);
				jo.put("updated_at", row.get("updated_at") != null ? row.get("updated_at").toString() : JSONObject.NULL);
				jo.put("change_type", row.get("change_type"));
				jo.put("version_number", row.get("version_number"));
				jo.put("updated_by_name", row.get("updated_by_name") != null ? row.get("updated_by_name").toString() : "Hệ thống");
				jsa.put(jo);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return jsa;
	}
}