package com.capa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import com.capa.dto.CapaDto;
import com.capa.dto.CapaActionDto;
import com.capa.dto.CapaStatsDto;

/**
 * CapaExtend — Data Access Layer for the CAPA Module.
 * Connects directly to primary jdbcTemplate (IQA database).
 */
@Component
public class CapaExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        initOnTemplate(jdbcTemplate, "IQA Database");
    }

    private void initOnTemplate(JdbcTemplate template, String dbName) {
        if (template == null) return;
        try {
            // 1. dbo.capa
            String sqlCapa =
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.capa') AND type = 'U') " +
                "BEGIN " +
                "    CREATE TABLE dbo.capa ( " +
                "        capa_id             INT IDENTITY(1,1) PRIMARY KEY, " +
                "        capa_code           NVARCHAR(30)  NOT NULL, " +
                "        title               NVARCHAR(500) NOT NULL, " +
                "        description         NVARCHAR(MAX) NULL, " +
                "        capa_type           NVARCHAR(30)  NOT NULL DEFAULT N'Khắc phục', " +
                "        status              NVARCHAR(30)  NOT NULL DEFAULT 'processing', " +
                "        department_id       NVARCHAR(50)  NULL, " +
                "        department_name     NVARCHAR(200) NOT NULL, " +
                "        open_date           DATE          NOT NULL DEFAULT CAST(GETDATE() AS DATE), " +
                "        due_date            DATE          NOT NULL, " +
                "        completed_date      DATE          NULL, " +
                "        effectiveness       NVARCHAR(50)  NOT NULL DEFAULT N'Đang đánh giá', " +
                "        feedback            NVARCHAR(MAX) NULL, " +
                "        priority            NVARCHAR(20)  NOT NULL DEFAULT 'Medium', " +
                "        source              NVARCHAR(100) NULL, " +
                "        source_ref          NVARCHAR(200) NULL, " +
                "        root_cause          NVARCHAR(MAX) NULL, " +
                "        action_plan         NVARCHAR(MAX) NULL, " +
                "        assigned_to         NVARCHAR(50)  NULL, " +
                "        assigned_to_name    NVARCHAR(200) NULL, " +
                "        created_by          NVARCHAR(50)  NULL, " +
                "        verified_by         NVARCHAR(50)  NULL, " +
                "        verified_date       DATE          NULL, " +
                "        is_deleted          BIT           NOT NULL DEFAULT 0, " +
                "        created_at          DATETIME2     NOT NULL DEFAULT GETDATE(), " +
                "        updated_at          DATETIME2     NOT NULL DEFAULT GETDATE() " +
                "    ); " +
                "END";
            template.execute(sqlCapa);

            // 2. dbo.capa_actions
            String sqlActions =
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.capa_actions') AND type = 'U') " +
                "BEGIN " +
                "    CREATE TABLE dbo.capa_actions ( " +
                "        action_id           INT IDENTITY(1,1) PRIMARY KEY, " +
                "        capa_id             INT           NOT NULL, " +
                "        action_description  NVARCHAR(MAX) NOT NULL, " +
                "        assigned_to         NVARCHAR(50)  NULL, " +
                "        assigned_to_name    NVARCHAR(200) NULL, " +
                "        due_date            DATE          NULL, " +
                "        completed_date      DATE          NULL, " +
                "        status              NVARCHAR(20)  NOT NULL DEFAULT 'Pending', " +
                "        sort_order          INT           NOT NULL DEFAULT 99, " +
                "        notes               NVARCHAR(MAX) NULL, " +
                "        is_deleted          BIT           NOT NULL DEFAULT 0, " +
                "        created_at          DATETIME2     NOT NULL DEFAULT GETDATE(), " +
                "        updated_at          DATETIME2     NOT NULL DEFAULT GETDATE(), " +
                "        CONSTRAINT FK_capa_actions_capa FOREIGN KEY (capa_id) REFERENCES dbo.capa (capa_id) ON DELETE CASCADE " +
                "    ); " +
                "END";
            template.execute(sqlActions);

            // 3. dbo.capa_history
            String sqlHistory =
                "IF NOT EXISTS (SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.capa_history') AND type = 'U') " +
                "BEGIN " +
                "    CREATE TABLE dbo.capa_history ( " +
                "        history_id          INT IDENTITY(1,1) PRIMARY KEY, " +
                "        capa_id             INT           NOT NULL, " +
                "        previous_status     NVARCHAR(30)  NULL, " +
                "        new_status          NVARCHAR(30)  NOT NULL, " +
                "        changed_by          NVARCHAR(50)  NULL, " +
                "        feedback_comment    NVARCHAR(MAX) NULL, " +
                "        changed_at          DATETIME2     NOT NULL DEFAULT GETDATE(), " +
                "        CONSTRAINT FK_capa_history_capa FOREIGN KEY (capa_id) REFERENCES dbo.capa (capa_id) ON DELETE CASCADE " +
                "    ); " +
                "END";
            template.execute(sqlHistory);

            // 4. Initial Seed Data
            String seedSql =
                "IF NOT EXISTS (SELECT 1 FROM dbo.capa WHERE capa_code = 'CAPA-26-001') " +
                "BEGIN " +
                "    INSERT INTO dbo.capa (capa_code, title, department_name, capa_type, status, open_date, due_date, completed_date, effectiveness, description, feedback) VALUES " +
                "    (N'CAPA-26-001', N'Khắc phục lỗi nghẽn cổng đăng ký học phần', N'Khoa Công nghệ thông tin', N'Khắc phục', 'processing', '2026-02-15', '2026-03-15', NULL, N'Đang đánh giá', N'Nâng cấp băng thông máy chủ và tối ưu hóa các chỉ mục cơ sở dữ liệu đăng ký môn học trực tuyến.', NULL), " +
                "    (N'CAPA-26-002', N'Cập nhật tài liệu thực hành mạng viễn thông thế hệ mới', N'Khoa Viễn thông', N'Phòng ngừa', 'closed', '2026-01-10', '2026-02-28', '2026-02-25', N'Đạt', N'Bổ sung các bài Lab mô phỏng mạng SDN/NFV vào chương trình đào tạo để chuẩn bị cho đợt kiểm định.', NULL), " +
                "    (N'CAPA-26-003', N'Rà soát quy trình in sao đề thi hết môn học kỳ 1', N'Phòng Khảo thí', N'Khắc phục', 'pending_closure', '2026-05-12', '2026-06-15', NULL, N'Đang đánh giá', N'Điều chỉnh quy trình giám sát chéo giữa các cán bộ in sao đề thi để tránh sai sót nội dung.', NULL), " +
                "    (N'CAPA-26-004', N'Sửa chữa thiết bị đo dao động phòng thí nghiệm tầng 4', N'Khoa Điện tử', N'Khắc phục', 'processing', '2026-03-01', '2026-04-15', NULL, N'Đang đánh giá', N'Hiệu chuẩn lại 5 thiết bị đo dao động ký bị lệch tín hiệu chuẩn sau học kỳ thực hành.', NULL), " +
                "    (N'CAPA-26-005', N'Tổ chức khảo sát doanh nghiệp về nhu cầu nhân lực logistics', N'Khoa Quản trị kinh doanh', N'Phòng ngừa', 'closed', '2026-01-05', '2026-03-01', '2026-02-28', N'Đạt', N'Thu thập ý kiến đóng góp từ 30 doanh nghiệp đối tác để hiệu chỉnh chương trình đào tạo logistics.', NULL), " +
                "    (N'CAPA-26-006', N'Bổ sung giáo trình tiếng Anh chuyên ngành cho thư viện số', N'Viện Đào tạo Quốc tế', N'Khắc phục', 'processing', '2026-05-20', '2026-06-30', NULL, N'Đang đánh giá', N'Mua bản quyền số cho 15 đầu sách giáo trình chuyên ngành Công nghệ thông tin phiên bản mới nhất.', NULL), " +
                "    (N'CAPA-26-007', N'Nâng cấp phần mềm đồ họa phòng máy thực hành đa phương tiện', N'Khoa Đa phương tiện', N'Khắc phục', 'processing', '2026-04-10', '2026-05-30', NULL, N'Đang đánh giá', N'Cài đặt và cấu hình bộ công cụ Adobe Creative Cloud bản quyền cho 45 máy tính phòng máy số 3.', N'Thiếu minh chứng bản quyền PDF được Học viện phê duyệt.'), " +
                "    (N'CAPA-26-008', N'Hoàn thiện quy trình giải quyết phản hồi trực tuyến của sinh viên', N'Phòng Công tác Chính trị & CTSV', N'Phòng ngừa', 'closed', '2026-02-01', '2026-03-15', '2026-03-10', N'Chưa đạt', N'Xây dựng biểu mẫu số tự động hóa việc tiếp nhận phản hồi từ app sinh viên, tuy nhiên thời gian phản hồi thực tế vẫn trễ.', NULL), " +
                "    (N'CAPA-26-009', N'Cập nhật vá lỗ hổng bảo mật trên cổng thông tin sinh viên', N'Khoa An toàn thông tin', N'Khắc phục', 'processing', '2026-05-25', '2026-06-25', NULL, N'Đang đánh giá', N'Sửa lỗi SQL Injection được phát hiện trong đợt đánh giá an ninh mạng nội bộ tháng 5.', NULL), " +
                "    (N'CAPA-26-010', N'Tối ưu hóa thời khóa biểu học kỳ hè giảm xung đột phòng học', N'Phòng Đào tạo', N'Phòng ngừa', 'processing', '2026-05-01', '2026-06-15', NULL, N'Đang đánh giá', N'Áp dụng thuật toán phân chia phòng học động để tránh trùng lặp khung giờ thực hành của các khóa.', NULL); " +
                "END";
            template.execute(seedSql);
            System.out.println("[CapaExtend] CAPA tables and seed data created/verified on " + dbName);
        } catch (Exception e) {
            System.err.println("[CapaExtend] Init notice for " + dbName + ": " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------
    // 1. LIST / SEARCH CAPAS
    // ---------------------------------------------------------------------------

    public List<CapaDto> listCapas(String status, String capaType, String departmentName,
                                   String priority, String keyword) {
        StringBuilder sql = new StringBuilder(
            "SELECT c.capa_id, c.capa_code, c.title, c.description, " +
            "       c.capa_type, c.source, c.source_ref, c.status, c.priority, " +
            "       c.root_cause, c.action_plan, " +
            "       c.department_id, c.department_name, " +
            "       c.assigned_to, c.assigned_to_name, c.created_by, " +
            "       CONVERT(VARCHAR(10), c.open_date, 120) AS open_date, " +
            "       CONVERT(VARCHAR(10), c.due_date, 120) AS due_date, " +
            "       CONVERT(VARCHAR(10), c.completed_date, 120) AS completed_date, " +
            "       CONVERT(VARCHAR(10), c.verified_date, 120) AS verified_date, " +
            "       c.verified_by, c.effectiveness, c.feedback, " +
            "       CONVERT(VARCHAR(19), c.created_at, 120) AS created_at, " +
            "       CONVERT(VARCHAR(19), c.updated_at, 120) AS updated_at " +
            "FROM dbo.capa c " +
            "WHERE (c.is_deleted = 0 OR c.is_deleted IS NULL) "
        );

        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            sql.append("AND c.status = ? ");
            params.add(status.trim());
        }
        if (capaType != null && !capaType.isBlank() && !"all".equalsIgnoreCase(capaType)) {
            sql.append("AND c.capa_type = ? ");
            params.add(capaType.trim());
        }
        if (departmentName != null && !departmentName.isBlank() && !"all".equalsIgnoreCase(departmentName)) {
            sql.append("AND c.department_name = ? ");
            params.add(departmentName.trim());
        }
        if (priority != null && !priority.isBlank() && !"all".equalsIgnoreCase(priority)) {
            sql.append("AND c.priority = ? ");
            params.add(priority.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (c.title LIKE ? OR c.capa_code LIKE ? OR c.description LIKE ? OR c.department_name LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw); params.add(kw); params.add(kw); params.add(kw);
        }
        sql.append("ORDER BY c.created_at DESC");

        return jdbcTemplate.query(sql.toString(),
            (rs, rowNum) -> mapRowToCapaDto(rs),
            params.toArray());
    }

    // ---------------------------------------------------------------------------
    // 2. GET SINGLE CAPA
    // ---------------------------------------------------------------------------

    public CapaDto getCapaById(int capaId) {
        String sql =
            "SELECT c.capa_id, c.capa_code, c.title, c.description, " +
            "       c.capa_type, c.source, c.source_ref, c.status, c.priority, " +
            "       c.root_cause, c.action_plan, " +
            "       c.department_id, c.department_name, " +
            "       c.assigned_to, c.assigned_to_name, c.created_by, " +
            "       CONVERT(VARCHAR(10), c.open_date, 120) AS open_date, " +
            "       CONVERT(VARCHAR(10), c.due_date, 120) AS due_date, " +
            "       CONVERT(VARCHAR(10), c.completed_date, 120) AS completed_date, " +
            "       CONVERT(VARCHAR(10), c.verified_date, 120) AS verified_date, " +
            "       c.verified_by, c.effectiveness, c.feedback, " +
            "       CONVERT(VARCHAR(19), c.created_at, 120) AS created_at, " +
            "       CONVERT(VARCHAR(19), c.updated_at, 120) AS updated_at " +
            "FROM dbo.capa c " +
            "WHERE c.capa_id = ? AND (c.is_deleted = 0 OR c.is_deleted IS NULL)";

        List<CapaDto> list = jdbcTemplate.query(sql,
            (rs, rowNum) -> mapRowToCapaDto(rs), capaId);
        return list.isEmpty() ? null : list.get(0);
    }

    // ---------------------------------------------------------------------------
    // 3. CREATE CAPA
    // ---------------------------------------------------------------------------

    public int createCapa(String title, String description, String capaType,
                          String dueDate, String departmentName, String departmentId,
                          String priority, String createdBy) {
        String capaCode = generateCapaCode();
        String typeVal = (capaType != null && !capaType.isBlank()) ? capaType : "Khắc phục";

        String sql =
            "INSERT INTO dbo.capa " +
            "(capa_code, title, description, capa_type, status, department_id, department_name, " +
            " open_date, due_date, effectiveness, priority, created_by, is_deleted, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, 'processing', ?, ?, CAST(GETDATE() AS DATE), " +
            " TRY_CONVERT(DATE, ?, 120), N'Đang đánh giá', ?, ?, 0, GETDATE(), GETDATE()); " +
            "SELECT SCOPE_IDENTITY();";

        Object[] params = {
            capaCode, title, description, typeVal, departmentId, departmentName,
            dueDate, (priority != null ? priority : "Medium"), createdBy
        };

        Integer newId = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return newId != null ? newId : -1;
    }

    // ---------------------------------------------------------------------------
    // 4. WORKFLOW TRANSITIONS (Pending Closure, Close, Revert with Feedback)
    // ---------------------------------------------------------------------------

    /**
     * Department submits completion: processing -> pending_closure
     */
    public boolean markPendingClosure(int capaId, String user) {
        String sql =
            "UPDATE dbo.capa SET " +
            "  status = 'pending_closure', " +
            "  feedback = NULL, " +
            "  updated_at = GETDATE() " +
            "WHERE capa_id = ? AND (is_deleted = 0 OR is_deleted IS NULL)";

        int rows = jdbcTemplate.update(sql, capaId);
        if (rows > 0) {
            logHistory(capaId, "processing", "pending_closure", user, "Báo cáo hoàn thành - Chờ TTKT thẩm định");
        }
        return rows > 0;
    }

    /**
     * TTKT Approves & Closes CAPA: pending_closure -> closed
     */
    public boolean closeCapa(int capaId, String user, String effectiveness) {
        String effVal = (effectiveness != null && !effectiveness.isBlank()) ? effectiveness : "Đạt";

        String sql =
            "UPDATE dbo.capa SET " +
            "  status = 'closed', " +
            "  completed_date = CAST(GETDATE() AS DATE), " +
            "  verified_date = CAST(GETDATE() AS DATE), " +
            "  verified_by = ?, " +
            "  effectiveness = ?, " +
            "  feedback = NULL, " +
            "  updated_at = GETDATE() " +
            "WHERE capa_id = ? AND (is_deleted = 0 OR is_deleted IS NULL)";

        int rows = jdbcTemplate.update(sql, user, effVal, capaId);
        if (rows > 0) {
            logHistory(capaId, "pending_closure", "closed", user, "Duyệt đóng (Khép vòng) hồ sơ CAPA - Hiệu lực: " + effVal);
        }
        return rows > 0;
    }

    /**
     * TTKT Rejects / Requests Improvements: pending_closure -> processing
     */
    public boolean revertCapa(int capaId, String user, String feedbackComment) {
        String sql =
            "UPDATE dbo.capa SET " +
            "  status = 'processing', " +
            "  feedback = ?, " +
            "  completed_date = NULL, " +
            "  effectiveness = N'Đang đánh giá', " +
            "  updated_at = GETDATE() " +
            "WHERE capa_id = ? AND (is_deleted = 0 OR is_deleted IS NULL)";

        int rows = jdbcTemplate.update(sql, feedbackComment, capaId);
        if (rows > 0) {
            logHistory(capaId, "pending_closure", "processing", user, "Yêu cầu cải tiến thêm: " + feedbackComment);
        }
        return rows > 0;
    }

    // ---------------------------------------------------------------------------
    // 5. UPDATE / DELETE
    // ---------------------------------------------------------------------------

    public boolean updateCapa(int capaId, String title, String description,
                              String priority, String dueDate, String departmentName) {
        String sql =
            "UPDATE dbo.capa SET " +
            "  title = COALESCE(?, title), " +
            "  description = COALESCE(?, description), " +
            "  priority = COALESCE(?, priority), " +
            "  due_date = COALESCE(TRY_CONVERT(DATE, ?, 120), due_date), " +
            "  department_name = COALESCE(?, department_name), " +
            "  updated_at = GETDATE() " +
            "WHERE capa_id = ? AND (is_deleted = 0 OR is_deleted IS NULL)";

        return jdbcTemplate.update(sql, title, description, priority, dueDate, departmentName, capaId) > 0;
    }

    public boolean deleteCapa(int capaId, String user) {
        String sql = "UPDATE dbo.capa SET is_deleted = 1, updated_at = GETDATE() WHERE capa_id = ?";
        return jdbcTemplate.update(sql, capaId) > 0;
    }

    // ---------------------------------------------------------------------------
    // 6. DASHBOARD STATS & CHARTS
    // ---------------------------------------------------------------------------

    public CapaStatsDto getStats(String departmentName) {
        StringBuilder sql = new StringBuilder(
            "SELECT " +
            "  COUNT(*) AS total, " +
            "  SUM(CASE WHEN status = 'pending_closure' THEN 1 ELSE 0 END) AS pending_closure, " +
            "  SUM(CASE WHEN status = 'processing' THEN 1 ELSE 0 END) AS processing, " +
            "  SUM(CASE WHEN status = 'closed' THEN 1 ELSE 0 END) AS closed, " +
            "  SUM(CASE WHEN status = 'processing' AND due_date < CAST(GETDATE() AS DATE) THEN 1 ELSE 0 END) AS overdue, " +
            "  SUM(CASE WHEN status = 'closed' AND effectiveness = N'Đạt' THEN 1 ELSE 0 END) AS closed_effective " +
            "FROM dbo.capa " +
            "WHERE (is_deleted = 0 OR is_deleted IS NULL) "
        );

        List<Object> params = new ArrayList<>();
        if (departmentName != null && !departmentName.isBlank() && !"all".equalsIgnoreCase(departmentName)) {
            sql.append("AND department_name = ? ");
            params.add(departmentName.trim());
        }

        return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
            int total = rs.getInt("total");
            int pendingClosure = rs.getInt("pending_closure");
            int processing = rs.getInt("processing");
            int closed = rs.getInt("closed");
            int overdue = rs.getInt("overdue");
            int closedEffective = rs.getInt("closed_effective");
            double effRate = closed > 0 ? (double) Math.round((closedEffective * 100.0 / closed) * 10.0) / 10.0 : 0.0;

            return new CapaStatsDto(total, pendingClosure, processing, overdue, closed, effRate);
        }, params.toArray());
    }

    public JSONArray getMonthlyTrend() {
        String sql =
            "SELECT " +
            "  MONTH(open_date) AS m_open, " +
            "  COUNT(capa_id) AS open_count, " +
            "  SUM(CASE WHEN status = 'closed' THEN 1 ELSE 0 END) AS closed_count " +
            "FROM dbo.capa " +
            "WHERE (is_deleted = 0 OR is_deleted IS NULL) " +
            "GROUP BY MONTH(open_date) " +
            "ORDER BY MONTH(open_date)";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        Map<Integer, Map<String, Object>> mapByMonth = new HashMap<>();
        for (Map<String, Object> r : rows) {
            Number m = (Number) r.get("m_open");
            if (m != null) {
                mapByMonth.put(m.intValue(), r);
            }
        }

        JSONArray result = new JSONArray();
        for (int i = 1; i <= 6; i++) {
            JSONObject item = new JSONObject();
            item.put("name", "Tháng " + i);
            if (mapByMonth.containsKey(i)) {
                Map<String, Object> r = mapByMonth.get(i);
                item.put("Mở mới", r.get("open_count") != null ? ((Number) r.get("open_count")).intValue() : 0);
                item.put("Hoàn thành", r.get("closed_count") != null ? ((Number) r.get("closed_count")).intValue() : 0);
            } else {
                item.put("Mở mới", 0);
                item.put("Hoàn thành", 0);
            }
            result.put(item);
        }
        return result;
    }

    public JSONArray getDepartmentDistribution() {
        String sql =
            "SELECT department_name, COUNT(capa_id) AS doc_count " +
            "FROM dbo.capa " +
            "WHERE (is_deleted = 0 OR is_deleted IS NULL) " +
            "GROUP BY department_name " +
            "ORDER BY doc_count DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        JSONArray result = new JSONArray();
        for (Map<String, Object> r : rows) {
            String dept = (String) r.get("department_name");
            Number count = (Number) r.get("doc_count");
            if (dept == null) continue;

            String shortName = dept
                .replace("Khoa ", "K. ")
                .replace("Phòng ", "P. ")
                .replace("Viện ", "V. ")
                .replace("Công nghệ thông tin", "CNTT")
                .replace("Công tác Chính trị & CTSV", "CTSV")
                .replace("Quản trị kinh doanh", "QTKD")
                .replace("Đào tạo Quốc tế", "ĐTQT")
                .replace("An toàn thông tin", "ATTT");

            JSONObject item = new JSONObject();
            item.put("name", shortName);
            item.put("Số hồ sơ", count != null ? count.intValue() : 0);
            item.put("fullName", dept);
            result.put(item);
        }
        return result;
    }

    // ---------------------------------------------------------------------------
    // 7. ACTIONS & AUDIT LOG
    // ---------------------------------------------------------------------------

    public List<CapaActionDto> getActionsByCapaId(int capaId) {
        String sql =
            "SELECT action_id, capa_id, action_description, assigned_to, assigned_to_name, " +
            "       CONVERT(VARCHAR(10), due_date, 120) AS due_date, " +
            "       CONVERT(VARCHAR(10), completed_date, 120) AS completed_date, " +
            "       status, sort_order, notes, " +
            "       CONVERT(VARCHAR(19), created_at, 120) AS created_at, " +
            "       CONVERT(VARCHAR(19), updated_at, 120) AS updated_at " +
            "FROM dbo.capa_actions " +
            "WHERE capa_id = ? AND (is_deleted = 0 OR is_deleted IS NULL) " +
            "ORDER BY sort_order ASC, created_at ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            CapaActionDto a = new CapaActionDto();
            a.setActionId(rs.getInt("action_id"));
            a.setCapaId(rs.getInt("capa_id"));
            a.setActionDescription(rs.getString("action_description"));
            a.setAssignedTo(rs.getString("assigned_to"));
            a.setAssignedToName(rs.getString("assigned_to_name"));
            a.setDueDate(rs.getString("due_date"));
            a.setCompletedDate(rs.getString("completed_date"));
            a.setStatus(rs.getString("status"));
            Object sOrder = rs.getObject("sort_order");
            a.setSortOrder(sOrder != null ? ((Number) sOrder).intValue() : null);
            a.setNotes(rs.getString("notes"));
            a.setCreatedAt(rs.getString("created_at"));
            a.setUpdatedAt(rs.getString("updated_at"));
            return a;
        }, capaId);
    }

    public JSONArray getCapaHistory(int capaId) {
        String sql =
            "SELECT history_id, capa_id, previous_status, new_status, changed_by, feedback_comment, " +
            "       CONVERT(VARCHAR(19), changed_at, 120) AS changed_at " +
            "FROM dbo.capa_history " +
            "WHERE capa_id = ? " +
            "ORDER BY changed_at DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, capaId);
        return new JSONArray(rows);
    }

    // ---------------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------------

    private CapaDto mapRowToCapaDto(java.sql.ResultSet rs) throws java.sql.SQLException {
        CapaDto dto = new CapaDto();
        dto.setCapaId(rs.getInt("capa_id"));
        dto.setCapaCode(rs.getString("capa_code"));
        dto.setTitle(rs.getString("title"));
        dto.setDescription(rs.getString("description"));
        dto.setCapaType(rs.getString("capa_type"));
        dto.setSource(rs.getString("source"));
        dto.setSourceRef(rs.getString("source_ref"));
        dto.setStatus(rs.getString("status"));
        dto.setPriority(rs.getString("priority"));
        dto.setRootCause(rs.getString("root_cause"));
        dto.setActionPlan(rs.getString("action_plan"));
        dto.setDepartmentId(rs.getString("department_id"));
        dto.setDepartmentName(rs.getString("department_name"));
        dto.setAssignedTo(rs.getString("assigned_to"));
        dto.setAssignedToName(rs.getString("assigned_to_name"));
        dto.setCreatedBy(rs.getString("created_by"));
        dto.setOpenDate(rs.getString("open_date"));
        dto.setDueDate(rs.getString("due_date"));
        dto.setCompletedDate(rs.getString("completed_date"));
        dto.setVerifiedDate(rs.getString("verified_date"));
        dto.setVerifiedBy(rs.getString("verified_by"));
        dto.setEffectivenessStatus(rs.getString("effectiveness"));
        dto.setFeedback(rs.getString("feedback"));
        dto.setCreatedAt(rs.getString("created_at"));
        dto.setUpdatedAt(rs.getString("updated_at"));
        return dto;
    }

    private void logHistory(int capaId, String prevStatus, String newStatus, String user, String comment) {
        String sql =
            "INSERT INTO dbo.capa_history (capa_id, previous_status, new_status, changed_by, feedback_comment, changed_at) " +
            "VALUES (?, ?, ?, ?, ?, GETDATE())";
        jdbcTemplate.update(sql, capaId, prevStatus, newStatus, user, comment);
    }

    private String generateCapaCode() {
        String yearMonth = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yy"));
        String prefix = "CAPA-" + yearMonth + "-";
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dbo.capa WHERE capa_code LIKE ?", Integer.class, prefix + "%");
        int seq = (count != null ? count : 0) + 1;
        return String.format("%s%03d", prefix, seq);
    }
}
