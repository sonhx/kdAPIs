package com.raci;

import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RaciAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(RaciAssignmentService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initTable() {
        try {
            log.info("Initializing 'raci_assignments' database table...");
            String sql = 
                "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'raci_assignments') " +
                "BEGIN " +
                "    CREATE TABLE raci_assignments ( " +
                "        id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                "        deptId VARCHAR(100) NOT NULL, " +
                "        compId VARCHAR(50) NOT NULL, " +
                "        namHoc VARCHAR(20) NOT NULL DEFAULT '2025-2026', " +
                "        isR BIT NOT NULL DEFAULT 0, " +
                "        isA BIT NOT NULL DEFAULT 0, " +
                "        isC BIT NOT NULL DEFAULT 0, " +
                "        isI BIT NOT NULL DEFAULT 0, " +
                "        slaDays INT NOT NULL DEFAULT 15, " +
                "        assignee NVARCHAR(250) NULL, " +
                "        createdAt DATETIME2 DEFAULT GETDATE(), " +
                "        updatedAt DATETIME2 DEFAULT GETDATE(), " +
                "        CONSTRAINT UQ_raci_dept_comp_year UNIQUE (deptId, compId, namHoc) " +
                "    ); " +
                "    CREATE INDEX IX_raci_deptId ON raci_assignments(deptId); " +
                "    CREATE INDEX IX_raci_compId ON raci_assignments(compId); " +
                "END";
            jdbcTemplate.execute(sql);
            log.info("'raci_assignments' table initialized successfully.");
            seedDefaultAssignments();
        } catch (Exception e) {
            log.error("Error initializing 'raci_assignments' table", e);
        }
    }

    private Object toNVarChar(String val) {
        return val == null ? null : new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NVARCHAR, val);
    }

    /**
     * Seed initial RACI assignments.
     */
    public void seedDefaultAssignments() {
        seedDefaultAssignments(false);
    }

    public void seedDefaultAssignments(boolean forceReset) {
        try {
            if (!forceReset) {
                Integer existingCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raci_assignments", Integer.class);
                if (existingCount != null && existingCount > 0) {
                    log.info("raci_assignments table already contains {} records. Preserving user custom RACI assignments.", existingCount);
                    return;
                }
            }

            log.info("Populating initial RACI matrix defaults into database table...");
            jdbcTemplate.execute("DELETE FROM raci_assignments");

            String[][] defaults = {
                {"HDHV", "C4", "0", "1", "1", "1", "7", "Chủ tịch Hội đồng Học viện"},
                {"HDHV", "C5", "0", "1", "1", "1", "10", "Chủ tịch Hội đồng Học viện"},
                    {"BGD1", "C4", "0", "1", "0", "1", "7", "Giám đốc Học viện"},
                    {"BGD1", "C5", "0", "1", "0", "1", "10", "Giám đốc Học viện"},
                    {"VPH1", "C4", "0", "0", "1", "1", "7", "Chánh Văn phòng"},
                    {"VPH1", "C5", "0", "0", "1", "1", "15", "Chánh Văn phòng"},
                    {"TKT1", "C4", "1", "1", "1", "1", "5", "Trưởng phòng KT&ĐBCL"},
                    {"TKT1", "C5", "1", "1", "1", "1", "7", "Trưởng phòng KT&ĐBCL"},
                    {"PDT1", "C4", "1", "0", "1", "0", "7", "Trưởng phòng Đào tạo"},
                    {"PDT1", "C5", "1", "0", "1", "0", "14", "Trưởng phòng Đào tạo"},
                    {"PTC1", "C4", "1", "0", "1", "0", "7", "Trưởng phòng TCCB"},
                    {"PTC1", "C5", "1", "0", "1", "0", "14", "Trưởng phòng TCCB"},
                    {"PQL1", "C4", "1", "0", "1", "0", "7", "Trưởng phòng QLKH"},
                    {"PQL1", "C5", "1", "0", "1", "0", "14", "Trưởng phòng QLKH"},
                    {"PKH1", "C4", "1", "0", "1", "0", "7", "Trưởng phòng KH-ĐT"},
                    {"PKH1", "C5", "1", "0", "1", "0", "14", "Trưởng phòng KH-ĐT"},
                    {"PKT1", "C4", "1", "0", "1", "0", "7", "Trưởng phòng TCKT"},
                    {"PKT1", "C5", "0", "0", "1", "1", "14", "Trưởng phòng TCKT"},
                    {"PSV1", "C4", "1", "0", "1", "0", "7", "Trưởng phòng CT&CTSV"},
                    {"PSV1", "C5", "1", "0", "1", "0", "14", "Trưởng phòng CT&CTSV"},
                    {"PGV1", "C4", "1", "0", "1", "0", "7", "Trưởng phòng Giáo vụ"},
                    {"PGV1", "C5", "0", "0", "1", "1", "14", "Trưởng phòng Giáo vụ"},
                    {"KAT1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa ATTT"},
                    {"KAT1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa ATTT"},
                    {"KCB1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa Cơ bản 1"},
                    {"KCB1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa Cơ bản 1"},
                    {"KCN1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa CNTT 1"},
                    {"KCN1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa CNTT 1"},
                    {"KDP1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa ĐPT"},
                    {"KDP1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa ĐPT"},
                    {"KSD1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa SĐH"},
                    {"KSD1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa SĐH"},
                    {"KDT1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa KTĐỆ 1"},
                    {"KDT1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa KTĐỆ 1"},
                    {"KQT1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa QTKD 1"},
                    {"KQT1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa QTKD 1"},
                    {"KTC1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa TCKT 1"},
                    {"KTC1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa TCKT 1"},
                    {"KTN1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa TTNT"},
                    {"KTN1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa TTNT"},
                    {"KVT1", "C4", "1", "0", "1", "0", "7", "Trưởng Khoa Viễn thông 1"},
                    {"KVT1", "C5", "1", "0", "1", "0", "14", "Trưởng Khoa Viễn thông 1"},
                    {"TDT1", "C4", "0", "0", "1", "1", "7", "Giám đốc TT ĐT BCVT"},
                    {"TDT1", "C5", "0", "0", "1", "1", "14", "Giám đốc TT ĐT BCVT"},
                    {"TQT1", "C4", "0", "0", "1", "1", "7", "Giám đốc TT ĐT Quốc tế"},
                    {"TQT1", "C5", "0", "0", "1", "1", "14", "Giám đốc TT ĐT Quốc tế"},
                    {"TDV1", "C4", "0", "0", "1", "1", "7", "Giám đốc TT Dịch vụ"},
                    {"TDV1", "C5", "0", "0", "1", "1", "14", "Giám đốc TT Dịch vụ"},
                    {"TDM1", "C4", "0", "0", "1", "1", "7", "Giám đốc TT ĐMST&KN"},
                    {"TDM1", "C5", "0", "0", "1", "1", "14", "Giám đốc TT ĐMST&KN"},
                    {"TTN1", "C4", "0", "0", "1", "1", "7", "Giám đốc TT TN-TH"},
                    {"TTN1", "C5", "0", "0", "1", "1", "14", "Giám đốc TT TN-TH"},
                    {"VCN1", "C4", "1", "0", "1", "0", "7", "Viện trưởng Viện CNTT&TT"},
                    {"VCN1", "C5", "1", "0", "1", "0", "14", "Viện trưởng Viện CNTT&TT"},
                    {"VKH1", "C4", "1", "0", "1", "0", "7", "Viện trưởng Viện KHKT Bưu điện"},
                    {"VKH1", "C5", "1", "0", "1", "0", "14", "Viện trưởng Viện KHKT Bưu điện"},
                    {"VKT1", "C4", "1", "0", "1", "0", "7", "Viện trưởng Viện Kinh tế Bưu điện"},
                    {"VKT1", "C5", "1", "0", "1", "0", "14", "Viện trưởng Viện Kinh tế Bưu điện"},
                    {"VLD1", "C4", "1", "0", "1", "0", "7", "Viện trưởng Viện LĐQT&QL"},
                    {"VLD1", "C5", "1", "0", "1", "0", "14", "Viện trưởng Viện LĐQT&QL"}
                };

                for (String[] row : defaults) {
                    upsertAssignment(row[0], row[1], "2025-2026", 
                        "1".equals(row[2]), "1".equals(row[3]), "1".equals(row[4]), "1".equals(row[5]), 
                        Integer.parseInt(row[6]), row[7]);
                }
                log.info("Successfully seeded initial RACI matrix assignments.");
        } catch (Exception e) {
            log.error("Error seeding default RACI assignments", e);
        }
    }

    /**
     * Get list of RACI assignments for a given academic year.
     */
    public List<Map<String, Object>> getAssignments(String namHoc) {
        if (namHoc == null || namHoc.isBlank()) {
            namHoc = "2025-2026";
        }
        String sql = "SELECT id, deptId, compId, namHoc, isR, isA, isC, isI, slaDays, assignee, createdAt, updatedAt " +
                     "FROM raci_assignments WHERE namHoc = ? ORDER BY deptId ASC, compId ASC";
        return jdbcTemplate.queryForList(sql, namHoc);
    }

    /**
     * Upsert a single RACI assignment cell.
     */
    public JSONObject upsertAssignment(String deptId, String compId, String namHoc, 
                                         boolean isR, boolean isA, boolean isC, boolean isI, 
                                         int slaDays, String assignee) {
        JSONObject res = new JSONObject();
        try {
            if (namHoc == null || namHoc.isBlank()) namHoc = "2025-2026";
            if (deptId == null || deptId.isBlank() || compId == null || compId.isBlank()) {
                res.put("status", "ERROR");
                res.put("message", "deptId và compId không được để trống.");
                return res;
            }

            String checkSql = "SELECT COUNT(*) FROM raci_assignments WHERE deptId = ? AND compId = ? AND namHoc = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, deptId, compId, namHoc);

            if (count != null && count > 0) {
                String updateSql = "UPDATE raci_assignments SET isR = ?, isA = ?, isC = ?, isI = ?, " +
                                   "slaDays = ?, assignee = ?, updatedAt = GETDATE() " +
                                   "WHERE deptId = ? AND compId = ? AND namHoc = ?";
                jdbcTemplate.update(updateSql, isR ? 1 : 0, isA ? 1 : 0, isC ? 1 : 0, isI ? 1 : 0, 
                                    slaDays, toNVarChar(assignee), deptId, compId, namHoc);
            } else {
                String insertSql = "INSERT INTO raci_assignments (deptId, compId, namHoc, isR, isA, isC, isI, slaDays, assignee, createdAt, updatedAt) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), GETDATE())";
                jdbcTemplate.update(insertSql, deptId, compId, namHoc, isR ? 1 : 0, isA ? 1 : 0, isC ? 1 : 0, isI ? 1 : 0, slaDays, toNVarChar(assignee));
            }

            res.put("status", "SUCCESS");
            res.put("message", "Cập nhật phân công RACI thành công.");
            return res;
        } catch (Exception e) {
            log.error("Error upserting RACI assignment", e);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return res;
        }
    }

    /**
     * Batch upsert list of RACI assignments.
     */
    public JSONObject batchUpsert(JSONArray items, String namHoc) {
        JSONObject res = new JSONObject();
        int countSuccess = 0;
        try {
            if (namHoc == null || namHoc.isBlank()) namHoc = "2025-2026";
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String deptId = item.optString("deptId");
                    String compId = item.optString("compId");
                    boolean r = item.optBoolean("isR", item.optBoolean("r", false));
                    boolean a = item.optBoolean("isA", item.optBoolean("a", false));
                    boolean c = item.optBoolean("isC", item.optBoolean("c", false));
                    boolean isI = item.optBoolean("isI", item.optBoolean("i", false));
                    int slaDays = item.optInt("slaDays", item.optInt("sla", 15));
                    String assignee = item.optString("assignee", "");

                    JSONObject single = upsertAssignment(deptId, compId, namHoc, r, a, c, isI, slaDays, assignee);
                    if ("SUCCESS".equalsIgnoreCase(single.optString("status"))) {
                        countSuccess++;
                    }
                }
            }
            res.put("status", "SUCCESS");
            res.put("message", "Đã lưu " + countSuccess + " bản ghi phân công RACI.");
            res.put("count", countSuccess);
            return res;
        } catch (Exception e) {
            log.error("Error batch upserting RACI assignments", e);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return res;
        }
    }
}
