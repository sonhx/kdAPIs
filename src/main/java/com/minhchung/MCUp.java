package com.minhchung;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MCUp {

	@Autowired
	private JdbcTemplate jdbcTemplate;

    @Autowired
    private MCExtend mcExtend;

	public boolean isMatch(String regex, String input) {
		Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(input);
		return matcher.find();
	}

	public boolean isFrame(JSONArray jsD, String name) {
		try {
			for (int j = 0; j < jsD.length(); j++) {
				JSONObject d = jsD.getJSONObject(j);
				String level_name = d.getString("name");
				if (name.toUpperCase().startsWith(level_name.toUpperCase())) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public JSONObject frameLevel(JSONArray jsD, String frame) {
		JSONObject joFr = new JSONObject();
		try {
			for (int j = 0; j < jsD.length(); j++) {
				JSONObject d = jsD.getJSONObject(j);
				int level = d.getInt("level");
				String level_name = d.getString("name");
				if (frame.toUpperCase().startsWith(level_name.toUpperCase())) {
					JSONObject joDoc = mcExtend.parseFrameTitles(frame, level_name);
					String chiso = joDoc.getString("chiso");

					joFr.put("ten", frame);
					joFr.put("chiso", chiso);
					joFr.put("level_name", level_name);
					joFr.put("level", level);
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return joFr;
	}

	public String f_f_index(String chiso) {
		String f_f_index = "";
		DecimalFormat df = new DecimalFormat("00");
		if (chiso.contains(".")) {
			String[] parts = chiso.split("\\.");
			for (int p = 0; p < parts.length; p++) {
				f_f_index += df.format(Integer.parseInt(parts[p])) + ".";
			}
			f_f_index = f_f_index.substring(0, f_f_index.length() - 1);
		} else {
			f_f_index = df.format(Integer.parseInt(chiso));
		}
		return f_f_index;
	}

	public int uploadMCFrame(JSONArray jsDef, JSONArray jsaData, int kd_id, String doituong_kd, int status, int user_id) {
		String ma_mc = "", ma_mc_chung = "", ten_mc = "", noi_ban_hanh = "";
		int last_frame_id = -1;

		JSONObject joLastMC = new JSONObject();
		try {
			JSONArray jsD = new JSONArray();
			for (int j = 0; j < jsDef.length(); j++) {
				JSONObject d = jsDef.getJSONObject(j);
				Iterator<String> keys = d.keys();
				while (keys.hasNext()) {
					JSONObject jo = new JSONObject();
					String key = keys.next();
					String name = d.getString(key);
					jo.put("level", Integer.parseInt(key));
					jo.put("name", name);

					if (key.equals("1")) jo.put("parent_id", 0);
					jsD.put(jo);
				}
			}

			int iParentID = 0;
			String proof_regex = "^(H|)\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}.*$";
			for (int i = 0; i < jsaData.length(); i++) {
				JSONObject joRow = jsaData.getJSONObject(i);
				ma_mc = joRow.has("ma_mc") ? joRow.getString("ma_mc") : null;
				ma_mc_chung = joRow.has("ma_mc_chung") ? joRow.getString("ma_mc_chung") : null;
				ten_mc = joRow.has("ten_mc") ? joRow.getString("ten_mc") : null;
				noi_ban_hanh = joRow.has("noi_ban_hanh") ? joRow.getString("noi_ban_hanh") : null;

				if (ma_mc == null) {
					ma_mc = joLastMC.getString("ma_mc");
				} else {
					ma_mc = ma_mc.trim();
					if (ma_mc.length() == 0) {
						ma_mc = joLastMC.getString("ma_mc");
					} else {
						joLastMC.put("ma_mc", ma_mc);
						joLastMC.put("ma_mc_chung", ma_mc_chung);
						joLastMC.put("ten_mc", ten_mc);
						joLastMC.put("noi_ban_hanh", noi_ban_hanh);
					}
				}

				if (isFrame(jsD, ma_mc)) {
					JSONObject joFrame = frameLevel(jsD, ma_mc);
					String chiso = joFrame.getString("chiso");
					String level_name = joFrame.getString("level_name");
					int level = joFrame.getInt("level");
					String f_f_index = f_f_index(chiso);

					if (level == 1) {
						iParentID = 0;
					} else {
						for (int k = 0; k < jsD.length(); k++) {
							JSONObject jTemp = jsD.getJSONObject(k);
							if (jTemp.getInt("level") == level - 1) {
								iParentID = jTemp.getInt("ID");
								break;
							}
						}
					}

					last_frame_id = registerFrame(level_name, chiso, f_f_index, ma_mc, iParentID, kd_id, doituong_kd, status, user_id);

					for (int n = 0; n < jsD.length(); n++) {
						JSONObject d = jsD.getJSONObject(n);
						if (d.getInt("level") == level) {
							d.put("ID", last_frame_id);
							jsD.remove(n);
							jsD.put(d);
							break;
						}
					}
				} else if (isMatch(proof_regex, ma_mc)) {
					if (ten_mc == null) {
						ten_mc = joLastMC.getString("ten_mc");
					} else {
						ten_mc = ten_mc.trim();
						if (ten_mc.length() == 0) {
							ten_mc = joLastMC.getString("ten_mc");
						}
					}

					if (noi_ban_hanh == null) {
						noi_ban_hanh = joLastMC.getString("noi_ban_hanh");
					} else {
						noi_ban_hanh = noi_ban_hanh.trim();
						if (noi_ban_hanh.length() == 0) {
							noi_ban_hanh = joLastMC.getString("noi_ban_hanh");
						}
					}
					registerMinhchungInfo(ma_mc, ten_mc, last_frame_id, user_id, kd_id, doituong_kd, status);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 1;
	}

	public int registerFrame(String level_name, String chiso, String f_f_index, String ma_mc, int iParentID, int kd_id, String doituong_kd, int status, int user_id) {
		try {
			String sql = "insert into TBL_FRAME(TYPE, F_INDEX, F_F_INDEX, NAME, ParentID, kd_id, doituong_kd, status, CreatedBy, CreatedTime, UpdatedBy, UpdatedTime, IsDeleted) "
					+ " OUTPUT inserted.ID values(?, ?, ?, ?, ?, ?, ?, ?, ?, getDate(), ?, getDate(), 0)";
			Integer ID = jdbcTemplate.queryForObject(sql, Integer.class, level_name, chiso, f_f_index, ma_mc, iParentID, kd_id, doituong_kd, status, user_id, user_id);
			return (ID != null) ? ID : 0;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public int registerMinhchungInfo(String ma_mc, String ten_mc, int frame_id, int UserID, int kd_id, String doituong_kd, int status) {
		try {
			String sql = "insert into TBL_Minhchung (ma_mc, ten_mc, status, Createdtime, CreatedBy, kd_id, doituong_kd, f_id) "
					+ " OUTPUT INSERTED.ID values (?, ?, ?, GETDATE(), ?, ?, ?, ?)";
			Integer mc_id = jdbcTemplate.queryForObject(sql, Integer.class, ma_mc, ten_mc, status, UserID, kd_id, doituong_kd, frame_id);
			return (mc_id != null) ? mc_id : 0;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
}