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
    public SseEmitter subscribe(@RequestParam(value = "session_id", required = false) String sessionId) {
        struct_session sst = sessionService.getSessionInfo(sessionId);
        String userId = (sst != null) ? String.valueOf(sst.UserID) : "1";
        return notificationExtend.subscribeUser(userId);
    }

    private String resolveUserId(JSONObject jin) {
        if (jin.has("userId") && !jin.isNull("userId") && !jin.get("userId").toString().isBlank()) {
            return String.valueOf(jin.get("userId"));
        }
        if (jin.has("user_id") && !jin.isNull("user_id") && !jin.get("user_id").toString().isBlank()) {
            return String.valueOf(jin.get("user_id"));
        }
        if (jin.has("user_name") && !jin.isNull("user_name") && !jin.get("user_name").toString().isBlank()) {
            return String.valueOf(jin.get("user_name"));
        }
        String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
        struct_session sst = sessionService.getSessionInfo(sessionId);
        if (sst != null && sst.UserID > 0) {
            return String.valueOf(sst.UserID);
        }
        return "1";
    }

    @RequestMapping(value = "/list", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/json;charset=UTF-8")
    public String getUserNotifications(
            @RequestBody(required = false) String sReq,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "user_id", required = false) String paramUserId2,
            @RequestParam(value = "session_id", required = false) String paramSessionId,
            @RequestParam(value = "category", required = false) String paramCategory,
            @RequestParam(value = "unread_only", required = false) Boolean paramUnreadOnly,
            @RequestParam(value = "page", required = false) Integer paramPage,
            @RequestParam(value = "page_size", required = false) Integer paramPageSize) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = (sReq != null && !sReq.isBlank()) ? new JSONObject(sReq) : new JSONObject();
            if (paramUserId != null && !paramUserId.isBlank()) jin.put("userId", paramUserId);
            if (paramUserId2 != null && !paramUserId2.isBlank()) jin.put("user_id", paramUserId2);
            if (paramSessionId != null && !paramSessionId.isBlank()) jin.put("session_id", paramSessionId);
            if (paramCategory != null && !paramCategory.isBlank()) jin.put("category", paramCategory);
            if (paramUnreadOnly != null) jin.put("unread_only", paramUnreadOnly);
            if (paramPage != null) jin.put("page", paramPage);
            if (paramPageSize != null) jin.put("page_size", paramPageSize);

            String userId = resolveUserId(jin);
            
            System.out.println("Fetching notifications for userId: " + userId);

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

    @RequestMapping(value = "/unread-count", method = {RequestMethod.GET, RequestMethod.POST}, produces = "application/json;charset=UTF-8")
    public String getUnreadCount(
            @RequestBody(required = false) String sReq,
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestParam(value = "user_id", required = false) String paramUserId2,
            @RequestParam(value = "session_id", required = false) String paramSessionId) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = (sReq != null && !sReq.isBlank()) ? new JSONObject(sReq) : new JSONObject();
            if (paramUserId != null && !paramUserId.isBlank()) jin.put("userId", paramUserId);
            if (paramUserId2 != null && !paramUserId2.isBlank()) jin.put("user_id", paramUserId2);
            if (paramSessionId != null && !paramSessionId.isBlank()) jin.put("session_id", paramSessionId);

            String userId = resolveUserId(jin);

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

    @PostMapping(value = "/mark-read", produces = "application/json;charset=UTF-8")
    public String markAsRead(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String userId = resolveUserId(jin);

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

    @PostMapping(value = "/mark-all-read", produces = "application/json;charset=UTF-8")
    public String markAllAsRead(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String userId = resolveUserId(jin);

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

    @PostMapping(value = "/delete", produces = "application/json;charset=UTF-8")
    public String deleteNotification(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String userId = resolveUserId(jin);

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

    @PostMapping(value = "/get-preferences", produces = "application/json;charset=UTF-8")
    public String getUserPreferences(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String userId = resolveUserId(jin);

            JSONObject prefs = notificationExtend.getUserPreferences(userId);
            return prefs.toString();
        } catch (Exception e) {
            jout.put("code", 500);
            jout.put("description", "Lỗi xử lý: " + e.getMessage());
            return jout.toString();
        }
    }

    @PostMapping(value = "/save-preferences", produces = "application/json;charset=UTF-8")
    public String saveUserPreferences(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String userId = resolveUserId(jin);

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
    @PostMapping(value = "/test-trigger", produces = "application/json;charset=UTF-8")
    public String triggerTestNotification(@RequestBody String sReq) {
        JSONObject jout = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.has("session_id") ? jin.getString("session_id") : null;
            struct_session sst = sessionService.getSessionInfo(sessionId);
            String currentUserId = (sst != null) ? String.valueOf(sst.UserID) : "1";

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
                "/dashboard",
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
