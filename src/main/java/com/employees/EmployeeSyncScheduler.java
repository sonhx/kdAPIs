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

	@jakarta.annotation.PostConstruct
	public void init() {
		new Thread(() -> {
			try {
				Thread.sleep(5000); // Wait 5 seconds for app to fully boot
				System.out.println("Checking database schema types...");
				try {
					List<Map<String, Object>> columns = jdbcTemplate.queryForList(
						"SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH " +
						"FROM INFORMATION_SCHEMA.COLUMNS " +
						"WHERE (TABLE_NAME = 'employees' AND COLUMN_NAME = 'uName') " +
						"   OR (TABLE_NAME = 'TBL_USER' AND COLUMN_NAME = 'FullName')"
					);
					for (Map<String, Object> col : columns) {
						System.out.println("SCHEMA DIAGNOSTIC: " + col.get("TABLE_NAME") + "." + col.get("COLUMN_NAME") + " -> " + col.get("DATA_TYPE") + "(" + col.get("CHARACTER_MAXIMUM_LENGTH") + ")");
					}
				} catch (Exception ex) {
					System.err.println("Schema check failed: " + ex.getMessage());
				}
				System.out.println("Triggering initial employee sync on startup...");
				syncEmployeesMonthly();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	@Scheduled(cron = "0 0 0 1 * ?") // Runs at midnight on the 1st of every month
	@Transactional // Ensures that if something fails, the DB changes are rolled back
	public void syncEmployeesMonthly() {
		System.out.println("Starting monthly employee sync...");

		try {
			org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
			requestFactory.setConnectTimeout(10000);
			requestFactory.setReadTimeout(10000);
			RestTemplate restTemplate = new RestTemplate(requestFactory);
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

				boolean exists = false;
				try {
					jdbcTemplate.queryForObject("SELECT 1 FROM employees WHERE uCode = ?", Integer.class, uCode);
					exists = true;
				} catch (org.springframework.dao.EmptyResultDataAccessException e) {}

				if (exists) {
					// UPDATE existing record
					String updateSql = "UPDATE employees SET uName = ?, uImage = ?, uEmail = ?, "
							+ "uUnit = ?, uGender = ?, updateType = ?, updatedAt = GETDATE() " + "WHERE uCode = ?";
					jdbcTemplate.update(connection -> {
						java.sql.PreparedStatement ps = connection.prepareStatement(updateSql);
						ps.setNString(1, uName);
						ps.setString(2, uImage);
						ps.setString(3, uEmail);
						ps.setNString(4, uUnit);
						ps.setString(5, uGender);
						ps.setString(6, updateType);
						ps.setString(7, uCode);
						return ps;
					});
					updateCount++;
				} else {
					// INSERT new record
					String insertSql = "INSERT INTO employees (uCode, uName, uImage, uEmail, uUnit, uGender, updateType, createdAt, updatedAt) "
							+ "VALUES (?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())";
					jdbcTemplate.update(connection -> {
						java.sql.PreparedStatement ps = connection.prepareStatement(insertSql);
						ps.setString(1, uCode);
						ps.setNString(2, uName);
						ps.setString(3, uImage);
						ps.setString(4, uEmail);
						ps.setNString(5, uUnit);
						ps.setString(6, uGender);
						ps.setString(7, updateType);
						return ps;
					});
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
				String rawDeptCode = (String) deptRow.get("dept_code");
				String rawDeptName = (String) deptRow.get("dept_name");

				if (rawDeptCode != null && !rawDeptCode.trim().isEmpty() && rawDeptName != null && !rawDeptName.trim().isEmpty()) {
					final String deptCode = rawDeptCode.trim();
					final String deptName = rawDeptName.trim();

					// Check if department exists by dept_code
					boolean dExists = false;
					try {
						jdbcTemplate.queryForObject("SELECT 1 FROM departments WHERE dept_code = ?", Integer.class, deptCode);
						dExists = true;
					} catch (org.springframework.dao.EmptyResultDataAccessException e) {}

					if (dExists) {
						// Update
						String updateDeptSql = "UPDATE departments SET dept_name = ? WHERE dept_code = ?";
						jdbcTemplate.update(connection -> {
							java.sql.PreparedStatement ps = connection.prepareStatement(updateDeptSql);
							ps.setNString(1, deptName);
							ps.setString(2, deptCode);
							return ps;
						});
						deptUpdateCount++;
					} else {
						// Insert
						String insertDeptSql = "INSERT INTO departments (dept_code, dept_name) VALUES (?, ?)";
						jdbcTemplate.update(connection -> {
							java.sql.PreparedStatement ps = connection.prepareStatement(insertDeptSql);
							ps.setString(1, deptCode);
							ps.setNString(2, deptName);
							return ps;
						});
						deptInsertCount++;
					}
				}
			}
			System.out.println(
					"Department Sync Successful! Inserted: " + deptInsertCount + ", Updated: " + deptUpdateCount);

			// Heal any corrupted names in TBL_USER using the corrected names from employees
			System.out.println("Healing TBL_USER names from employees table...");
			try {
				int healedCount = jdbcTemplate.update(
					"UPDATE u " +
					"SET u.FullName = e.uName " +
					"FROM TBL_USER u " +
					"JOIN employees e ON e.uEmail = u.Email " +
					"WHERE u.FullName <> e.uName AND (u.IsDeleted IS NULL OR u.IsDeleted = '0')"
				);
				System.out.println("Healed " + healedCount + " user names in TBL_USER.");
			} catch (Exception ex) {
				System.err.println("Error healing user names: " + ex.getMessage());
			}

		} catch (Exception e) {
			System.err.println("Error during monthly employee sync:");
			e.printStackTrace();
		}
	}
}
