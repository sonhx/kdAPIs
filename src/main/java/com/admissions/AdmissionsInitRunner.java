package com.admissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdmissionsInitRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdmissionsInitRunner.class);

    @Autowired
    private AdmissionsScraperService scraperService;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing 2026 Admissions data sync upon application startup...");
        try {
            int count = scraperService.scrapeAndSaveForYear(2026);
            if (count > 0) {
                logger.info("Startup Init: Successfully populated {} records for year 2026.", count);
            } else {
                logger.warn("Startup Init: Could not fetch 2026 admissions data or no records found.");
            }
        } catch (Exception e) {
            logger.error("Startup Init: Error during initial 2026 admissions sync", e);
        }
    }
}
