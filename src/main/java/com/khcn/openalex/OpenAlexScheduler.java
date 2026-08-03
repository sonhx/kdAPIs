package com.khcn.openalex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OpenAlexScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OpenAlexScheduler.class);

    @Autowired
    private OpenAlexService openAlexService;

    /**
     * Cron schedule to automatically sync PTIT research statistics from OpenAlex API every week.
     * Fires at 02:00 AM every Sunday.
     */
//    @Scheduled(cron = "0 0 2 ? * SUN") //TODO: Uncomment this line to enable the scheduled job
    @EventListener(ApplicationReadyEvent.class)
    public void syncWeeklyOpenAlexStats() {
        logger.info("Executing weekly scheduled job: OpenAlex PTIT Statistics Sync...");
        try {
            // 1. Sync institutional metadata & author stats
            openAlexService.syncOpenAlexData();
            
            
            // 2. Sync new/current year works (Lite version)
            openAlexService.syncOpenAlexWorks(false);
            
            logger.info("Weekly scheduled job for OpenAlex PTIT Statistics completed successfully.");
        } catch (Exception e) {
            logger.error("Error executing weekly scheduled job for OpenAlex PTIT Statistics: {}", e.getMessage(), e);
        }
    }
}
