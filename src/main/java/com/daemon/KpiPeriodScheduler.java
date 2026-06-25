package com.daemon;

import com.daemon.KpiAutomationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
public class KpiPeriodScheduler {

    private static final Logger logger = LoggerFactory.getLogger(KpiPeriodScheduler.class);

    @Autowired
    private KpiAutomationService kpiAutomationService;

    // Cron expression: Giây Phút Giờ Ngày Tháng Thứ
    // "0 5 0 1 * *" nghĩa là: 00:05:00 ngày 1 của tất cả các tháng
    @Scheduled(cron = "0 5 0 1 * *")
    public void autoGeneratePeriodsAndDataPoints() {
        int currentYear = LocalDate.now().getYear();
        logger.info("--- BẮT ĐẦU CHẠY CRON JOB SINH CHU KỲ KPI CHO NĂM {} ---", currentYear);
        
        try {
            kpiAutomationService.runPeriodAutomation(currentYear);
            logger.info("--- CHẠY CRON JOB KPI THÀNH CÔNG ---");
        } catch (Exception e) {
            logger.error("LỖI KHI THỰC THI CRON JOB KPI: ", e);
            // Bạn có thể bổ sung thêm logic gửi mail cảnh báo cho Admin hệ thống ở đây nếu lỗi
        }
    }
}