package com.minhchung;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;
import com.file.UploadBase64;

@Service
public class MCExtend {
	public final String host = Config.host;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public int UploadFrame(JSONArray jsFrame) {
		// Logic was incomplete in original code, but here's a skeletal refactor
		try {
			int iParentID = 0;
			int iLastLVID = 0;
			int iLastTCID = 0;
			for (int i = 0; i < jsFrame.length(); i++) {
				JSONObject jo = jsFrame.getJSONObject(i);
				String phanloai = jo.getString("phanloai").trim();
				String chiso = jo.getString("chiso").trim();
				String ten = jo.getString("ten").trim();
				
				// Original code had empty SQL here. If needed, implement properly.
			}
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
		return 1;
	}

	public JSONArray loop_frame(int root_org_id, JSONArray result_arr, 
			int kd_id, String doituong_kd, int status, JSONArray mc_list) {
		String sql = "select * from TBL_FRAME where ParentID=? and kd_id = ? and doituong_kd = ? and status = ? and (IsDeleted is null or IsDeleted=0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_org_id, kd_id, doituong_kd, status);
		
		for (Map<String, Object> row : rows) {
			JSONObject c = new JSONObject();
			int frame_id = (int) row.get("ID");
			c.put("id", frame_id);
			c.put("type", row.get("Type"));
			c.put("f_index", row.get("F_INDEX"));
			c.put("label", row.get("NAME"));
			
			String sqlMC = "select * from TBL_MINHCHUNG WHERE F_ID = ?";
			List<Map<String, Object>> mcRows = jdbcTemplate.queryForList(sqlMC, frame_id);
			for (Map<String, Object> mcRow : mcRows) {
				JSONObject joMC = new JSONObject();
				joMC.put("ID", mcRow.get("ID"));
				joMC.put("ma_mc", mcRow.get("ma_mc"));
				joMC.put("ten_mc", mcRow.get("ten_mc"));
				joMC.put("path", mcRow.get("path"));
				joMC.put("status", mcRow.get("status"));
				joMC.put("is_locked", mcRow.get("is_locked"));
				mc_list.put(joMC);
			}
			
			JSONArray sarr = new JSONArray();
			loop_frame(frame_id, sarr, kd_id, doituong_kd, status, mc_list);
			c.put("children", sarr);
			result_arr.put(c);
		}
		return result_arr;
	}

	public JSONArray listFilesbyMc(String kd_type, int kd_id, String ma_mc) {
		JSONArray jsFiles = new JSONArray();
		String path = Config.homeDir + "/" + kd_id + "/" + kd_type + "/mc/" + ma_mc.replaceAll("\\.", "/");
		File dir = new File(path);
		if (!dir.isDirectory()) return new JSONArray();
		
		File[] files = dir.listFiles();
		if (files != null) {
			for (File f : files) {
				String fName = f.getName();
				if (!fName.startsWith(ma_mc)) continue;
				JSONObject joFile = new JSONObject();
				joFile.put("name", fName);
				joFile.put("path", path + "/" + fName);
				jsFiles.put(joFile);
			}
		}
		return jsFiles;
	}

	public JSONArray listMCbyFrame(int frame_id) {
		JSONArray mc_list = new JSONArray();
		String sql = "select a.*, b.Fullname as creator, c.Fullname as emp from TBL_MINHCHUNG a "
				+ " inner join TBL_USER b on b.id = a.CreatedBy "
				+ " left join TBL_USER c on c.ID = a.emp_id "
				+ " where a.f_id = ?" 
				+ " and (a.IsDeleted is null or a.IsDeleted =0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, frame_id);
		for (Map<String, Object> row : rows) {
			JSONObject joMc = new JSONObject();
			joMc.put("id", row.get("ID"));
			joMc.put("ma_mc", row.get("ma_mc"));
			joMc.put("ten_mc", row.get("ten_mc"));
			joMc.put("ten_file", row.get("ten_file"));
			joMc.put("path", row.get("path"));
			joMc.put("is_locked", row.get("is_locked"));
			joMc.put("created_time", row.get("CreatedTime"));
			joMc.put("creator", row.get("creator"));
			joMc.put("emp_id", row.get("emp_id"));
			mc_list.put(joMc);
		}
		return mc_list;
	}

	public int get_mc_list(int root_id, JSONArray result_arr) {
		String sql = "select * from TBL_FRAME where ParentID=? and (IsDeleted is null or IsDeleted = 0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_id);
		for (Map<String, Object> row : rows) {
			int frame_id = (int) row.get("ID");
			JSONArray js = listMCbyFrame(frame_id);
			if (js != null) {
				for (int i = 0; i < js.length(); i++) {
					result_arr.put(js.getJSONObject(i));
				}
			}
			get_mc_list(frame_id, result_arr);
		}
		return 1;
	}

	public JSONArray fn_loop_org_all(int root_org_id, JSONArray result_arr, int kd_id, String doituong_kd, int status) {
		String sql = "select * from TBL_FRAME where ParentID=? and kd_id = ? and doituong_kd = ? and status = ? and (IsDeleted is null or IsDeleted=0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_org_id, kd_id, doituong_kd, status);
		for (Map<String, Object> row : rows) {
			JSONObject c = new JSONObject();
			int frame_id = (int) row.get("ID");
			c.put("id", frame_id);
			c.put("type", row.get("Type"));
			c.put("f_index", row.get("F_INDEX"));
			c.put("label", row.get("NAME"));
			JSONArray sarr = new JSONArray();
			fn_loop_org_all(frame_id, sarr, kd_id, doituong_kd, status);
			c.put("children", sarr);
			result_arr.put(c);
		}
		return result_arr;
	}

	public boolean isMaMCUnique(String ma_mc, int kd_id, String doituong_kd, int status) {
		String sql = "select count(*) from TBL_MINHCHUNG where ma_mc = ? and kd_id = ? and doituong_kd = ? and status = ? and (IsDeleted is null or IsDeleted=0)";
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ma_mc, kd_id, doituong_kd, status);
		return count == null || count == 0;
	}

	public boolean isProofExisted(String ma_mc, String ten_mc) {
		String sql = "select count(*) from TBL_Minhchung where ma_mc = ? and ten_mc like ?";
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, ma_mc, ma_mc + "- %" + ten_mc);
		return count != null && count > 0;
	}

	public int addProof2List(int org_id, String ma_mc, String ten_mc) {
		String[] parts = ma_mc.split("[.]");
		String tieu_chuan = parts[0];
		String tieu_chi = parts[1] + "." + parts[2];
		String full_ten_mc = ma_mc + "- " + ten_mc;
		String sql = "insert into TBL_Minhchung (tieu_chuan, tieu_chi, ma_mc, ten_mc, org_id, status, IsDeleted) "
				+ " values(?, ?, ?, ?, ?, 0, 0)";
		return jdbcTemplate.update(sql, tieu_chuan, tieu_chi, ma_mc, full_ten_mc, org_id);
	}

	public int updateMCTable(int mc_id, String ten_mc, String ten_file, String path, int emp_id, int created_by) {
		StringBuilder sql = new StringBuilder("update TBL_Minhchung set ten_mc = ?, CreatedTime = GETDATE(), CreatedBy = ?");
		List<Object> params = new ArrayList<>();
		params.add(ten_mc);
		params.add(created_by);
		if (ten_file != null) { sql.append(", ten_file = ?"); params.add(ten_file); }
		if (path != null) { sql.append(", path = ?"); params.add(path); }
		if (emp_id != -1) { sql.append(", emp_id = ?"); params.add(emp_id); }
		sql.append(" where ID = ?");
		params.add(mc_id);
		return jdbcTemplate.update(sql.toString(), params.toArray());
	}

	public int updateProofwUpload(int mc_id, JSONObject joInputFile, int emp_id, int created_by) {
		try {
			String fname = joInputFile.getString("filename");
			String ext = fname.substring(fname.lastIndexOf("."));
			JSONObject joFile = mcDetailbyID(mc_id);
			String ten_mc = joFile.getString("ten_mc");
			String ten_file = sanitizeFilename(ten_mc) + ext;
			int kd_id = joFile.getInt("kd_id");
			String doituong_kd = joFile.getString("doituong_kd");
			String ma_mc = joFile.getString("ma_mc");
			
			String path = UploadBase64.fPath(kd_id, doituong_kd, "mc", null);
			String fdir = path.replaceFirst(Config.homePath, Config.homeDir);
			String ma_mc_path = ma_mc.replaceAll("\\.", "/");
			String sFileDir = fdir + "/" + ma_mc_path;
			File dir = new File(sFileDir);
			if (!dir.exists()) dir.mkdirs();
			
			String sFile = sFileDir + "/" + ten_file;
			UploadBase64.b64Decode(joInputFile.getString("base64"), sFile);
			updateMCTable(mc_id, ten_mc, ten_file, path, emp_id, created_by);
			return 1;
		} catch (Exception e) {
			e.printStackTrace();
			return -2;
		}
	}

	public String sanitizeFilename(String name) {
		return name.replaceAll("[:\\\\/*?|<>]", "_");
	}

	public JSONObject mcDetailbyID(int mc_id) {
		String sql = "select * from TBL_Minhchung where ID = ?";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, mc_id);
		JSONObject jo = new JSONObject();
		if (!rows.isEmpty()) {
			Map<String, Object> row = rows.get(0);
			jo.put("id", mc_id);
			jo.put("ma_mc", row.get("ma_mc"));
			jo.put("ten_mc", row.get("ten_mc"));
			jo.put("path", row.get("path"));
			jo.put("kd_id", row.get("kd_id"));
			jo.put("doituong_kd", row.get("doituong_kd"));
		}
		return jo;
	}

	public boolean isFrameIndexExisted(String type, String f_index) {
		String sql = "select count(*) from TBL_FRAME where TYPE = ? and F_INDEX = ?";
		Integer count = jdbcTemplate.queryForObject(sql, Integer.class, type, f_index);
		return count != null && count > 0;
	}

	public JSONObject frameInfo(int id) {
		String sql = "select * from TBL_FRAME where ID = ?";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
		JSONObject jo = new JSONObject();
		if (!rows.isEmpty()) {
			Map<String, Object> row = rows.get(0);
			jo.put("ID", id);
			jo.put("F_INDEX", row.get("F_INDEX"));
			jo.put("F_F_INDEX", row.get("F_F_INDEX"));
		}
		return jo;
	}

	public JSONObject parentInfo(int id) {
		String sql = "select * from TBL_FRAME where ID in (select ParentID from TBL_FRAME where ID = ?)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
		JSONObject jo = new JSONObject();
		if (!rows.isEmpty()) {
			Map<String, Object> row = rows.get(0);
			jo.put("ID", row.get("ID"));
			jo.put("F_INDEX", row.get("F_INDEX"));
			jo.put("F_F_INDEX", row.get("F_F_INDEX"));
		}
		return jo;
	}

	public String padLeftZeros(String inputString, int length) {
		if (inputString.length() >= length) return inputString;
		StringBuilder sb = new StringBuilder();
		while (sb.length() < length - inputString.length()) sb.append('0');
		sb.append(inputString);
		return sb.toString();
	}

	public int parentID(int id) {
		String sql = "select ParentID from TBL_FRAME where ID = ?";
		List<Integer> list = jdbcTemplate.queryForList(sql, Integer.class, id);
		return list.isEmpty() ? -1 : list.get(0);
	}

	public int addSiblingFrame(String type, int ref_id, String f_index, String name, int created_by, int status, String doituong_kd, int kd_id) {
		int parentID = parentID(ref_id);
		String f_f_index = "";
		if (parentID == 0) {
			f_f_index = padLeftZeros(f_index, 2);
		} else {
			JSONObject joParent = parentInfo(ref_id);
			String parent_f_f_index = joParent.optString("F_F_INDEX", "");
			f_f_index = (parent_f_f_index.isEmpty() ? "" : parent_f_f_index + ".") + padLeftZeros(f_index, 2);
		}
		String sql = "INSERT INTO TBL_FRAME ( TYPE, PARENTID, F_INDEX, F_F_INDEX, NAME, CREATEDBY, CREATEDTIME, UPDATEDBY, UPDATEDTIME, IsDeleted, status, doituong_kd, kd_id) "
				+ " SELECT ?, PARENTID, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), 0, ?, ?, ? FROM TBL_FRAME WHERE ID = ?";
		return jdbcTemplate.update(sql, type, f_index, f_f_index, name, created_by, created_by, status, doituong_kd, kd_id, ref_id);
	}

	public int addChildFrame(String type, int ref_id, String f_index, String name, int created_by, int status, String doituong_kd, int kd_id) {
		JSONObject joParent = frameInfo(ref_id);
		String parent_f_f_index = joParent.optString("F_F_INDEX", "");
		String f_f_index = (parent_f_f_index.isEmpty() ? "" : parent_f_f_index + ".") + padLeftZeros(f_index, 2);
		String sql = "INSERT INTO TBL_FRAME ( TYPE, PARENTID, F_INDEX, F_F_INDEX, NAME, CREATEDBY, CREATEDTIME, UPDATEDBY, UPDATEDTIME, IsDeleted, status, doituong_kd, kd_id) "
				+ " values (?, ?, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), 0, ?, ?, ?)";
		return jdbcTemplate.update(sql, type, ref_id, f_index, f_f_index, name, created_by, created_by, status, doituong_kd, kd_id);
	}

	public int deleteFrame(int frame_id) {
		return jdbcTemplate.update("update TBL_FRAME set IsDeleted = 1 where ID = ?", frame_id);
	}

	public int updateFrame(int frame_id, String label) {
		return jdbcTemplate.update("update TBL_FRAME set Name = ? where ID = ?", label, frame_id);
	}

	public JSONArray fn_loop_org_all_with_assigment(int root_org_id, JSONArray result_arr, int tc_id, int kd_id, String doituong_kd) {
		String sql = "select * from TBL_FRAME where ParentID=? and kd_id = ? and doituong_kd = ? and (IsDeleted is null or IsDeleted=0)";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_org_id, kd_id, doituong_kd);
		for (Map<String, Object> row : rows) {
			JSONObject c = new JSONObject();
			int frame_id = (int) row.get("ID");
			String sType = (String) row.get("Type");
			String f_index = (String) row.get("F_INDEX");
			String label = (sType.equals("lv") ? "Lĩnh vực" : sType.equals("tc") ? "Tiêu chuẩn" : "Tiêu chí") + " " + f_index + ". " + row.get("NAME");
			JSONArray jsaProofs = frameProofsWithAssigment(f_index, sType, tc_id, kd_id, doituong_kd);
			if (jsaProofs.length() == 0) continue;
			
			c.put("id", frame_id);
			c.put("proofs", jsaProofs);
			c.put("number_of_proofs", jsaProofs.length());
			int done = 0;
			for (int p = 0; p < jsaProofs.length(); p++) {
				if (jsaProofs.getJSONObject(p).optString("path", "").length() > 0) done++;
			}
			c.put("number_of_done", done);
			c.put("name", label);
			c.put("label", "(" + jsaProofs.length() + "/" + done + ") " + label);
			JSONArray sarr = new JSONArray();
			fn_loop_org_all_with_assigment(frame_id, sarr, tc_id, kd_id, doituong_kd);
			c.put("children", sarr);
			result_arr.put(c);
		}
		return result_arr;
	}

	public JSONArray frameProofsWithAssigment(String f_index, String type, int org_id, int kd_id, String doituong_kd) {
		if (type.equals("tieuchi")) return level3ProofsWithAssigment(f_index, org_id, kd_id, doituong_kd);
		if (type.equals("tc")) return level2ProofsWithAssigment(f_index, org_id, kd_id, doituong_kd);
		if (type.equals("lv")) return level1ProofsWithAssigment(f_index, org_id, kd_id, doituong_kd);
		return new JSONArray();
	}

	public JSONArray level1ProofsWithAssigment(String f_index, int org_id, int kd_id, String doituong_kd) {
		String sql = "select a.*, b.Name as org_name, b.Code as org_code from TBL_Minhchung a left join TBL_ORG b on b.ID = a.org_id "
				+ " where a.tieu_chuan in (select concat('H',F_INDEX) from TBL_frame where ParentID in (select ID from TBL_FRAME where F_INDEX= ? and type = 'LV' and kd_id = ? and doituong_kd = ?)) "
				+ " and a.kd_id = ? and a.doituong_kd = ? and (a.IsDeleted is null or a.IsDeleted =0) and a.org_id = ?";
		return rowsToJSONArray(jdbcTemplate.queryForList(sql, f_index, kd_id, doituong_kd, kd_id, doituong_kd, org_id));
	}

	public JSONArray level2ProofsWithAssigment(String f_index, int org_id, int kd_id, String doituong_kd) {
		String sql = "select a.*, b.Name as org_name, b.Code as org_code from TBL_Minhchung a left join TBL_ORG b on b.ID = a.org_id "
				+ " where a.tieu_chuan = ? and a.kd_id = ? and a.doituong_kd = ? and (a.IsDeleted is null or a.IsDeleted =0) and a.org_id = ?";
		return rowsToJSONArray(jdbcTemplate.queryForList(sql, "H" + f_index, kd_id, doituong_kd, org_id));
	}

	public JSONArray level3ProofsWithAssigment(String f_index, int org_id, int kd_id, String doituong_kd) {
		String[] idx = f_index.split("\\.");
		String tieu_chi = padLeftZeros(idx[0], 2) + "." + padLeftZeros(idx[1], 2);
		String sql = "select a.*, b.Name as org_name, b.Code as org_code from TBL_Minhchung a left join TBL_ORG b on b.ID = a.org_id "
				+ " where a.tieu_chi = ? and a.kd_id = ? and a.doituong_kd = ? and (a.IsDeleted is null or a.IsDeleted =0) and a.org_id = ?";
		return rowsToJSONArray(jdbcTemplate.queryForList(sql, tieu_chi, kd_id, doituong_kd, org_id));
	}

	public JSONArray level1Proofs(String f_index, int kd_id, String doituong_kd, int status) {
		String sql = "select a.*, b.Name as org_name, b.Code as org_code from TBL_Minhchung a left join TBL_ORG b on b.ID = a.org_id "
				+ " where a.tieu_chuan in (select concat('H',F_INDEX) from TBL_frame where ParentID in (select ID from TBL_FRAME where F_INDEX= ? and type = 'LV' and kd_id = ? and doituong_kd = ?)) "
				+ " and a.kd_id = ? and a.doituong_kd = ? and a.status = ? and (a.IsDeleted is null or a.IsDeleted =0)";
		return rowsToJSONArray(jdbcTemplate.queryForList(sql, f_index, kd_id, doituong_kd, kd_id, doituong_kd, status));
	}

	public JSONArray level2Proofs(String f_index, int kd_id, String doituong_kd, int status) {
		String sql = "select a.*, b.Name as org_name, b.Code as org_code from TBL_Minhchung a left join TBL_ORG b on b.ID = a.org_id "
				+ " where a.tieu_chuan = ? and a.kd_id = ? and a.doituong_kd = ? and a.status = ? and (a.IsDeleted is null or a.IsDeleted =0)";
		return rowsToJSONArray(jdbcTemplate.queryForList(sql, "H" + f_index, kd_id, doituong_kd, status));
	}

	public JSONArray level3Proofs(String f_index, int kd_id, String doituong_kd, int status) {
		String[] idx = f_index.split("\\.");
		String tieu_chi = padLeftZeros(idx[0], 2) + "." + padLeftZeros(idx[1], 2);
		String sql = "select a.*, b.Name as org_name, b.Code as org_code from TBL_Minhchung a left join TBL_ORG b on b.ID = a.org_id "
				+ " where a.tieu_chi = ? and a.kd_id = ? and a.doituong_kd = ? and a.status = ? and (a.IsDeleted is null or a.IsDeleted =0)";
		return rowsToJSONArray(jdbcTemplate.queryForList(sql, tieu_chi, kd_id, doituong_kd, status));
	}

	private JSONArray rowsToJSONArray(List<Map<String, Object>> rows) {
		JSONArray ja = new JSONArray();
		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("id", row.get("ID"));
			jo.put("tieu_chuan", row.get("tieu_chuan"));
			jo.put("tieu_chi", row.get("tieu_chi"));
			jo.put("ma_mc", row.get("ma_mc"));
			jo.put("ten_mc", row.get("ten_mc"));
			jo.put("path", row.get("path") == null ? "" : host + row.get("path"));
			jo.put("org_id", row.get("org_id"));
			jo.put("org_name", row.get("org_name"));
			jo.put("org_code", row.get("org_code"));
			jo.put("is_locked", row.get("is_locked"));
			jo.put("created_time", row.get("CreatedTime"));
			ja.put(jo);
		}
		return ja;
	}

	public JSONArray frameProofs(int id, int kd_id, String doituong_kd, int status) {
		JSONArray jsa = listMCbyFrame(id);
		get_mc_list(id, jsa);
		return jsa;
	}

	public int changeLockStateFrameProofs(int id, int is_locked) {
		jdbcTemplate.update("Update TBL_MINHCHUNG set is_locked = ? where f_id = ?", is_locked, id);
		update_state_frame_tree(id, is_locked);
		return 1;
	}

	public int update_state_frame_tree(int root_id, int state) {
		List<Integer> ids = jdbcTemplate.queryForList("select ID from TBL_FRAME where ParentID=?", Integer.class, root_id);
		for (int frame_id : ids) {
			jdbcTemplate.update("Update TBL_MINHCHUNG set is_locked = ? where f_id = ?", state, frame_id);
			update_state_frame_tree(frame_id, state);
		}
		return 1;
	}

	public int deleteProof(int proof_id) {
		return jdbcTemplate.update("update TBL_Minhchung set IsDeleted = 1 where ID = ?", proof_id);
	}

	public int updateState(int id, int is_locked) {
		return jdbcTemplate.update("update TBL_Minhchung set is_locked = ? where ID = ?", is_locked, id);
	}

	public void convert(int kd_id, String doituong_kd) {
		String sql = "select * from tbl_minhchung where kd_id = ? and doituong_kd = ?";
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id, doituong_kd);
		for (Map<String, Object> row : rows) {
			String tieu_chi = (String) row.get("tieu_chi");
			String[] parts = tieu_chi.split("\\.");
			String f_index = Integer.parseInt(parts[0]) + "." + Integer.parseInt(parts[1]);
			jdbcTemplate.update("update tbl_minhchung set f_id = (select id from tbl_frame where F_INDEX = ?) where ID = ?", f_index, row.get("ID"));
		}
	}

	public void convert1() {
		DecimalFormat df = new DecimalFormat("00");
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from tbl_frame where type = 'tieuchi'");
		for (Map<String, Object> row : rows) {
			String F_INDEX = (String) row.get("F_INDEX");
			String[] parts = F_INDEX.split("\\.");
			String f_f_index = df.format(Integer.parseInt(parts[0])) + "." + df.format(Integer.parseInt(parts[1]));
			jdbcTemplate.update("update tbl_frame set f_f_index = ? where ID = ?", f_f_index, row.get("ID"));
		}
	}

	public JSONArray kdProofs(int kd_id, String doituong_kd, int status) {
		StringBuilder sql = new StringBuilder("select * from TBL_MINHCHUNG where kd_id = ? and (IsDeleted is null or IsDeleted =0)");
		List<Object> params = new ArrayList<>();
		params.add(kd_id);
		if (doituong_kd != null) { sql.append(" and doituong_kd = ?"); params.add(doituong_kd); }
		if (status != -1) { sql.append(" and status = ?"); params.add(status); }
		return rowsToJSONArray(jdbcTemplate.queryForList(sql.toString(), params.toArray()));
	}

	public JSONArray kdProofs_reduced(int kd_id, String doituong_kd, int status) {
		StringBuilder sql = new StringBuilder("select ID, ma_mc, ten_mc from TBL_MINHCHUNG where kd_id = ? and (IsDeleted is null or IsDeleted =0)");
		List<Object> params = new ArrayList<>();
		params.add(kd_id);
		if (doituong_kd != null) { sql.append(" and doituong_kd = ?"); params.add(doituong_kd); }
		if (status != -1) { sql.append(" and status = ?"); params.add(status); }
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
		JSONArray ja = new JSONArray();
		for (Map<String, Object> row : rows) {
			JSONObject jo = new JSONObject();
			jo.put("id", row.get("ID"));
			jo.put("ma_mc", row.get("ma_mc"));
			jo.put("ten_mc", row.get("ten_mc"));
			ja.put(jo);
		}
		return ja;
	}

	public JSONObject parseFrameTitles(String sName, String type) {
		JSONObject joDoc = new JSONObject();
		try {
			int index = sName.toUpperCase().indexOf(type.toUpperCase());
			String s_reduced = sName.substring(index + type.length()).trim();
			String chiso = s_reduced.substring(0, s_reduced.indexOf(" "));
			String sRest = s_reduced.replaceFirst(chiso, "");
			String ten = sRest.replaceFirst("^(\\.|:)", "").trim();
			chiso = chiso.substring(0, chiso.length() - 1);
			joDoc.put("ten", ten);
			joDoc.put("chiso", chiso);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return joDoc;
	}
}
