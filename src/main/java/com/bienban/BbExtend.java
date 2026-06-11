package com.bienban;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class BbExtend {
	public final String host = Config.host;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public JSONArray listToChuc() {
		JSONArray jsaTochuc = new JSONArray();
		String sql = "select * from TBL_Tochuc where (IsDeleted is null or IsDeleted =0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
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
		String sql = "SELECT a.*, c.Fullname as Creator FROM TBL_Bienban a "
				+ " INNER JOIN TBL_USER c on c.ID = a.CreatedBy "
				+ " where a.kd_id = ? and a.doituong_kd = ? and (a.IsDeleted is null or a.IsDeleted=0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id, doituong_kd);
		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("id", row.get("ID"));
			jo.put("ten", row.get("ten"));
			jo.put("ghi_chu", row.get("ghi_chu"));
			jo.put("created_time", row.get("CreatedTime"));
			jo.put("creator", row.get("Creator"));
			String path = row.get("path") == null ? "" : host + row.get("path");
			jo.put("path", path);
			jo.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
			jo.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh"));
			jsa.put(jo);
		}
		return jsa;
	}

	public boolean isBMExisted(String ten) {
		String sql = "select count(*) from TBL_Bieumau where ten = ? and (IsDeleted is null or IsDeleted=0)";
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ten);
		return count != null && count > 0;
	}

	public int updateBb(String so_vb, String ngay_bh, int bm_id, String ghi_chu, int created_by) {
		String sql = "update TBL_Bienban set so_vb = ?, ngay_bh = CONVERT(DATETIME, ?, 102), ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
		return jdbcTemplate.update(sql, so_vb, ngay_bh, ghi_chu, created_by, bm_id);
	}

	public int deleteBb(int id) {
		return jdbcTemplate.update("update TBL_Bienban set IsDeleted = 1 where ID = ?", id);
	}
}
