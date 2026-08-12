package com.notification;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationExtend {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String LD_HOC_VIEN_ID     = "66a308ce8068e53428da202c";
    private static final String LD_HOC_VIEN_NAM_ID = "66a308ce8068e53428da202d";

    // SSE Emitter registry for real-time push: userId -> List<SseEmitter>
    private final Map<String, List<SseEmitter>> activeEmitters = new ConcurrentHashMap<>();

	/* @PostConstruct
	public void init() {
	    try {
	        // 1. Create master notifications table if missing
	        String createNotificationsSql =
	            "IF OBJECT_ID('dbo.notifications', 'U') IS NULL " +
	            "BEGIN " +
	            "    CREATE TABLE notifications ( " +
	            "        id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
	            "        notification_code VARCHAR(100) NOT NULL, " +
	            "        category VARCHAR(50) NOT NULL, " +
	            "        severity VARCHAR(20) NOT NULL DEFAULT 'info', " +
	            "        title NVARCHAR(255) NOT NULL, " +
	            "        message NVARCHAR(MAX) NOT NULL, " +
	            "        target_role VARCHAR(50) NULL, " +
	            "        target_dept_id VARCHAR(100) NULL, " +
	            "        entity_type VARCHAR(50) NULL, " +
	            "        entity_id VARCHAR(100) NULL, " +
	            "        action_url VARCHAR(500) NULL, " +
	            "        created_by VARCHAR(100) NULL, " +
	            "        created_at DATETIME DEFAULT GETDATE(), " +
	            "        is_deleted BIT DEFAULT 0 " +
	            "    ); " +
	            "    CREATE INDEX IX_notifications_category ON notifications(category); " +
	            "    CREATE INDEX IX_notifications_target ON notifications(target_role, target_dept_id); " +
	            "END";
	        jdbcTemplate.execute(createNotificationsSql);
	
	        // 2. Create user_notifications delivery table if missing
	        String createUserNotificationsSql =
	            "IF OBJECT_ID('dbo.user_notifications', 'U') IS NULL " +
	            "BEGIN " +
	            "    CREATE TABLE user_notifications ( " +
	            "        id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
	            "        notification_id BIGINT NOT NULL FOREIGN KEY REFERENCES notifications(id) ON DELETE CASCADE, " +
	            "        user_id VARCHAR(100) NOT NULL, " +
	            "        is_read BIT DEFAULT 0, " +
	            "        read_at DATETIME NULL, " +
	            "        delivered_at DATETIME DEFAULT GETDATE(), " +
	            "        is_archived BIT DEFAULT 0 " +
	            "    ); " +
	            "    CREATE INDEX IX_user_notifications_user ON user_notifications(user_id, is_read); " +
	            "END";
	        jdbcTemplate.execute(createUserNotificationsSql);
	
	        // 3. Create user_notification_preferences table if missing
	        String createPreferencesSql =
	            "IF OBJECT_ID('dbo.user_notification_preferences', 'U') IS NULL " +
	            "BEGIN " +
	            "    CREATE TABLE user_notification_preferences ( " +
	            "        user_id VARCHAR(100) PRIMARY KEY, " +
	            "        opt_kpi BIT DEFAULT 1, " +
	            "        opt_capa BIT DEFAULT 1, " +
	            "        opt_evidence BIT DEFAULT 1, " +
	            "        opt_survey BIT DEFAULT 1, " +
	            "        opt_system BIT DEFAULT 1, " +
	            "        retention_days INT DEFAULT 30, " +
	            "        updated_at DATETIME DEFAULT GETDATE() " +
	            "    ); " +
	            "END";
	        jdbcTemplate.execute(createPreferencesSql);
	
	        System.out.println("[NotificationCenter] Advanced schema initialization complete.");
	        seedInitialSystemNotifications();
	    } catch (Exception e) {
	        System.err.println("[NotificationCenter] Schema init notice: " + e.getMessage());
	    }
	}
	*/
    /**
     * Subscribe a user's SSE Emitter connection for real-time event streaming.
     */
    public SseEmitter subscribeUser(String userId) {
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes timeout
        List<SseEmitter> userEmitters = activeEmitters.computeIfAbsent(userId, k -> new ArrayList<>());
        synchronized (userEmitters) {
            userEmitters.add(emitter);
        }

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        // Send initial connection heartbeat and initial unread count push
        try {
            int count = getUnreadCount(userId);
            JSONObject countPayload = new JSONObject();
            countPayload.put("unread_count", count);

            emitter.send(SseEmitter.event().name("connected").data("Realtime notifications connected"));
            emitter.send(SseEmitter.event().name("unread_count").data(countPayload.toString()));
        } catch (Exception e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    public void pushUnreadCountToUser(String userId) {
        List<SseEmitter> userEmitters = activeEmitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        int count = getUnreadCount(userId);
        JSONObject payload = new JSONObject();
        payload.put("unread_count", count);

        synchronized (userEmitters) {
            Iterator<SseEmitter> it = userEmitters.iterator();
            while (it.hasNext()) {
                SseEmitter emitter = it.next();
                try {
                    emitter.send(SseEmitter.event().name("unread_count").data(payload.toString()));
                } catch (Exception e) {
                    it.remove();
                }
            }
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = activeEmitters.get(userId);
        if (userEmitters != null) {
            synchronized (userEmitters) {
                userEmitters.remove(emitter);
                if (userEmitters.isEmpty()) {
                    activeEmitters.remove(userId);
                }
            }
        }
    }

    /**
     * Dispatch notification with category opt-in checking and real-time SSE push.
     */
    @Transactional
    public Long dispatchNotification(
            String code,
            String category,
            String severity,
            String title,
            String message,
            String targetRole,
            String targetDeptId,
            String entityType,
            String entityId,
            String actionUrl,
            String createdBy,
            List<String> explicitUserIds) {

        try {
            // 1. Insert master notification
            String insertSql =
                "INSERT INTO notifications (notification_code, category, severity, title, message, target_role, " +
                "target_dept_id, entity_type, entity_id, action_url, created_by, created_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE(), 0)";

            jdbcTemplate.update(insertSql, code, category, severity, title, message, targetRole, targetDeptId, entityType, entityId, actionUrl, createdBy);

            Long notificationId = jdbcTemplate.queryForObject("SELECT SCOPE_IDENTITY()", Long.class);
            if (notificationId == null) return null;

            // 2. Resolve target user IDs
            Set<String> recipientUserIds = new HashSet<>();

            if (explicitUserIds != null && !explicitUserIds.isEmpty()) {
                recipientUserIds.addAll(explicitUserIds);
            }

            if (targetRole != null && !targetRole.isEmpty()) {
                List<String> roleUserIds = resolveUserIdsByRoleAndDept(targetRole, targetDeptId);
                recipientUserIds.addAll(roleUserIds);
            }

            if (recipientUserIds.isEmpty()) {
                List<String> defaultAdmins = jdbcTemplate.queryForList(
                    "SELECT CAST(u.ID AS VARCHAR(100)) FROM users u WHERE u.Type IN (1, 2) OR u.Email = 'admin@ptit.edu.vn'",
                    String.class
                );
                recipientUserIds.addAll(defaultAdmins);
            }

            // 3. Filter by category opt-in preferences for non-mandatory items
            String insertDeliverySql = "INSERT INTO user_notifications (notification_id, user_id, is_read, delivered_at) VALUES (?, ?, 0, GETDATE())";
            String categoryColumn = getCategoryPreferenceColumn(category);

            for (String uid : recipientUserIds) {
                if (uid == null || uid.isBlank()) continue;

                // Check opt-in preference unless it's a mandatory strategic danger alert
                boolean shouldDeliver = true;
                if (!"danger".equalsIgnoreCase(severity) && categoryColumn != null) {
                    try {
                        String optSql = "SELECT " + categoryColumn + " FROM user_notification_preferences WHERE user_id = ?";
                        List<Map<String, Object>> prefRows = jdbcTemplate.queryForList(optSql, uid.trim());
                        if (!prefRows.isEmpty()) {
                            Object optVal = prefRows.get(0).get(categoryColumn);
                            if (optVal != null && (Boolean.FALSE.equals(optVal) || ((Number) optVal).intValue() == 0)) {
                                shouldDeliver = false;
                            }
                        }
                    } catch (Exception prefEx) {}
                }

                if (shouldDeliver) {
                    jdbcTemplate.update(insertDeliverySql, notificationId, uid.trim());

                    // Real-time SSE push payload if user is currently online
                    pushRealtimeNotificationToUser(uid.trim(), notificationId, code, category, severity, title, message, actionUrl);
                    pushUnreadCountToUser(uid.trim());
                }
            }

            return notificationId;
        } catch (Exception e) {
            System.err.println("[NotificationCenter] Dispatch error: " + e.getMessage());
            return null;
        }
    }

    private void pushRealtimeNotificationToUser(String userId, Long notificationId, String code, String category, String severity, String title, String message, String actionUrl) {
        List<SseEmitter> userEmitters = activeEmitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        JSONObject payload = new JSONObject();
        payload.put("notification_id", notificationId);
        payload.put("code", code);
        payload.put("category", category);
        payload.put("severity", severity);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("action_url", actionUrl);
        payload.put("delivered_at", new Date().toString());

        synchronized (userEmitters) {
            Iterator<SseEmitter> it = userEmitters.iterator();
            while (it.hasNext()) {
                SseEmitter emitter = it.next();
                try {
                    emitter.send(SseEmitter.event().name("notification").data(payload.toString()));
                } catch (Exception e) {
                    it.remove();
                }
            }
        }
    }

    private String getCategoryPreferenceColumn(String category) {
        if ("KPI".equalsIgnoreCase(category)) return "opt_kpi";
        if ("CAPA".equalsIgnoreCase(category)) return "opt_capa";
        if ("EVIDENCE".equalsIgnoreCase(category)) return "opt_evidence";
        if ("SURVEY".equalsIgnoreCase(category)) return "opt_survey";
        if ("SYSTEM".equalsIgnoreCase(category)) return "opt_system";
        return null;
    }

    /**
     * Resolve target user IDs by role name and optional department filter.
     */
    public List<String> resolveUserIdsByRoleAndDept(String role, String deptId) {
        List<String> uids = new ArrayList<>();
        try {
            if ("ADMIN".equalsIgnoreCase(role)) {
                return jdbcTemplate.queryForList(
                    "SELECT CAST(u.ID AS VARCHAR(100)) FROM users u WHERE (u.Type IN (1, 2) OR u.Email = 'admin@ptit.edu.vn') AND (u.IsDeleted IS NULL OR u.IsDeleted = '0')",
                    String.class
                );
            } else if ("LANH_DAO".equalsIgnoreCase(role)) {
                String sql =
                    "SELECT DISTINCT CAST(p.id AS VARCHAR(100)) FROM personnel p " +
                    "WHERE p.isDeleted = 0 AND (p.donViChinhId IN ('" + LD_HOC_VIEN_ID + "', '" + LD_HOC_VIEN_NAM_ID + "') " +
                    "  OR p.donViL3Id IN ('" + LD_HOC_VIEN_ID + "', '" + LD_HOC_VIEN_NAM_ID + "'))";
                return jdbcTemplate.queryForList(sql, String.class);
            } else if ("TRUONG_DON_VI".equalsIgnoreCase(role)) {
                String sql = "SELECT DISTINCT CAST(o.leaderId AS VARCHAR(100)) FROM orgs o WHERE o.leaderId IS NOT NULL AND o.leaderId <> '' AND (o.isDeleted IS NULL OR o.isDeleted = 0)";
                if (deptId != null && !deptId.isBlank()) {
                    sql += " AND o.id = '" + deptId.trim() + "'";
                }
                return jdbcTemplate.queryForList(sql, String.class);
            } else if ("CHUYEN_VIEN".equalsIgnoreCase(role)) {
                String sql = "SELECT DISTINCT CAST(p.id AS VARCHAR(100)) FROM personnel p WHERE p.isDeleted = 0";
                if (deptId != null && !deptId.isBlank()) {
                    sql += " AND (p.donViL3Id = '" + deptId.trim() + "' OR p.donViChinhId = '" + deptId.trim() + "')";
                }
                return jdbcTemplate.queryForList(sql, String.class);
            } else if ("ALL".equalsIgnoreCase(role)) {
                return jdbcTemplate.queryForList("SELECT DISTINCT CAST(u.ID AS VARCHAR(100)) FROM users u WHERE (u.IsDeleted IS NULL OR u.IsDeleted = '0')", String.class);
            }
        } catch (Exception e) {
            System.err.println("[NotificationCenter] Role resolution error: " + e.getMessage());
        }
        return uids;
    }

    /**
     * Get paginated notifications for user, respecting retention_days and opt-in settings.
     */
    public JSONObject getUserNotifications(String userId, String categoryFilter, Boolean unreadOnly, int page, int pageSize) {
        JSONObject res = new JSONObject();
        JSONArray list = new JSONArray();
        try {
            int offset = Math.max(0, (page - 1) * pageSize);
            int retentionDays = getUserRetentionDays(userId);

            StringBuilder countSql = new StringBuilder(
                "SELECT COUNT(*) FROM user_notifications un " +
                "JOIN notifications n ON n.id = un.notification_id " +
                "WHERE un.user_id = ? AND un.is_archived = 0 AND n.is_deleted = 0 " +
                "  AND un.delivered_at >= DATEADD(day, -" + retentionDays + ", GETDATE()) "
            );

            StringBuilder querySql = new StringBuilder(
                "SELECT un.id AS delivery_id, un.is_read, un.read_at, un.delivered_at, " +
                "n.id AS notification_id, n.notification_code, n.category, n.severity, n.title, n.message, " +
                "n.target_role, n.target_dept_id, n.entity_type, n.entity_id, n.action_url, n.created_at " +
                "FROM user_notifications un " +
                "JOIN notifications n ON n.id = un.notification_id " +
                "WHERE un.user_id = ? AND un.is_archived = 0 AND n.is_deleted = 0 " +
                "  AND un.delivered_at >= DATEADD(day, -" + retentionDays + ", GETDATE()) "
            );

            List<Object> params = new ArrayList<>();
            params.add(userId);

            if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
                countSql.append("AND n.category = ? ");
                querySql.append("AND n.category = ? ");
                params.add(categoryFilter.trim());
            }

            if (Boolean.TRUE.equals(unreadOnly)) {
                countSql.append("AND un.is_read = 0 ");
                querySql.append("AND un.is_read = 0 ");
            }

            querySql.append("ORDER BY un.delivered_at DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");

            Integer totalCount = jdbcTemplate.queryForObject(countSql.toString(), Integer.class, params.toArray());

            List<Object> queryParams = new ArrayList<>(params);
            queryParams.add(offset);
            queryParams.add(pageSize);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql.toString(), queryParams.toArray());

            for (Map<String, Object> r : rows) {
                JSONObject item = new JSONObject();
                item.put("delivery_id", r.get("delivery_id"));
                item.put("notification_id", r.get("notification_id"));
                item.put("code", r.get("notification_code"));
                item.put("category", r.get("category"));
                item.put("severity", r.get("severity"));
                item.put("title", r.get("title"));
                item.put("message", r.get("message"));
                item.put("target_role", r.get("target_role"));
                item.put("target_dept_id", r.get("target_dept_id"));
                item.put("entity_type", r.get("entity_type"));
                item.put("entity_id", r.get("entity_id"));
                item.put("action_url", r.get("action_url"));
                item.put("is_read", r.get("is_read") != null && (Boolean.TRUE.equals(r.get("is_read")) || ((Number) r.get("is_read")).intValue() == 1));
                item.put("read_at", r.get("read_at") != null ? r.get("read_at").toString() : JSONObject.NULL);
                item.put("delivered_at", r.get("delivered_at") != null ? r.get("delivered_at").toString() : JSONObject.NULL);
                list.put(item);
            }

            int unreadCount = getUnreadCount(userId);

            res.put("code", 200);
            res.put("notifications", list);
            res.put("total", totalCount != null ? totalCount : 0);
            res.put("unread_count", unreadCount);
            res.put("page", page);
            res.put("page_size", pageSize);
            res.put("has_more", (offset + list.length()) < (totalCount != null ? totalCount : 0));
        } catch (Exception e) {
            res.put("code", 500);
            res.put("description", "Error fetching notifications: " + e.getMessage());
        }
        return res;
    }

    public int getUnreadCount(String userId) {
        try {
            int retentionDays = getUserRetentionDays(userId);
            String sql =
                "SELECT COUNT(*) FROM user_notifications un " +
                "JOIN notifications n ON n.id = un.notification_id " +
                "WHERE un.user_id = ? AND un.is_read = 0 AND un.is_archived = 0 AND n.is_deleted = 0 " +
                "  AND un.delivered_at >= DATEADD(day, -" + retentionDays + ", GETDATE())";
            Integer cnt = jdbcTemplate.queryForObject(sql, Integer.class, userId);
            return cnt != null ? cnt : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean markAsRead(String userId, Long deliveryId) {
        try {
            String sql = "UPDATE user_notifications SET is_read = 1, read_at = GETDATE() WHERE id = ? AND user_id = ?";
            boolean updated = jdbcTemplate.update(sql, deliveryId, userId) > 0;
            if (updated) {
                pushUnreadCountToUser(userId);
            }
            return updated;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean markAllAsRead(String userId) {
        try {
            String sql = "UPDATE user_notifications SET is_read = 1, read_at = GETDATE() WHERE user_id = ? AND is_read = 0";
            jdbcTemplate.update(sql, userId);
            pushUnreadCountToUser(userId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteNotification(String userId, Long deliveryId) {
        try {
            String sql = "UPDATE user_notifications SET is_archived = 1 WHERE id = ? AND user_id = ?";
            boolean updated = jdbcTemplate.update(sql, deliveryId, userId) > 0;
            if (updated) {
                pushUnreadCountToUser(userId);
            }
            return updated;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get user personal preferences.
     */
    public JSONObject getUserPreferences(String userId) {
        JSONObject res = new JSONObject();
        try {
            String sql = "SELECT opt_kpi, opt_capa, opt_evidence, opt_survey, opt_system, retention_days FROM user_notification_preferences WHERE user_id = ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                res.put("opt_kpi", getBool(r.get("opt_kpi")));
                res.put("opt_capa", getBool(r.get("opt_capa")));
                res.put("opt_evidence", getBool(r.get("opt_evidence")));
                res.put("opt_survey", getBool(r.get("opt_survey")));
                res.put("opt_system", getBool(r.get("opt_system")));
                res.put("retention_days", r.get("retention_days") != null ? ((Number) r.get("retention_days")).intValue() : 30);
            } else {
                // Defaults
                res.put("opt_kpi", true);
                res.put("opt_capa", true);
                res.put("opt_evidence", true);
                res.put("opt_survey", true);
                res.put("opt_system", true);
                res.put("retention_days", 30);
            }
            res.put("code", 200);
        } catch (Exception e) {
            res.put("code", 500);
            res.put("description", e.getMessage());
        }
        return res;
    }

    /**
     * Save user personal preferences.
     */
    public boolean saveUserPreferences(String userId, JSONObject prefs) {
        try {
            boolean optKpi      = prefs.optBoolean("opt_kpi", true);
            boolean optCapa     = prefs.optBoolean("opt_capa", true);
            boolean optEvidence = prefs.optBoolean("opt_evidence", true);
            boolean optSurvey   = prefs.optBoolean("opt_survey", true);
            boolean optSystem   = prefs.optBoolean("opt_system", true);
            int retentionDays   = prefs.optInt("retention_days", 30);

            String mergeSql =
                "IF EXISTS (SELECT 1 FROM user_notification_preferences WHERE user_id = ?) " +
                "    UPDATE user_notification_preferences SET opt_kpi = ?, opt_capa = ?, opt_evidence = ?, opt_survey = ?, opt_system = ?, retention_days = ?, updated_at = GETDATE() WHERE user_id = ? " +
                "ELSE " +
                "    INSERT INTO user_notification_preferences (user_id, opt_kpi, opt_capa, opt_evidence, opt_survey, opt_system, retention_days) VALUES (?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(mergeSql,
                userId,
                optKpi ? 1 : 0, optCapa ? 1 : 0, optEvidence ? 1 : 0, optSurvey ? 1 : 0, optSystem ? 1 : 0, retentionDays, userId,
                userId, optKpi ? 1 : 0, optCapa ? 1 : 0, optEvidence ? 1 : 0, optSurvey ? 1 : 0, optSystem ? 1 : 0, retentionDays
            );
            return true;
        } catch (Exception e) {
            System.err.println("[NotificationCenter] Save preferences error: " + e.getMessage());
            return false;
        }
    }

    public int getUserRetentionDays(String userId) {
        try {
            String sql = "SELECT retention_days FROM user_notification_preferences WHERE user_id = ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
            if (!rows.isEmpty() && rows.get(0).get("retention_days") != null) {
                return ((Number) rows.get(0).get("retention_days")).intValue();
            }
        } catch (Exception e) {}
        return 30; // default 30 days
    }

    /**
     * Purge notifications older than user retention settings.
     */
    public void purgeExpiredNotifications() {
        try {
            String sql =
                "UPDATE un SET un.is_archived = 1 " +
                "FROM user_notifications un " +
                "LEFT JOIN user_notification_preferences p ON p.user_id = un.user_id " +
                "WHERE un.delivered_at < DATEADD(day, -ISNULL(p.retention_days, 30), GETDATE()) " +
                "  AND un.is_archived = 0";
            int count = jdbcTemplate.update(sql);
            if (count > 0) {
                System.out.println("[NotificationCenter] Purged " + count + " expired notification(s) based on user retention settings.");
            }
        } catch (Exception e) {
            System.err.println("[NotificationCenter] Retention purge error: " + e.getMessage());
        }
    }

    private boolean getBool(Object obj) {
        if (obj == null) return true;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).intValue() == 1;
        return "1".equalsIgnoreCase(obj.toString()) || "true".equalsIgnoreCase(obj.toString());
    }

    private void seedInitialSystemNotifications() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
            if (count != null && count > 0) return;

            System.out.println("[NotificationCenter] Seeding initial sample notifications...");

            dispatchNotification(
                "KPI_RISK_HIGH", "KPI", "danger",
                "Cảnh báo rủi ro KPI: C5.02",
                "Tỷ lệ đóng CAPA đúng hạn dưới mục tiêu chiến lược (78.3% < 85.0%). Yêu cầu kiểm tra tiến độ giải quyết.",
                "LANH_DAO", null, "KPI_DATA", "C5.02", "/kpi?code=C5.02", "SYSTEM", null
            );

            dispatchNotification(
                "KPI_RISK_HIGH", "KPI", "warning",
                "Cảnh báo KPI: G2.01 - Khoa CNTT",
                "Tỷ lệ Giảng viên có học vị Tiến sĩ chưa đạt chỉ tiêu (42.6% < 45.0%).",
                "TRUONG_DON_VI", null, "KPI_DATA", "G2.01", "/kpi?code=G2.01", "SYSTEM", null
            );

            dispatchNotification(
                "CAPA_ASSIGNED", "CAPA", "info", "Hồ sơ CAPA mới: C5-2026-004",
                "Bạn được phân công giải quyết hồ sơ cải tiến chất lượng về phổ điểm thi môn Trí tuệ nhân tạo.",
                "CHUYEN_VIEN", null, "CAPA_TICKET", "C5-2026-004", "/capa?id=C5-2026-004", "SYSTEM", null
            );

            dispatchNotification(
                "SYSTEM_DATA_SYNC", "SYSTEM", "success", "Đồng bộ dữ liệu nhân sự thành công",
                "Hệ thống đã đồng bộ thành công danh sách cán bộ và phân công lãnh đạo đơn vị mới nhất.",
                "ADMIN", null, "SYSTEM", "SYNC_01", "/definitions", "SYSTEM", null
            );
        } catch (Exception e) {
            System.err.println("[NotificationCenter] Seed notice: " + e.getMessage());
        }
    }
}
