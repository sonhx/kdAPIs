package com.user;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserExtend {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public int RegisterUser(String full_name, String email, String password, String mobile, int type) {
		try {
			String sql = "insert into TBL_USER (FullName, Email, Password, Mobile, Status, Type) "
					+ "OUTPUT INSERTED.ID "
					+ "values (?, ?, ?, ?, 1, ?)";
			return jdbcTemplate.queryForObject(sql, Integer.class, full_name, email, password, mobile, type);
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
			return jdbcTemplate.update(sql, full_name, email, user_type, user_id);
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
}
