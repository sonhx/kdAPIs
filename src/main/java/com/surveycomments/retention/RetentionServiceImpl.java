package com.surveycomments.retention;

import com.surveycomments.config.SurveyCommentsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * Service enforcing data retention policies (PLAN.md §6).
 *
 * <p>Cleans up files and run folders older than {@code survey-comments.storage.retention-days}
 * to prevent indefinite retention of student survey data.
 */
@Service
public class RetentionServiceImpl implements RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionServiceImpl.class);

    @Autowired
    private SurveyCommentsProperties props;

    public RetentionServiceImpl() {}

    public RetentionServiceImpl(SurveyCommentsProperties props) {
        this.props = props;
    }

    /**
     * Daily maintenance task running at 02:00 server time.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledPurge() {
        log.info("[survey-comments] Running scheduled retention purge...");
        int count = purgeExpiredRuns();
        log.info("[survey-comments] Scheduled retention purge completed. Purged {} expired runs.", count);
    }

    @Override
    public int purgeExpiredRuns() {
        int retentionDays = props.getStorage().getRetentionDays();
        if (retentionDays <= 0) {
            log.info("[survey-comments] Retention days set to {} (disabled)", retentionDays);
            return 0;
        }

        String baseDirStr = props.getStorage().getBaseDir();
        File runsDir = new File(baseDirStr, "runs");
        if (!runsDir.exists() || !runsDir.isDirectory()) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        File[] runFolders = runsDir.listFiles(File::isDirectory);
        if (runFolders == null) return 0;

        int purgedCount = 0;
        for (File folder : runFolders) {
            try {
                Path folderPath = folder.toPath();
                BasicFileAttributes attrs = Files.readAttributes(folderPath, BasicFileAttributes.class);
                Instant lastModified = attrs.lastModifiedTime().toInstant();

                if (lastModified.isBefore(cutoff)) {
                    log.info("[survey-comments] Purging expired run directory: {} (modified {})", folder.getName(), lastModified);
                    deleteRecursively(folderPath);
                    purgedCount++;
                }
            } catch (Exception e) {
                log.warn("[survey-comments] Error checking/purging folder {}: {}", folder.getName(), e.getMessage());
            }
        }

        return purgedCount;
    }

    private void deleteRecursively(Path root) throws IOException {
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}

