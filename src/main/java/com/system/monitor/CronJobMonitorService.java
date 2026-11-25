package com.system.monitor;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.email.SendMailTLS;
import com.notification.NotificationExtend;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * CronJobMonitorService
 *
 * Centralized service to monitor scheduled background updates/cronjobs,
 * automatically detecting connection loss to external systems (e.g. S-Link),
 * managing retries with exponential backoff (2m, 5m, 15m), persisting execution logs,
 * and dispatching warning alerts via both In-App Notifications (SSE) and Email to Administrators.
 */
@Service
public class CronJobMonitorService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private NotificationExtend notificationExtend;

    @Value("${slink.api-key:}")
    private String slinkApiKey;

    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2);
    private final ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    // Retry delays in minutes: 2 min, 5 min, 15 min
    private static final long[] RETRY_DELAYS_MINUTES = { 2L, 5L, 15L };

    @FunctionalInterface
    public interface JobRunnable {
        void run() throws Exception;
    }

    @PostConstruct
    public void init() {
        try {
            // 1. Create sys_cron_job_logs table
            String createLogsSql =
                "IF OBJECT_ID('dbo.sys_cron_job_logs', 'U') IS NULL " +
                "BEGIN " +
                "    CREATE TABLE dbo.sys_cron_job_logs ( " +
                "        id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                "        job_name VARCHAR(100) NOT NULL, " +
                "        job_description NVARCHAR(255) NULL, " +
                "        target_system VARCHAR(50) NOT NULL, " +
                "        start_time DATETIME NOT NULL, " +
                "        end_time DATETIME NULL, " +
                "        duration_ms BIGINT NULL, " +
                "        status VARCHAR(20) NOT NULL, " + // RUNNING, SUCCESS, RETRYING, FAILED
                "        is_connection_error BIT DEFAULT 0, " +
                "        error_type VARCHAR(100) NULL, " +
                "        error_message NVARCHAR(2000) NULL, " +
                "        retry_count INT DEFAULT 0, " +
                "        created_at DATETIME DEFAULT GETDATE() " +
                "    ); " +
                "    CREATE INDEX IX_cron_job_logs ON dbo.sys_cron_job_logs(job_name, status, created_at); " +
                "END";
            jdbcTemplate.execute(createLogsSql);

            // 2. Create sys_config table for admin configurations
            String createConfigSql =
                "IF OBJECT_ID('dbo.sys_config', 'U') IS NULL " +
                "BEGIN " +
                "    CREATE TABLE dbo.sys_config ( " +
                "        config_key VARCHAR(100) PRIMARY KEY, " +
                "        config_value NVARCHAR(MAX) NULL, " +
                "        description NVARCHAR(255) NULL, " +
                "        updated_at DATETIME DEFAULT GETDATE() " +
                "    ); " +
                "END";
            jdbcTemplate.execute(createConfigSql);

            // 3. Initialize default alert email configuration if not present
            ensureDefaultConfig("cron_alert_email_recipients", "admin@ptit.edu.vn,sonhx@ptit.edu.vn", "Danh sách email nhận cảnh báo khi cronjob đồng bộ thất bại");
            ensureDefaultConfig("cron_alert_email_enabled", "true", "Bật/Tắt tính năng gửi email cảnh báo khi lỗi kết nối");

            System.out.println("CronJobMonitorService initialized successfully with log tables and alert configs.");
        } catch (Exception e) {
            System.err.println("CronJobMonitorService init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void destroy() {
        retryExecutor.shutdown();
        emailExecutor.shutdown();
    }

    private void ensureDefaultConfig(String key, String defaultValue, String desc) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.sys_config WHERE config_key = ?", Integer.class, key
            );
            if (count == null || count == 0) {
                jdbcTemplate.update(
                    "INSERT INTO dbo.sys_config (config_key, config_value, description, updated_at) VALUES (?, ?, ?, GETDATE())",
                    key, defaultValue, desc
                );
            }
        } catch (Exception ignored) {}
    }

    /**
     * Executes a scheduled cron job with connection monitoring and automatic retry.
     */
    public void executeMonitoredJob(String jobName, String jobDescription, String targetSystem, JobRunnable action) {
        long startTime = System.currentTimeMillis();
        Long logId = createJobLog(jobName, jobDescription, targetSystem, startTime);

        try {
            System.out.println("[CronJobMonitor] Running job: " + jobName + " (" + jobDescription + ")");
            action.run();

            long duration = System.currentTimeMillis() - startTime;
            updateJobSuccess(logId, duration);
            System.out.println("[CronJobMonitor] Job " + jobName + " completed successfully in " + duration + "ms.");
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            boolean isConnErr = isConnectionError(e);
            String errType = resolveErrorType(e, isConnErr);
            String errMsg = extractErrorMessage(e);

            System.err.println("[CronJobMonitor] Job " + jobName + " failed. ConnErr: " + isConnErr + ", Type: " + errType + ", Msg: " + errMsg);

            if (isConnErr) {
                // Schedule retry with exponential backoff
                scheduleRetry(logId, jobName, jobDescription, targetSystem, action, 1, e);
            } else {
                // Non-connection fatal failure (logic / syntax / bad data)
                updateJobFailure(logId, duration, false, errType, errMsg, 0);
                triggerAlerts(jobName, jobDescription, targetSystem, false, errType, errMsg, 0);
            }
        }
    }

    /**
     * Handles retry execution at 2 min, 5 min, 15 min.
     */
    private void scheduleRetry(Long logId, String jobName, String jobDescription, String targetSystem,
                               JobRunnable action, int retryAttempt, Exception lastException) {
        if (retryAttempt > RETRY_DELAYS_MINUTES.length) {
            // All retries exhausted! Mark as final hard failure and send warning notifications
            String errMsg = extractErrorMessage(lastException);
            String errType = resolveErrorType(lastException, true);
            updateJobFailure(logId, 0, true, errType, "Đã thử lại 3 lần thất bại: " + errMsg, retryAttempt - 1);
            triggerAlerts(jobName, jobDescription, targetSystem, true, errType, errMsg, retryAttempt - 1);
            return;
        }

        long delayMinutes = RETRY_DELAYS_MINUTES[retryAttempt - 1];
        System.out.println("[CronJobMonitor] Scheduling retry #" + retryAttempt + " for job " + jobName + " in " + delayMinutes + " minutes...");
        updateJobRetrying(logId, retryAttempt, "Đang chờ thử lại lần " + retryAttempt + " (sau " + delayMinutes + " phút)...");

        retryExecutor.schedule(() -> {
            System.out.println("[CronJobMonitor] Executing retry #" + retryAttempt + " for job " + jobName);
            try {
                action.run();
                // If succeeded on retry:
                updateJobSuccess(logId, 0);
                System.out.println("[CronJobMonitor] Job " + jobName + " succeeded on retry #" + retryAttempt + "!");
            } catch (Exception retryEx) {
                System.err.println("[CronJobMonitor] Retry #" + retryAttempt + " for job " + jobName + " failed: " + retryEx.getMessage());
                scheduleRetry(logId, jobName, jobDescription, targetSystem, action, retryAttempt + 1, retryEx);
            }
        }, delayMinutes, TimeUnit.MINUTES);
    }

    /**
     * Determines whether a given exception is caused by connection / network failure.
     */
    public static boolean isConnectionError(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof ConnectException ||
                curr instanceof SocketTimeoutException ||
                curr instanceof UnknownHostException ||
                curr instanceof NoRouteToHostException ||
                curr instanceof ResourceAccessException) {
                return true;
            }
            if (curr instanceof HttpStatusCodeException) {
                HttpStatusCode status = ((HttpStatusCodeException) curr).getStatusCode();
                if (status.is5xxServerError() || status.value() == 408 || status.value() == 429) {
                    return true;
                }
            }
            String msg = curr.getMessage() != null ? curr.getMessage().toLowerCase() : "";
            if (msg.contains("connect timed out") ||
                msg.contains("read timed out") ||
                msg.contains("connection refused") ||
                msg.contains("network is unreachable") ||
                msg.contains("remote host closed connection") ||
                msg.contains("server returned http response code: 502") ||
                msg.contains("server returned http response code: 503") ||
                msg.contains("server returned http response code: 504") ||
                msg.contains("failed to respond") ||
                msg.contains("sslhandshakeexception")) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    private String resolveErrorType(Throwable t, boolean isConnErr) {
        if (!isConnErr) return "BUSINESS_LOGIC_ERROR";
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof SocketTimeoutException) return "CONNECTION_TIMEOUT";
            if (curr instanceof UnknownHostException) return "DNS_RESOLUTION_FAILURE";
            if (curr instanceof ConnectException) return "CONNECTION_REFUSED";
            if (curr instanceof HttpStatusCodeException) {
                return "HTTP_SERVER_ERROR_" + ((HttpStatusCodeException) curr).getStatusCode().value();
            }
            curr = curr.getCause();
        }
        return "NETWORK_UNREACHABLE";
    }

    private String extractErrorMessage(Throwable t) {
        if (t == null) return "Unknown error";
        String msg = t.getMessage();
        if (msg == null && t.getCause() != null) {
            msg = t.getCause().getMessage();
        }
        return (msg != null && !msg.isBlank()) ? msg : t.getClass().getSimpleName();
    }

    // --- Database Log Operations ---

    private Long createJobLog(String jobName, String jobDescription, String targetSystem, long startTime) {
        try {
            String sql =
                "INSERT INTO dbo.sys_cron_job_logs (job_name, job_description, target_system, start_time, status, created_at) " +
                "VALUES (?, ?, ?, ?, 'RUNNING', GETDATE())";
            jdbcTemplate.update(sql, jobName, jobDescription, targetSystem, new Timestamp(startTime));
            return jdbcTemplate.queryForObject("SELECT SCOPE_IDENTITY()", Long.class);
        } catch (Exception e) {
            System.err.println("Failed to insert sys_cron_job_logs: " + e.getMessage());
            return null;
        }
    }

    private void updateJobSuccess(Long logId, long duration) {
        if (logId == null) return;
        try {
            String sql =
                "UPDATE dbo.sys_cron_job_logs " +
                "SET end_time = GETDATE(), duration_ms = ?, status = 'SUCCESS', is_connection_error = 0, error_message = NULL " +
                "WHERE id = ?";
            jdbcTemplate.update(sql, duration, logId);
        } catch (Exception ignored) {}
    }

    private void updateJobRetrying(Long logId, int retryAttempt, String note) {
        if (logId == null) return;
        try {
            String sql =
                "UPDATE dbo.sys_cron_job_logs " +
                "SET status = 'RETRYING', retry_count = ?, error_message = ? " +
                "WHERE id = ?";
            jdbcTemplate.update(sql, retryAttempt, note, logId);
        } catch (Exception ignored) {}
    }

    private void updateJobFailure(Long logId, long duration, boolean isConnErr, String errType, String errMsg, int retryCount) {
        if (logId == null) return;
        try {
            String trimmedMsg = errMsg != null && errMsg.length() > 1950 ? errMsg.substring(0, 1950) : errMsg;
            String sql =
                "UPDATE dbo.sys_cron_job_logs " +
                "SET end_time = GETDATE(), duration_ms = ?, status = 'FAILED', is_connection_error = ?, " +
                "    error_type = ?, error_message = ?, retry_count = ? " +
                "WHERE id = ?";
            jdbcTemplate.update(sql, duration, isConnErr ? 1 : 0, errType, trimmedMsg, retryCount, logId);
        } catch (Exception ignored) {}
    }

    // --- Alert Dispatching: In-App & Email ---

    private void triggerAlerts(String jobName, String jobDescription, String targetSystem,
                               boolean isConnErr, String errType, String errMsg, int retryCount) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestampStr = sdf.format(new Date());

        String title = isConnErr
            ? "Cảnh báo: Lỗi kết nối khi đồng bộ " + (jobDescription != null ? jobDescription : jobName)
            : "Lỗi thực thi cập nhật: " + (jobDescription != null ? jobDescription : jobName);

        String message = String.format(
            "Tiến trình: %s (%s)%n" +
            "Hệ thống đích: %s%n" +
            "Thời điểm: %s%n" +
            "Trạng thái: THẤT BẠI (Sau %d lần thử lại)%n" +
            "Loại lỗi: %s%n" +
            "Chi tiết: %s",
            jobName, jobDescription, targetSystem, timestampStr, retryCount, errType, errMsg
        );

        // 1. In-App Notification to Admins via SSE
        if (notificationExtend != null) {
            try {
                notificationExtend.dispatchNotification(
                    "CRON_SYNC_FAILED",
                    "SYSTEM_SYNC",
                    "danger",
                    title,
                    message,
                    "Admin",
                    null,
                    "CRON_JOB",
                    jobName,
                    "/settings/sync-history",
                    "SYSTEM",
                    null
                );
            } catch (Exception e) {
                System.err.println("Could not dispatch in-app notification: " + e.getMessage());
            }
        }

        // 2. Email Notification to Configured Administrators (Async)
        emailExecutor.submit(() -> {
            sendAlertEmail(title, message, jobName, jobDescription, targetSystem, timestampStr, errType, errMsg, retryCount);
        });
    }

    private void sendAlertEmail(String subject, String textBody, String jobName, String jobDesc,
                                String targetSystem, String timestampStr, String errType, String errMsg, int retryCount) {
        try {
            String enabledStr = getConfigValue("cron_alert_email_enabled", "true");
            if (!"true".equalsIgnoreCase(enabledStr)) {
                System.out.println("[CronJobMonitor] Email alerts are disabled in sys_config (cron_alert_email_enabled=false).");
                return;
            }

            String recipients = getConfigValue("cron_alert_email_recipients", "");
            if (recipients == null || recipients.isBlank()) {
                System.out.println("[CronJobMonitor] No recipient emails configured in sys_config (cron_alert_email_recipients).");
                return;
            }

            String[] emailList = recipients.split("[,;]");
            SendMailTLS mailSender = new SendMailTLS();

            String emailContent =
                "Kính gửi Quản trị viên hệ thống QA-IMS,\r\n\r\n" +
                "Hệ thống giám sát định kỳ phát hiện tiến trình đồng bộ dữ liệu tự động bị gián đoạn do mất kết nối mạng:\r\n\r\n" +
                "  • Tên tiến trình: " + jobName + " (" + jobDesc + ")\r\n" +
                "  • Hệ thống đích: " + targetSystem + "\r\n" +
                "  • Thời điểm ghi nhận: " + timestampStr + "\r\n" +
                "  • Số lần tự động thử lại: " + retryCount + " lần (sau 2m, 5m, 15m) đều không thành công\r\n" +
                "  • Phân loại lỗi: " + errType + "\r\n" +
                "  • Chi tiết lỗi: " + errMsg + "\r\n\r\n" +
                "HƯỚNG XỬ LÝ ĐỀ XUẤT:\r\n" +
                "1. Kiểm tra lại kết nối mạng Internet của máy chủ và cổng kết nối đến hệ thống S-Link (" + targetSystem + ").\r\n" +
                "2. Sau khi đường truyền mạng ổn định, Quản trị viên có thể vào màn hình quản trị hệ thống để kích hoạt đồng bộ bù dữ liệu thủ công.\r\n\r\n" +
                "---\r\n" +
                "Ban Đảm bảo Chất lượng & Trung tâm Khảo thí - Học viện CNBCVT\r\n" +
                "Hệ thống Quản lý ĐBCL QA-IMS (Cảnh báo tự động)";

            for (String email : emailList) {
                String to = email.trim();
                if (!to.isEmpty() && to.contains("@")) {
                    System.out.println("[CronJobMonitor] Sending failure alert email to: " + to);
                    mailSender.tlsmain(to, "[CẢNH BÁO QA-IMS] " + subject, emailContent);
                }
            }
        } catch (Exception e) {
            System.err.println("[CronJobMonitor] Error sending failure alert email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Active External Probing (S-Link Gateway) ---

    /**
     * Proactively checks whether the S-Link gateway is reachable.
     */
    public JSONObject checkSLinkHealth() {
        JSONObject res = new JSONObject();
        long start = System.currentTimeMillis();

        try {
            String cleanApiKey = slinkApiKey != null ? slinkApiKey.replace("\"", "").trim() : "";
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(5000);

            RestTemplate rest = new RestTemplate(factory);
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", cleanApiKey);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String probeUrl = "https://gw.aisoftech.vn/ptit/slink/internal/khao-sat/page?page=1&limit=1";
            ResponseEntity<String> response = rest.exchange(probeUrl, HttpMethod.GET, entity, String.class);

            long latency = System.currentTimeMillis() - start;
            res.put("status", response.getStatusCode().is2xxSuccessful() ? "UP" : "DEGRADED");
            res.put("httpStatus", response.getStatusCode().value());
            res.put("latencyMs", latency);
            res.put("target", "gw.aisoftech.vn");
            res.put("message", "S-Link API Gateway is reachable");
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            res.put("status", "DOWN");
            res.put("latencyMs", latency);
            res.put("target", "gw.aisoftech.vn");
            res.put("errorType", resolveErrorType(e, isConnectionError(e)));
            res.put("message", extractErrorMessage(e));
        }

        return res;
    }

    // --- Config & Log Query Helper Methods ---

    public String getConfigValue(String key, String defaultValue) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT config_value FROM dbo.sys_config WHERE config_key = ?",
                String.class, key
            );
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public void setConfigValue(String key, String value, String desc) {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE dbo.sys_config SET config_value = ?, updated_at = GETDATE() WHERE config_key = ?",
                value, key
            );
            if (updated == 0) {
                jdbcTemplate.update(
                    "INSERT INTO dbo.sys_config (config_key, config_value, description, updated_at) VALUES (?, ?, ?, GETDATE())",
                    key, value, desc
                );
            }
        } catch (Exception e) {
            System.err.println("Error saving sys_config key " + key + ": " + e.getMessage());
        }
    }

    public JSONArray getRecentJobLogs(int limit) {
        JSONArray arr = new JSONArray();
        try {
            int max = Math.min(Math.max(limit, 1), 100);
            String sql =
                "SELECT TOP (?) id, job_name, job_description, target_system, start_time, end_time, " +
                "       duration_ms, status, is_connection_error, error_type, error_message, retry_count, created_at " +
                "FROM dbo.sys_cron_job_logs " +
                "ORDER BY id DESC";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, max);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (Map<String, Object> r : rows) {
                JSONObject obj = new JSONObject();
                obj.put("id", r.get("id"));
                obj.put("jobName", r.get("job_name"));
                obj.put("jobDescription", r.get("job_description"));
                obj.put("targetSystem", r.get("target_system"));
                obj.put("startTime", r.get("start_time") != null ? sdf.format((Date) r.get("start_time")) : null);
                obj.put("endTime", r.get("end_time") != null ? sdf.format((Date) r.get("end_time")) : null);
                obj.put("durationMs", r.get("duration_ms"));
                obj.put("status", r.get("status"));
                obj.put("isConnectionError", Boolean.TRUE.equals(r.get("is_connection_error")));
                obj.put("errorType", r.get("error_type"));
                obj.put("errorMessage", r.get("error_message"));
                obj.put("retryCount", r.get("retry_count"));
                arr.put(obj);
            }
        } catch (Exception e) {
            System.err.println("Error fetching sys_cron_job_logs: " + e.getMessage());
        }
        return arr;
    }
}

