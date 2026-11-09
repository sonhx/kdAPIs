package com.surveycomments;

import com.surveycomments.classify.CommentClassifier;
import com.surveycomments.clean.CleanService;
import com.surveycomments.config.SurveyCommentsConfig;
import com.surveycomments.config.SurveyCommentsProperties;
import com.surveycomments.ingest.IngestService;
import com.surveycomments.redact.RedactService;
import com.surveycomments.report.ReportService;
import com.surveycomments.run.RunRepository;
import com.surveycomments.run.FileRunRepository;
import com.surveycomments.classify.StubCommentClassifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SurveyCommentsSmokeTest.TestConfig.class)
class SurveyCommentsSmokeTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    })
    @Import({
        SurveyCommentsProperties.class,
        SurveyCommentsConfig.class,
        FileRunRepository.class,
        StubCommentClassifier.class
    })
    static class TestConfig {}

    @Autowired
    private SurveyCommentsProperties props;

    @Autowired
    private SurveyCommentsConfig config;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private CommentClassifier commentClassifier;

    // These are interfaces; implementations added in T2/T4.
    // Null checks here confirm we can selectively autowire without breaking context.

    /**
     * Verifies the Spring context starts and all required beans are present.
     */
    @Test
    void contextLoads() {
        assertNotNull(props,            "SurveyCommentsProperties must be injected");
        assertNotNull(config,           "SurveyCommentsConfig must be injected");
        assertNotNull(runRepository,    "RunRepository must be injected");
        assertNotNull(commentClassifier,"CommentClassifier (stub) must be injected");
    }

    /**
     * Verifies configuration properties are bound with correct defaults.
     * No network access; reads only from application.properties.
     */
    @Test
    void propertiesHaveCorrectDefaults() {
        // Gemini config
        assertNotNull(props.getGemini(), "Gemini config section must not be null");
        assertTrue(props.getGemini().getRequestsPerMinute() > 0,
                "requestsPerMinute must be positive");
        assertTrue(props.getGemini().getMaxRetries() > 0,
                "maxRetries must be positive");

        // Model must NOT be a secret — it is an ID string, not the API key
        // We only verify it is not null; the value comes from GEMINI_MODEL env var
        // which may be blank in CI.
        assertNotNull(props.getGemini().getModel(),
                "model field must not be null (may be empty string if env var not set)");

        // Codebook / prompt versions
        assertFalse(props.getCodebookVersion().isBlank(), "codebookVersion must not be blank");
        assertFalse(props.getPromptVersion().isBlank(),   "promptVersion must not be blank");

        // Storage
        assertNotNull(props.getStorage().getBaseDir(), "storage.baseDir must not be null");
        assertTrue(props.getStorage().getRetentionDays() > 0, "retentionDays must be positive");

        // Upload
        assertTrue(props.getUpload().getMaxFileSizeBytes() > 0, "maxFileSizeBytes must be positive");
    }

    /**
     * Verifies the fake test CSV resource is present on the classpath.
     * The classifier and ingest implementations will read it in T2/T4.
     */
    @Test
    void fakeTestCsvIsOnClasspath() {
        InputStream csv = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/sample_fake_comments.csv");
        assertNotNull(csv, "sample_fake_comments.csv must be present in src/test/resources/survey-comments/");
    }

    /**
     * Verifies schema.json is present on the classpath (used for local validation in T4).
     */
    @Test
    void schemaJsonIsOnClasspath() {
        InputStream schema = getClass().getClassLoader()
                .getResourceAsStream("survey-comments/schema.json");
        assertNotNull(schema, "schema.json must be present in src/main/resources/survey-comments/");
    }

    /**
     * Verifies the stub classifier throws UnsupportedOperationException (not NPE / something else),
     * confirming it is wired correctly without network access.
     */
    @Test
    void stubClassifierThrowsUnsupportedOperation() {
        com.surveycomments.redact.RedactedComment dummy =
                new com.surveycomments.redact.RedactedComment(
                        "test-id", "test-survey", "K12",
                        "[REDACTED TEXT FOR TEST]",
                        0, 0, 0, 0, false, false);

        assertThrows(UnsupportedOperationException.class,
                () -> commentClassifier.classify(dummy),
                "StubCommentClassifier must throw UnsupportedOperationException until T4");
    }
}
