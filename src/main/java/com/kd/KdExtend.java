package com.kd;

import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KdExtend {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public JSONArray listKd(int kd_scope, String type) {
		JSONArray jsaKds = new JSONArray();
		String sScopeClause = kd_scope == -1 ? "" : " and e.loaihinh_id = ?";
		String sTypeClause = type == null ? ""
				: type.equals("cs") ? " where c.abbr = 'cs'"
						: type.equals("ct") ? " where c.abbr <> 'cs'" : " where c.abbr = ?";

		String sql = "SELECT a.ID, a.ten, a.ghi_chu, "
				+ " b.ten as cycle, b.id as cycle_id, "
				+ " c.Fullname AS Creator, "
				+ " d.ten AS loai_hinh, d.ID as loai_hinh_id, "
				+ " e.id as standard_id, "
				+ " e.ten as standard, a.IsCurrent, a.CreatedTime "
				+ " FROM TBL_Kiemdinh a "
				+ " INNER JOIN TBL_USER c ON c.ID = a.CreatedBy  "
				+ " INNER JOIN TBL_CYCLE b on b.ID = a.CYCLE_ID "
				+ " INNER JOIN TBL_STANDARD e on e.ID = b.StandardID  "
				+ " INNER JOIN DEF_LOAIHINH_KD d ON d.ID = e.loaihinh_id  "
				+ " WHERE  (a.IsDeleted IS NULL OR a.IsDeleted= 0) "
				+ " and a.ID in (SELECT DISTINCT a.ID FROM [dbo].[TBL_Kiemdinh] a "
				+ " INNER JOIN TBL_KIEMDINH_CT b on b.KD_ID = a.ID "
				+ " INNER JOIN TBL_Nganh_daotao c on c.ID = b.CT_ID "
				+ sTypeClause + " )"
				+ sScopeClause;

		List<Object> params = new ArrayList<>();
		if (type != null && !type.equals("cs") && !type.equals("ct")) {
			params.add(type);
		}
		if (kd_scope != -1) {
			params.add(kd_scope);
		}

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
		for (Map<String, Object> row : rows) {
			JSONObject joKd = new JSONObject();
			int kd_id = (int) row.get("ID");
			joKd.put("id", kd_id);
			joKd.put("ten", row.get("ten"));
			joKd.put("ghi_chu", row.get("ghi_chu"));
			joKd.put("standard_id", row.get("standard_id"));
			joKd.put("standard", row.get("standard"));
			joKd.put("cycle", row.get("cycle"));
			joKd.put("cycle_id", row.get("cycle_id"));
			joKd.put("progs", ListCTsbyKD(kd_id));
			joKd.put("created_time", row.get("CreatedTime"));
			joKd.put("creator", row.get("Creator"));
			joKd.put("is_current", row.get("IsCurrent"));
			joKd.put("loai_hinh", row.get("loai_hinh"));
			joKd.put("loai_hinh_id", row.get("loai_hinh_id"));
			jsaKds.put(joKd);
		}
		return jsaKds;
	}

	public JSONArray listKdbyCT(int kd_scope, String type, int ct_id, int cycle_id) {
		JSONArray jsaKds = new JSONArray();
		String sScopeClause = kd_scope == -1 ? "" : " and e.loaihinh_id = ?";
		String sTypeClause = type == null ? ""
				: type.equals("cs") ? " where c.abbr = 'cs'"
						: type.equals("ct") ? " where c.abbr <> 'cs'" : " where c.abbr = ?";
		String sCtClause = ct_id == -1 ? "" : " and b.CT_ID = ?";
		String sCycleClause = cycle_id == -1 ? "" : " and b.ID = ?";

		String sql = "SELECT a.ID, a.ten, a.ghi_chu, "
				+ " b.ten as cycle, b.id as cycle_id, "
				+ " c.Fullname AS Creator, "
				+ " d.ten AS loai_hinh, d.ID as loai_hinh_id, "
				+ " e.id as standard_id, "
				+ " e.ten as standard, a.IsCurrent, a.CreatedTime "
				+ " FROM TBL_Kiemdinh a "
				+ " INNER JOIN TBL_USER c ON c.ID = a.CreatedBy  "
				+ " INNER JOIN TBL_CYCLE b on b.ID = a.CYCLE_ID "
				+ " INNER JOIN TBL_STANDARD e on e.ID = b.StandardID  "
				+ " INNER JOIN DEF_LOAIHINH_KD d ON d.ID = e.loaihinh_id  "
				+ " WHERE  (a.IsDeleted IS NULL OR a.IsDeleted= 0) "
				+ " and a.ID in (SELECT DISTINCT a.ID FROM [dbo].[TBL_Kiemdinh] a "
				+ " INNER JOIN TBL_KIEMDINH_CT b on b.KD_ID = a.ID "
				+ " INNER JOIN TBL_Nganh_daotao c on c.ID = b.CT_ID "
				+ sCtClause
				+ sTypeClause + " )"
				+ sScopeClause
				+ sCycleClause;

		List<Object> params = new ArrayList<>();
		if (ct_id != -1) params.add(ct_id);
		if (type != null && !type.equals("cs") && !type.equals("ct")) params.add(type);
		if (kd_scope != -1) params.add(kd_scope);
		if (cycle_id != -1) params.add(cycle_id);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
		for (Map<String, Object> row : rows) {
			JSONObject joKd = new JSONObject();
			joKd.put("id", row.get("ID"));
			joKd.put("ten", row.get("ten"));
			joKd.put("ghi_chu", row.get("ghi_chu"));
			joKd.put("standard_id", row.get("standard_id"));
			joKd.put("standard", row.get("standard"));
			joKd.put("cycle", row.get("cycle"));
			joKd.put("cycle_id", row.get("cycle_id"));
			joKd.put("created_time", row.get("CreatedTime"));
			joKd.put("creator", row.get("Creator"));
			joKd.put("is_current", row.get("IsCurrent"));
			joKd.put("loai_hinh", row.get("loai_hinh"));
			joKd.put("loai_hinh_id", row.get("loai_hinh_id"));
			jsaKds.put(joKd);
		}
		return jsaKds;
	}

	public JSONArray listCyclebyCT_ed(int kd_scope, String type, int ct_id) {
		JSONArray jsaKds = new JSONArray();
		String sScopeClause = kd_scope == -1 ? "" : " and e.loaihinh_id = ?";
		String sTypeClause = type == null ? ""
				: type.equals("cs") ? " where c.abbr = 'cs'"
						: type.equals("ct") ? " where c.abbr <> 'cs'" : " where c.abbr = ?";
		String sCtClause = ct_id == -1 ? "" : " and b.CT_ID = ?";

		String sql = "SELECT a.ID, a.ten, a.ghi_chu, "
				+ " b.ten as cycle, b.id as cycle_id, "
				+ " c.Fullname AS Creator, "
				+ " d.ten AS loai_hinh, d.ID as loai_hinh_id, "
				+ " e.id as standard_id, "
				+ " e.ten as standard, a.IsCurrent, a.CreatedTime "
				+ " FROM TBL_Kiemdinh a "
				+ " INNER JOIN TBL_USER c ON c.ID = a.CreatedBy  "
				+ " INNER JOIN TBL_CYCLE b on b.ID = a.CYCLE_ID "
				+ " INNER JOIN TBL_STANDARD e on e.ID = b.StandardID  "
				+ " INNER JOIN DEF_LOAIHINH_KD d ON d.ID = e.loaihinh_id  "
				+ " WHERE  (a.IsDeleted IS NULL OR a.IsDeleted= 0) "
				+ " and a.ID in (SELECT DISTINCT a.ID FROM [dbo].[TBL_Kiemdinh] a "
				+ " INNER JOIN TBL_KIEMDINH_CT b on b.KD_ID = a.ID "
				+ " INNER JOIN TBL_Nganh_daotao c on c.ID = b.CT_ID "
				+ sCtClause
				+ sTypeClause + " )"
				+ sScopeClause;

		List<Object> params = new ArrayList<>();
		if (ct_id != -1) params.add(ct_id);
		if (type != null && !type.equals("cs") && !type.equals("ct")) params.add(type);
		if (kd_scope != -1) params.add(kd_scope);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
		for (Map<String, Object> row : rows) {
			int kd_id = (int) row.get("ID");
			String cycle = (String) row.get("cycle");
			int cycle_id = (int) row.get("cycle_id");

			JSONObject joKd = new JSONObject();
			joKd.put("id", kd_id);
			joKd.put("ten", row.get("ten"));
			joKd.put("ghi_chu", row.get("ghi_chu"));
			joKd.put("standard_id", row.get("standard_id"));
			joKd.put("standard", row.get("standard"));
			joKd.put("cycle", cycle);
			joKd.put("cycle_id", cycle_id);
			joKd.put("created_time", row.get("CreatedTime"));
			joKd.put("creator", row.get("Creator"));
			joKd.put("is_current", row.get("IsCurrent"));
			joKd.put("loai_hinh", row.get("loai_hinh"));
			joKd.put("loai_hinh_id", row.get("loai_hinh_id"));

			boolean found = false;
			for (int i = 0; i < jsaKds.length(); i++) {
				JSONObject joCycl = jsaKds.getJSONObject(i);
				if (joCycl.getInt("cycle_id") == cycle_id) {
					JSONArray jsK = joCycl.getJSONArray("kds");
					jsK.put(joKd);
					found = true;
					break;
				}
			}
			if (!found) {
				JSONObject joCycl = new JSONObject();
				joCycl.put("cycle", cycle);
				joCycl.put("cycle_id", cycle_id);
				JSONArray jsK = new JSONArray();
				jsK.put(joKd);
				joCycl.put("kds", jsK);
				jsaKds.put(joCycl);
			}
		}
		return jsaKds;
	}

	public JSONArray ListCTsbyKD(int kd_id) {
		JSONArray jsaCTs = new JSONArray();
		String sql = "select b.* from TBL_KIEMDINH_CT a INNER JOIN TBL_Nganh_daotao b on b.ID = a.CT_ID where a.KD_ID = ? and (a.IsDeleted is null or a.IsDeleted =0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id);
		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("id", row.get("ID"));
			jo.put("ten", row.get("ten"));
			jo.put("abbr", row.get("abbr"));
			jsaCTs.put(jo);
		}
		return jsaCTs;
	}

	public JSONArray listNganhDT(int kd_id) {
		JSONArray jsaCTKds = new JSONArray();
		String sql = (kd_id == -1)
				? "SELECT a.*, b.Fullname as creator FROM TBL_Nganh_daotao a INNER JOIN TBL_USER b on b.id = a.CreatedBy where (a.IsDeleted is null or a.IsDeleted=0)"
				: "SELECT a.*, b.Fullname AS creator FROM TBL_Nganh_daotao a INNER JOIN TBL_USER b ON b.id = a.CreatedBy INNER JOIN TBL_KIEMDINH_CT c ON c.CT_ID = a.ID WHERE ( a.IsDeleted IS NULL OR a.IsDeleted= 0 ) and ( c.IsDeleted IS NULL OR c.IsDeleted= 0 ) AND c.KD_ID = ?";

		List<Map<String, Object>> rows = (kd_id == -1) ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, kd_id);
		for (Map<String, Object> row : rows) {
			JSONObject joCTKd = new JSONObject();
			joCTKd.put("id", row.get("ID"));
			joCTKd.put("ten", row.get("ten"));
			joCTKd.put("abbr", row.get("abbr"));
			joCTKd.put("ghi_chu", row.get("ghi_chu"));
			joCTKd.put("created_time", row.get("CreatedTime"));
			joCTKd.put("creator", row.get("creator"));
			jsaCTKds.put(joCTKd);
		}
		return jsaCTKds;
	}

	public boolean isKdExisted(String ten) {
		String sql = "select 1 from TBL_Kiemdinh where ten = ? and (IsDeleted is null or IsDeleted=0)";
		List<Integer> list = jdbcTemplate.queryForList(sql, Integer.class, ten);
		return !list.isEmpty();
	}

	@Transactional
	public int updateKd(int id, String ten, int loai_hinh_id, String ghi_chu, JSONArray jsaProgs, int created_by) {
		try {
			jdbcTemplate.update("update TBL_Kiemdinh set ten = ?, loai_hinh_id = ?, ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?",
					ten, loai_hinh_id, ghi_chu, created_by, id);

			jdbcTemplate.update("update TBL_KIEMDINH_CT set IsDeleted = 1 where KD_ID = ?", id);

			for (int i = 0; i < jsaProgs.length(); i++) {
				JSONObject joProg = jsaProgs.getJSONObject(i);
				jdbcTemplate.update("insert into TBL_KIEMDINH_CT (KD_ID, CT_ID, CREATED_TIME, IsDeleted) values (?, ?, GETDATE(), 0)",
						id, joProg.getInt("id"));
			}
			return 1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public int deleteKd(int id) {
		return jdbcTemplate.update("update TBL_Kiemdinh set IsDeleted = 1 where ID = ?", id);
	}

	public void setCurrentKd(int kd_id, int createdby) {
		jdbcTemplate.update("UPDATE TBL_Kiemdinh SET ISCURRENT = CASE WHEN ID = ? THEN 1 ELSE 0 END, CreatedBy = ?", kd_id, createdby);
	}

	@Transactional
	public int addKd(String ten, int cycle_id, String ghi_chu, JSONArray jsaProgs, int createdby) {
		try {
			String sqlInsertKd = "insert into TBL_Kiemdinh (ten, cycle_id, ghi_chu, CreatedTime, CreatedBy, IsDeleted) output inserted.ID values(?, ?, ?, GETDATE(), ?, 0)";
			int newKdId = jdbcTemplate.queryForObject(sqlInsertKd, Integer.class, ten, cycle_id, ghi_chu, createdby);

			for (int i = 0; i < jsaProgs.length(); i++) {
				JSONObject joProg = jsaProgs.getJSONObject(i);
				jdbcTemplate.update("insert into TBL_KIEMDINH_CT (KD_ID, CT_ID, CREATED_TIME, IsDeleted) values (?, ?, GETDATE(), 0)",
						newKdId, joProg.getInt("id"));
			}
			return 1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public int createStandard(String ten, String abbr, int loaihinh_id, int createdby) {
		return jdbcTemplate.update("insert into TBL_Standard (ten, abbr, loaihinh_id, CreatedTime, CreatedBy, IsDeleted) values(?, ?, ?, GETDATE(), ?, 0)",
				ten, abbr, loaihinh_id, createdby);
	}

	public JSONArray listLoaihinh() {
		JSONArray jsaLoaihinhs = new JSONArray();
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, ten FROM DEF_LOAIHINH_KD");
		for (Map<String, Object> row : rows) {
			JSONObject joLoaihinh = new JSONObject();
			joLoaihinh.put("id", row.get("id"));
			joLoaihinh.put("ten", row.get("ten"));
			jsaLoaihinhs.put(joLoaihinh);
		}
		return jsaLoaihinhs;
	}

	public JSONArray listStandard(int loaihinh_id) {
		JSONArray jsaStandards = new JSONArray();
		String sLoaihinh_clause = loaihinh_id == -1 ? "" : " and a.loaihinh_id = ?";
		String sql = "SELECT a.*, b.Fullname as creator, c.id as loaihinh_id, c.ten as loaihinh FROM TBL_Standard a "
				+ " INNER JOIN TBL_USER b on b.id = a.CreatedBy "
				+ " INNER JOIN DEF_LOAIHINH_KD c on c.id = a.loaihinh_id "
				+ " where (a.IsDeleted is null or a.IsDeleted=0) "
				+ sLoaihinh_clause;
		List<Map<String, Object>> rows = (loaihinh_id == -1) ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, loaihinh_id);
		for (Map<String, Object> row : rows) {
			JSONObject joStandard = new JSONObject();
			joStandard.put("id", row.get("ID"));
			joStandard.put("ten", row.get("ten"));
			joStandard.put("abbr", row.get("abbr"));
			joStandard.put("loaihinh_id", row.get("loaihinh_id"));
			joStandard.put("loaihinh", row.get("loaihinh"));
			joStandard.put("created_time", row.get("CreatedTime"));
			joStandard.put("creator", row.get("creator"));
			jsaStandards.put(joStandard);
		}
		return jsaStandards;
	}

	public int updateStandard(int id, String ten, String abbr, int created_by) {
		return jdbcTemplate.update("update TBL_Standard set ten = ?, abbr = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?",
				ten, abbr, created_by, id);
	}

	public int deleteStandard(int id) {
		return jdbcTemplate.update("update TBL_Standard set IsDeleted = 1 where ID = ?", id);
	}

	public int createCycle(String ten, int standard_id, int createdby) {
		return jdbcTemplate.update("insert into TBL_Cycle (ten, StandardID, CreatedTime, CreatedBy, IsDeleted) values(?, ?, GETDATE(), ?, 0)",
				ten, standard_id, createdby);
	}

	public JSONArray listCycleByStandard(int standard_id) {
		JSONArray jsaCycles = new JSONArray();
		String sStandard_clause = standard_id < 0 ? "" : " and StandardID = ?";
		String sql = "SELECT a.*, b.Fullname as creator, c.ten as standard, c.ID as standard_id, d.ten as loaihinh, d.id as loaihinh_id "
				+ " FROM TBL_Cycle a "
				+ " INNER JOIN TBL_USER b on b.id = a.CreatedBy "
				+ " INNER JOIN TBL_STANDARD c on c.id = a.StandardID "
				+ " INNER JOIN DEF_LOAIHINH_KD d on d.id = c.loaihinh_id "
				+ " where (a.IsDeleted is null or a.IsDeleted=0)"
				+ sStandard_clause;
		List<Map<String, Object>> rows = (standard_id < 0) ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, standard_id);
		for (Map<String, Object> row : rows) {
			JSONObject joCycle = new JSONObject();
			joCycle.put("id", row.get("ID"));
			joCycle.put("ten", row.get("ten"));
			joCycle.put("abbr", row.get("abbr"));
			joCycle.put("created_time", row.get("CreatedTime"));
			joCycle.put("creator", row.get("creator"));
			joCycle.put("standard_id", row.get("StandardID"));
			joCycle.put("standard", row.get("standard"));
			joCycle.put("loaihinh", row.get("loaihinh"));
			joCycle.put("loaihinh_id", row.get("loaihinh_id"));
			jsaCycles.put(joCycle);
		}
		return jsaCycles;
	}

	public JSONArray listCycleByLoaihinh(int loaihinh_id) {
		JSONArray jsaCycles = new JSONArray();
		String sLoaihinh_clause = loaihinh_id < 0 ? "" : " and c.loaihinh_id = ?";
		String sql = "SELECT a.*, b.Fullname as creator, c.ten as standard, c.ID as standard_id, d.ten as loaihinh, d.id as loaihinh_id "
				+ " FROM TBL_Cycle a "
				+ " INNER JOIN TBL_USER b on b.id = a.CreatedBy "
				+ " INNER JOIN TBL_STANDARD c on c.id = a.StandardID "
				+ " INNER JOIN DEF_LOAIHINH_KD d on d.id = c.loaihinh_id "
				+ " where (a.IsDeleted is null or a.IsDeleted=0)"
				+ sLoaihinh_clause;
		List<Map<String, Object>> rows = (loaihinh_id < 0) ? jdbcTemplate.queryForList(sql) : jdbcTemplate.queryForList(sql, loaihinh_id);
		for (Map<String, Object> row : rows) {
			JSONObject joCycle = new JSONObject();
			joCycle.put("id", row.get("ID"));
			joCycle.put("ten", row.get("ten"));
			joCycle.put("abbr", row.get("abbr"));
			joCycle.put("created_time", row.get("CreatedTime"));
			joCycle.put("creator", row.get("creator"));
			joCycle.put("standard_id", row.get("StandardID"));
			joCycle.put("standard", row.get("standard"));
			joCycle.put("loaihinh", row.get("loaihinh"));
			joCycle.put("loaihinh_id", row.get("loaihinh_id"));
			jsaCycles.put(joCycle);
		}
		return jsaCycles;
	}

	public int updateCycle(int id, String ten, int created_by) {
		return jdbcTemplate.update("update TBL_Cycle set ten = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?",
				ten, created_by, id);
	}

	public int deleteCycle(int id) {
		return jdbcTemplate.update("update TBL_Cycle set IsDeleted = 1 where ID = ?", id);
	}
}
