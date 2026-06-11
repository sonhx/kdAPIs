package com.ketqua;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.config.Config;
import com.kd.KdExtend;

@Service
public class KqExtend {
    public final String host = Config.host;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KdExtend kdExtend;

    public JSONArray listKq(int kd_scope) {
        JSONArray jsaKds = new JSONArray();
        try {
            JSONArray jsaTemp = kdExtend.listKd(kd_scope, null);
            for (int d = 0; d < jsaTemp.length(); d++) {
                JSONObject joKd = jsaTemp.getJSONObject(d);
                int kd_id = joKd.getInt("id");
                
                String sql = "SELECT a.ten as ten_ct, a.abbr, b.ten, b.ghi_chu, b.CreatedTime, b.ID as kq_id "
                        + " FROM TBL_Nganh_daotao a "
                        + " INNER JOIN TBL_Ketqua b on b.doituong_kd = a.abbr "
                        + " where (a.IsDeleted is null or a.IsDeleted=0) "
                        + (kd_scope == -1 ? "" : " and b.scope = " + kd_scope)
                        + " and (b.IsDeleted is null or b.IsDeleted=0) "
                        + " and KD_ID = ? ";

                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id);
                JSONArray jsaCts = new JSONArray();
                for (Map<String, Object> row : rows) {
                    int kq_id = (int) row.get("kq_id");
                    JSONObject joCT = kqDetails(kq_id);
                    joCT.put("id", kq_id);
                    joCT.put("ten_ct", row.get("ten_ct"));
                    joCT.put("ten", row.get("ten"));
                    joCT.put("abbr", row.get("abbr"));
                    joCT.put("ghi_chu", row.get("ghi_chu"));
                    joCT.put("created_time", row.get("CreatedTime"));
                    jsaCts.put(joCT);
                }
                joKd.put("cts", jsaCts);
                jsaKds.put(joKd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsaKds;
    }

    public JSONObject kqDetails(int kq_id) {
        JSONObject joKQ = new JSONObject();
        try {
            JSONArray jsaDocs = kqDocs(kq_id);
            String nq_so = "", nq_ten = "", nq_path = "";
            String qd_so = "", qd_ten = "", qd_path = "";
            String gcn_so = "", gcn_ten = "", gcn_path = "";
            String gcn_thoihan = null, gcn_thoihan1 = null, sTdg = null, sBcrs = null;
            boolean bTdg_overdue = false, bTdg_warning = false, bBcrs_overdue = false, bBcrs_warning = false;

            for (int i = 0; i < jsaDocs.length(); i++) {
                JSONObject joDoc = jsaDocs.getJSONObject(i);
                String loai = joDoc.getString("loai");
                switch (loai) {
                    case "nq":
                        nq_so = joDoc.getString("so"); nq_ten = joDoc.getString("ten"); nq_path = joDoc.getString("path"); break;
                    case "qd":
                        qd_so = joDoc.getString("so"); qd_ten = joDoc.getString("ten"); qd_path = joDoc.getString("path"); break;
                    case "gcn":
                        gcn_so = joDoc.getString("so"); gcn_ten = joDoc.getString("ten"); gcn_path = joDoc.getString("path");
                        gcn_thoihan = joDoc.optString("expiry_date", null);
                        if (gcn_thoihan != null && !gcn_thoihan.isEmpty() && !gcn_thoihan.equals("null")) {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                            Date dGcn = sdf.parse(gcn_thoihan);
                            Date dTdg = addMonth(dGcn, -9);
                            Date dTdg_warning = addMonth(dTdg, -3);
                            Date dBcrs = addMonth(dGcn, -30);
                            Date dBcrs_warning = addMonth(dBcrs, -3);
                            Date now = new Date();
                            gcn_thoihan = sdf.format(dGcn); gcn_thoihan1 = sdf1.format(dGcn);
                            sTdg = sdf.format(dTdg); sBcrs = sdf.format(dBcrs);
                            if (dTdg.before(now)) bTdg_overdue = true;
                            if (dTdg_warning.before(now)) bTdg_warning = true;
                            if (dBcrs.before(now)) bBcrs_overdue = true;
                            if (dBcrs_warning.before(now)) bBcrs_warning = true;
                        }
                        break;
                }
            }
            joKQ.put("nq_so", nq_so); joKQ.put("nq_ten", nq_ten); joKQ.put("nq_path", nq_path);
            joKQ.put("qd_so", qd_so); joKQ.put("qd_ten", qd_ten); joKQ.put("qd_path", qd_path);
            joKQ.put("gcn_so", gcn_so); joKQ.put("gcn_ten", gcn_ten); joKQ.put("gcn_path", gcn_path);
            joKQ.put("gcn_thoihan", gcn_thoihan); joKQ.put("gcn_th", gcn_thoihan1);
            joKQ.put("tdg_next", sTdg); joKQ.put("tdg_overdue", bTdg_overdue); joKQ.put("tdg_warning", bTdg_warning);
            joKQ.put("bcrs_next", sBcrs); joKQ.put("bcrs_overdue", bBcrs_overdue); joKQ.put("bcrs_warning", bBcrs_warning);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return joKQ;
    }

    public JSONArray kqDocs(int kq_id) {
        JSONArray jsaDocs = new JSONArray();
        String sql = "select * from TBL_KETQUA_DOC where kq_id = ? and (IsDeleted is null or IsDeleted =0)";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kq_id);
        for (Map<String, Object> row : rows) {
            JSONObject jo = new JSONObject();
            jo.put("id", row.get("ID"));
            jo.put("ten", row.get("ten"));
            jo.put("so", row.get("so"));
            jo.put("loai", row.get("loai"));
            jo.put("expiry_date", row.get("ExpiryDate"));
            jo.put("path", row.get("path") == null ? "" : host + row.get("path"));
            jsaDocs.put(jo);
        }
        return jsaDocs;
    }

    private Date addMonth(Date date, int months) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MONTH, months);
        return cal.getTime();
    }

    public int updateKq(int kq_id, int kd_id, String doituong_kd, String qd_so, String nq_so, String gcn_so, String gcn_thoihan, String ghi_chu, int created_by) {
        String sql = "update TBL_Ketqua set kd_id = ?, doituong_kd = ?, ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
        jdbcTemplate.update(sql, kd_id, doituong_kd, ghi_chu, created_by, kq_id);
        if (nq_so != null) jdbcTemplate.update("update TBL_Ketqua_doc set so = ? where kq_id = ? and loai = 'nq'", nq_so, kq_id);
        if (qd_so != null) jdbcTemplate.update("update TBL_Ketqua_doc set so = ? where kq_id = ? and loai = 'qd'", qd_so, kq_id);
        if (gcn_so != null) jdbcTemplate.update("update TBL_Ketqua_doc set so = ?, ExpiryDate = CONVERT(DATETIME, ?, 102) where kq_id = ? and loai = 'gcn'", gcn_so, gcn_thoihan, kq_id);
        return 1;
    }

    public int updateKqDoc_short(int kqId, String path, String ten, String loai, int userId) {
        String fullPath = path + "/" + ten;
        return jdbcTemplate.update("update TBL_Ketqua_doc set ten = ?, path = ?, Createdtime = GETDATE(), CreatedBy = ? where kq_id = ? and loai = ?", ten, fullPath, userId, kqId, loai);
    }

    public int deleteKq(int id) {
        return jdbcTemplate.update("update TBL_Ketqua set IsDeleted = 1 where ID = ?", id);
    }
}
