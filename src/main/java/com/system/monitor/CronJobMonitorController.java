package com.system.monitor;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.email.SendMailTLS;
import com.session.SessionService;
import com.session.struct_session;

/**
 * CronJobMonitorController
 *
 * REST API endpoints for administrators to:
 * 1. View & configure alert email recipients when cronjobs fail.
 * 2. View execution history and failure logs of background sync jobs.
 * 3. Proactively test connectivity to the S-Link API gateway.
 * 4. Send a test alert email.
 */
@RestController
@RequestMapping("/system/cron-monitor")
public class CronJobMonitorController {

    @Autowired
    private CronJobMonitorService monitorService;

    @Autowired
    private SessionService sessionService;

    /**
     * GET /system/cron-monitor/config
     * Returns email alert settings.
     */
    @GetMapping("/config")
    public String getConfig(@RequestParam(value = "session_id", required = false) String sessionId) {
        JSONObject res = new JSONObject();
        try {
            String recipients = monitorService.getConfigValue("cron_alert_email_recipients", "admin@ptit.edu.vn,sonhx@ptit.edu.vn");
            String enabledStr = monitorService.getConfigValue("cron_alert_email_enabled", "true");

            res.put("code", 200);
            res.put("recipients", recipients);
            res.put("enabled", "true".equalsIgnoreCase(enabledStr));
        } catch (Exception e) {
            res.put("code", 500);
            res.put("error", e.getMessage());
        }
        return res.toString();
    }

    /**
     * POST /system/cron-monitor/config
     * Updates email alert settings.
     */
    @PostMapping("/config")
    public String updateConfig(@RequestBody String sReq) {
        JSONObject res = new JSONObject();
        try {
            JSONObject jin = new JSONObject(sReq);
            String sessionId = jin.optString("session_id", null);

            // Verify admin privilege
            struct_session sst = sessionService.getSessionInfo(sessionId);
            if (sst != null && sst.UserType != 2 && !isAuthorizedAdmin(sst)) {
                res.put("code", 403);
                res.put("description", "Không đủ quyền quản trị để thay đổi cấu hình cảnh báo");
                return res.toString();
            }

            if (jin.has("recipients")) {
                monitorService.setConfigValue(
                    "cron_alert_email_recipients",
                    jin.getString("recipients").trim(),
                    "Danh sách email nhận cảnh báo khi cronjob đồng bộ thất bại"
                );
            }

            if (jin.has("enabled")) {
                monitorService.setConfigValue(
                    "cron_alert_email_enabled",
                    String.valueOf(jin.getBoolean("enabled")),
                    "Bật/Tắt tính năng gửi email cảnh báo khi lỗi kết nối"
                );
            }

            res.put("code", 200);
            res.put("description", "Cập nhật cấu hình email cảnh báo thành công");
            res.put("recipients", monitorService.getConfigValue("cron_alert_email_recipients", ""));
            res.put("enabled", "true".equalsIgnoreCase(monitorService.getConfigValue("cron_alert_email_enabled", "true")));
        } catch (Exception e) {
            res.put("code", 500);
            res.put("error", e.getMessage());
        }
        return res.toString();
    }

    /**
     * GET /system/cron-monitor/logs
     * Returns recent background job runs.
     */
    @GetMapping("/logs")
    public String getLogs(@RequestParam(value = "limit", defaultValue = "30") int limit) {
        JSONObject res = new JSONObject();
        try {
            JSONArray logs = monitorService.getRecentJobLogs(limit);
            res.put("code", 200);
            res.put("data", logs);
            res.put("total", logs.length());
        } catch (Exception e) {
            res.put("code", 500);
            res.put("error", e.getMessage());
        }
        return res.toString();
    }

    /**
     * GET /system/cron-monitor/health
     * Proactively tests connection to the external S-Link API Gateway.
     */
    @GetMapping("/health")
    public String checkHealth() {
        JSONObject res = new JSONObject();
        try {
            JSONObject slinkHealth = monitorService.checkSLinkHealth();
            res.put("code", 200);
            res.put("slink", slinkHealth);
        } catch (Exception e) {
            res.put("code", 500);
            res.put("error", e.getMessage());
        }
        return res.toString();
    }

    /**
     * POST /system/cron-monitor/test-email
     * Sends a test alert email to the configured recipients or a specified target email.
     */
    @PostMapping("/test-email")
    public String sendTestEmail(@RequestBody(required = false) String sReq) {
        JSONObject res = new JSONObject();
        try {
            String targetEmail = null;
            if (sReq != null && !sReq.isBlank()) {
                try {
                    JSONObject jin = new JSONObject(sReq);
                    targetEmail = jin.optString("email", null);
                } catch (Exception ignored) {}
            }

            if (targetEmail == null || targetEmail.isBlank()) {
                String recipients = monitorService.getConfigValue("cron_alert_email_recipients", "");
                if (recipients != null && !recipients.isBlank()) {
                    targetEmail = recipients.split("[,;]")[0].trim();
                }
            }

            if (targetEmail == null || targetEmail.isBlank() || !targetEmail.contains("@")) {
                res.put("code", 400);
                res.put("description", "Không tìm thấy địa chỉ email hợp lệ để gửi thử");
                return res.toString();
            }

            SendMailTLS mailSender = new SendMailTLS();
            String subject = "[TEST QA-IMS] Thử nghiệm gửi email cảnh báo hệ thống";
            String body =
                "Chào Quản trị viên,\r\n\r\n" +
                "Đây là email thử nghiệm từ tính năng giám sát kết nối hệ thống QA-IMS.\r\n" +
                "Hệ thống gửi thư cảnh báo tự động hoạt động bình thường.\r\n\r\n" +
                "Thời gian gửi: " + new java.util.Date() + "\r\n\r\n" +
                "--\r\nBan ĐBCL & TTKT Học viện Công nghệ Bưu chính Viễn thông";

            String sendErr = mailSender.tlsmain(targetEmail, subject, body);
            if (sendErr == null) {
                res.put("code", 200);
                res.put("description", "Đã gửi email thử nghiệm thành công tới: " + targetEmail);
            } else {
                res.put("code", 500);
                res.put("description", "Gửi email thất bại: " + sendErr);
            }
        } catch (Exception e) {
            res.put("code", 500);
            res.put("error", e.getMessage());
        }
        return res.toString();
    }

    private boolean isAuthorizedAdmin(struct_session sst) {
        if (sst == null) return false;
        return sst.UserType == 2 || sst.UserType == 1;
    }
}
