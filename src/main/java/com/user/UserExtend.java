package com.user;

import java.util.List;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.json.JSONObject;

@Service
public class UserExtend {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PostConstruct
	public void init() {
		// 1. Ensure Hash column exists
		try {
			jdbcTemplate.execute("SELECT Hash FROM TBL_USER WHERE 1=0");
		} catch (Exception e) {
			try {
				jdbcTemplate.execute("ALTER TABLE TBL_USER ADD Hash VARCHAR(255)");
				System.out.println("Added 'Hash' column to TBL_USER.");
			} catch (Exception ex) {
				System.err.println("Failed to add 'Hash' column to TBL_USER: " + ex.getMessage());
			}
		}

		// 2. Perform one-time migration if Password column still exists
		boolean passwordColumnExists = false;
		try {
			jdbcTemplate.execute("SELECT Password FROM TBL_USER WHERE 1=0");
			passwordColumnExists = true;
		} catch (Exception e) {
			// Password column already dropped
		}

		if (passwordColumnExists) {
			try {
				String query = "SELECT ID, Password FROM TBL_USER WHERE (Hash IS NULL OR Hash = '') AND Password IS NOT NULL AND Password <> ''";
				List<Map<String, Object>> legacyUsers = jdbcTemplate.queryForList(query);
				if (!legacyUsers.isEmpty()) {
					System.out.println("Found " + legacyUsers.size() + " legacy users to migrate to BCrypt hashing.");
					for (Map<String, Object> user : legacyUsers) {
						int id = (int) user.get("ID");
						String rawPassword = (String) user.get("Password");
						String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
						jdbcTemplate.update("UPDATE TBL_USER SET Hash = ? WHERE ID = ?", hashedPassword, id);
					}
					System.out.println("Successfully migrated all legacy user passwords to BCrypt hashes.");
				}
			} catch (Exception e) {
				System.err.println("Error migrating legacy passwords: " + e.getMessage());
			}
		}
	}

	public int RegisterUser(String full_name, String email, String password, String mobile, int type) {
		try {
			String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
			String sql = "insert into TBL_USER (FullName, Email, Hash, Mobile, Status, Type) "
					+ "OUTPUT INSERTED.ID "
					+ "values (?, ?, ?, ?, 1, ?)";
			return jdbcTemplate.query(
				connection -> {
					java.sql.PreparedStatement ps = connection.prepareStatement(sql);
					ps.setNString(1, full_name);
					ps.setString(2, email);
					ps.setString(3, hashedPassword);
					ps.setString(4, mobile);
					ps.setInt(5, type);
					return ps;
				},
				rs -> {
					if (rs.next()) {
						return rs.getInt(1);
					}
					return -1;
				}
			);
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public String fn_user_type_name(int type_id) {
		try {
			String sql = "select Name from dbo.def_user_type where Value=?";
			List<String> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Name"), type_id);
			return results.isEmpty() ? "unknown" : results.get(0);
		} catch (Exception e) {
			e.printStackTrace();
			return "unknown";
		}
	}

	public String fn_org_name(int org_id) {
		try {
			String sql = "select Name from dbo.tbl_org where ID=?";
			List<String> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("Name"), org_id);
			return results.isEmpty() ? "unknown" : results.get(0);
		} catch (Exception e) {
			e.printStackTrace();
			return "unknown";
		}
	}

	public int UpdateUser(int user_id, int user_type, String full_name, String email) {
		try {
			String sql = "Update TBL_USER set Fullname = ?, Email = ?, Type = ? where ID = ?";
			return jdbcTemplate.update(
				connection -> {
					java.sql.PreparedStatement ps = connection.prepareStatement(sql);
					ps.setNString(1, full_name);
					ps.setString(2, email);
					ps.setInt(3, user_type);
					ps.setInt(4, user_id);
					return ps;
				}
			);
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int UpdateLockUserRight(int user_id, int lock_user) {
		try {
			String sql = "Update TBL_USER set LockUser = ? where ID = ?";
			return jdbcTemplate.update(sql, lock_user, user_id);
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int UpdateLockDocRight(int user_id, int lock_doc) {
		try {
			String sql = "Update TBL_USER set LockDoc = ? where ID = ?";
			return jdbcTemplate.update(sql, lock_doc, user_id);
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int UpdateUserOrg(int user_id, int org_id) {
		try {
			String sql = "Update TBL_ORG_MEMBER set ORG_ID = ? where MEMBER_ID = ?";
			return jdbcTemplate.update(sql, org_id, user_id);
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public boolean typeExisted(int type, int userId) {
		try {
			String sql = "select count(*) from dbo.tbl_user where ID=? and Type=?";
			Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, type);
			return count != null && count > 0;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public JSONObject getUserDepartmentInfo(int userId) {
		JSONObject joOrg = new JSONObject();
		try {
			// Query tbl_user to get email, join employees to get dept code, then look in
			// departments
			String sql = "SELECT d.dept_id, d.dept_name, d.dept_code "
					+ "FROM tbl_user u "
					+ "INNER JOIN employees e ON e.uEmail = u.Email "
					+ "INNER JOIN departments d ON d.dept_code = ( "
					+ "    CASE "
					+ "        WHEN e.uUnit = N'Trung tâm Đào tạo Bưu chính Viễn thông' THEN 'TDT1' "
					+ "        WHEN e.uCode LIKE '%.%.%' THEN SUBSTRING(e.uCode, CHARINDEX('.', e.uCode) + 1, "
					+ "            CHARINDEX('.', e.uCode, CHARINDEX('.', e.uCode) + 1) - (CHARINDEX('.', e.uCode) + 1)) "
					+ "        ELSE LEFT(e.uCode, 4) "
					+ "    END "
					+ ") "
					+ "WHERE u.ID = ? AND (u.IsDeleted is null or u.IsDeleted = '0')";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
			if (!rows.isEmpty()) {
				Map<String, Object> row = rows.get(0);
				joOrg.put("dept_id", row.get("dept_id"));
				joOrg.put("dept_name", row.get("dept_name"));
				joOrg.put("dept_code", row.get("dept_code"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return joOrg;
	}
}
