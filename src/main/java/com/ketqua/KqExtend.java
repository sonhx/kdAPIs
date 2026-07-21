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
    @org.springframework.beans.factory.annotation.Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KdExtend kdExtend;

    public JSONArray listToChuc() {
        JSONArray jsaTochuc = new JSONArray();
        String sql = "select * from TBL_Tochuc where (IsDeleted is null or IsDeleted =0)";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                JSONObject jo = new JSONObject();
                jo.put("ten_tochuc", row.get("ten_tochuc"));
                jo.put("id", row.get("ID"));
                jsaTochuc.put(jo);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return jsaTochuc;
    }

    public String removeExtra(String sInput, String regex) {
        while (sInput.matches(regex)) {
            sInput = sInput.split(regex)[1].trim();
            removeExtra(sInput, regex);
        }
        return sInput;
    }

    private Date addMonth(Date date, int months) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MONTH, months);
        return cal.getTime();
    }

    public JSONArray listKq_ed(int kd_scope) {
        JSONArray jsaKds = new JSONArray();
        try {
            JSONArray jsaTemp = kdExtend.listKd(kd_scope, null);
            if (jsaTemp != null && jsaTemp.length() != 0) {
                for (int d = 0; d < jsaTemp.length(); d++) {
                    JSONObject joKd = jsaTemp.getJSONObject(d);
                    int kd_id = joKd.getInt("id");

                    JSONObject jsaCts = kqDetails_ed(kd_id);

                    if (jsaCts.length() > 0) {
                        String[] keys = JSONObject.getNames(jsaCts);
                        if (keys != null) {
                            for (String key : keys) {
                                joKd.put(key, jsaCts.get(key));
                            }
                        }
                    }
                    jsaKds.put(joKd);
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return jsaKds;
    }

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
                        + " and KD_ID = ? "
                        + " and abbr ='cs' "
                        + " UNION ALL "
                        + " SELECT a.ten as ten_ct, a.abbr, b.ten, b.ghi_chu, b.CreatedTime, b.ID as kq_id "
                        + " FROM TBL_Nganh_daotao a "
                        + " INNER JOIN TBL_Ketqua b on b.doituong_kd = a.abbr "
                        + " where (a.IsDeleted is null or a.IsDeleted=0) "
                        + (kd_scope == -1 ? "" : " and b.scope = " + kd_scope)
                        + " and (b.IsDeleted is null or b.IsDeleted=0) "
                        + " and KD_ID = ? "
                        + " and abbr <>'cs' ";

                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id, kd_id);
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

    public JSONObject kqDetails_ed(int kd_id) {
        JSONObject joKQ = new JSONObject();
        try {
            String sql = "SELECT a.* FROM [dbo].[TBL_Ketqua_doc] a "
                    + " INNER JOIN TBL_Ketqua b on b.ID = a.kq_id "
                    + " INNER JOIN TBL_Kiemdinh c on c.ID = b.kd_id "
                    + " where (a.IsDeleted is null or a.IsDeleted =0) "
                    + " and (b.IsDeleted is null or b.IsDeleted = 0) "
                    + " and (c.IsDeleted is null or c.IsDeleted=0) "
                    + " and c.ID = ? ";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kd_id);
            for (Map<String, Object> row : rows) {
                String nq_so = "", nq_ten = "", nq_path = "";
                String qd_so = "", qd_ten = "", qd_path = "";
                String gcn_so = "", gcn_ten = "", gcn_path = "";
                String gcn_thoihan = null;
                String gcn_thoihan1 = null;
                String sTdg = null;
                String sBcrs = null;
                boolean bTdg_overdue = false;
                boolean bTdg_warning = false;
                boolean bBcrs_overdue = false;
                boolean bBcrs_warning = false;

                String loai = (String) row.get("loai");
                if (loai != null) {
                    switch (loai) {
                        case "nq":
                            nq_so = (String) row.get("so");
                            nq_ten = (String) row.get("ten");
                            nq_path = (String) row.get("path");

                            joKQ.put("nq_so", nq_so);
                            joKQ.put("nq_ten", nq_ten);
                            joKQ.put("nq_path", nq_path);
                            break;
                        case "qd":
                            qd_so = (String) row.get("so");
                            qd_ten = (String) row.get("ten");
                            qd_path = (String) row.get("path");

                            joKQ.put("qd_so", qd_so);
                            joKQ.put("qd_ten", qd_ten);
                            joKQ.put("qd_path", qd_path);
                            break;
                        case "gcn":
                            gcn_so = (String) row.get("so");
                            gcn_ten = (String) row.get("ten");
                            gcn_path = (String) row.get("path");

                            Object expiryDateObj = row.get("ExpiryDate");
                            if (expiryDateObj != null) {
                                Date dGcn = null;
                                if (expiryDateObj instanceof Date) {
                                    dGcn = (Date) expiryDateObj;
                                } else {
                                    String expStr = expiryDateObj.toString();
                                    try {
                                        dGcn = new SimpleDateFormat("dd/MM/yyyy").parse(expStr);
                                    } catch (Exception e1) {
                                        try {
                                            dGcn = new SimpleDateFormat("yyyy-MM-dd").parse(expStr);
                                        } catch (Exception e2) {}
                                    }
                                }

                                if (dGcn != null) {
                                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                                    SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                                    Date dTdg = addMonth(dGcn, -9);
                                    Date dTdg_warning = addMonth(dTdg, -3);
                                    Date dBcrs = addMonth(dGcn, -30);
                                    Date dBcrs_warning = addMonth(dBcrs, -3);

                                    gcn_thoihan = sdf.format(dGcn);
                                    gcn_thoihan1 = sdf1.format(dGcn);
                                    sTdg = sdf.format(dTdg);
                                    sBcrs = sdf.format(dBcrs);

                                    Date now = new Date();
                                    if (dTdg.before(now)) bTdg_overdue = true;
                                    if (dTdg_warning.before(now)) bTdg_warning = true;

                                    if (dBcrs.before(now)) bBcrs_overdue = true;
                                    if (dBcrs_warning.before(now)) bBcrs_warning = true;
                                }
                            }

                            joKQ.put("gcn_so", gcn_so);
                            joKQ.put("gcn_ten", gcn_ten);
                            joKQ.put("gcn_path", gcn_path);
                            joKQ.put("gcn_thoihan", gcn_thoihan);
                            joKQ.put("gcn_th", gcn_thoihan1);
                            joKQ.put("tdg_next", sTdg);
                            joKQ.put("tdg_overdue", bTdg_overdue);
                            joKQ.put("tdg_warning", bTdg_warning);
                            joKQ.put("bcrs_next", sBcrs);
                            joKQ.put("bcrs_overdue", bBcrs_overdue);
                            joKQ.put("bcrs_warning", bBcrs_warning);
                            break;
                    }
                }
                joKQ.put("kq_id", row.get("kq_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return joKQ;
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
                        nq_so = joDoc.getString("so");
                        nq_ten = joDoc.getString("ten");
                        nq_path = joDoc.getString("path");
                        break;
                    case "qd":
                        qd_so = joDoc.getString("so");
                        qd_ten = joDoc.getString("ten");
                        qd_path = joDoc.getString("path");
                        break;
                    case "gcn":
                        gcn_so = joDoc.getString("so");
                        gcn_ten = joDoc.getString("ten");
                        gcn_path = joDoc.getString("path");
                        gcn_thoihan = joDoc.optString("expiry_date", null);

                        if (gcn_thoihan != null && !gcn_thoihan.isEmpty() && !gcn_thoihan.equals("null")) {
                            Date dGcn = null;
                            try {
                                dGcn = new SimpleDateFormat("dd/MM/yyyy").parse(gcn_thoihan);
                            } catch (Exception e1) {
                                try {
                                    dGcn = new SimpleDateFormat("yyyy-MM-dd").parse(gcn_thoihan);
                                } catch (Exception e2) {}
                            }

                            if (dGcn != null) {
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                                SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
                                Date dTdg = addMonth(dGcn, -9);
                                Date dTdg_warning = addMonth(dTdg, -3);
                                Date dBcrs = addMonth(dGcn, -30);
                                Date dBcrs_warning = addMonth(dBcrs, -3);
                                Date now = new Date();

                                gcn_thoihan = sdf.format(dGcn);
                                gcn_thoihan1 = sdf1.format(dGcn);
                                sTdg = sdf.format(dTdg);
                                sBcrs = sdf.format(dBcrs);

                                if (dTdg.before(now)) bTdg_overdue = true;
                                if (dTdg_warning.before(now)) bTdg_warning = true;
                                if (dBcrs.before(now)) bBcrs_overdue = true;
                                if (dBcrs_warning.before(now)) bBcrs_warning = true;
                            }
                        }
                        break;
                }
            }
            joKQ.put("nq_so", nq_so);
            joKQ.put("nq_ten", nq_ten);
            joKQ.put("nq_path", nq_path);
            joKQ.put("qd_so", qd_so);
            joKQ.put("qd_ten", qd_ten);
            joKQ.put("qd_path", qd_path);
            joKQ.put("gcn_so", gcn_so);
            joKQ.put("gcn_ten", gcn_ten);
            joKQ.put("gcn_path", gcn_path);
            joKQ.put("gcn_thoihan", gcn_thoihan);
            joKQ.put("gcn_th", gcn_thoihan1);
            joKQ.put("tdg_next", sTdg);
            joKQ.put("tdg_overdue", bTdg_overdue);
            joKQ.put("tdg_warning", bTdg_warning);
            joKQ.put("bcrs_next", sBcrs);
            joKQ.put("bcrs_overdue", bBcrs_overdue);
            joKQ.put("bcrs_warning", bBcrs_warning);
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
            jo.put("kq_id", kq_id);
            jo.put("ten_ct", row.get("ten_ct"));
            jo.put("ten", row.get("ten"));
            jo.put("so", row.get("so"));
            jo.put("loai", row.get("loai"));
            jo.put("ghi_chu", row.get("ghi_chu"));
            jo.put("created_time", row.get("CreatedTime"));
            jo.put("issue_date", row.get("IssueDate"));
            jo.put("expiry_date", row.get("ExpiryDate"));
            jo.put("path", row.get("path") == null ? "" : host + row.get("path"));
            jsaDocs.put(jo);
        }
        return jsaDocs;
    }

    public boolean isKQExisted(String ten_kq) {
        String sql = "select 1 from TBL_Ketqua where ten = ? and (IsDeleted is null or IsDeleted=0)";
        List<Integer> list = jdbcTemplate.queryForList(sql, Integer.class, ten_kq);
        return !list.isEmpty();
    }

    public int updateKq(int kq_id, int kd_id, String doituong_kd, String qd_so, String nq_so, String gcn_so, String gcn_thoihan, String ghi_chu, int created_by) {
        String sql = "update TBL_Ketqua set kd_id = ?, doituong_kd = ?, ghi_chu = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?";
        jdbcTemplate.update(sql, kd_id, doituong_kd, ghi_chu, created_by, kq_id);
        if (nq_so != null) {
            jdbcTemplate.update("update TBL_Ketqua_doc set so = ? where kq_id = ? and loai = 'nq'", nq_so, kq_id);
        }
        if (qd_so != null) {
            jdbcTemplate.update("update TBL_Ketqua_doc set so = ? where kq_id = ? and loai = 'qd'", qd_so, kq_id);
        }
        if (gcn_so != null) {
            if (gcn_thoihan != null && !gcn_thoihan.isEmpty() && !gcn_thoihan.equals("null")) {
                jdbcTemplate.update("update TBL_Ketqua_doc set so = ?, ExpiryDate = CONVERT(DATETIME, LEFT(?, 23), 126) where kq_id = ? and loai = 'gcn'", gcn_so, gcn_thoihan, kq_id);
            } else {
                jdbcTemplate.update("update TBL_Ketqua_doc set so = ? where kq_id = ? and loai = 'gcn'", gcn_so, kq_id);
            }
        }
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
