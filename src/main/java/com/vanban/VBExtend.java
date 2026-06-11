package com.vanban;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class VBExtend {
	public final String host = Config.host;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public int createTochuc(String ten_tc) {
		return jdbcTemplate.update("insert into TBL_Tochuc (ten_tochuc) values (?)", ten_tc);
	}

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

	public JSONArray listVB(int kd_id, String doituong_kd, String type) {
		JSONArray jsaVBs = new JSONArray();
		String sql = "SELECT a.*, b.ten_tochuc as tentc, c.Fullname as Creator FROM TBL_Vanban a "
				+ " LEFT JOIN TBL_Tochuc b on b.ID = a.id_tochuc "
				+ " INNER JOIN TBL_USER c on c.ID = a.CreatedBy "
				+ " where a.kd_id = ? and a.doituong_kd = ? and a.type = ? and (a.IsDeleted is null or a.IsDeleted=0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id, doituong_kd, type);
		for (Map<String, Object> row : rows) {
			JSONObject joVB = new JSONObject();
			joVB.put("id", row.get("ID"));
			joVB.put("ten", row.get("ten"));
			joVB.put("id_tochuc", row.get("id_tochuc"));
			joVB.put("ten_tochuc", row.get("tentc"));
			joVB.put("ngay_gui", row.get("ngay_gui"));
			joVB.put("ngay_nhan", row.get("ngay_nhan"));
			joVB.put("ghi_chu", row.get("ghi_chu"));
			joVB.put("created_time", row.get("CreatedTime"));
			joVB.put("creator", row.get("Creator"));
			String path = row.get("path") == null ? "" : host + row.get("path");
			joVB.put("path", path);
			joVB.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
			joVB.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh"));
			joVB.put("type", row.get("type"));
			jsaVBs.put(joVB);
		}
		return jsaVBs;
	}

	public boolean isVBExisted(String ten_vb) {
		String sql = "select count(*) from TBL_Vanban where ten = ? and (IsDeleted is null or IsDeleted=0)";
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ten_vb);
		return count != null && count > 0;
	}

	public int updateVb(int vb_id, String so_vb, String ngay_bh, String ngay_gui, int tc_id, String ghi_chu, int created_by) {
		StringBuilder sql = new StringBuilder("update TBL_Vanban set ");
		List<Object> params = new ArrayList<>();
		sql.append(" so_vb = ? ,"); params.add(so_vb);
		sql.append(" ngay_bh = CONVERT(DATETIME, ?, 102),"); params.add(ngay_bh);
		if (ngay_gui != null && !ngay_gui.isEmpty()) {
			sql.append(" ngay_gui = CONVERT(DATETIME, ?, 102),");
			params.add(ngay_gui);
		}
		sql.append(" ghi_chu = ?,"); params.add(ghi_chu);
		if (tc_id > 0) {
			sql.append(" id_tochuc = ?,");
			params.add(tc_id);
		}
		sql.append(" CreatedTime = GETDATE(), CreatedBy = ? ");
		params.add(created_by);
		sql.append(" where ID = ?");
		params.add(vb_id);

		return jdbcTemplate.update(sql.toString(), params.toArray());
	}

	public int deleteVb(int id) {
		return jdbcTemplate.update("update TBL_Vanban set IsDeleted = 1 where ID = ?", id);
	}
}
