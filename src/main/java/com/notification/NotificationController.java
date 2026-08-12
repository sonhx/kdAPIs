package com.notification;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.session.SessionService;
import com.session.struct_session;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationExtend notificationExtend;

    @Autowired
    private SessionService sessionService;

    /**
     * Server-Sent Events (SSE) real-time push endpoint for live notification updates.
     */
    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestParam("session_id") String sessionId) {
        struct_session sst = sessionService.getSessionInfo(sessionId);
        if (sst == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.complete();
            return emitter;
        }
        String userId = String.valueOf(sst.UserID);
        return notificationExtend.subscribeUser(userId);
    }

    @PostMapping("/list")
    public String getUserNotifications(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("description", "Chưa đăng nhập");
                return jout.toString();
            }

            String userId = String.valueOf(sst.UserID);
            String category = jin.has("category") ? jin.getString("category") : "ALL";
            Boolean unreadOnly = jin.has("unread_only") ? jin.getBoolean("unread_only") : false;
            int page = jin.has("page") ? jin.getInt("page") : 1;
            int pageSize = jin.has("page_size") ? jin.getInt("page_size") : 15;

            JSONObject data = notificationExtend.getUserNotifications(userId, category, unreadOnly, page, pageSize);
            return data.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý: " + e.getMessage());
            return jout.toString();
        }
    }

    @PostMapping("/unread-count")
    public String getUnreadCount(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("unread_count", 0);
                return jout.toString();
            }

            String userId = String.valueOf(sst.UserID);
            int count = notificationExtend.getUnreadCount(userId);

            jout.put("code", 200);
            jout.put("unread_count", count);
            return jout.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("unread_count", 0);
            return jout.toString();
        }
    }

    @PostMapping("/mark-read")
    public String markAsRead(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("description", "Chưa đăng nhập");
                return jout.toString();
            }

            String userId = String.valueOf(sst.UserID);
            Long deliveryId = jin.has("delivery_id") ? jin.getLong("delivery_id") : null;

            if (deliveryId == null) {
                jout.put("code", 400);
                jout.put("description", "Thiếu delivery_id");
                return jout.toString();
            }

            boolean ok = notificationExtend.markAsRead(userId, deliveryId);
            jout.put("code", ok ? 200 : 400);
            jout.put("description", ok ? "Thành công" : "Không thể cập nhật trạng thái");
            return jout.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý: " + e.getMessage());
            return jout.toString();
        }
    }

    @PostMapping("/mark-all-read")
    public String markAllAsRead(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("description", "Chưa đăng nhập");
                return jout.toString();
            }

            String userId = String.valueOf(sst.UserID);
            boolean ok = notificationExtend.markAllAsRead(userId);

            jout.put("code", ok ? 200 : 500);
            jout.put("description", ok ? "Thành công" : "Lỗi cập nhật");
            return jout.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý: " + e.getMessage());
            return jout.toString();
        }
    }

    @PostMapping("/delete")
    public String deleteNotification(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("description", "Chưa đăng nhập");
                return jout.toString();
            }

            String userId = String.valueOf(sst.UserID);
            Long deliveryId = jin.has("delivery_id") ? jin.getLong("delivery_id") : null;

            if (deliveryId == null) {
                jout.put("code", 400);
                jout.put("description", "Thiếu delivery_id");
                return jout.toString();
            }

            boolean ok = notificationExtend.deleteNotification(userId, deliveryId);
            jout.put("code", ok ? 200 : 400);
            jout.put("description", ok ? "Thành công" : "Không thể xóa thông báo");
            return jout.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý: " + e.getMessage());
            return jout.toString();
        }
    }

    @PostMapping("/get-preferences")
    public String getUserPreferences(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("description", "Chưa đăng nhập");
                return jout.toString();
            }

            String userId = String.valueOf(sst.UserID);
            JSONObject prefs = notificationExtend.getUserPreferences(userId);
            return prefs.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý: " + e.getMessage());
            return jout.toString();
        }
    }

    @PostMapping("/save-preferences")
    public String saveUserPreferences(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("description", "Chưa đăng nhập");
                return jout.toString();
            }

            String userId = String.valueOf(sst.UserID);
            boolean ok = notificationExtend.saveUserPreferences(userId, jin);

            jout.put("code", ok ? 200 : 500);
            jout.put("description", ok ? "Lưu cài đặt thông báo cá nhân thành công!" : "Lỗi khi lưu cài đặt");
            return jout.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý: " + e.getMessage());
            return jout.toString();
        }
    }

    /**
     * Test endpoint: Triggers real-time notifications to test Notification Center.
     */
    @PostMapping("/test-trigger")
    public String triggerTestNotification(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);

            if (sst == null) {
                jout.put("code", 700);
                jout.put("description", "Chưa đăng nhập");
                return jout.toString();
            }

            String currentUserId = String.valueOf(sst.UserID);
            String customTitle = jin.has("title") ? jin.getString("title") : "Thông báo thử nghiệm E-IQA";
            String customMsg = jin.has("message") ? jin.getString("message") : "Đây là thông báo thử nghiệm thời gian thực từ hệ thống E-IQA.";
            String targetRole = jin.has("role") ? jin.getString("role") : "ALL";

            Long nId = notificationExtend.dispatchNotification(
                "TEST_EVENT_" + System.currentTimeMillis(),
                jin.has("category") ? jin.getString("category") : "KPI",
                jin.has("severity") ? jin.getString("severity") : "danger",
                customTitle,
                customMsg,
                targetRole,
                null,
                "KPI_DATA",
                "TEST_01",
                "/kpi?code=T1.03",
                currentUserId,
                java.util.Collections.singletonList(currentUserId)
            );

            jout.put("code", 200);
            jout.put("description", "Đã bắn thông báo thử nghiệm thành công!");
            jout.put("notification_id", nId);
            return jout.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi bắn thử nghiệm: " + e.getMessage());
            return jout.toString();
        }
    }
}
