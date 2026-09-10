package com.qaex;

import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/qa-ex/roles")
@CrossOrigin(originPatterns = "*", maxAge = 3600, allowCredentials = "true")
public class QaExRoleController {

    private static final Logger log = LoggerFactory.getLogger(QaExRoleController.class);

    @Autowired(required = false)
    @Qualifier("evidenceJdbcTemplate")
    private JdbcTemplate evidenceJdbcTemplate;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private JdbcTemplate getJdbc() {
        return evidenceJdbcTemplate != null ? evidenceJdbcTemplate : jdbcTemplate;
    }

    /**
     * Automatically create database tables and seed default roles on application startup.
     */
	/*    @PostConstruct
	public void autoInitSchema() {
	    java.util.concurrent.CompletableFuture.runAsync(() -> {
	        try {
	            // Wait briefly for DataSource initialization if needed
	            Thread.sleep(2000);
	            JdbcTemplate jdbc = getJdbc();
	            if (jdbc == null) {
	                log.warn("[QA-EX Roles] JdbcTemplate not ready yet.");
	                return;
	            }
	
	            // 1. Create dbo.qa_ex_roles
	            String sqlRoles =
	                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_roles') AND type = 'U') " +
	                "BEGIN " +
	                "    CREATE TABLE dbo.qa_ex_roles ( " +
	                "        role_id             INT IDENTITY(1,1) PRIMARY KEY, " +
	                "        role_code           VARCHAR(50)   NOT NULL, " +
	                "        role_name           NVARCHAR(150) NOT NULL, " +
	                "        description         NVARCHAR(500) NULL, " +
	                "        is_active           BIT           NOT NULL DEFAULT 1, " +
	                "        created_at          DATETIME2     NOT NULL DEFAULT GETDATE(), " +
	                "        updated_at          DATETIME2     NOT NULL DEFAULT GETDATE() " +
	                "    ); " +
	                "    CREATE UNIQUE NONCLUSTERED INDEX UQ_qa_ex_roles_code ON dbo.qa_ex_roles (role_code); " +
	                "END";
	            jdbc.execute(sqlRoles);
	
	            // 2. Create dbo.qa_ex_user_roles (linking to personnel.id)
	            String sqlUserRoles =
	                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_user_roles') AND type = 'U') " +
	                "BEGIN " +
	                "    CREATE TABLE dbo.qa_ex_user_roles ( " +
	                "        user_role_id        INT IDENTITY(1,1) PRIMARY KEY, " +
	                "        user_id             VARCHAR(100)  NOT NULL, " +
	                "        role_id             INT           NOT NULL, " +
	                "        is_active           BIT           NOT NULL DEFAULT 1, " +
	                "        assigned_at         DATETIME2     NOT NULL DEFAULT GETDATE(), " +
	                "        assigned_by         VARCHAR(100)  NULL, " +
	                "        CONSTRAINT UQ_qa_ex_user_roles_user_role UNIQUE (user_id, role_id), " +
	                "        CONSTRAINT FK_qa_ex_user_roles_role FOREIGN KEY (role_id) REFERENCES dbo.qa_ex_roles (role_id) ON DELETE CASCADE " +
	                "    ); " +
	                "    CREATE NONCLUSTERED INDEX IX_qa_ex_user_roles_user_id ON dbo.qa_ex_user_roles (user_id) WHERE is_active = 1; " +
	                "    CREATE NONCLUSTERED INDEX IX_qa_ex_user_roles_role_id ON dbo.qa_ex_user_roles (role_id); " +
	                "END";
	            jdbc.execute(sqlUserRoles);
	
	            // 3. Seed default role definitions
	            String seedRolesSql =
	                "IF NOT EXISTS (SELECT 1 FROM dbo.qa_ex_roles WHERE role_code = 'EX_ADMIN') " +
	                "BEGIN " +
	                "    INSERT INTO dbo.qa_ex_roles (role_code, role_name, description) VALUES " +
	                "    ('EX_ADMIN',     N'Quản trị viên QA-EX',        N'Toàn quyền quản trị hệ thống Đánh giá ngoài, phân quyền và cấu hình tiêu chuẩn.'), " +
	                "    ('EX_LEADER',    N'Trưởng đoàn ĐGN',           N'Phụ trách chỉ đạo đoàn Đánh giá ngoài, phê duyệt báo cáo và quyết định cổng sẵn sàng.'), " +
	                "    ('EX_EVALUATOR', N'Thành viên Đoàn ĐGN',       N'Thực hiện thẩm định minh chứng, chấm điểm tiêu chuẩn và tham gia phỏng vấn.'), " +
	                "    ('EX_SECRETARY', N'Thư ký Đoàn ĐGN',           N'Ghi nhận nhật ký vận hành, tổng hợp báo cáo DSR và theo dõi yêu cầu bằng chứng.'), " +
	                "    ('EX_MONITOR',   N'Cán bộ Giám sát E-IQA',     N'Theo dõi tiến độ vận hành Onsite, bảng điều hành OT-02 và cảnh báo leo thang.'); " +
	                "END";
	            jdbc.execute(seedRolesSql);
	
	            log.info("[QA-EX Roles] Database tables dbo.qa_ex_roles and dbo.qa_ex_user_roles initialized and verified successfully.");
	        } catch (Exception e) {
	            log.error("[QA-EX Roles] Notice initializing tables: ", e);
	        }
	    });
	}
	*/
    /**
     * GET /api/qa-ex/roles/definitions
     * Retrieve all active role definitions for QA-EX.
     */
    @GetMapping(value = "/definitions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getRoleDefinitions() {
        JSONObject res = new JSONObject();
        try {
            String sql = "SELECT role_id, role_code, role_name, description, is_active, created_at FROM dbo.qa_ex_roles WHERE is_active = 1 ORDER BY role_id ASC";
            List<Map<String, Object>> roles = getJdbc().queryForList(sql);
            res.put("status", "SUCCESS");
            res.put("data", roles);
            res.put("total", roles.size());
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error retrieving QA-EX role definitions: ", e);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * GET /api/qa-ex/roles/user/{userId}
     * Retrieve assigned QA-EX roles for a specific user (userId matching personnel.id).
     */
    @GetMapping(value = "/user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getUserRoles(@PathVariable("userId") String userId) {
        JSONObject res = new JSONObject();
        try {
            String sql = "SELECT ur.user_role_id, ur.user_id, r.role_id, r.role_code, r.role_name, r.description, ur.assigned_at, ur.assigned_by " +
                         "FROM dbo.qa_ex_user_roles ur " +
                         "JOIN dbo.qa_ex_roles r ON ur.role_id = r.role_id " +
                         "WHERE ur.user_id = ? AND ur.is_active = 1 AND r.is_active = 1";
            List<Map<String, Object>> roles = getJdbc().queryForList(sql, userId);
            res.put("status", "SUCCESS");
            res.put("userId", userId);
            res.put("roles", roles);
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error fetching user roles for userId=" + userId + ": ", e);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * GET /api/qa-ex/roles/user-assignments
     * List all QA-EX user role assignments with personnel details (fullname, maCanBo, email, donViName).
     */
    @GetMapping(value = "/user-assignments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAllUserRoleAssignments() {
        JSONObject res = new JSONObject();
        try {
            String sql = "SELECT ur.user_role_id, ur.user_id, p.fullname, p.maCanBo, p.emailCanBo, p.tenDonViChinh, " +
                         "r.role_id, r.role_code, r.role_name, ur.assigned_at " +
                         "FROM dbo.qa_ex_user_roles ur " +
                         "JOIN dbo.qa_ex_roles r ON ur.role_id = r.role_id " +
                         "LEFT JOIN dbo.personnel p ON ur.user_id = CAST(p.id AS VARCHAR(100)) AND p.isDeleted = 0 " +
                         "WHERE ur.is_active = 1 AND r.is_active = 1 " +
                         "ORDER BY ur.assigned_at DESC";
            List<Map<String, Object>> assignments = getJdbc().queryForList(sql);
            res.put("status", "SUCCESS");
            res.put("data", assignments);
            res.put("total", assignments.size());
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error fetching all QA-EX user role assignments: ", e);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * POST /api/qa-ex/roles/assign
     * Assign a QA-EX role to a personnel user.
     * Request body JSON: { "userId": "123", "roleId": 1, "assignedBy": "ADMIN" }
     */
    @PostMapping(value = "/assign", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> assignRoleToUser(@RequestBody String requestBody) {
        JSONObject res = new JSONObject();
        try {
            JSONObject body = new JSONObject(requestBody);
            String userId = body.getString("userId");
            int roleId = body.getInt("roleId");
            String assignedBy = body.optString("assignedBy", "SYSTEM");

            // Check if assignment already exists
            String checkSql = "SELECT COUNT(*) FROM dbo.qa_ex_user_roles WHERE user_id = ? AND role_id = ? AND is_active = 1";
            Integer count = getJdbc().queryForObject(checkSql, Integer.class, userId, roleId);

            if (count != null && count > 0) {
                res.put("status", "SUCCESS");
                res.put("message", "User already has this role assigned.");
                return ResponseEntity.ok(res.toString());
            }

            // Insert role assignment
            String insertSql = "INSERT INTO dbo.qa_ex_user_roles (user_id, role_id, is_active, assigned_by) VALUES (?, ?, 1, ?)";
            getJdbc().update(insertSql, userId, roleId, assignedBy);

            res.put("status", "SUCCESS");
            res.put("message", "Role assigned successfully.");
            res.put("userId", userId);
            res.put("roleId", roleId);
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error assigning QA-EX role: ", e);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }

    /**
     * POST /api/qa-ex/roles/revoke
     * Revoke a QA-EX role from a personnel user.
     * Request body JSON: { "userId": "123", "roleId": 1 }
     */
    @PostMapping(value = "/revoke", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> revokeRoleFromUser(@RequestBody String requestBody) {
        JSONObject res = new JSONObject();
        try {
            JSONObject body = new JSONObject(requestBody);
            String userId = body.getString("userId");
            int roleId = body.getInt("roleId");

            String updateSql = "UPDATE dbo.qa_ex_user_roles SET is_active = 0 WHERE user_id = ? AND role_id = ?";
            int updated = getJdbc().update(updateSql, userId, roleId);

            res.put("status", "SUCCESS");
            res.put("message", updated > 0 ? "Role revoked successfully." : "No active role assignment found.");
            return ResponseEntity.ok(res.toString());
        } catch (Exception e) {
            log.error("Error revoking QA-EX role: ", e);
            res.put("status", "ERROR");
            res.put("message", e.getMessage());
            return ResponseEntity.status(500).body(res.toString());
        }
    }
}
