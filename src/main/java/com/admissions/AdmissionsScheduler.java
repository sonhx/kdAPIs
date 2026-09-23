package com.admissions;

import java.time.Year;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AdmissionsScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AdmissionsScheduler.class);

    @Autowired
    private AdmissionsScraperService scraperService;

    // Retry counter for current year
    private volatile int currentRetryCount = 0;
    private volatile boolean currentYearSynced = false;

    /**
     * Primary Cron Job: Triggers yearly on June 1st at 01:00 AM.
     * Cron expression: "0 0 1 1 6 ?"
     */
    @Scheduled(cron = "0 0 1 1 6 ?")
    public void scheduleJune1stSync() {
        int year = Year.now().getValue();
        logger.info("Executing scheduled annual PTIT Admissions Sync for year {} on June 1st.", year);
        currentRetryCount = 0;
        currentYearSynced = false;

        executeSyncWithRetry(year);
    }

    /**
     * Weekly Retry Schedule for June (June 8th, 15th, 22nd at 01:00 AM)
     * If June 1st attempt had no data, this scheduled job retries up to 3 times.
     */
    @Scheduled(cron = "0 0 1 8,15,22 6 ?")
    public void scheduleWeeklyRetryInJune() {
        int year = Year.now().getValue();
        if (currentYearSynced) {
            logger.info("Admissions data for year {} is already successfully synced. Skipping weekly retry.", year);
            return;
        }

        currentRetryCount++;
        logger.warn("Retrying PTIT Admissions Sync for year {} (Attempt {} of 3)...", year, currentRetryCount);
        executeSyncWithRetry(year);
    }

    /**
     * Executes the scraper and handles warning alerts if data remains missing.
     */
    public boolean executeSyncWithRetry(int year) {
        int count = scraperService.scrapeAndSaveForYear(year);

        if (count > 0) {
            currentYearSynced = true;
            logger.info("Successfully fetched and saved {} admissions records for year {}.", count, year);
            return true;
        } else {
            currentYearSynced = false;
            logger.warn("No admissions data found for year {} at attempt {}.", year, currentRetryCount);

            if (currentRetryCount >= 3) {
                logger.error("==========================================================================");
                logger.error("WARNING: PTIT Admissions data for year {} could not be fetched after 3 weekly retries (June 1st, 8th, 15th, 22nd).", year);
                logger.error("Please verify that the admissions announcement has been published at: https://tuyensinh.ptit.edu.vn/de-an-tuyen-sinh/thong-tin-tuyen-sinh-dai-hoc-chinh-quy-nam-{}/", year);
                logger.error("==========================================================================");
            }
            return false;
        }
    }
}
