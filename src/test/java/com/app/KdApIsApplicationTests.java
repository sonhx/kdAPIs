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
}
