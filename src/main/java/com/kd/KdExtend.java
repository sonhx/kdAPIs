package com.kd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KdExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate evidenceJdbcTemplate;

    @jakarta.annotation.PostConstruct
    public void initIndexes() {
        try {
            evidenceJdbcTemplate.execute(
                "IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_TBL_Nganh_daotao_IsDeleted') " +
                "CREATE INDEX IX_TBL_Nganh_daotao_IsDeleted ON TBL_Nganh_daotao(IsDeleted);"
            );
            evidenceJdbcTemplate.execute(
                "IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_TBL_KIEMDINH_CT_KD_ID') " +
                "CREATE INDEX IX_TBL_KIEMDINH_CT_KD_ID ON TBL_KIEMDINH_CT(KD_ID, CT_ID, IsDeleted);"
            );
            evidenceJdbcTemplate.execute(
                "IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_TBL_Kiemdinh_IsDeleted_CT_ID') " +
                "CREATE INDEX IX_TBL_Kiemdinh_IsDeleted_CT_ID ON TBL_Kiemdinh(IsDeleted, CT_ID, standard_id);"
            );
        } catch (Exception ignored) {}
    }

    private Map<String, String> getCreatorNames(Set<String> createdByIds) {
        Map<String, String> resultMap = new HashMap<>();
        if (createdByIds == null || createdByIds.isEmpty()) return resultMap;

        List<String> idList = new ArrayList<>(createdByIds);
        String inSql = String.join(",", Collections.nCopies(idList.size(), "?"));

        try {
            String sql = "SELECT CAST(id AS VARCHAR(100)) as id, fullname FROM personnel WHERE CAST(id AS VARCHAR(100)) IN (" + inSql + ") AND isDeleted = 0";
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, idList.toArray());
            for (Map<String, Object> r : list) {
                if (r.get("id") != null && r.get("fullname") != null) {
                    resultMap.put(r.get("id").toString().trim(), r.get("fullname").toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    public static String removeExtra(String sInput, String regex) {
        while (sInput.matches(regex)) {
            sInput = sInput.split(regex)[1].trim();
            removeExtra(sInput, regex);
        }
        return sInput;
    }

    public JSONArray listKd(int kd_scope, String type) {
        JSONArray jsaKds = new JSONArray();
        String sScopeClause = kd_scope == -1 ? "" : " and e.loaihinh_id = ?";
        String sTypeClause = type == null ? ""
                : type.equals("cs") ? " where c.abbr = 'cs'"
                        : type.equals("ct") ? " where c.abbr <> 'cs'" : " where c.abbr = ?";

        String sql = "SELECT a.*, "
                + " f.ten as ten_ct, "
                + " d.ten AS loai_hinh, d.ID as loai_hinh_id, "
                + " e.id as standard_id, "
                + " e.ten as standard  "
                + " FROM TBL_Kiemdinh a "
                + " INNER JOIN TBL_STANDARD e on e.ID = a.standard_id  "
                + " INNER JOIN DEF_LOAIHINH_KD d ON d.ID = e.loaihinh_id  "
                + " INNER JOIN TBL_Nganh_daotao f on f.id = a.CT_ID  "
                + " WHERE  (a.IsDeleted IS NULL OR a.IsDeleted= 0) "
                + " and a.ID in (SELECT DISTINCT a.ID FROM [dbo].[TBL_Kiemdinh] a "
                + " INNER JOIN TBL_Nganh_daotao c on c.ID = a.CT_ID "
                + sTypeClause + " )"
                + sScopeClause
                + " order by ID asc";
        
        System.out.println("SQL: " + sql);

        List<Object> params = new ArrayList<>();
        if (type != null && !type.equals("cs") && !type.equals("ct")) {
            params.add(type);
        }
        if (kd_scope != -1) {
            params.add(kd_scope);
        }

        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, params.toArray());

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            JSONObject joKd = new JSONObject();
            int kd_id = (int) row.get("ID");
            joKd.put("id", kd_id);
            joKd.put("ten", row.get("ten"));
            joKd.put("ghi_chu", row.get("ghi_chu"));
            joKd.put("standard_id", row.get("standard_id"));
            joKd.put("standard", row.get("standard"));
            joKd.put("giaidoan", row.get("FromYear") + " - " + row.get("ToYear"));
            joKd.put("cycle", row.get("cycle"));
            joKd.put("cycle_id", row.get("cycle_id"));
            joKd.put("progs", ListCTsbyKD(kd_id));
            joKd.put("created_time", row.get("CreatedTime"));

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            joKd.put("creator", creatorNames.getOrDefault(cbStr, ""));

            joKd.put("is_current", row.get("IsCurrent"));
            joKd.put("loai_hinh", row.get("loai_hinh"));
            joKd.put("loai_hinh_id", row.get("loai_hinh_id"));
            joKd.put("ct_id", row.get("CT_ID"));
            joKd.put("ten_ct", row.get("ten_ct"));
            jsaKds.put(joKd);
        }
        return jsaKds;
    }

    public JSONObject kdInfo(int kd_id) {
        JSONObject joKd = new JSONObject();
        String sql = "SELECT a.*, "
                + " f.ten as ten_ct, "
                + " d.ten AS loai_hinh, d.ID as loai_hinh_id, "
                + " e.id as standard_id, "
                + " e.ten as standard  "
                + " FROM TBL_Kiemdinh a "
                + " INNER JOIN TBL_STANDARD e on e.ID = a.standard_id  "
                + " INNER JOIN DEF_LOAIHINH_KD d ON d.ID = e.loaihinh_id  "
                + " INNER JOIN TBL_Nganh_daotao f on f.id = a.CT_ID  "
                + " WHERE  (a.IsDeleted IS NULL OR a.IsDeleted= 0) "
                + " and a.ID = ?";
        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, kd_id);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            joKd.put("id", kd_id);
            joKd.put("ten", row.get("ten"));
            joKd.put("ghi_chu", row.get("ghi_chu"));
            joKd.put("standard_id", row.get("standard_id"));
            joKd.put("standard", row.get("standard"));
            joKd.put("giaidoan", row.get("FromYear") + " - " + row.get("ToYear"));
            joKd.put("cycle", row.get("cycle"));
            joKd.put("cycle_id", row.get("cycle_id"));
            joKd.put("created_time", row.get("CreatedTime"));

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            Set<String> s = new HashSet<>();
            if (!cbStr.isEmpty()) s.add(cbStr);
            Map<String, String> creatorNames = getCreatorNames(s);
            joKd.put("creator", creatorNames.getOrDefault(cbStr, ""));

            joKd.put("is_current", row.get("IsCurrent"));
            joKd.put("loai_hinh", row.get("loai_hinh"));
            joKd.put("loai_hinh_id", row.get("loai_hinh_id"));
            joKd.put("ct_id", row.get("CT_ID"));
            joKd.put("ten_ct", row.get("ten_ct"));
        }
        return joKd;
    }

    public JSONArray listKdbyCT(int kd_scope, int ct_id) {
        JSONArray jsaKds = new JSONArray();
        String sScopeClause = kd_scope == -1 ? "" : " and e.loaihinh_id = ?";
        String sCtClause = ct_id == -1 ? "" : " and a.CT_ID = ?";
        String sql = "SELECT a.ID, "
        		+ "a.ghi_chu, a.standard_id, a.FromYear, a.ToYear, a.cycle, a.CreatedTime, a.CreatedBy, "
        		+ "a.status, "
                + " b.ten as ten_ct, b.ID as ct_id, "
                + " d.ten AS loai_hinh, d.ID as loai_hinh_id, "
                + " e.ten as standard  "
                + " FROM TBL_Kiemdinh a "
                + " LEFT JOIN TBL_STANDARD e on e.ID = a.standard_id "
                + " LEFT JOIN DEF_LOAIHINH_KD d ON d.ID = e.loaihinh_id  "
                + " LEFT JOIN TBL_Nganh_daotao b on b.ID  = a.CT_ID "
                + " WHERE (a.IsDeleted IS NULL OR a.IsDeleted = 0) "
                + sCtClause
                + sScopeClause
                + " ORDER BY a.ID DESC";

        List<Object> params = new ArrayList<>();
        if (ct_id != -1) params.add(ct_id);
        if (kd_scope != -1) params.add(kd_scope);

        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, params.toArray());

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            JSONObject joKd = new JSONObject();
            joKd.put("id", row.get("ID"));
            joKd.put("ghi_chu", row.get("ghi_chu") != null ? row.get("ghi_chu") : "");
            joKd.put("standard_id", row.get("standard_id"));
            joKd.put("standard", row.get("standard") != null ? row.get("standard") : "");
            joKd.put("giaidoan", row.get("FromYear") + " - " + row.get("ToYear"));
            joKd.put("_from", row.get("FromYear"));
            joKd.put("_to", row.get("ToYear"));
            joKd.put("cycle", row.get("cycle"));
            joKd.put("created_time", row.get("CreatedTime") != null ? row.get("CreatedTime").toString() : "");

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            joKd.put("creator", creatorNames.getOrDefault(cbStr, ""));

            joKd.put("loai_hinh", row.get("loai_hinh") != null ? row.get("loai_hinh") : "");
            joKd.put("loai_hinh_id", row.get("loai_hinh_id") != null ? row.get("loai_hinh_id") : 0);
            joKd.put("ten_ct", row.get("ten_ct") != null ? row.get("ten_ct") : "");
            joKd.put("ct_id", row.get("ct_id") != null ? row.get("ct_id") : 0);
            joKd.put("status", row.get("status") != null ? row.get("status") : 0);
            jsaKds.put(joKd);
        }
        return jsaKds;
    }

    public JSONArray listCyclebyCT_ed(int kd_scope, String type, int ct_id) {
        JSONArray jsaKds = new JSONArray();
        String sScopeClause = kd_scope == -1 ? "" : " and e.loaihinh_id = ?";
        String sql = "SELECT a.*, "
                + " d.ten AS loai_hinh, d.ID as loai_hinh_id, "
                + " e.id as standard_id, "
                + " e.ten as standard  "
                + " FROM TBL_Kiemdinh a "
                + " INNER JOIN TBL_STANDARD e on e.ID = a.standard_id  "
                + " INNER JOIN DEF_LOAIHINH_KD d ON d.ID = e.loaihinh_id  "
                + " WHERE  (a.IsDeleted IS NULL OR a.IsDeleted= 0) "
                + " and a.CT_ID = ? "
                + sScopeClause;

        List<Object> params = new ArrayList<>();
        params.add(ct_id);
        if (kd_scope != -1) params.add(kd_scope);

        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, params.toArray());

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            int kd_id = (int) row.get("ID");
            String cycle = row.get("cycle") != null ? String.valueOf(row.get("cycle")) : "";

            JSONObject joKd = new JSONObject();
            joKd.put("id", kd_id);
            joKd.put("standard_id", row.get("standard_id"));
            joKd.put("standard", row.get("standard"));
            joKd.put("giaidoan", row.get("FromYear") + " - " + row.get("ToYear"));
            joKd.put("cycle", cycle);
            joKd.put("created_time", row.get("CreatedTime"));

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            joKd.put("creator", creatorNames.getOrDefault(cbStr, ""));

            joKd.put("loai_hinh", row.get("loai_hinh"));
            joKd.put("loai_hinh_id", row.get("loai_hinh_id"));

            boolean found = false;
            for (int i = 0; i < jsaKds.length(); i++) {
                JSONObject joCycl = jsaKds.getJSONObject(i);
                if (joCycl.getString("cycle").equals(cycle)) {
                    JSONArray jsK = joCycl.getJSONArray("kds");
                    jsK.put(joKd);
                    found = true;
                    break;
                }
            }
            if (!found) {
                JSONObject joCycl = new JSONObject();
                joCycl.put("cycle", cycle);
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
        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList(sql, kd_id);
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
                ? "SELECT a.ID, a.ten, a.abbr, a.ghi_chu, a.CreatedTime, a.CreatedBy FROM TBL_Nganh_daotao a WHERE (a.IsDeleted IS NULL OR a.IsDeleted = 0) ORDER BY a.ten ASC"
                : "SELECT a.ID, a.ten, a.abbr, a.ghi_chu, a.CreatedTime, a.CreatedBy FROM TBL_Nganh_daotao a INNER JOIN TBL_KIEMDINH_CT c ON c.CT_ID = a.ID WHERE (a.IsDeleted IS NULL OR a.IsDeleted = 0) AND (c.IsDeleted IS NULL OR c.IsDeleted = 0) AND c.KD_ID = ? ORDER BY a.ten ASC";

        List<Map<String, Object>> rows = (kd_id == -1) ? evidenceJdbcTemplate.queryForList(sql) : evidenceJdbcTemplate.queryForList(sql, kd_id);

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            JSONObject joCTKd = new JSONObject();
            joCTKd.put("id", row.get("ID"));
            joCTKd.put("ten", row.get("ten"));
            joCTKd.put("abbr", row.get("abbr"));
            joCTKd.put("ghi_chu", row.get("ghi_chu") != null ? row.get("ghi_chu") : "");
            joCTKd.put("created_time", row.get("CreatedTime") != null ? row.get("CreatedTime").toString() : "");

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            joCTKd.put("creator", creatorNames.getOrDefault(cbStr, ""));

            jsaCTKds.put(joCTKd);
        }
        return jsaCTKds;
    }

    public boolean isKdExisted(String ten) {
        String sql = "select 1 from TBL_Kiemdinh where ten = ? and (IsDeleted is null or IsDeleted=0)";
        List<Integer> list = evidenceJdbcTemplate.queryForList(sql, Integer.class, ten);
        return !list.isEmpty();
    }

    public int updateKd(int id, int loai_hinh_id, int FromYear, int ToYear, int cycle, int standard_id, int status, int created_by) {
        String sql = "update TBL_Kiemdinh set "
                + " loai_hinh_id = ?, "
                + " FromYear = ?, "
                + " ToYear = ?, "
                + " cycle = ?, "
                + " standard_id = ?, "
                + " status = ?, "
                + " CreatedTime = GETDATE(), CreatedBy = ? "
                + " where ID = ?";
        return evidenceJdbcTemplate.update(sql, loai_hinh_id, FromYear, ToYear, cycle, standard_id, status, created_by, id);
    }

    public int deleteKd(int id) {
        return evidenceJdbcTemplate.update("update TBL_Kiemdinh set IsDeleted = 1 where ID = ?", id);
    }

    public boolean isDeletable(int kd_id) {
        String sql = "SELECT kd_id FROM TBL_Ketqua where kd_id = ? and (IsDeleted is null or IsDeleted=0) "
                + " UNION ALL SELECT kd_id FROM TBL_Minhchung where kd_id = ? and (IsDeleted is null or IsDeleted=0) "
                + " UNION ALL SELECT kd_id FROM TBL_Tudanhgia where kd_id = ? and (IsDeleted is null or IsDeleted=0) "
                + " UNION ALL SELECT kd_id FROM TBL_Frame where kd_id = ? and (IsDeleted is null or IsDeleted=0) "
                + " UNION ALL SELECT kd_id FROM TBL_Vanbanden where kd_id = ? and (IsDeleted is null or IsDeleted=0) "
                + " UNION ALL SELECT kd_id FROM TBL_Vanbandi where kd_id = ? and (IsDeleted is null or IsDeleted=0)";
        List<Integer> rows = evidenceJdbcTemplate.queryForList(sql, Integer.class, kd_id, kd_id, kd_id, kd_id, kd_id, kd_id);
        return rows.isEmpty();
    }

    public void setCurrentKd(int kd_id, int createdby) {
        evidenceJdbcTemplate.update("UPDATE TBL_Kiemdinh SET ISCURRENT = CASE WHEN ID = ? THEN 1 ELSE 0 END, CreatedBy = ?", kd_id, createdby);
    }

    @Transactional
    public int addKd(String ten, int loai_hinh_id, int FromYear, int ToYear, int ct_id, int standard_id, String ghi_chu, int createdby) {
        try {
            // check current max cycle
            String sqlMaxCycle = "select cycle from TBL_KIEMDINH where loai_hinh_id = ? and CT_ID = ? and (IsDeleted is null or IsDeleted = 0)";
            List<Integer> cycles = evidenceJdbcTemplate.queryForList(sqlMaxCycle, Integer.class, loai_hinh_id, ct_id);
            int cycle = 0;
            for (Integer c : cycles) {
                if (c != null && c > cycle) {
                    cycle = c;
                }
            }
            cycle = cycle + 1;

            String sqlInsert = "insert into TBL_Kiemdinh (cycle, loai_hinh_id, FromYear, ToYear, CT_ID, standard_id, ghi_chu, CreatedTime, CreatedBy, IsDeleted) "
                    + " values(?, ?, ?, ?, ?, ?, ?, GETDATE(), ?, 0)";
            evidenceJdbcTemplate.update(sqlInsert, cycle, loai_hinh_id, FromYear, ToYear, ct_id, standard_id, ghi_chu, createdby);
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public int createStandard(String ten, String abbr, int loaihinh_id, int createdby) {
        return evidenceJdbcTemplate.update("insert into TBL_Standard (ten, abbr, loaihinh_id, CreatedTime, CreatedBy, IsDeleted) values(?, ?, ?, GETDATE(), ?, 0)",
                ten, abbr, loaihinh_id, createdby);
    }

    public JSONArray listLoaihinh() {
        JSONArray jsaLoaihinhs = new JSONArray();
        List<Map<String, Object>> rows = evidenceJdbcTemplate.queryForList("SELECT id, ten FROM DEF_LOAIHINH_KD");
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
        String sql = "SELECT a.*, c.id as loaihinh_id, c.ten as loaihinh FROM TBL_Standard a "
                + " INNER JOIN DEF_LOAIHINH_KD c on c.id = a.loaihinh_id "
                + " where (a.IsDeleted is null or a.IsDeleted=0) "
                + sLoaihinh_clause;
        List<Map<String, Object>> rows = (loaihinh_id == -1) ? evidenceJdbcTemplate.queryForList(sql) : evidenceJdbcTemplate.queryForList(sql, loaihinh_id);

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            JSONObject joStandard = new JSONObject();
            joStandard.put("id", row.get("ID"));
            joStandard.put("ten", row.get("ten"));
            joStandard.put("abbr", row.get("abbr"));
            joStandard.put("loaihinh_id", row.get("loaihinh_id"));
            joStandard.put("loaihinh", row.get("loaihinh"));
            joStandard.put("created_time", row.get("CreatedTime"));

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            joStandard.put("creator", creatorNames.getOrDefault(cbStr, ""));

            jsaStandards.put(joStandard);
        }
        return jsaStandards;
    }

    public int updateStandard(int id, String ten, String abbr, int created_by) {
        return evidenceJdbcTemplate.update("update TBL_Standard set ten = ?, abbr = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?",
                ten, abbr, created_by, id);
    }

    public int deleteStandard(int id) {
        return evidenceJdbcTemplate.update("update TBL_Standard set IsDeleted = 1 where ID = ?", id);
    }

    public int createCycle(String ten, int standard_id, int _from, int _to, int createdby) {
        return evidenceJdbcTemplate.update("insert into TBL_Cycle (ten, StandardID, FromYear, ToYear, CreatedTime, CreatedBy, IsDeleted) values(?, ?, ?, ?, GETDATE(), ?, 0)",
                ten, standard_id, _from, _to, createdby);
    }

    public JSONArray listCycleByStandard(int standard_id) {
        JSONArray jsaCycles = new JSONArray();
        String sStandard_clause = standard_id < 0 ? "" : " and StandardID = ?";
        String sql = "SELECT a.*, c.ten as standard, c.ID as standard_id, d.ten as loaihinh, d.id as loaihinh_id "
                + " FROM TBL_Cycle a "
                + " INNER JOIN TBL_STANDARD c on c.id = a.StandardID "
                + " INNER JOIN DEF_LOAIHINH_KD d on d.id = c.loaihinh_id "
                + " where (a.IsDeleted is null or a.IsDeleted=0)"
                + sStandard_clause;
        List<Map<String, Object>> rows = (standard_id < 0) ? evidenceJdbcTemplate.queryForList(sql) : evidenceJdbcTemplate.queryForList(sql, standard_id);

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            JSONObject joCycle = new JSONObject();
            joCycle.put("id", row.get("ID"));
            joCycle.put("ten", row.get("ten"));
            joCycle.put("giaidoan", row.get("FromYear") + " - " + row.get("ToYear"));
            joCycle.put("created_time", row.get("CreatedTime"));

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            joCycle.put("creator", creatorNames.getOrDefault(cbStr, ""));

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
        String sql = "SELECT a.*, c.ten as standard, c.ID as standard_id, d.ten as loaihinh, d.id as loaihinh_id "
                + " FROM TBL_Cycle a "
                + " INNER JOIN TBL_STANDARD c on c.id = a.StandardID "
                + " INNER JOIN DEF_LOAIHINH_KD d on d.id = c.loaihinh_id "
                + " where (a.IsDeleted is null or a.IsDeleted=0)"
                + sLoaihinh_clause;
        List<Map<String, Object>> rows = (loaihinh_id < 0) ? evidenceJdbcTemplate.queryForList(sql) : evidenceJdbcTemplate.queryForList(sql, loaihinh_id);

        Set<String> creatorIds = new HashSet<>();
        for (Map<String, Object> r : rows) {
            if (r.get("CreatedBy") != null) creatorIds.add(r.get("CreatedBy").toString().trim());
        }
        Map<String, String> creatorNames = getCreatorNames(creatorIds);

        for (Map<String, Object> row : rows) {
            JSONObject joCycle = new JSONObject();
            joCycle.put("id", row.get("ID"));
            joCycle.put("ten", row.get("ten"));
            joCycle.put("giaidoan", row.get("FromYear") + " - " + row.get("ToYear"));
            joCycle.put("created_time", row.get("CreatedTime"));

            String cbStr = row.get("CreatedBy") != null ? row.get("CreatedBy").toString().trim() : "";
            joCycle.put("creator", creatorNames.getOrDefault(cbStr, ""));

            joCycle.put("standard_id", row.get("StandardID"));
            joCycle.put("standard", row.get("standard"));
            joCycle.put("loaihinh", row.get("loaihinh"));
            joCycle.put("loaihinh_id", row.get("loaihinh_id"));
            jsaCycles.put(joCycle);
        }
        return jsaCycles;
    }

    public int updateCycle(int id, String ten, int _from, int _to, int created_by) {
        return evidenceJdbcTemplate.update("update TBL_Cycle set ten = ?, FromYear = ?, ToYear = ?, CreatedTime = GETDATE(), CreatedBy = ? where ID = ?",
                ten, _from, _to, created_by, id);
    }

    public int deleteCycle(int id) {
        return evidenceJdbcTemplate.update("update TBL_Cycle set IsDeleted = 1 where ID = ?", id);
    }
}
