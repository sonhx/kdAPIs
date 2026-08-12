package com.bienban;

import java.util.ArrayList;
import java.util.Collections;
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
public class BbExtend {
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

	public JSONArray listToChuc() {
		JSONArray jsaTochuc = new JSONArray();
		String sql = "select * from TBL_Tochuc where (IsDeleted is null or IsDeleted =0)";
		List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql);
		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("ten_tochuc", row.get("ten_tochuc"));
			jo.put("id", row.get("ID"));
			jsaTochuc.put(jo);
		}
		return jsaTochuc;
	}

	public String removeExtra(String sInput, String regex) {
		while (sInput.matches(regex)) {
			sInput = sInput.split(regex)[1].trim();
		}
		return sInput;
	}

	public JSONArray listBb(int kd_id, String doituong_kd) {
		JSONArray jsa = new JSONArray();
		String sql = "SELECT a.* FROM TBL_Bienban a "
				+ " where a.kd_id = ? and a.doituong_kd = ? and (a.IsDeleted is null or a.IsDeleted=0)";
		List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, kd_id, doituong_kd);

		Set<String> creatorIds = new HashSet<>();
		for (Map<String, Object> r : rows) {
			if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
		}
		Map<String, String> creatorNames = getCreatorNames(creatorIds);

		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("id", row.get("ID"));
			jo.put("ten", row.get("ten"));
			jo.put("ghi_chu", row.get("ghi_chu"));
			jo.put("created_time", row.get("CreatedTime"));

			String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
			jo.put("creator", creatorNames.getOrDefault(cbStr, ""));

			String path = row.get("path") == null ? "" : host + row.get("path");
			jo.put("path", path);
			jo.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
			jo.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh"));
			jsa.put(jo);
		}
		return jsa;
	}

	public boolean isBMExisted(String ten) {
		String sql = "select count(*) from TBL_Bienban where ten = ? and (IsDeleted is null or IsDeleted=0)";
		Integer count = evidenceJdbcTemplate.queryForObject(sql, Integer.class, ten);
		return count != null && count > 0;
	}

	public int updateBb(String so_vb, String ngay_bh, int bm_id, String ghi_chu, int created_by) {
		String sql = "update TBL_Bienban set so_vb = ?, ngay_bh = CONVERT(DATETIME, ?, 102), ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
		return evidenceJdbcTemplate.update(sql, so_vb, ngay_bh, ghi_chu, created_by, bm_id);
	}

	public int deleteBb(int id) {
		return evidenceJdbcTemplate.update("update TBL_Bienban set IsDeleted = 1 where ID = ?", id);
	}
}
