package com.ptit.qa.ims.scheduler;

import com.ptit.qa.ims.service.CourseCrawlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CourseCrawlerScheduler {

    private static final Logger log = LoggerFactory.getLogger(CourseCrawlerScheduler.class);

    @Autowired
    private CourseCrawlerService courseCrawlerService;

    @Scheduled(cron = "0 0 2 1 * ?")
//    @Scheduled(fixedRate = 300000) // 300,000 ms = 5 phút
    public void runMonthlyCourseCrawler() {
        log.info("[Scheduler] Bắt đầu chạy tiến trình cào dữ liệu chương trình và học phần định kỳ hàng tháng...");
        try {
            courseCrawlerService.crawlAndSaveProgramsAndCourses();
            log.info("[Scheduler] Tiến trình cào dữ liệu định kỳ hoàn thành thành công.");
        } catch (Exception e) {
            log.error("[Scheduler] Có lỗi xảy ra trong quá trình cào dữ liệu định kỳ: {}", e.getMessage(), e);
        }
    }
}
