package com.minhchung;

import java.text.DecimalFormat;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public String removeDiacritics(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("");
    }

    public boolean isMatch(String regex, String input) {
        if (input == null) return false;
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }

    public boolean isFrame(JSONArray jsD, String name) {
        try {
            if (name == null) return false;
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
        if (chiso == null) return "";
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

    public int uploadMCFrame(JSONArray jsDef, JSONArray jsaData, int kd_id, String doituong_kd, boolean create_sub, int user_id) {
        String ma_mc = "", ma_mc_chung = "", ten_mc = "", noi_ban_hanh = "", so_ngay_thang = "";
        int last_frame_id = -1;

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
            for (int i = 0; i < jsaData.length(); i++) {
                JSONObject joRow = jsaData.getJSONObject(i);

                ma_mc = joRow.has("ma_mc") ? joRow.getString("ma_mc").trim() : null;
                ma_mc_chung = joRow.has("ma_mc_chung") ? joRow.getString("ma_mc_chung").trim() : null;
                ten_mc = joRow.has("ten_mc") ? joRow.getString("ten_mc").trim() : null;
                so_ngay_thang = joRow.has("so_ngay_thang") ? joRow.getString("so_ngay_thang").trim() : null;
                noi_ban_hanh = joRow.has("noi_ban_hanh") ? joRow.getString("noi_ban_hanh").trim() : null;

                if (ma_mc == null) {
                    registerMinhchungInfo(ma_mc, ma_mc_chung, ten_mc, noi_ban_hanh, so_ngay_thang, last_frame_id, user_id, kd_id, doituong_kd);
                } else if (isFrame(jsD, ma_mc)) {
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

                    last_frame_id = registerFrame(level_name, chiso, f_f_index, ma_mc, iParentID, kd_id, doituong_kd, create_sub, user_id);

                    for (int n = 0; n < jsD.length(); n++) {
                        JSONObject d = jsD.getJSONObject(n);
                        if (d.getInt("level") == level) {
                            d.put("ID", last_frame_id);
                            jsD.remove(n);
                            jsD.put(d);
                            break;
                        }
                    }
                } else {
                    registerMinhchungInfo(ma_mc, ma_mc_chung, ten_mc, noi_ban_hanh, so_ngay_thang, last_frame_id, user_id, kd_id, doituong_kd);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 1;
    }

    public int registerFrame(String level_name, String chiso, String f_f_index, String ma_mc, int iParentID, int kd_id, String doituong_kd, boolean create_sub, int user_id) {
        try {
            String sql = "insert into TBL_FRAME(TYPE, F_INDEX, F_F_INDEX, NAME, ParentID, kd_id, doituong_kd, CreatedBy, CreatedTime, UpdatedBy, UpdatedTime, IsDeleted) "
                    + " OUTPUT inserted.ID values(?, ?, ?, ?, ?, ?, ?, ?, getDate(), ?, getDate(), 0)";
            Integer ID = jdbcTemplate.queryForObject(sql, Integer.class, level_name, chiso, f_f_index, ma_mc, iParentID, kd_id, doituong_kd, user_id, user_id);
            if (ID != null && create_sub) {
                if (normalize(level_name).equalsIgnoreCase(normalize("Tiêu chí"))) {
                    subFoldersCreation(ID);
                }
            }
            return (ID != null) ? ID : -1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFC).trim();
    }

    public int subFoldersCreation(int iParentID) {
        try {
            String sql = "insert into TBL_FRAME(TYPE, NAME, ParentID, kd_id, doituong_kd, status, CreatedBy, CreatedTime, UpdatedBy, UpdatedTime, IsDeleted) "
                    + " SELECT 'sub', Names.NAME, ?, kd_id, doituong_kd, status, CreatedBy, CreatedTime, UpdatedBy, UpdatedTime, IsDeleted "
                    + " from TBL_FRAME "
                    + " CROSS JOIN (SELECT N'Quy định' AS NAME "
                    + " UNION ALL SELECT N'Quy trình thực hiện' "
                    + " UNION ALL SELECT N'Công cụ triển khai' "
                    + " UNION ALL SELECT N'Kết quả' "
                    + " UNION ALL SELECT N'Khác') AS Names "
                    + " where ID = ?";
            return jdbcTemplate.update(sql, iParentID, iParentID);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int registerMinhchungInfo(String ma_mc, String ma_mc_chung, String ten_mc, String noi_ban_hanh, String so_ngay_thang, int frame_id, int UserID, int kd_id, String doituong_kd) {
        try {
            String sql = "insert into TBL_Minhchung (ma_mc, ma_mc_chung, ten_mc, noi_ban_hanh, so_ngay_thang, kd_id, doituong_kd, f_id) "
                    + " OUTPUT INSERTED.ID values (?, ?, ?, ?, ?, ?, ?, ?)";
            Integer mc_id = jdbcTemplate.queryForObject(sql, Integer.class, ma_mc, ma_mc_chung, ten_mc, noi_ban_hanh, so_ngay_thang, kd_id, doituong_kd, frame_id);
            return (mc_id != null) ? mc_id : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}