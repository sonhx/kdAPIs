package com.bcrs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;

@Service
public class BcrsExtend {
	public final String host = Config.host;

	@Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

	public String removeExtra(String sInput, String regex) {
		while (sInput.matches(regex)) {
			sInput = sInput.split(regex)[1].trim();
		}
		return sInput;
	}

	public JSONArray listBcrs(int kd_id, String doituong_kd) {
		JSONArray jsaBcrss = new JSONArray();
		String sql = "SELECT a.*, c.Fullname as Creator FROM TBL_BCRasoat a "
				+ " INNER JOIN TBL_USER c on c.ID = a.CreatedBy "
				+ " where a.kd_id = ? and a.doituong_kd = ? and (a.IsDeleted is null or a.IsDeleted=0)";
		
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id, doituong_kd);
		for (Map<String, Object> row : rows) {
			JSONObject joBcrs = new JSONObject();
			joBcrs.put("id", row.get("ID"));
			joBcrs.put("ten", row.get("ten"));
			joBcrs.put("ghi_chu", row.get("ghi_chu"));
			joBcrs.put("created_time", row.get("CreatedTime"));
			joBcrs.put("creator", row.get("Creator"));
			String path = row.get("path") == null ? "" : host + row.get("path");
			joBcrs.put("path", path);
			joBcrs.put("so_vb", row.get("so_vb") == null ? "" : row.get("so_vb"));
			joBcrs.put("ngay_bh", row.get("ngay_bh") == null ? "" : row.get("ngay_bh"));
			jsaBcrss.put(joBcrs);
		}
		return jsaBcrss;
	}

	public boolean isBcrsExisted(String ten) {
		String sql = "select count(*) from TBL_BCRasoat where ten_vb = ? and (IsDeleted is null or IsDeleted=0)";
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ten);
		return count != null && count > 0;
	}

	public int updateBcrs(int id, String so_vb, String ngay_bh, String ghi_chu, int created_by) {
		String sql = "update TBL_BCRasoat set so_vb = ?, ngay_bh = CONVERT(DATETIME, ?, 102), ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
		return jdbcTemplate.update(sql, so_vb, ngay_bh, ghi_chu, created_by, id);
	}

	public int deleteBcrs(int id) {
		return jdbcTemplate.update("update TBL_BCRasoat set IsDeleted = 1 where ID = ?", id);
	}
}
