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
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			try {
				jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_users_ID' AND object_id = OBJECT_ID('users')) CREATE NONCLUSTERED INDEX IX_users_ID ON dbo.users(ID);");
				jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_users_Email' AND object_id = OBJECT_ID('users')) CREATE NONCLUSTERED INDEX IX_users_Email ON dbo.users(Email);");
				jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_personnel_email' AND object_id = OBJECT_ID('personnel')) CREATE NONCLUSTERED INDEX IX_personnel_email ON dbo.personnel(email);");
				System.out.println("[UserExtend] Verified database performance indexes on users and personnel tables.");
			} catch (Exception e) {
				System.err.println("[UserExtend] Index init notice: " + e.getMessage());
			}
		});
	}

	/**
	 * Provision personnel who are:
	 *   (a) leaderId of any org (orgs.leaderId IS NOT NULL → match personnel.id)
	 *   (b) belonging to Lãnh đạo Học viện org units (Bắc + Nam)
	 * into the users table if they are not already present.
	 * Default password = BCrypt(maCanBo.toLowerCase()), falling back to a UUID.
	 */
	private static final String LD_HOC_VIEN_ID     = "66a308ce8068e53428da202c";
	private static final String LD_HOC_VIEN_NAM_ID = "66a308ce8068e53428da202d";

	private void provisionOrgLeadersAndLanhDao() {
		try {
			// Query all personnel who are org leaders or Lãnh đạo Học viện
			String sql =
				"SELECT DISTINCT p.id, p.emailCanBo, p.maCanBo, u.ID AS existingUserId, u.Hash AS existingHash " +
				"FROM personnel p " +
				"LEFT JOIN users u ON u.ID = p.id OR (p.emailCanBo IS NOT NULL AND u.Email = p.emailCanBo) " +
				"WHERE p.isDeleted = 0 " +
				"  AND (p.emailCanBo IS NOT NULL AND p.emailCanBo <> '') " +
				"  AND (" +
				"    p.id IN (SELECT leaderId FROM orgs WHERE leaderId IS NOT NULL AND leaderId <> '') " +
				"    OR p.donViChinhId IN (?, ?) " +
				"    OR p.donViL3Id   IN (?, ?) " +
				"  )";

			List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
				sql,
				LD_HOC_VIEN_ID, LD_HOC_VIEN_NAM_ID,
				LD_HOC_VIEN_ID, LD_HOC_VIEN_NAM_ID
			);

			if (candidates.isEmpty()) {
				System.out.println("[AutoProvision] No org leaders / Lãnh đạo Học viện found in personnel.");
				return;
			}

			System.out.println("[AutoProvision] Processing " + candidates.size() + " org leader(s) / Lãnh đạo Học viện...");
			int inserted = 0;
			int updated = 0;

			for (Map<String, Object> row : candidates) {
				try {
					String id             = row.get("id")             != null ? row.get("id").toString()             : null;
					String email          = row.get("emailCanBo")     != null ? row.get("emailCanBo").toString()     : null;
					String maCb           = row.get("maCanBo")        != null ? row.get("maCanBo").toString()        : null;
					String existingUserId = row.get("existingUserId") != null ? row.get("existingUserId").toString() : null;

					if (id == null || email == null) continue;

					if (maCb != null && !maCb.isBlank()) {
						String rawPassword = maCb.toLowerCase().trim();
						String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());

						if (existingUserId == null) {
							// Insert new user with default role_code = 'CHUYEN_VIEN'
							jdbcTemplate.update(
								"INSERT INTO users (ID, Email, Hash, Status, role_code) VALUES (?, ?, ?, 1, 'CHUYEN_VIEN')",
								id, email, hashedPassword
							);
							inserted++;
							System.out.println("[AutoProvision] Inserted user: " + email + " (id=" + id + ", pwd=maCanBo:" + rawPassword + ")");
						} else {
							// Update existing user hash to BCrypt(maCanBo.toLowerCase())
							jdbcTemplate.update(
								"UPDATE users SET Hash = ?, Email = ? WHERE ID = ?",
								hashedPassword, email, existingUserId
							);
							updated++;
							System.out.println("[AutoProvision] Updated hash for user: " + email + " (id=" + existingUserId + ", pwd=maCanBo:" + rawPassword + ")");
						}
					}
				} catch (Exception rowEx) {
					System.err.println("[AutoProvision] Error processing row: " + rowEx.getMessage());
				}
			}

			System.out.println("[AutoProvision] Done. Inserted " + inserted + ", updated " + updated + " user account(s).");
		} catch (Exception e) {
			System.err.println("[AutoProvision] Error during org leader provisioning: " + e.getMessage());
		}
	}


	public String RegisterUser(String idOrEmail, String email, String password, String mobile, String roleCode) {
		try {
			String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
			String targetEmail = (email != null && !email.isBlank()) ? email.trim() : (idOrEmail != null ? idOrEmail.trim() : "");
			
			String userId = null;
			if (idOrEmail != null && !idOrEmail.isBlank() && isNumeric(idOrEmail.trim())) {
				userId = idOrEmail.trim();
			}

			if (userId == null || userId.isBlank()) {
				try {
					List<String> pIds = jdbcTemplate.queryForList(
						"SELECT TOP 1 id FROM personnel WHERE (emailCanBo = ? OR email = ?) AND isDeleted = 0 AND ISNUMERIC(id) = 1", 
						String.class, targetEmail, targetEmail
					);
					if (!pIds.isEmpty() && pIds.get(0) != null && isNumeric(pIds.get(0))) {
						userId = pIds.get(0).trim();
					}
				} catch (Exception ex) {
				}
			}

			if (userId == null || userId.isBlank()) {
				try {
					List<Integer> maxIds = jdbcTemplate.queryForList(
						"SELECT COALESCE(MAX(CAST(ID AS INT)), 1000) + 1 FROM users WHERE ISNUMERIC(ID) = 1",
						Integer.class
					);
					if (!maxIds.isEmpty() && maxIds.get(0) != null) {
						userId = String.valueOf(maxIds.get(0));
					} else {
						userId = "1001";
					}
				} catch (Exception ex) {
					userId = String.valueOf(System.currentTimeMillis() / 1000);
				}
			}

			String finalRoleCode = (roleCode != null && !roleCode.isBlank()) ? roleCode : "CHUYEN_VIEN";
			String sql = "INSERT INTO users (ID, Email, Hash, Status, role_code) VALUES (?, ?, ?, 1, ?)";
			final String finalUserId = userId;
			int rows = jdbcTemplate.update(sql, finalUserId, targetEmail, hashedPassword, finalRoleCode);
			return rows > 0 ? finalUserId : null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public String RegisterUser(String idOrEmail, String email, String password, String mobile, int type) {
		String roleCode = type == 1 ? "ADMIN" : (type == 2 ? "LANH_DAO_HV" : (type == 3 ? "TRUONG_DON_VI" : "CHUYEN_VIEN"));
		return RegisterUser(idOrEmail, email, password, mobile, roleCode);
	}

	private static boolean isNumeric(String str) {
		if (str == null || str.isBlank()) return false;
		try {
			Long.parseLong(str.trim());
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public String fn_user_type_name(String roleCode) {
		if (roleCode == null) return "Chuyên viên";
		switch (roleCode.toUpperCase()) {
			case "ADMIN": return "Admin";
			case "LANH_DAO_HV": return "Lãnh đạo HV";
			case "TRUONG_DON_VI": return "Trưởng đơn vị";
			default: return "Chuyên viên";
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

	public int UpdateUser(Object user_id, String roleCode, String email, String updaterId) {
		try {
			String sql = "Update users set Email = ?, role_code = ?, UpdatedBy = ?, UpdatedTime = GETDATE() where ID = ?";
			return jdbcTemplate.update(sql, email, roleCode, updaterId, user_id.toString());
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int UpdateUser(Object user_id, int user_type, String email, String updaterId) {
		String roleCode = user_type == 1 ? "ADMIN" : (user_type == 2 ? "LANH_DAO_HV" : (user_type == 3 ? "TRUONG_DON_VI" : "CHUYEN_VIEN"));
		return UpdateUser(user_id, roleCode, email, updaterId);
	}

	public int UpdateUser(Object user_id, String roleCode, String email) {
		return UpdateUser(user_id, roleCode, email, (String) null);
	}

	public int UpdateLockUserRight(Object user_id, int lock_user, String updaterId) {
		try {
			String sql = "Update users set LockUser = ?, UpdatedBy = ?, UpdatedTime = GETDATE() where ID = ?";
			return jdbcTemplate.update(sql, lock_user, updaterId, user_id.toString());
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int UpdateLockUserRight(Object user_id, int lock_user) {
		return UpdateLockUserRight(user_id, lock_user, null);
	}

	public int UpdateLockDocRight(Object user_id, int lock_doc, String updaterId) {
		try {
			String sql = "Update users set LockDoc = ?, UpdatedBy = ?, UpdatedTime = GETDATE() where ID = ?";
			return jdbcTemplate.update(sql, lock_doc, updaterId, user_id.toString());
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int UpdateLockDocRight(Object user_id, int lock_doc) {
		return UpdateLockDocRight(user_id, lock_doc, null);
	}

	public int UpdateUserOrg(Object user_id, int org_id) {
		try {
			String sql = "Update TBL_ORG_MEMBER set ORG_ID = ? where MEMBER_ID = ?";
			return jdbcTemplate.update(sql, org_id, user_id.toString());
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public boolean typeExisted(String roleCode, Object userId) {
		try {
			String sql = "select count(*) from dbo.users where ID=? and role_code=?";
			Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId.toString(), roleCode);
			return count != null && count > 0;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean typeExisted(int userType, Object userId) {
		String roleCode = userType == 1 ? "ADMIN" : (userType == 2 ? "LANH_DAO_HV" : (userType == 3 ? "TRUONG_DON_VI" : "CHUYEN_VIEN"));
		return typeExisted(roleCode, userId);
	}

	/**
	 * Map user to Level 3 Organization via personnel table.
	 */
	public JSONObject getUserDepartmentInfo(Object userId) {
		JSONObject joOrg = new JSONObject();
		try {
			String sql = 
				"SELECT TOP 1 " +
				"    COALESCE(p0.donViL3Id, p0.donViChinhId, p1.donViL3Id, p1.donViChinhId, p2.donViL3Id, p2.donViChinhId) AS dept_id, " +
				"    COALESCE(o3.ten, oChinh.ten, N'Chưa xếp đơn vị') AS dept_name, " +
				"    COALESCE(o3.maDonVi, oChinh.maDonVi, '') AS dept_code " +
				"FROM users u WITH (NOLOCK) " +
				"LEFT JOIN personnel p0 WITH (NOLOCK) ON CAST(p0.id AS VARCHAR(100)) = CAST(u.ID AS VARCHAR(100)) AND p0.isDeleted = 0 " +
				"LEFT JOIN personnel p1 WITH (NOLOCK) ON p0.id IS NULL AND p1.emailCanBo = u.Email AND p1.isDeleted = 0 " +
				"LEFT JOIN personnel p2 WITH (NOLOCK) ON p0.id IS NULL AND p1.id IS NULL AND p2.email = u.Email AND p2.isDeleted = 0 " +
				"LEFT JOIN orgs o3 WITH (NOLOCK) ON o3.id = COALESCE(p0.donViL3Id, p1.donViL3Id, p2.donViL3Id) " +
				"LEFT JOIN orgs oChinh WITH (NOLOCK) ON oChinh.id = COALESCE(p0.donViChinhId, p1.donViChinhId, p2.donViChinhId) " +
				"WHERE u.ID = ? AND (u.IsDeleted IS NULL OR u.IsDeleted = '0')";

			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId != null ? userId.toString() : "");
			if (!rows.isEmpty()) {
				Map<String, Object> row = rows.get(0);
				joOrg.put("dept_id", row.get("dept_id") != null ? row.get("dept_id") : "");
				joOrg.put("dept_name", row.get("dept_name") != null ? row.get("dept_name") : "N/A");
				joOrg.put("dept_code", row.get("dept_code") != null ? row.get("dept_code") : "");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return joOrg;
	}
}
