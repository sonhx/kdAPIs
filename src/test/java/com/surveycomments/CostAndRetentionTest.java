package com.surveycomments;

import com.surveycomments.classify.ClassificationResult;
import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.cost.CostReport;
import com.surveycomments.cost.CostServiceImpl;
import com.surveycomments.retention.RetentionServiceImpl;
import com.surveycomments.run.Run;
import com.surveycomments.run.RunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CostServiceImpl} and {@link RetentionServiceImpl} (Task T8).
 */
@ExtendWith(MockitoExtension.class)
class CostAndRetentionTest {

    @Mock
    private RunRepository runRepository;

    @Spy
    private SurveyCommentsProperties props = new SurveyCommentsProperties();

    @InjectMocks
    private CostServiceImpl costService;

    @Test
    void calculateCost_computesCorrectUsdAndVnd() {
        Run run = new Run("run-cost-1", "s1", "SYNC");
        when(runRepository.findById("run-cost-1")).thenReturn(Optional.of(run));

        ClassificationResult r1 = new ClassificationResult(
                "c1", List.of(), "positive", 0.95, false, false,
                "gemini-3.8-flash", "p0", "v0.2-draft", 1_000_000, 500_000);

        when(runRepository.findResultsByRunId("run-cost-1")).thenReturn(List.of(r1));

        props.getPricing().setUsdPerMillionInputTokens(0.15);
        props.getPricing().setUsdPerMillionOutputTokens(0.60);
        props.getPricing().setVndExchangeRate(25_000.0);

        CostReport report = costService.calculateCost("run-cost-1");

        assertNotNull(report);
        assertEquals(1, report.getTotalClassifiedComments());
        assertEquals(1_000_000, report.getTotalInputTokens());
        assertEquals(500_000, report.getTotalOutputTokens());
        assertEquals(1_500_000, report.getTotalTokens());

        // 1M * 0.15 = 0.15; 0.5M * 0.60 = 0.30 -> Total 0.45 USD
        assertEquals(0.45, report.getCostUsd(), 0.0001);
        // 0.45 * 25,000 = 11,250 VND
        assertEquals(11_250L, report.getCostVnd());
    }

    @Test
    void purgeExpiredRuns_deletesOnlyOldFolders(@TempDir Path tempDir) throws IOException {
        SurveyCommentsProperties testProps = new SurveyCommentsProperties();
        testProps.getStorage().setBaseDir(tempDir.toString());
        testProps.getStorage().setRetentionDays(30);

        Path runsDir = tempDir.resolve("runs");
        Files.createDirectories(runsDir);

        Path recentDir = runsDir.resolve("run-recent");
        Files.createDirectories(recentDir);
        Files.writeString(recentDir.resolve("run.json"), "{}");

        Path oldDir = runsDir.resolve("run-old");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("run.json"), "{}");

        // Set old folder last-modified time to 60 days ago
        Instant oldTime = Instant.now().minus(60, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldDir, FileTime.from(oldTime));

        RetentionServiceImpl retentionService = new RetentionServiceImpl(testProps);
        int purged = retentionService.purgeExpiredRuns();

        assertEquals(1, purged, "Should purge exactly 1 expired run");
        assertFalse(Files.exists(oldDir), "Expired directory should be deleted");
        assertTrue(Files.exists(recentDir), "Recent directory must be retained");
    }
}

