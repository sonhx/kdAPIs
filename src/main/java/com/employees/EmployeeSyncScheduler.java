package com.employees;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;

@Service
//public class EmployeeSyncScheduler {
public class EmployeeSyncScheduler {
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${slink.api-key}")
	private String slinkApiKey;


	@Scheduled(cron = "0 0 0 1 * ?") // Runs at midnight on the 1st of every month
	@Transactional // Ensures that if something fails, the DB changes are rolled back
	public void syncEmployeesMonthly() {
		System.out.println("Starting monthly employee sync...");

		try {
			RestTemplate restTemplate = new RestTemplate();
			String url = "https://gw.aisoftech.vn/ptit/tcns/internal/face-rec/log/user";
			
			String apiKey = slinkApiKey;
			if (apiKey != null) {
				apiKey = apiKey.replace("\"", "").trim();
			}

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set("x-api-key", apiKey);

			// Calculate exact time 1 month ago in ISO format for the API
			String lastMonthDate = ZonedDateTime.now().minusMonths(1).format(DateTimeFormatter.ISO_INSTANT);

			JSONObject requestBody = new JSONObject();
			requestBody.put("isForceSync", false);
			requestBody.put("lastSyncTime", lastMonthDate);
			requestBody.put("limit", 10000);
			requestBody.put("page", 1);

			HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

			// Parse the API Response
			JSONObject jsonResponse = new JSONObject(response.getBody());

			// NOTE: You may need to change "data" below to whatever the actual
			// JSON array field name is in the API's response.
			JSONArray employeesArray = jsonResponse.getJSONObject("data").optJSONArray("result"); // Adjust this based
																									// on actual response
																									// structure

			if (employeesArray == null) {
				// If it's a direct array, you might use: employeesArray = new
				// JSONArray(response.getBody());
				System.out.println("No employee data found in response.");
				return;
			}

			int insertCount = 0;
			int updateCount = 0;

			for (int i = 0; i < employeesArray.length(); i++) {
				JSONObject emp = employeesArray.getJSONObject(i);

				// Extract fields (using optString to avoid crashes if a field is missing)
				String uCode = emp.optString("uCode", null);
				if (uCode == null || uCode.isEmpty())
					continue; // Skip if no code

				String uName = emp.optString("uName", "");
				String uImage = emp.optString("uImage", null);
				String uEmail = emp.optString("uEmail", null);
				String uUnit = emp.optString("uUnit", null);
				String uGender = emp.optString("uGender", null);
				String updateType = emp.optString("updateType", "api_sync");

				// Check if employee already exists in DB
				String checkSql = "SELECT COUNT(*) FROM employees WHERE uCode = ?";
				Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, uCode);

				if (count != null && count > 0) {
					// UPDATE existing record
					String updateSql = "UPDATE employees SET uName = ?, uImage = ?, uEmail = ?, "
							+ "uUnit = ?, uGender = ?, updateType = ?, updatedAt = GETDATE() " + "WHERE uCode = ?";
					jdbcTemplate.update(updateSql, uName, uImage, uEmail, uUnit, uGender, updateType, uCode);
					updateCount++;
				} else {
					// INSERT new record
					String insertSql = "INSERT INTO employees (uCode, uName, uImage, uEmail, uUnit, uGender, updateType, createdAt, updatedAt) "
							+ "VALUES (?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())";
					jdbcTemplate.update(insertSql, uCode, uName, uImage, uEmail, uUnit, uGender, updateType);
					insertCount++;
				}
			}

			System.out.println("Sync Successful! Inserted: " + insertCount + ", Updated: " + updateCount);

			// ------------------------------------------------------------------
			// UPDATE DEPARTMENTS TABLE FROM EMPLOYEES DATA
			// ------------------------------------------------------------------
			System.out.println("Syncing departments from employees table...");
			String skipPrefixes = "  AND uCode NOT LIKE 'DUYN%' AND uCode NOT LIKE 'GIAO%' "
					+ "  AND uCode NOT LIKE 'HUNG%' AND uCode NOT LIKE 'LUON%' "
					+ "  AND uCode NOT LIKE 'OANH%' AND uCode NOT LIKE 'RA00%' "
					+ "  AND uCode NOT LIKE 'TG00%' AND uCode NOT LIKE 'TG01%' "
					+ "  AND uCode NOT LIKE 'TG02%' AND uCode NOT LIKE 'TG03%' "
					+ "  AND uCode NOT LIKE 'TG04%' AND uCode NOT LIKE 'TG05%' "
					+ "  AND uCode NOT LIKE 'TG06%' AND uCode NOT LIKE 'TG07%' "
					+ "  AND uCode NOT LIKE 'TG13%' AND uCode NOT LIKE 'TG19%' "
					+ "  AND uCode NOT LIKE 'TG20%' AND uCode NOT LIKE 'TG21%' "
					+ "  AND uCode NOT LIKE 'TG22%' AND uCode NOT LIKE 'TG99%' ";
			String deptSql = "SELECT DISTINCT "
					+ "    SUBSTRING(uCode, CHARINDEX('.', uCode) + 1, "
					+ "        CHARINDEX('.', uCode, CHARINDEX('.', uCode) + 1) - (CHARINDEX('.', uCode) + 1)) AS dept_code, "
					+ "    uUnit AS dept_name "
					+ "FROM employees "
					+ "WHERE uCode LIKE '%.%.%' AND uUnit IS NOT NULL AND uUnit <> '' "
					+ "  AND uUnit <> N'Trung tâm Đào tạo Bưu chính Viễn thông' "
					+ skipPrefixes
					+ "UNION "
					+ "SELECT DISTINCT "
					+ "    LEFT(uCode, 4) AS dept_code, "
					+ "    uUnit AS dept_name "
					+ "FROM employees "
					+ "WHERE uCode NOT LIKE '%.%' AND uUnit IS NOT NULL AND uUnit <> '' "
					+ "  AND uUnit <> N'Trung tâm Đào tạo Bưu chính Viễn thông' "
					+ skipPrefixes
					+ "UNION "
					+ "SELECT DISTINCT "
					+ "    'TDT1' AS dept_code, "
					+ "    uUnit AS dept_name "
					+ "FROM employees "
					+ "WHERE uUnit = N'Trung tâm Đào tạo Bưu chính Viễn thông'";

			List<Map<String, Object>> deptRows = jdbcTemplate.queryForList(deptSql);
			int deptInsertCount = 0;
			int deptUpdateCount = 0;

			for (Map<String, Object> deptRow : deptRows) {
				String deptCode = (String) deptRow.get("dept_code");
				String deptName = (String) deptRow.get("dept_name");

				if (deptCode != null && !deptCode.trim().isEmpty() && deptName != null && !deptName.trim().isEmpty()) {
					deptCode = deptCode.trim();
					deptName = deptName.trim();

					// Check if department exists by dept_code
					String checkDeptSql = "SELECT COUNT(*) FROM departments WHERE dept_code = ?";
					Integer dCount = jdbcTemplate.queryForObject(checkDeptSql, Integer.class, deptCode);

					if (dCount != null && dCount > 0) {
						// Update
						String updateDeptSql = "UPDATE departments SET dept_name = ? WHERE dept_code = ?";
						jdbcTemplate.update(updateDeptSql, deptName, deptCode);
						deptUpdateCount++;
					} else {
						// Insert
						String insertDeptSql = "INSERT INTO departments (dept_code, dept_name) VALUES (?, ?)";
						jdbcTemplate.update(insertDeptSql, deptCode, deptName);
						deptInsertCount++;
					}
				}
			}
			System.out.println(
					"Department Sync Successful! Inserted: " + deptInsertCount + ", Updated: " + deptUpdateCount);

		} catch (Exception e) {
			System.err.println("Error during monthly employee sync:");
			e.printStackTrace();
		}
	}
}
