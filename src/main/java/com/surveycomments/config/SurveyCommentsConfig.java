package com.surveycomments.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring beans for the survey-comments module.
 *
 * <p>Defines:
 * <ul>
 *   <li>A bounded {@link ThreadPoolTaskExecutor} for background classification runs.</li>
 * </ul>
 *
 * <p>The Gemini client bean will be added in T3, after dependency approval.
 */
@Configuration
public class SurveyCommentsConfig {

    private static final Logger log = LoggerFactory.getLogger(SurveyCommentsConfig.class);

    @Autowired
    private SurveyCommentsProperties props;

    /**
     * Bounded thread pool for async comment classification runs.
     * Core size = 2, max = 4; keeps run processing from starving other app threads.
     * Named "surveyComments-" for easy identification in thread dumps.
     */
    @Bean(name = "surveyCommentsExecutor")
    public Executor surveyCommentsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("surveyComments-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[survey-comments] Executor initialized: core=2, max=4, queue=50");
        return executor;
    }
}

