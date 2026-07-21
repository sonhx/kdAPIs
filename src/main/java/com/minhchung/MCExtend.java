package com.minhchung;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.config.Config;
import com.file.UploadBase64;
import com.ocr.OcrExtend;

@Service
public class MCExtend {
    public final String host = Config.host;

    @Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    public int UploadFrame(JSONArray jsFrame) {
        try {
            int iParentID = 0;
            int iLastLVID = 0;
            int iLastTCID = 0;
            for (int i = 0; i < jsFrame.length(); i++) {
                JSONObject jo = jsFrame.getJSONObject(i);
                String phanloai = jo.getString("phanloai").trim();
                String chiso = jo.getString("chiso").trim();
                String ten = jo.getString("ten").trim();

                if (phanloai.equalsIgnoreCase("lv")) {
                    iParentID = 0;
                } else if (phanloai.equalsIgnoreCase("tc")) {
                    iParentID = iLastLVID;
                } else if (phanloai.equalsIgnoreCase("tieuchi")) {
                    iParentID = iLastTCID;
                }

                String sql = "insert into TBL_FRAME(TYPE, F_INDEX, NAME, ParentID, CreatedBy, CreatedTime, UpdatedBy, UpdatedTime, IsDeleted) "
                        + " OUTPUT inserted.ID values(?, ?, ?, ?, 8, getDate(), 8, getDate(), 0)";
                Integer ID = jdbcTemplate.queryForObject(sql, Integer.class, phanloai, chiso, ten, iParentID);
                if (ID == null) ID = 0;

                if (phanloai.equalsIgnoreCase("lv")) {
                    iLastLVID = ID;
                } else if (phanloai.equalsIgnoreCase("tc")) {
                    iLastTCID = ID;
                }
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
            
            String sqlMC = "select * from TBL_MINHCHUNG WHERE F_ID = ? and (IsDeleted is null or IsDeleted = 0)";
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
        return listMCbyFrame(frame_id, null);
    }

    public JSONArray listMCbyFrame(int frame_id, String sGroups) {
        JSONArray mc_list = new JSONArray();
        StringBuilder sql = new StringBuilder("select a.*, b.Fullname as creator, c.Fullname as emp, c1.Fullname as uploader, d.group_name "
                + " from TBL_MINHCHUNG a "
                + " left join TBL_USER b on b.id = a.CreatedBy "
                + " left join TBL_USER c on c.ID = a.emp_id "
                + " left join TBL_USER c1 on c1.ID = a.UploadedBy "
                + " left join TBL_GROUP d on d.ID = a.group_id "
                + " where a.f_id = ? "
                + " and (a.IsDeleted is null or a.IsDeleted = 0)");
        List<Object> params = new ArrayList<>();
        params.add(frame_id);
        if (sGroups != null && !sGroups.trim().isEmpty() && sGroups.matches("^[0-9,\\s]+$")) {
            sql.append(" and a.group_id in (").append(sGroups).append(")");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        for (Map<String, Object> row : rows) {
            JSONObject joMc = new JSONObject();
            int id = (int) row.get("ID");
            joMc.put("id", id);
            joMc.put("ma_mc", row.get("ma_mc"));
            joMc.put("ten_mc", row.get("ten_mc"));
            joMc.put("ten_file", row.get("ten_file"));
            joMc.put("path", row.get("path"));
            joMc.put("is_locked", row.get("is_locked"));
            joMc.put("created_time", row.get("CreatedTime") != null ? row.get("CreatedTime").toString() : "");
            joMc.put("has_ocr", row.get("content") != null);
            joMc.put("creator", row.get("creator"));
            joMc.put("emp_id", row.get("emp_id") != null ? row.get("emp_id") : -1);
            joMc.put("emp", row.get("emp") != null ? row.get("emp") : "Chưa phân công");
            int org_id = row.get("org_id") != null ? (int) row.get("org_id") : 0;
            if (org_id > 0) joMc.put("org_id", org_id);
            int group_id = row.get("group_id") != null ? (int) row.get("group_id") : 0;
            if (group_id > 0) {
                joMc.put("group_id", group_id);
                joMc.put("group_name", row.get("group_name"));
            } else {
                joMc.put("group_id", -1);
                joMc.put("group_name", "Chưa phân công");
            }
            if (row.get("deadline") != null) joMc.put("deadline", row.get("deadline").toString());
            int uploaded_by = row.get("UploadedBy") != null ? (int) row.get("UploadedBy") : 0;
            if (uploaded_by > 0) {
                joMc.put("uploaded_by", uploaded_by);
                joMc.put("uploader", row.get("uploader"));
                joMc.put("uploaded_time", row.get("UploadedTime") != null ? row.get("UploadedTime").toString() : "");
            } else {
                joMc.put("uploaded_by", -1);
                joMc.put("uploader", "");
                joMc.put("uploaded_time", "");
            }
            mc_list.put(joMc);
        }
        return mc_list;
    }

    public int get_mc_list(int root_id, JSONArray result_arr) {
        return get_mc_list(root_id, null, result_arr);
    }

    public int get_mc_list(int root_id, String sGroups, JSONArray result_arr) {
        try {
            String sql = "select ID from TBL_FRAME where ParentID = ? and (IsDeleted is null or IsDeleted = 0)";
            List<Integer> ids = jdbcTemplate.queryForList(sql, Integer.class, root_id);
            for (int frame_id : ids) {
                JSONArray js = listMCbyFrame(frame_id, sGroups);
                if (js != null) {
                    for (int i = 0; i < js.length(); i++) {
                        result_arr.put(js.getJSONObject(i));
                    }
                }
                get_mc_list(frame_id, sGroups, result_arr);
            }
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int get_mc_list_optimized(int root_id, String sGroups, JSONArray result_arr) {
        StringBuilder sql = new StringBuilder();
        sql.append("WITH RecursiveFrames AS ( ")
           .append("  SELECT ID FROM TBL_FRAME WHERE ID = ? AND (IsDeleted IS NULL OR IsDeleted = 0) ")
           .append("  UNION ALL ")
           .append("  SELECT f.ID FROM TBL_FRAME f INNER JOIN RecursiveFrames rf ON f.ParentID = rf.ID ")
           .append("  WHERE (f.IsDeleted IS NULL OR f.IsDeleted = 0) ")
           .append(") ")
           .append("SELECT a.ID, a.ma_mc, a.ten_mc, a.ten_file, a.path, a.is_locked, a.CreatedTime, ")
           .append("       a.emp_id, a.org_id, a.group_id, a.deadline, a.UploadedBy, a.UploadedTime, ")
           .append("       CASE WHEN a.content IS NOT NULL THEN 1 ELSE 0 END as has_ocr, ")
           .append("       b.Fullname as creator, c.Fullname as emp, c1.Fullname as uploader, d.group_name ")
           .append("FROM TBL_MINHCHUNG a ")
           .append("INNER JOIN RecursiveFrames rf ON a.f_id = rf.ID ")
           .append("LEFT JOIN TBL_USER b ON b.id = a.CreatedBy ")
           .append("LEFT JOIN TBL_USER c ON c.ID = a.emp_id ")
           .append("LEFT JOIN TBL_USER c1 ON c1.ID = a.UploadedBy ")
           .append("LEFT JOIN TBL_GROUP d ON d.ID = a.group_id ")
           .append("WHERE (a.IsDeleted IS NULL OR a.IsDeleted = 0) ");
        if (sGroups != null && !sGroups.trim().isEmpty() && sGroups.matches("^[0-9,\\s]+$")) {
            sql.append(" AND a.group_id IN (").append(sGroups).append(")");
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), root_id);
            for (Map<String, Object> row : rows) {
                JSONObject joMc = new JSONObject();
                int id = (int) row.get("ID");
                joMc.put("id", id);
                joMc.put("ma_mc", row.get("ma_mc"));
                joMc.put("ten_mc", row.get("ten_mc"));
                joMc.put("ten_file", row.get("ten_file"));
                joMc.put("path", row.get("path"));
                joMc.put("is_locked", row.get("is_locked"));
                joMc.put("created_time", row.get("CreatedTime") != null ? row.get("CreatedTime").toString() : "");
                joMc.put("has_ocr", (int) row.get("has_ocr") == 1);
                joMc.put("creator", row.get("creator"));
                joMc.put("emp_id", row.get("emp_id") != null ? row.get("emp_id") : -1);
                joMc.put("emp", row.get("emp") != null ? row.get("emp") : "Chưa phân công");
                int org_id = row.get("org_id") != null ? (int) row.get("org_id") : 0;
                if (org_id > 0) joMc.put("org_id", org_id);
                int group_id = row.get("group_id") != null ? (int) row.get("group_id") : 0;
                if (group_id > 0) {
                    joMc.put("group_id", group_id);
                    joMc.put("group_name", row.get("group_name"));
                } else {
                    joMc.put("group_id", -1);
                    joMc.put("group_name", "Chưa phân công");
                }
                if (row.get("deadline") != null) joMc.put("deadline", row.get("deadline").toString());
                int uploaded_by = row.get("UploadedBy") != null ? (int) row.get("UploadedBy") : 0;
                if (uploaded_by > 0) {
                    joMc.put("uploaded_by", uploaded_by);
                    joMc.put("uploader", row.get("uploader"));
                    joMc.put("uploaded_time", row.get("UploadedTime") != null ? row.get("UploadedTime").toString() : "");
                } else {
                    joMc.put("uploaded_by", -1);
                    joMc.put("uploader", "");
                    joMc.put("uploaded_time", "");
                }
                result_arr.put(joMc);
            }
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public JSONObject get_mc_list_paginated(int root_id, String sGroups, Integer member_id, int page, int page_size, String search, JSONArray result_arr) {
        JSONObject stats = new JSONObject();
        stats.put("total_count", 0);
        stats.put("uploaded_count", 0);
        stats.put("pending_count", 0);

        StringBuilder sql = new StringBuilder();
        sql.append("WITH RecursiveFrames AS ( ")
           .append("  SELECT ID FROM TBL_FRAME WHERE ID = ? AND (IsDeleted IS NULL OR IsDeleted = 0) ")
           .append("  UNION ALL ")
           .append("  SELECT f.ID FROM TBL_FRAME f INNER JOIN RecursiveFrames rf ON f.ParentID = rf.ID ")
           .append("  WHERE (f.IsDeleted IS NULL OR f.IsDeleted = 0) ")
           .append(") ")
           .append("SELECT a.ID, a.ma_mc, a.ten_mc, a.ten_file, a.path, a.is_locked, a.CreatedTime, ")
           .append("       a.emp_id, a.org_id, a.group_id, a.deadline, a.UploadedBy, a.UploadedTime, ")
           .append("       CASE WHEN a.content IS NOT NULL THEN 1 ELSE 0 END as has_ocr, ")
           .append("       b.Fullname as creator, c.Fullname as emp, c1.Fullname as uploader, d.group_name, ")
           .append("       COUNT(*) OVER() as total_count, ")
           .append("       SUM(CASE WHEN a.path IS NOT NULL OR a.UploadedTime IS NOT NULL THEN 1 ELSE 0 END) OVER() as uploaded_count ")
           .append("FROM TBL_MINHCHUNG a ")
           .append("INNER JOIN RecursiveFrames rf ON a.f_id = rf.ID ")
           .append("LEFT JOIN TBL_USER b ON b.id = a.CreatedBy ")
           .append("LEFT JOIN TBL_USER c ON c.ID = a.emp_id ")
           .append("LEFT JOIN TBL_USER c1 ON c1.ID = a.UploadedBy ")
           .append("LEFT JOIN TBL_GROUP d ON d.ID = a.group_id ")
           .append("WHERE (a.IsDeleted IS NULL OR a.IsDeleted = 0) ");

        List<Object> params = new ArrayList<>();
        params.add(root_id);

        if (sGroups != null && !sGroups.trim().isEmpty() && sGroups.matches("^[0-9,\\s]+$")) {
            sql.append(" AND a.group_id IN (").append(sGroups).append(")");
        }

        if (member_id != null && member_id > 0) {
            sql.append(" AND a.emp_id = ?");
            params.add(member_id);
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (a.ma_mc LIKE ? OR a.ten_mc LIKE ?)");
            params.add("%" + search.trim() + "%");
            params.add("%" + search.trim() + "%");
        }

        sql.append(" ORDER BY a.ma_mc ASC ");

        if (page > 0 && page_size > 0) {
            sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ");
            params.add((page - 1) * page_size);
            params.add(page_size);
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            boolean firstRow = true;
            for (Map<String, Object> row : rows) {
                if (firstRow) {
                    int total = row.get("total_count") != null ? ((Number) row.get("total_count")).intValue() : 0;
                    int uploaded = row.get("uploaded_count") != null ? ((Number) row.get("uploaded_count")).intValue() : 0;
                    stats.put("total_count", total);
                    stats.put("uploaded_count", uploaded);
                    stats.put("pending_count", total - uploaded);
                    firstRow = false;
                }

                JSONObject joMc = new JSONObject();
                int id = (int) row.get("ID");
                joMc.put("id", id);
                joMc.put("ma_mc", row.get("ma_mc"));
                joMc.put("ten_mc", row.get("ten_mc"));
                joMc.put("ten_file", row.get("ten_file"));
                joMc.put("path", row.get("path"));
                joMc.put("is_locked", row.get("is_locked"));
                joMc.put("created_time", row.get("CreatedTime") != null ? row.get("CreatedTime").toString() : "");
                joMc.put("has_ocr", (int) row.get("has_ocr") == 1);
                joMc.put("creator", row.get("creator"));
                joMc.put("emp_id", row.get("emp_id") != null ? row.get("emp_id") : -1);
                joMc.put("emp", row.get("emp") != null ? row.get("emp") : "Chưa phân công");
                int org_id = row.get("org_id") != null ? (int) row.get("org_id") : 0;
                if (org_id > 0) joMc.put("org_id", org_id);
                int group_id = row.get("group_id") != null ? (int) row.get("group_id") : 0;
                if (group_id > 0) {
                    joMc.put("group_id", group_id);
                    joMc.put("group_name", row.get("group_name"));
                } else {
                    joMc.put("group_id", -1);
                    joMc.put("group_name", "Chưa phân công");
                }
                if (row.get("deadline") != null) joMc.put("deadline", row.get("deadline").toString());
                int uploaded_by = row.get("UploadedBy") != null ? (int) row.get("UploadedBy") : 0;
                if (uploaded_by > 0) {
                    joMc.put("uploaded_by", uploaded_by);
                    joMc.put("uploader", row.get("uploader"));
                    joMc.put("uploaded_time", row.get("UploadedTime") != null ? row.get("UploadedTime").toString() : "");
                } else {
                    joMc.put("uploaded_by", -1);
                    joMc.put("uploader", "");
                    joMc.put("uploaded_time", "");
                }
                result_arr.put(joMc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stats;
    }

    public String getOCRContent(int mc_id) {
        try {
            String sql = "select a.content from TBL_MINHCHUNG a where a.id = ?";
            return jdbcTemplate.queryForObject(sql, String.class, mc_id);
        } catch (Exception e) {
            return null;
        }
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

    public JSONArray fn_loop_org_all_edited(int root_org_id, JSONArray result_arr, int kd_id, String sGroups, String doituong_kd) {
        try {
            String sql = "select * from TBL_FRAME where ParentID = ? and kd_id = ? and doituong_kd = ? and (IsDeleted is null or IsDeleted = 0)";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, root_org_id, kd_id, doituong_kd);
            int skipped_id = -1;
            for (Map<String, Object> row : rows) {
                int frame_id = (int) row.get("ID");
                if (root_org_id == skipped_id) {
                    skipped_id = frame_id;
                    continue;
                }
                if (sGroups != null) {
                    JSONArray jsProofs = frameProofs(frame_id, sGroups);
                    if (jsProofs.length() == 0) {
                        skipped_id = frame_id;
                        continue;
                    }
                }
                JSONObject c = new JSONObject();
                c.put("id", frame_id);
                c.put("type", row.get("Type"));
                c.put("f_index", row.get("F_INDEX"));
                c.put("label", row.get("NAME"));
                JSONArray sarr = new JSONArray();
                fn_loop_org_all_edited(frame_id, sarr, kd_id, sGroups, doituong_kd);
                c.put("children", sarr);
                result_arr.put(c);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result_arr;
    }

    public JSONArray frameProofs(int id, String sGroups) {
        JSONArray jsaProofs = listMCbyFrame(id, sGroups);
        JSONArray ja = new JSONArray();
        get_mc_list(id, sGroups, ja);
        for (int i = 0; i < ja.length(); i++) {
            jsaProofs.put(ja.getJSONObject(i));
        }
        return jsaProofs;
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
        return updateMCTable(mc_id, ten_mc, ten_file, path, emp_id, -1, null, created_by);
    }

    public int updateMCTable(int mc_id, String ten_mc, String ten_file, String path, int emp_id, int group_id, String deadline, int created_by) {
        StringBuilder sql = new StringBuilder("update TBL_Minhchung set ten_mc = ?");
        List<Object> params = new ArrayList<>();
        params.add(ten_mc);
        if (ten_file != null) { sql.append(", ten_file = ?"); params.add(ten_file); }
        if (path != null) { sql.append(", path = ?"); params.add(path); }
        if (emp_id != -1) { sql.append(", emp_id = ?"); params.add(emp_id); }
        if (group_id != -1) { sql.append(", group_id = ?"); params.add(group_id); }
        if (deadline != null) { sql.append(", deadline = CONVERT(datetime, ?, 103)"); params.add(deadline); }
        sql.append(", CreatedTime = GETDATE(), CreatedBy = ? where ID = ?");
        params.add(created_by);
        params.add(mc_id);
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    public int updateProofwUpload(int mc_id, JSONObject joInputFile, int emp_id, int created_by) {
        return updateProofwUpload(mc_id, joInputFile, emp_id, -1, null, created_by);
    }

    public int updateProofwUpload(int mc_id, JSONObject joInputFile, int emp_id, int group_id, String deadline, int created_by) {
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
            updateMCTable(mc_id, ten_mc, ten_file, path, emp_id, group_id, deadline, created_by);
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

    public boolean isFrameIndexExisted(String type, String f_index, int kd_id) {
        String sql = "select count(*) from TBL_FRAME where TYPE = ? and F_INDEX = ? and kd_id = ? and (IsDeleted is null or IsDeleted = 0)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, type, f_index, kd_id);
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
        return addSiblingFrame(type, ref_id, f_index, name, created_by, status, doituong_kd, kd_id, false);
    }

    public int addSiblingFrame(String type, int ref_id, String f_index, String name, int created_by, int status, String doituong_kd, int kd_id, boolean create_sub) {
        Integer ID = -1;
        try {
            String f_f_index = "";
            int parentID = parentID(ref_id);
            if (parentID == 0) {
                f_f_index = padLeftZeros(f_index, 2);
            } else {
                JSONObject joParent = parentInfo(ref_id);
                String parent_f_f_index = joParent.optString("F_F_INDEX", "");
                if (parent_f_f_index.isEmpty()) {
                    f_f_index = padLeftZeros(f_index, 2);
                } else {
                    f_f_index = parent_f_f_index + "." + padLeftZeros(f_index, 2);
                }
            }
            String fullName = type + " " + f_index + ". " + name;
            String sql = "INSERT INTO TBL_FRAME ( TYPE, PARENTID, F_INDEX, F_F_INDEX, NAME, CREATEDBY, CREATEDTIME, UPDATEDBY, UPDATEDTIME, IsDeleted, status, doituong_kd, kd_id) "
                       + " OUTPUT INSERTED.ID "
                       + " SELECT ?, PARENTID, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), 0, ?, ?, ? FROM TBL_FRAME WHERE ID = ?";
            ID = jdbcTemplate.queryForObject(sql, Integer.class, type, f_index, f_f_index, fullName, created_by, created_by, status, doituong_kd, kd_id, ref_id);
            if (create_sub && ID != null) {
                subFoldersCreation(ID);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
        return ID != null ? ID : -1;
    }

    public String nextPeerFindex(int ID, int kd_id) {
        String sql = "WITH ChildIndexes AS ( "
                   + " SELECT F_INDEX, "
                   + " CASE WHEN F_INDEX LIKE '%.%' THEN CAST(SUBSTRING(F_INDEX, CHARINDEX('.', F_INDEX) + 1, LEN(F_INDEX)) AS INT) "
                   + "      ELSE CAST(F_INDEX AS INT) "
                   + " END AS NumericIndex, "
                   + " CASE WHEN F_INDEX LIKE '%.%' THEN LEFT(F_INDEX, CHARINDEX('.', F_INDEX) - 1) "
                   + "      ELSE NULL "
                   + " END AS ParentPart "
                   + " FROM TBL_FRAME "
                   + " WHERE ParentID = (SELECT ParentID FROM TBL_FRAME WHERE ID = ?) "
                   + "   AND KD_ID = ? AND (IsDeleted IS NULL OR IsDeleted = 0) "
                   + " ), "
                   + " MaxIndex AS ( "
                   + " SELECT MAX(NumericIndex) AS MaxNumIndex, MAX(ParentPart) AS ParentPart "
                   + " FROM ChildIndexes "
                   + " ) "
                   + " SELECT CASE WHEN MaxIndex.ParentPart IS NOT NULL THEN CONVERT(NVARCHAR(10), MaxIndex.ParentPart) + '.' + CONVERT(NVARCHAR(10), MaxIndex.MaxNumIndex + 1) "
                   + "             ELSE CONVERT(NVARCHAR(10), MaxIndex.MaxNumIndex + 1) "
                   + "        END AS f_index "
                   + " FROM MaxIndex";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, ID, kd_id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String nextChildFindex(int ID, int kd_id) {
        String sql = "WITH ParentDetails AS ( "
                   + "    SELECT F_INDEX, TYPE FROM TBL_FRAME WHERE ID = ? "
                   + "), "
                   + "ChildIndexes AS ( "
                   + "    SELECT F_INDEX, CAST(SUBSTRING(F_INDEX, CHARINDEX('.', F_INDEX) + 1, LEN(F_INDEX)) AS INT) AS NumericIndex "
                   + "    FROM TBL_FRAME "
                   + "    WHERE ParentID = ? AND KD_ID = ? AND (IsDeleted IS NULL OR IsDeleted = 0) "
                   + "), "
                   + "MaxIndex AS ( "
                   + "    SELECT MAX(NumericIndex) AS MaxNumIndex FROM ChildIndexes "
                   + ") "
                   + "SELECT CASE WHEN MaxIndex.MaxNumIndex IS NOT NULL THEN CONVERT(NVARCHAR(10), (SELECT F_INDEX FROM ParentDetails)) + '.' + CONVERT(NVARCHAR(10), MaxIndex.MaxNumIndex + 1) "
                   + "            ELSE CASE (SELECT TYPE FROM ParentDetails) "
                   + "                     WHEN N'Lĩnh vực' THEN N'1' "
                   + "                     WHEN N'Tiêu chuẩn' THEN CONVERT(NVARCHAR(10), (SELECT F_INDEX FROM ParentDetails)) + N'.1' "
                   + "                     ELSE NULL "
                   + "                 END "
                   + "       END AS f_index "
                   + "FROM MaxIndex";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, ID, ID, kd_id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int addChildFrame(String type, int ref_id, String f_index, String name, int created_by, int status, String doituong_kd, int kd_id) {
        return addChildFrame(type, ref_id, f_index, name, created_by, status, doituong_kd, kd_id, false);
    }

    public int addChildFrame(String type, int ref_id, String f_index, String name, int created_by, int status, String doituong_kd, int kd_id, boolean create_sub) {
        Integer ID = -1;
        try {
            String f_f_index = "";
            JSONObject joParent = frameInfo(ref_id);
            String parent_f_f_index = joParent.optString("F_F_INDEX", "");
            if (parent_f_f_index.isEmpty()) {
                f_f_index = padLeftZeros(f_index, 2);
            } else {
                f_f_index = parent_f_f_index + "." + padLeftZeros(f_index, 2);
            }
            String fullName = type + " " + f_index + ". " + name;
            String sql = "INSERT INTO TBL_FRAME ( TYPE, PARENTID, F_INDEX, F_F_INDEX, NAME, CREATEDBY, CREATEDTIME, UPDATEDBY, UPDATEDTIME, IsDeleted, status, doituong_kd, kd_id) "
                       + " OUTPUT INSERTED.ID values (?, ?, ?, ?, ?, ?, GETDATE(), ?, GETDATE(), 0, ?, ?, ?)";
            ID = jdbcTemplate.queryForObject(sql, Integer.class, type, ref_id, f_index, f_f_index, fullName, created_by, created_by, status, doituong_kd, kd_id);
            if (create_sub && ID != null) {
                subFoldersCreation(ID);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
        return ID != null ? ID : -1;
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

    public JSONArray fileDuplications(JSONArray jsFiles, int kd_id, String doituong_kd, int status) {
        JSONArray jsRes = new JSONArray();
        try {
            JSONArray jsaKdproofs = kdProofs(kd_id, doituong_kd, status);
            for (int i = 0; i < jsFiles.length(); i++) {
                JSONObject joFile = jsFiles.getJSONObject(i);
                String sFileName = joFile.getString("filename");
                for (int j = 0; j < jsaKdproofs.length(); j++) {
                    JSONObject joP = jsaKdproofs.getJSONObject(j);
                    if (sFileName.equalsIgnoreCase(joP.getString("ten_mc"))) {
                        JSONObject jo = new JSONObject();
                        jo.put("ten_mc", sFileName);
                        jsRes.put(jo);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsRes;
    }

    public void assignMC2Employee(int mc_id, int emp_id) {
        String sql = "update TBL_MINHCHUNG set emp_id = ? where ID = ?";
        jdbcTemplate.update(sql, emp_id, mc_id);
    }

    public void approveMC(int mc_id, int emp_id, int status, String note, int user_id) {
        String sql = "insert into TBL_APPROVAL_HISTORY (MC_ID, EMP_ID, STATUS, NOTE, CREATEDBY, CREATEDTIME, ISDELETED) VALUES (?, ?, ?, ?, ?, GETDATE(), 0)";
        jdbcTemplate.update(sql, mc_id, emp_id, status, note, user_id);
    }

    public int cloneMc(int src_mc_id, int dest_mc_id, int user_id) {
        String sql = "update DEST set DEST.ten_mc = SRC.ten_mc, DEST.path = SRC.path, DEST.content = SRC.content, DEST.CreatedBy = ?, DEST.CreatedTime = GETDATE() "
                   + " from TBL_MINHCHUNG DEST INNER JOIN TBL_MINHCHUNG SRC on DEST.ID = ? and SRC.ID = ?";
        return jdbcTemplate.update(sql, user_id, dest_mc_id, src_mc_id);
    }

    public JSONArray searchByMaMc(String ma_mc, int kd_id) {
        String sql = "select * from TBL_MINHCHUNG where kd_id = ? and ma_mc = ? and (IsDeleted is null or IsDeleted = 0)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id, ma_mc);
        return rowsToJSONArray(rows);
    }

    public JSONObject frameStats(int frame_id, int status) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        JSONObject joStats = new JSONObject();
        try {
            JSONArray jsProofs = frameProofs(frame_id, null);
            int number_of_proofs = jsProofs.length();
            if (number_of_proofs == 0) return null;
            
            JSONArray jsDone = new JSONArray();
            JSONArray jsAssigned = new JSONArray();
            JSONArray jsOverdue = new JSONArray();
            for (int i = 0; i < jsProofs.length(); i++) {
                JSONObject joP = jsProofs.getJSONObject(i);
                if (joP.has("path") && joP.optString("path", "").length() > 0) {
                    jsDone.put(joP);
                }
                if (joP.has("group_id") && joP.optInt("group_id", -1) > 0) {
                    jsAssigned.put(joP);
                }
                if (joP.has("deadline") && !joP.isNull("deadline") && !joP.optString("deadline", "").isEmpty()) {
                    String sDeadline = joP.getString("deadline");
                    try {
                        Date dDeadline = sdf.parse(sDeadline);
                        if (new Date().after(dDeadline)) {
                            jsOverdue.put(joP);
                        }
                    } catch (Exception parseEx) {
                        // ignore bad date format
                    }
                }
            }
            joStats.put("number_of_proofs", number_of_proofs);
            joStats.put("number_of_done", jsDone.length());
            joStats.put("done", jsDone);
            joStats.put("number_of_assigned", jsAssigned.length());
            joStats.put("assigned", jsAssigned);
            joStats.put("number_of_overdue", jsOverdue.length());
            joStats.put("overdue", jsOverdue);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return joStats;
    }

    public JSONArray frameStatsByGroups(int frame_id, int status) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        JSONArray jsaGroups = new JSONArray();
        JSONArray jsaGroupsStats = new JSONArray();
        try {
            JSONArray jsProofs = frameProofs(frame_id, null);
            int number_of_proofs = jsProofs.length();
            if (number_of_proofs == 0) return null;
            
            for (int i = 0; i < jsProofs.length(); i++) {
                JSONObject joP = jsProofs.getJSONObject(i);
                boolean isDone = joP.has("path") && joP.optString("path", "").length() > 0;
                boolean isDeadlinePassed = false;
                if (joP.has("deadline") && !joP.isNull("deadline") && !joP.optString("deadline", "").isEmpty()) {
                    try {
                        Date dDeadline = sdf.parse(joP.getString("deadline"));
                        if (new Date().after(dDeadline)) isDeadlinePassed = true;
                    } catch (Exception ex) {}
                }
                boolean isDue = isDeadlinePassed && !isDone;
                int group_id = -1;
                String group_name = "Chưa phân công";
                boolean isAssigned = false;
                if (joP.has("group_id") && joP.optInt("group_id", -1) > 0) {
                    group_id = joP.getInt("group_id");
                    group_name = groupName(group_id);
                    isAssigned = true;
                }
                
                if (jsaGroupsStats.length() == 0) {
                    JSONObject jo = new JSONObject();
                    jo.put("name", group_name);
                    jo.put("group_id", group_id);
                    jo.put("assigned", isAssigned ? 1 : 0);
                    jo.put("done", isDone ? 1 : 0);
                    jo.put("overdue", isDue ? 1 : 0);
                    jo.put("proofs", new JSONArray().put(joP));
                    jsaGroupsStats.put(jo);
                } else {
                    boolean found = false;
                    for (int k = 0; k < jsaGroupsStats.length(); k++) {
                        JSONObject joGroup = jsaGroupsStats.getJSONObject(k);
                        if (joGroup.getInt("group_id") == group_id) {
                            found = true;
                            JSONObject jo = new JSONObject();
                            jo.put("group_id", group_id);
                            jo.put("name", group_name);
                            jo.put("assigned", joGroup.getInt("assigned") + (isAssigned ? 1 : 0));
                            jo.put("done", joGroup.getInt("done") + (isDone ? 1 : 0));
                            jo.put("overdue", joGroup.getInt("overdue") + (isDue ? 1 : 0));
                            jo.put("proofs", joGroup.getJSONArray("proofs").put(joP));
                            jsaGroupsStats.remove(k);
                            jsaGroupsStats.put(jo);
                            break;
                        }
                    }
                    if (!found) {
                        JSONObject jo = new JSONObject();
                        jo.put("name", group_name);
                        jo.put("group_id", group_id);
                        jo.put("assigned", isAssigned ? 1 : 0);
                        jo.put("done", isDone ? 1 : 0);
                        jo.put("overdue", isDue ? 1 : 0);
                        jo.put("proofs", new JSONArray().put(joP));
                        jsaGroupsStats.put(jo);
                    }
                }
            }
            
            JSONArray jsDonePercentage = new JSONArray();
            JSONArray jsOverduePercentage = new JSONArray();
            for (int g = 0; g < jsaGroupsStats.length(); g++) {
                JSONObject jo = jsaGroupsStats.getJSONObject(g);
                JSONObject jsonDone = new JSONObject();
                JSONObject jsonOverdue = new JSONObject();
                int assigned = jo.getInt("assigned");
                int done = jo.getInt("done");
                int overdue = jo.getInt("overdue");
                
                jsonDone.put("name", jo.getString("name"));
                jsonOverdue.put("name", jo.getString("name"));
                jsonDone.put("drilldown", jo.getString("name"));
                jsonOverdue.put("drilldown", jo.getString("name"));
                jsonDone.put("proofs", jo.getJSONArray("proofs"));
                
                if (assigned > 0) {
                    jsonDone.put("y", Math.round((double) done / assigned * 100));
                    jsonOverdue.put("y", Math.round((double) overdue / assigned * 100));
                } else {
                    jsonDone.put("y", 0);
                    jsonOverdue.put("y", 0);
                }
                jsDonePercentage.put(jsonDone);
                jsOverduePercentage.put(jsonOverdue);
            }
            
            JSONObject joDone = new JSONObject();
            joDone.put("name", "Tỷ lệ hoàn thành");
            joDone.put("colorByPoint", true);
            joDone.put("data", jsDonePercentage);
            joDone.put("emp_data", jsDonePercentage);
            
            jsaGroups.put(new JSONArray().put(joDone));
            
            JSONObject joOverdue = new JSONObject();
            joOverdue.put("name", "Tỷ lệ trễ hạn");
            joOverdue.put("colorByPoint", true);
            joOverdue.put("data", jsOverduePercentage);
            
            jsaGroups.put(new JSONArray().put(joOverdue));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return jsaGroups;
    }

    public JSONArray statsByMembers(JSONArray jsaProofs) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        JSONArray jsMemStats = new JSONArray();
        JSONArray jsEmp = new JSONArray();
        try {
            for (int i = 0; i < jsaProofs.length(); i++) {
                JSONObject joP = jsaProofs.getJSONObject(i);
                boolean isDone = joP.has("path") && joP.optString("path", "").length() > 0;
                boolean isDeadlinePassed = false;
                if (joP.has("deadline") && !joP.isNull("deadline") && !joP.optString("deadline", "").isEmpty()) {
                    try {
                        Date dDeadline = sdf.parse(joP.getString("deadline"));
                        if (new Date().after(dDeadline)) isDeadlinePassed = true;
                    } catch (Exception ex) {}
                }
                boolean isDue = isDeadlinePassed && !isDone;
                int emp_id = -1;
                String emp = "Chưa phân công";
                boolean isAssigned = false;
                if (joP.has("emp_id") && joP.optInt("emp_id", -1) > 0) {
                    // source property is emp_id
                    emp_id = joP.getInt("emp_id");
                    emp = joP.getString("emp");
                    isAssigned = true;
                }
                
                if (jsEmp.length() == 0) {
                    JSONObject jo = new JSONObject();
                    jo.put("emp", emp);
                    jo.put("emp_id", emp_id);
                    jo.put("assigned", isAssigned ? 1 : 0);
                    jo.put("done", isDone ? 1 : 0);
                    jo.put("overdue", isDue ? 1 : 0);
                    jsEmp.put(jo);
                } else {
                    boolean found = false;
                    for (int k = 0; k < jsEmp.length(); k++) {
                        JSONObject joEmp = jsEmp.getJSONObject(k);
                        if (joEmp.getInt("emp_id") == emp_id) {
                            found = true;
                            JSONObject jo = new JSONObject();
                            jo.put("emp_id", emp_id);
                            jo.put("emp", emp);
                            jo.put("assigned", joEmp.getInt("assigned") + (isAssigned ? 1 : 0));
                            jo.put("done", joEmp.getInt("done") + (isDone ? 1 : 0));
                            jo.put("overdue", joEmp.getInt("overdue") + (isDue ? 1 : 0));
                            jsEmp.remove(k);
                            jsEmp.put(jo);
                            break;
                        }
                    }
                    if (!found) {
                        JSONObject jo = new JSONObject();
                        jo.put("emp", emp);
                        jo.put("emp_id", emp_id);
                        jo.put("assigned", isAssigned ? 1 : 0);
                        jo.put("done", isDone ? 1 : 0);
                        jo.put("overdue", isDue ? 1 : 0);
                        jsEmp.put(jo);
                    }
                }
            }
            
            JSONArray jsDonePercentage = new JSONArray();
            JSONArray jsOverduePercentage = new JSONArray();
            for (int e = 0; e < jsEmp.length(); e++) {
                JSONObject jo = jsEmp.getJSONObject(e);
                JSONObject jsonDone = new JSONObject();
                JSONObject jsonOverdue = new JSONObject();
                int assigned = jo.getInt("assigned");
                int done = jo.getInt("done");
                int overdue = jo.getInt("overdue");
                
                jsonDone.put("emp", jo.getString("emp"));
                jsonOverdue.put("emp", jo.getString("emp"));
                if (assigned > 0) {
                    jsonDone.put("y", Math.round((double) done / assigned * 100));
                    jsonOverdue.put("y", Math.round((double) overdue / assigned * 100));
                } else {
                    jsonDone.put("y", 0);
                    jsonOverdue.put("y", 0);
                }
                jsDonePercentage.put(jsonDone);
                jsOverduePercentage.put(jsonOverdue);
            }
            
            JSONObject joDone = new JSONObject();
            joDone.put("name", "Tỷ lệ hoàn thành");
            joDone.put("colorByPoint", true);
            joDone.put("data", jsDonePercentage);
            jsMemStats.put(new JSONArray().put(joDone));
            
            JSONObject joOverdue = new JSONObject();
            joOverdue.put("name", "Tỷ lệ trễ hạn");
            joOverdue.put("colorByPoint", true);
            joOverdue.put("data", jsOverduePercentage);
            jsMemStats.put(new JSONArray().put(joOverdue));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return jsMemStats;
    }

    public String groupName(int group_id) {
        try {
            String sql = "select group_name from TBL_GROUP where ID = ?";
            return jdbcTemplate.queryForObject(sql, String.class, group_id);
        } catch (Exception e) {
            return "Chưa phân công";
        }
    }

    public JSONObject uploadMCStats() {
        JSONObject joStats = new JSONObject();
        try {
            String sql = "SELECT COUNT( CASE WHEN path is not null THEN 1 END ) AS uploaded, "
                       + " COUNT( CASE WHEN content is not null THEN 1 END ) AS ocred, "
                       + " COUNT( * ) AS total "
                       + " FROM TBL_Minhchung ";
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
            if (!list.isEmpty()) {
                Map<String, Object> map = list.get(0);
                joStats.put("total", map.get("total"));
                joStats.put("uploaded", map.get("uploaded"));
                joStats.put("ocred", map.get("ocred"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return joStats;
    }

    public JSONObject mcDetails(String ma_mc, int kd_id) {
        JSONObject joDetails = new JSONObject();
        try {
            String sql = "select ten_mc, so_ngay_thang, noi_ban_hanh from TBL_Minhchung "
                       + " where ma_mc = ? and kd_id = ? and (IsDeleted is null or IsDeleted = 0)";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ma_mc, kd_id);
            if (!rows.isEmpty()) {
                Map<String, Object> map = rows.get(0);
                joDetails.put("ten_mc", map.get("ten_mc"));
                joDetails.put("so_ngay_thang", map.get("so_ngay_thang"));
                joDetails.put("noi_ban_hanh", map.get("noi_ban_hanh"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return joDetails;
    }

    public JSONArray getSubs(int frame_id) {
        JSONArray jsaSubs = new JSONArray();
        try {
            String sql = "SELECT T1.ID, T1.type, T1.ParentID, T1.Name "
                       + " FROM TBL_FRAME AS T1 "
                       + " JOIN TBL_FRAME AS T2 ON (T2.ID = ? AND T2.TYPE = 'sub' AND T1.ParentID = T2.ParentID AND T1.TYPE = 'sub') "
                       + "                     OR (T2.ID = ? AND T2.TYPE = N'Tiêu chí' AND T1.ParentID = T2.ID)";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, frame_id, frame_id);
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                jo.put("ID", row.get("ID"));
                jo.put("type", row.get("type"));
                jo.put("ParentID", row.get("ParentID"));
                jo.put("Name", row.get("Name"));
                jsaSubs.put(jo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsaSubs;
    }

    public int moveMc(JSONArray jsMcs, int frame_id) {
        try {
            String sIDs = parseIdsToString(jsMcs);
            if (sIDs.isEmpty() || !sIDs.matches("^[0-9,\\s]+$")) return 0;
            String sql = "UPDATE TBL_Minhchung SET f_id = ? WHERE ID IN (" + sIDs + ")";
            return jdbcTemplate.update(sql, frame_id);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public String parseIdsToString(JSONArray jsonArray) {
        List<Integer> idList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (jsonObject.has("id")) {
                    idList.add(jsonObject.getInt("id"));
                }
            } catch (Exception e) {
                System.err.println("Error parsing JSON object at index " + i + ": " + e.getMessage());
            }
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < idList.size(); i++) {
            result.append(idList.get(i));
            if (i < idList.size() - 1) {
                result.append(",");
            }
        }
        return result.toString();
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

    public int assignMC2Group(int mc_id, int group_id) {
        String sql = "update TBL_MINHCHUNG set group_id = ? where ID = ?";
        return jdbcTemplate.update(sql, group_id, mc_id);
    }

    public int assignFrame2Group(int frame_id, int group_id, int emp_id, String deadline) {
        try {
            JSONArray jsProofs = frameProofs(frame_id, null);
            List<Integer> arrIDs = new ArrayList<>();
            for (int i = 0; i < jsProofs.length(); i++) {
                JSONObject joP = jsProofs.getJSONObject(i);
                arrIDs.add(joP.getInt("id"));
            }
            if (arrIDs.isEmpty()) return 0;
            StringBuilder sql = new StringBuilder("update TBL_MINHCHUNG set group_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(group_id);
            if (emp_id != -1) {
                sql.append(", emp_id = ?");
                params.add(emp_id);
            } else {
                // When admin assigns to group, clear any previous member assignment
                sql.append(", emp_id = NULL");
            }
            sql.append(", deadline = CONVERT(DATETIME, ?, 103) where ID in (");
            params.add(deadline);
            for (int i = 0; i < arrIDs.size(); i++) {
                sql.append("?");
                params.add(arrIDs.get(i));
                if (i < arrIDs.size() - 1) sql.append(",");
            }
            sql.append(")");
            return jdbcTemplate.update(sql.toString(), params.toArray());
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
    }

    /**
     * Get proofs list filtered by member (emp_id = user_id).
     * Member sees only proofs assigned to them.
     */
    public int get_mc_list_by_member(int root_id, int user_id, JSONArray result_arr) {
        StringBuilder sql = new StringBuilder();
        sql.append("WITH RecursiveFrames AS ( ")
           .append("  SELECT ID FROM TBL_FRAME WHERE ID = ? AND (IsDeleted IS NULL OR IsDeleted = 0) ")
           .append("  UNION ALL ")
           .append("  SELECT f.ID FROM TBL_FRAME f INNER JOIN RecursiveFrames rf ON f.ParentID = rf.ID ")
           .append("  WHERE (f.IsDeleted IS NULL OR f.IsDeleted = 0) ")
           .append(") ")
           .append("SELECT a.ID, a.ma_mc, a.ten_mc, a.ten_file, a.path, a.is_locked, a.CreatedTime, ")
           .append("       a.emp_id, a.org_id, a.group_id, a.deadline, a.UploadedBy, a.UploadedTime, ")
           .append("       CASE WHEN a.content IS NOT NULL THEN 1 ELSE 0 END as has_ocr, ")
           .append("       b.Fullname as creator, c.Fullname as emp, c1.Fullname as uploader, d.group_name ")
           .append("FROM TBL_MINHCHUNG a ")
           .append("INNER JOIN RecursiveFrames rf ON a.f_id = rf.ID ")
           .append("LEFT JOIN TBL_USER b ON b.id = a.CreatedBy ")
           .append("LEFT JOIN TBL_USER c ON c.ID = a.emp_id ")
           .append("LEFT JOIN TBL_USER c1 ON c1.ID = a.UploadedBy ")
           .append("LEFT JOIN TBL_GROUP d ON d.ID = a.group_id ")
           .append("WHERE (a.IsDeleted IS NULL OR a.IsDeleted = 0) ")
           .append("AND a.emp_id = ?");
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), root_id, user_id);
            for (Map<String, Object> row : rows) {
                JSONObject joMc = new JSONObject();
                int id = (int) row.get("ID");
                joMc.put("id", id);
                joMc.put("ma_mc", row.get("ma_mc"));
                joMc.put("ten_mc", row.get("ten_mc"));
                joMc.put("ten_file", row.get("ten_file"));
                joMc.put("path", row.get("path"));
                joMc.put("is_locked", row.get("is_locked"));
                joMc.put("created_time", row.get("CreatedTime") != null ? row.get("CreatedTime").toString() : "");
                joMc.put("has_ocr", (int) row.get("has_ocr") == 1);
                joMc.put("creator", row.get("creator"));
                joMc.put("emp_id", row.get("emp_id") != null ? row.get("emp_id") : -1);
                joMc.put("emp", row.get("emp") != null ? row.get("emp") : "Chưa phân công");
                int org_id = row.get("org_id") != null ? (int) row.get("org_id") : 0;
                if (org_id > 0) joMc.put("org_id", org_id);
                int group_id = row.get("group_id") != null ? (int) row.get("group_id") : 0;
                if (group_id > 0) {
                    joMc.put("group_id", group_id);
                    joMc.put("group_name", row.get("group_name"));
                } else {
                    joMc.put("group_id", -1);
                    joMc.put("group_name", "Chưa phân công");
                }
                if (row.get("deadline") != null) joMc.put("deadline", row.get("deadline").toString());
                int uploaded_by = row.get("UploadedBy") != null ? (int) row.get("UploadedBy") : 0;
                if (uploaded_by > 0) {
                    joMc.put("uploaded_by", uploaded_by);
                    joMc.put("uploader", row.get("uploader"));
                    joMc.put("uploaded_time", row.get("UploadedTime") != null ? row.get("UploadedTime").toString() : "");
                } else {
                    joMc.put("uploaded_by", -1);
                    joMc.put("uploader", "");
                    joMc.put("uploaded_time", "");
                }
                result_arr.put(joMc);
            }
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Assign a single proof to a member (set emp_id).
     * Used by group leader to delegate a specific proof to a team member.
     */
    public int assignMC2Member(int mc_id, int member_id, String deadline, int assigned_by) {
        StringBuilder sql = new StringBuilder("UPDATE TBL_MINHCHUNG SET emp_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(member_id);
        if (deadline != null && !deadline.isEmpty()) {
            sql.append(", deadline = CONVERT(DATETIME, ?, 103)");
            params.add(deadline);
        }
        sql.append(" WHERE ID = ?");
        params.add(mc_id);
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    /**
     * Assign all proofs in a frame branch to a member (set emp_id).
     * Used by group leader to delegate an entire branch to a team member.
     * The group_id is preserved (already set by admin).
     */
    public int assignFrameProofs2Member(int frame_id, int member_id, String deadline) {
        try {
            JSONArray jsProofs = frameProofs(frame_id, null);
            List<Integer> arrIDs = new ArrayList<>();
            for (int i = 0; i < jsProofs.length(); i++) {
                JSONObject joP = jsProofs.getJSONObject(i);
                arrIDs.add(joP.getInt("id"));
            }
            if (arrIDs.isEmpty()) return 0;
            StringBuilder sql = new StringBuilder("UPDATE TBL_MINHCHUNG SET emp_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(member_id);
            if (deadline != null && !deadline.isEmpty()) {
                sql.append(", deadline = CONVERT(DATETIME, ?, 103)");
                params.add(deadline);
            }
            sql.append(" WHERE ID IN (");
            for (int i = 0; i < arrIDs.size(); i++) {
                sql.append("?");
                params.add(arrIDs.get(i));
                if (i < arrIDs.size() - 1) sql.append(",");
            }
            sql.append(")");
            return jdbcTemplate.update(sql.toString(), params.toArray());
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
    }
}
