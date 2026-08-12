package com.ctcl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class CtclExtend {
	public final String host = Config.host;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	@Qualifier("evidenceJdbcTemplate")
	private JdbcTemplate evidenceJdbcTemplate;

	private Map<String, String> getCreatorNames(Set<String> createdByIds) {
		Map<String, String> resultMap = new HashMap<>();
		if (createdByIds == null || createdByIds.isEmpty()) return resultMap;

		List<String> idList = new ArrayList<>(createdByIds);
		String inSql = String.join(",", Collections.nCopies(idList.size(), "?"));

		try {
			String sql = "SELECT CAST(id AS VARCHAR(100)) as id, fullname FROM personnel WHERE CAST(id AS VARCHAR(100)) IN (" + inSql + ") AND isDeleted = 0";
			List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, idList.toArray());
			for (Map<String, Object> r : list) {
				if (r.get("id") != null && r.get("fullname") != null) {
					resultMap.put(r.get("id").toString().trim(), r.get("fullname").toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultMap;
	}

	public String removeExtra(String sInput, String regex) {
		while (sInput.matches(regex)) {
			sInput = sInput.split(regex)[1].trim();
			removeExtra(sInput, regex);
		}
		return sInput;
	}

	public int UserOrg(int userID) {
		String sql = "select ORG_ID from tbl_org_member where member_id = ? and (isdeleted is null or isdeleted = 0)";
		List<Integer> list = evidenceJdbcTemplate.queryForList(sql, Integer.class, userID);
		return list.isEmpty() ? 0 : list.get(0);
	}

	public JSONArray listCtcl(int kd_id, String doituong_kd) {
		JSONArray jsa = new JSONArray();
		String sql = "SELECT a.*, b.Name as org_name, b.Code as org_code FROM TBL_CaitienCL a "
				+ " LEFT JOIN TBL_ORG b ON b.ID = a.org_id "
				+ " WHERE a.kd_id = ? AND a.doituong_kd = ? AND (a.IsDeleted IS NULL OR a.IsDeleted = 0)";

		List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, kd_id, doituong_kd);

		Set<String> creatorIds = new HashSet<>();
		for (Map<String, Object> row : rows) {
			Object cb = row.get("CreatedBy");
			if (cb != null) creatorIds.add(cb.toString().trim());
		}
		Map<String, String> creatorNames = getCreatorNames(creatorIds);

		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("id", row.get("ID"));
			jo.put("noi_dung", row.get("noi_dung"));
			jo.put("ten", row.get("ten"));
			jo.put("ghi_chu", row.get("ghi_chu"));
			jo.put("created_time", row.get("CreatedTime"));

			String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
			jo.put("creator", creatorNames.getOrDefault(cbStr, ""));

			String path = row.get("path") == null ? "" : host + row.get("path");
			jo.put("path", path);
			jo.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
			jo.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh"));
			jo.put("thoi_han", row.get("thoi_han") == null ? "" : row.get("thoi_han"));
			jo.put("org_id", row.get("org_id"));
			jo.put("org_name", row.get("org_name"));
			jo.put("org_code", row.get("org_code"));
			jo.put("is_locked", row.get("is_locked"));
			jsa.put(jo);
		}
		return jsa;
	}

	public JSONArray listCtclWithOrg(int kd_id, String doituong_kd, int org_id) {
		JSONArray jsa = new JSONArray();
		String sql = "SELECT a.*, b.Name as org_name, b.Code as org_code FROM TBL_CaitienCL a "
				+ " LEFT JOIN TBL_ORG b ON b.ID = a.org_id "
				+ " WHERE a.kd_id = ? AND a.org_id = ? AND a.doituong_kd = ? AND (a.IsDeleted IS NULL OR a.IsDeleted = 0)";

		List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, kd_id, org_id, doituong_kd);

		Set<String> creatorIds = new HashSet<>();
		for (Map<String, Object> row : rows) {
			Object cb = row.get("CreatedBy");
			if (cb != null) creatorIds.add(cb.toString().trim());
		}
		Map<String, String> creatorNames = getCreatorNames(creatorIds);

		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("id", row.get("ID"));
			jo.put("noi_dung", row.get("noi_dung"));
			jo.put("ten", row.get("ten"));
			jo.put("ghi_chu", row.get("ghi_chu"));
			jo.put("created_time", row.get("CreatedTime"));

			String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
			jo.put("creator", creatorNames.getOrDefault(cbStr, ""));

			String path = row.get("path") == null ? "" : host + row.get("path");
			jo.put("path", path);
			jo.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
			jo.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh"));
			jo.put("thoi_han", row.get("thoi_han") == null ? "" : row.get("thoi_han"));
			jo.put("org_id", row.get("org_id"));
			jo.put("org_name", row.get("org_name"));
			jo.put("org_code", row.get("org_code"));
			jo.put("is_locked", row.get("is_locked"));
			jsa.put(jo);
		}
		return jsa;
	}

	public boolean isCtclExisted(String ten) {
		String sql = "select count(*) from TBL_CaitienCL where ten = ? and (IsDeleted is null or IsDeleted=0)";
		Integer count = evidenceJdbcTemplate.queryForObject(sql, Integer.class, ten);
		return count != null && count > 0;
	}

	public int updateCtcl(int id, int org_id, String so_vb, String ngay_bh, String path, String ghi_chu, int created_by) {
		StringBuilder sql = new StringBuilder("update TBL_CaitienCL set so_vb = ?, ngay_bh = CONVERT(DATETIME, ?, 102), ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ?");
		List<Object> params = new ArrayList<>();
		params.add(so_vb);
		params.add(ngay_bh);
		params.add(ghi_chu);
		params.add(created_by);

		if (org_id > -1) {
			sql.append(", org_id = ?");
			params.add(org_id);
		}
		if (path != null) {
			sql.append(", path = ?");
			params.add(path);
		}
		sql.append(" where ID = ?");
		params.add(id);

		return evidenceJdbcTemplate.update(sql.toString(), params.toArray());
	}

	public int updateStateCtcl(int id, int is_locked) {
		String sql = "update TBL_CaitienCL set is_locked = ? where ID = ?";
		return evidenceJdbcTemplate.update(sql, is_locked, id);
	}

	public int assignCtcl(int org_id, String noi_dung, String thoi_han, String ghi_chu, int created_by,
			int kd_id, String doituong_kd) {
		String sql = (thoi_han == null)
				? "insert into TBL_CaitienCL (noi_dung, org_id, ghi_chu, CreatedTime, CreatedBy, kd_id, doituong_kd) values (?, ?, ?, GETDATE(), ?, ?, ?)"
				: "insert into TBL_CaitienCL (noi_dung, org_id, thoi_han, ghi_chu, CreatedTime, CreatedBy, kd_id, doituong_kd) values (?, ?, CONVERT(DATETIME, ?, 102), ?, GETDATE(), ?, ?, ?)";

		if (thoi_han == null) {
			return evidenceJdbcTemplate.update(sql, noi_dung, org_id, ghi_chu, created_by, kd_id, doituong_kd);
		} else {
			return evidenceJdbcTemplate.update(sql, noi_dung, org_id, thoi_han, ghi_chu, created_by, kd_id, doituong_kd);
		}
	}

	public int assignCtcl_upload(String noi_dung, String ten, String so_vb, String ngay_bh,
			String path, String ghi_chu, int created_by,
			int kd_id, String doituong_kd) {
		String sql = "insert into TBL_CaitienCL (noi_dung, ten, so_vb, ngay_bh, path, ghi_chu, CreatedTime, CreatedBy, kd_id, doituong_kd) "
				+ " values (?, ?, ?, CONVERT(DATETIME, ?, 102), ?, ?, GETDATE(), ?, ?, ?)";
		return evidenceJdbcTemplate.update(sql, noi_dung, ten, so_vb, ngay_bh, path, ghi_chu, created_by, kd_id, doituong_kd);
	}

	public int deleteCtcl(int id) {
		String sql = "update TBL_CaitienCL set IsDeleted = 1 where ID = ?";
		return evidenceJdbcTemplate.update(sql, id);
	}

	public JSONObject getDetails(int id) {
		JSONObject joDetails = new JSONObject();
		String sql = "select * from TBL_CaitienCL where ID = ?";
		List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, id);
		if (!rows.isEmpty()) {
			Map<String, Object> row = rows.get(0);
			joDetails.put("id", id);
			joDetails.put("noi_dung", row.get("noi_dung"));
			joDetails.put("path", row.get("path"));
			joDetails.put("doituong_kd", row.get("doituong_kd"));
			joDetails.put("kd_id", row.get("kd_id"));
		}
		return joDetails;
	}
}
