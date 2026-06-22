package com.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.kpi.kpiExtend;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KdApIsApplicationTests {

	@Autowired
	private kpiExtend kpiExtend;

	@Test
	void testGetKpisWithAssignments() {
		System.out.println("--- START TEST: testGetKpisWithAssignments ---");
		
		// Setup/Ensure KPI 5 is assigned to department 1 for testing
		JSONObject saveResponse = kpiExtend.saveAssignment(5, 1, "A", 10000000);
		assertNotNull(saveResponse);
		assertEquals(200, saveResponse.optInt("code"));

		JSONArray kpis = kpiExtend.getKpisWithAssignments();
		assertNotNull(kpis);
		assertTrue(kpis.length() > 0, "KPI list should not be empty");

		System.out.println("Total KPIs retrieved: " + kpis.length());

		// Inspect the first KPI
		JSONObject firstKpi = kpis.getJSONObject(0);
		System.out.println("Sample KPI structure: " + firstKpi.toString(2));

		// Validate structure of all KPIs
		for (int i = 0; i < kpis.length(); i++) {
			JSONObject kpi = kpis.getJSONObject(i);
			assertTrue(kpi.has("kpi_id"));
			assertTrue(kpi.has("kpi_code"));
			assertTrue(kpi.has("kpi_name"));
			assertTrue(kpi.has("assignment_status"));
			assertTrue(kpi.has("assignments"));
			assertTrue(kpi.has("data_points"));

			String status = kpi.getString("assignment_status");
			assertTrue("assigned".equals(status) || "unassigned".equals(status), 
					"Status must be either 'assigned' or 'unassigned'");

			JSONArray assignments = kpi.getJSONArray("assignments");
			if (kpi.getInt("kpi_id") == 5) {
				assertEquals("assigned", status, "KPI 5 must be assigned");
				assertTrue(assignments.length() > 0, "KPI 5 must have assignments");
				JSONObject assignment = assignments.getJSONObject(0);
				assertEquals(1, assignment.getInt("department_id"), "KPI 5 department must be 1");
				assertEquals("A", assignment.getString("role"), "KPI 5 role must be A");
				System.out.println("Verified assigned KPI 5: " + kpi.toString(2));
			}

			if ("unassigned".equals(status)) {
				assertEquals(0, assignments.length(), "Unassigned KPI must have 0 assignments");
			} else {
				assertTrue(assignments.length() > 0, "Assigned KPI must have at least 1 assignment");
			}

			JSONArray dataPoints = kpi.getJSONArray("data_points");
			assertNotNull(dataPoints);
		}
		System.out.println("--- END TEST: testGetKpisWithAssignments (SUCCESS) ---");
	}

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	@Test
	void testGetVertexDataByCategory() {
		System.out.println("--- START TEST: testGetVertexDataByCategory ---");
		
		// 1. Get a sample active category from the database
		String category = jdbcTemplate.queryForObject(
				"SELECT TOP 1 category FROM kpi_definitions WHERE (is_deleted = 0 OR is_deleted IS NULL) AND category IS NOT NULL", 
				String.class);
		
		assertNotNull(category, "Database must have at least one active KPI category");
		System.out.println("Testing category: " + category);
		
		// 2. Fetch vertex data for this category
		JSONArray kpis = kpiExtend.getVertexDataByCategory(category);
		assertNotNull(kpis, "Result should not be null");
		System.out.println("Total KPIs retrieved for category: " + kpis.length());
		
		// 3. Verify elements
		for (int i = 0; i < kpis.length(); i++) {
			JSONObject kpi = kpis.getJSONObject(i);
			assertEquals(category, kpi.getString("category"), "KPI category must match the requested category");
			assertTrue(kpi.has("assignments"), "KPI must contain assignments");
			assertTrue(kpi.has("data_points"), "KPI must contain data_points");
			assertTrue(kpi.has("normalized_values"), "KPI must contain normalized_values");
			
			JSONArray normalizedValues = kpi.getJSONArray("normalized_values");
			System.out.println("KPI Code: " + kpi.getString("kpi_code") + ", Normalized Values count: " + normalizedValues.length());
		}
		
		System.out.println("--- END TEST: testGetVertexDataByCategory (SUCCESS) ---");
	}
}
